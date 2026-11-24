// Motor de áudio do SF2 Player: até 8 instrumentos SoundFont, como canais de uma mesa de som.
// Cadeia de cada canal: SF2 (TinySoundFont) -> cutoff -> EQ 3 bandas -> compressor -> fader -> mix.
// Oboe entrega o áudio com baixa latência.

#define TSF_IMPLEMENTATION
#include "tsf.h"

#include <jni.h>
#include <oboe/Oboe.h>
#include <android/log.h>
#include <atomic>
#include <cmath>
#include <mutex>
#include <vector>
#include <cstring>
#include <algorithm>
#include <memory>

#define LOG_TAG "SF2Engine"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

static constexpr int kSlots = 8;
static constexpr int kMaxVoices = 96;
static constexpr int kChunk = 1024;   // quadros processados por vez
static constexpr float kPi = 3.14159265358979f;

// ------------------------------------------------------------------ filtros

/** Biquad estéreo (fórmulas RBJ), forma direta transposta II. */
struct Biquad {
    float b0 = 1, b1 = 0, b2 = 0, a1 = 0, a2 = 0;
    float z1[2] = {0, 0}, z2[2] = {0, 0};
    bool active = false;

    void set(float nb0, float nb1, float nb2, float a0, float na1, float na2) {
        b0 = nb0 / a0; b1 = nb1 / a0; b2 = nb2 / a0; a1 = na1 / a0; a2 = na2 / a0;
    }
    void lowShelf(float sr, float f, float db) {
        active = std::fabs(db) > 0.05f; if (!active) return;
        float A = std::pow(10.f, db / 40.f), w = 2 * kPi * f / sr, c = std::cos(w), s = std::sin(w);
        float al = s / 2 * std::sqrt(2.f), sa = 2 * std::sqrt(A) * al;
        set(A * ((A + 1) - (A - 1) * c + sa), 2 * A * ((A - 1) - (A + 1) * c), A * ((A + 1) - (A - 1) * c - sa),
            (A + 1) + (A - 1) * c + sa, -2 * ((A - 1) + (A + 1) * c), (A + 1) + (A - 1) * c - sa);
    }
    void highShelf(float sr, float f, float db) {
        active = std::fabs(db) > 0.05f; if (!active) return;
        float A = std::pow(10.f, db / 40.f), w = 2 * kPi * f / sr, c = std::cos(w), s = std::sin(w);
        float al = s / 2 * std::sqrt(2.f), sa = 2 * std::sqrt(A) * al;
        set(A * ((A + 1) + (A - 1) * c + sa), -2 * A * ((A - 1) + (A + 1) * c), A * ((A + 1) + (A - 1) * c - sa),
            (A + 1) - (A - 1) * c + sa, 2 * ((A - 1) - (A + 1) * c), (A + 1) - (A - 1) * c - sa);
    }
    void peak(float sr, float f, float q, float db) {
        active = std::fabs(db) > 0.05f; if (!active) return;
        float A = std::pow(10.f, db / 40.f), w = 2 * kPi * f / sr, c = std::cos(w), al = std::sin(w) / (2 * q);
        set(1 + al * A, -2 * c, 1 - al * A, 1 + al / A, -2 * c, 1 - al / A);
    }
    void lowPass(float sr, float f, float q) {
        active = f < sr * 0.45f && f < 19500.f; if (!active) return;
        float w = 2 * kPi * f / sr, c = std::cos(w), al = std::sin(w) / (2 * q);
        set((1 - c) / 2, 1 - c, (1 - c) / 2, 1 + al, -2 * c, 1 - al);
    }
    void process(float* buf, int frames) {
        if (!active) return;
        for (int i = 0; i < frames; ++i) {
            for (int ch = 0; ch < 2; ++ch) {
                float x = buf[i * 2 + ch];
                float y = b0 * x + z1[ch];
                z1[ch] = b1 * x - a1 * y + z2[ch];
                z2[ch] = b2 * x - a2 * y;
                buf[i * 2 + ch] = y;
            }
        }
    }
    void reset() { z1[0] = z1[1] = z2[0] = z2[1] = 0; }
};

/** Compressor de um botão só: quanto mais "amount", menor o limiar e maior a razão. */
struct Compressor {
    float amount = 0, thresholdDb = 0, ratio = 1, makeup = 1, env = 0, attack = 0, release = 0;
    void set(float sr, float a) {
        amount = a;
        thresholdDb = -a * 30.f;
        ratio = 1.f + a * 7.f;
        makeup = std::pow(10.f, (-thresholdDb * (1.f - 1.f / ratio) * 0.55f) / 20.f);
        attack = std::exp(-1.f / (0.004f * sr));
        release = std::exp(-1.f / (0.15f * sr));
    }
    void process(float* buf, int frames) {
        if (amount < 0.01f) return;
        for (int i = 0; i < frames; ++i) {
            float l = buf[i * 2], r = buf[i * 2 + 1];
            float lvl = std::max(std::fabs(l), std::fabs(r));
            env = lvl > env ? attack * env + (1 - attack) * lvl : release * env + (1 - release) * lvl;
            float g = makeup;
            if (env > 1e-6f) {
                float db = 20.f * std::log10(env);
                if (db > thresholdDb) g *= std::pow(10.f, ((thresholdDb - db) * (1.f - 1.f / ratio)) / 20.f);
            }
            buf[i * 2] = l * g; buf[i * 2 + 1] = r * g;
        }
    }
};

/** Reverb estilo Freeverb (8 combs + 4 allpass por lado). */
struct Comb {
    std::vector<float> buf; int idx = 0; float store = 0;
    void init(int n) { buf.assign(std::max(n, 1), 0.f); idx = 0; store = 0; }
    inline float process(float in, float fb, float damp) {
        float out = buf[idx];
        store = out * (1 - damp) + store * damp;
        buf[idx] = in + store * fb;
        if (++idx >= (int)buf.size()) idx = 0;
        return out;
    }
};
struct Allpass {
    std::vector<float> buf; int idx = 0;
    void init(int n) { buf.assign(std::max(n, 1), 0.f); idx = 0; }
    inline float process(float in) {
        float b = buf[idx];
        buf[idx] = in + b * 0.5f;
        if (++idx >= (int)buf.size()) idx = 0;
        return b - in;
    }
};
struct Reverb {
    Comb cl[8], cr[8]; Allpass al[4], ar[4];
    float feedback = 0.84f, damp = 0.3f, level = 0.35f;
    void init(float sr) {
        static const int combs[8] = {1116, 1188, 1277, 1356, 1422, 1491, 1557, 1617};
        static const int aps[4] = {556, 441, 341, 225};
        float k = sr / 44100.f;
        for (int i = 0; i < 8; ++i) { cl[i].init((int)(combs[i] * k)); cr[i].init((int)((combs[i] + 23) * k)); }
        for (int i = 0; i < 4; ++i) { al[i].init((int)(aps[i] * k)); ar[i].init((int)((aps[i] + 23) * k)); }
    }
    /** size 0..1, tone 0..1 (1 = brilhante), level 0..1 */
    void set(float size, float tone, float lvl) {
        feedback = 0.7f + 0.28f * size;
        damp = 0.05f + 0.6f * (1.f - tone);
        level = lvl;
    }
    /** in = envio mono para o reverb; soma o resultado em out (estéreo intercalado) */
    void process(const float* in, float* out, int frames) {
        if (level < 0.001f) return;
        for (int i = 0; i < frames; ++i) {
            float x = in[i] * 0.015f, l = 0, r = 0;
            for (int c = 0; c < 8; ++c) { l += cl[c].process(x, feedback, damp); r += cr[c].process(x, feedback, damp); }
            for (int a = 0; a < 4; ++a) { l = al[a].process(l); r = ar[a].process(r); }
            out[i * 2] += l * level * 3.f;
            out[i * 2 + 1] += r * level * 3.f;
        }
    }
};

// ------------------------------------------------------------------ canais

struct Slot {
    tsf* font = nullptr;
    bool enabled = true;
    float gain = 1.f, curGain = 1.f;   // ganho do fader (curGain suaviza a mudança)
    float pan = 0.5f;
    int transpose = 0, lowKey = 0, highKey = 127, midiChannel = -1, preset = 0;
    float releaseMul = 1.f;    // multiplica o release do SF2
    float velSens = 1.f;       // 0 = velocity fixa, 1 = resposta total
    float eqLow = 0, eqMid = 0, eqHigh = 0, cutoff = 20000.f, comp = 0;
    float reverbSend = 0;
    int output = 0;            // modo 2 saídas: 0 = L e R, 1 = só L, 2 = só R
    Biquad lp, low, mid, high;
    Compressor compressor;
    int8_t active[128];
    Slot() { std::memset(active, -1, sizeof(active)); }

    void updateDsp(float sr) {
        lp.lowPass(sr, cutoff, 0.85f);
        low.lowShelf(sr, 120.f, eqLow);
        mid.peak(sr, 1000.f, 0.8f, eqMid);
        high.highShelf(sr, 6000.f, eqHigh);
        compressor.set(sr, comp);
    }
};

enum EvType : uint8_t { EV_NOTE_ON, EV_NOTE_OFF, EV_CC, EV_PITCH, EV_PANIC };
struct Event { EvType type; uint8_t ch; uint8_t a; uint8_t b; int16_t value; };

class Engine : public oboe::AudioStreamDataCallback, public oboe::AudioStreamErrorCallback {
public:
    std::mutex lock;
    Slot slots[kSlots];
    std::vector<Event> pending, processing;
    std::vector<float> tmp, revIn;
    Reverb reverb;
    float revSize = 0.6f, revTone = 0.6f, revLevel = 0.35f;
    float master = 0.9f;
    bool dualMono = false;            // L e R como duas saídas independentes (ex.: caixa e mesa)
    float outGain[2] = {1.f, 1.f}, curOut[2] = {1.f, 1.f};
    int deviceId = 0;                 // 0 = saída padrão do Android
    int sampleRate = 48000;
    std::shared_ptr<oboe::AudioStream> stream;
    std::atomic<float> peaks[kSlots + 3];   // canais, master, saída L, saída R

    Engine() {
        pending.reserve(1024); processing.reserve(1024); tmp.resize(kChunk * 2); revIn.resize(kChunk);
        reverb.init(48000.f); reverb.set(revSize, revTone, revLevel);
        for (auto& p : peaks) p.store(0.f);
    }

    bool start() {
        oboe::AudioStreamBuilder b;
        b.setDeviceId(deviceId);
        b.setDirection(oboe::Direction::Output)
         ->setPerformanceMode(oboe::PerformanceMode::LowLatency)
         ->setSharingMode(oboe::SharingMode::Exclusive)
         ->setFormat(oboe::AudioFormat::Float)
         ->setChannelCount(oboe::ChannelCount::Stereo)
         ->setUsage(oboe::Usage::Game)
         ->setDataCallback(this)
         ->setErrorCallback(this);
        oboe::Result r = b.openStream(stream);
        if (r != oboe::Result::OK && deviceId != 0) {
            LOGE("saída %d indisponível (%s), usando a padrão", deviceId, oboe::convertToText(r));
            b.setDeviceId(0);
            r = b.openStream(stream);
        }
        if (r != oboe::Result::OK) { LOGE("openStream: %s", oboe::convertToText(r)); return false; }
        stream->setBufferSizeInFrames(stream->getFramesPerBurst() * 2);
        {
            std::lock_guard<std::mutex> g(lock);
            sampleRate = stream->getSampleRate();
            reverb.init((float)sampleRate);
            reverb.set(revSize, revTone, revLevel);
            for (auto& s : slots) {
                if (s.font) tsf_set_output(s.font, TSF_STEREO_INTERLEAVED, sampleRate, 0.0f);
                s.updateDsp((float)sampleRate);
            }
        }
        r = stream->requestStart();
        LOGI("stream: %d Hz, burst %d, %s", sampleRate, stream->getFramesPerBurst(), oboe::convertToText(r));
        return r == oboe::Result::OK;
    }

    void stop() { if (stream) { stream->stop(); stream->close(); stream.reset(); } }

    void onErrorAfterClose(oboe::AudioStream*, oboe::Result err) override {
        LOGI("stream fechado (%s), reabrindo", oboe::convertToText(err));
        stream.reset();
        start();
    }

    void push(const Event& e) {
        std::lock_guard<std::mutex> g(lock);
        if (pending.size() < 4096) pending.push_back(e);
    }

    static bool chMatch(const Slot& s, int ch) { return s.midiChannel < 0 || s.midiChannel == ch; }

    /** Aplica o multiplicador de release nas vozes que acabaram de nascer. */
    static void scaleRelease(tsf* f, float mul) {
        if (std::fabs(mul - 1.f) < 0.01f) return;
        unsigned int idx = f->voicePlayIndex - 1;
        for (int i = 0; i < f->voiceNum; ++i) {
            tsf_voice& v = f->voices[i];
            if (v.playingPreset == -1 || v.playIndex != idx) continue;
            float r = std::max(v.ampenv.parameters.release, 0.006f);
            v.ampenv.parameters.release = std::min(r * mul, 20.f);
            float m = std::max(v.modenv.parameters.release, 0.006f);
            v.modenv.parameters.release = std::min(m * mul, 20.f);
        }
    }

    void handle(const Event& e) {
        switch (e.type) {
        case EV_NOTE_ON: {
            float raw = e.b / 127.0f;
            for (auto& s : slots) {
                if (!s.font || !s.enabled || !chMatch(s, e.ch)) continue;
                if (e.a < s.lowKey || e.a > s.highKey) continue;
                int k = e.a + s.transpose;
                if (k < 0 || k > 127) continue;
                if (s.active[e.a] >= 0) tsf_channel_note_off(s.font, 0, s.active[e.a]);
                float vel = 1.f - s.velSens * (1.f - raw);   // sensibilidade de velocity
                tsf_channel_note_on(s.font, 0, k, std::max(vel, 0.01f));
                scaleRelease(s.font, s.releaseMul);
                s.active[e.a] = (int8_t)k;
            }
            break;
        }
        case EV_NOTE_OFF:
            for (auto& s : slots) {
                if (!s.font || s.active[e.a] < 0) continue;
                tsf_channel_note_off(s.font, 0, s.active[e.a]);
                s.active[e.a] = -1;
            }
            break;
        case EV_CC:
            if (e.a == 7 || e.a == 10) break;   // volume e pan ficam com a mesa do app
            for (auto& s : slots)
                if (s.font && chMatch(s, e.ch)) tsf_channel_midi_control(s.font, 0, e.a, e.b);
            break;
        case EV_PITCH:
            for (auto& s : slots)
                if (s.font && chMatch(s, e.ch)) tsf_channel_set_pitchwheel(s.font, 0, e.value);
            break;
        case EV_PANIC:
            for (auto& s : slots) {
                if (s.font) tsf_channel_sounds_off_all(s.font, 0);
                std::memset(s.active, -1, sizeof(s.active));
            }
            break;
        }
    }

    static void peakOf(const float* b, int n, float& p) {
        for (int i = 0; i < n; ++i) { float a = std::fabs(b[i]); if (a > p) p = a; }
    }
    void storePeak(int i, float p) {
        float old = peaks[i].load(std::memory_order_relaxed);
        if (p > old) peaks[i].store(p, std::memory_order_relaxed);
    }

    oboe::DataCallbackResult onAudioReady(oboe::AudioStream*, void* data, int32_t frames) override {
        float* out = static_cast<float*>(data);
        std::memset(out, 0, sizeof(float) * frames * 2);
        std::unique_lock<std::mutex> g(lock, std::try_to_lock);
        if (!g.owns_lock()) return oboe::DataCallbackResult::Continue;

        processing.swap(pending);
        for (const auto& e : processing) handle(e);
        processing.clear();

        float slotPeak[kSlots] = {0};
        for (int done = 0; done < frames; done += kChunk) {
            int n = std::min(kChunk, frames - done);
            float* dst = out + done * 2;
            std::memset(revIn.data(), 0, sizeof(float) * n);
            for (int si = 0; si < kSlots; ++si) {
                Slot& s = slots[si];
                if (!s.font) continue;
                float* t = tmp.data();
                std::memset(t, 0, sizeof(float) * n * 2);
                tsf_render_float(s.font, t, n, 0);
                s.lp.process(t, n);
                s.low.process(t, n);
                s.mid.process(t, n);
                s.high.process(t, n);
                s.compressor.process(t, n);
                // fader com rampa (sem estalos ao mexer rápido)
                float g0 = s.curGain, g1 = s.gain, step = (g1 - g0) / n;
                if (!dualMono) {
                    for (int i = 0; i < n; ++i) {
                        float gg = g0 + step * i;
                        t[i * 2] *= gg; t[i * 2 + 1] *= gg;
                        dst[i * 2] += t[i * 2]; dst[i * 2 + 1] += t[i * 2 + 1];
                    }
                } else {
                    // 2 saídas independentes: cada lado recebe a mistura mono, conforme o roteamento do canal
                    const float toL = s.output != 2 ? 1.f : 0.f, toR = s.output != 1 ? 1.f : 0.f;
                    for (int i = 0; i < n; ++i) {
                        float gg = g0 + step * i;
                        t[i * 2] *= gg; t[i * 2 + 1] *= gg;
                        float m = (t[i * 2] + t[i * 2 + 1]) * 0.7071f;
                        dst[i * 2] += m * toL; dst[i * 2 + 1] += m * toR;
                    }
                }
                s.curGain = g1;
                peakOf(t, n * 2, slotPeak[si]);
                if (s.reverbSend > 0.001f) {
                    const float snd = s.reverbSend;
                    for (int i = 0; i < n; ++i) revIn[i] += (t[i * 2] + t[i * 2 + 1]) * snd;
                }
            }
            reverb.process(revIn.data(), dst, n);   // sempre: mantém a cauda soando depois do envio
        }
        for (int si = 0; si < kSlots; ++si) storePeak(si, slotPeak[si]);

        const float m = master;
        float mp = 0, op[2] = {0, 0};
        float step[2] = {(outGain[0] - curOut[0]) / frames, (outGain[1] - curOut[1]) / frames};
        for (int f = 0; f < frames; ++f) {
            for (int ch = 0; ch < 2; ++ch) {
                float v = out[f * 2 + ch] * m;
                float a = std::fabs(v);
                if (a > mp) mp = a;
                v *= curOut[ch] + step[ch] * f;          // volume da saída L / R
                a = std::fabs(v);
                if (a > op[ch]) op[ch] = a;
                // saturação suave acima de 0,8 para não clipar seco
                if (a > 0.8f) v = (v > 0 ? 1.f : -1.f) * (0.8f + 0.2f * std::tanh((a - 0.8f) / 0.2f));
                out[f * 2 + ch] = v;
            }
        }
        curOut[0] = outGain[0]; curOut[1] = outGain[1];
        storePeak(kSlots, mp);
        storePeak(kSlots + 1, op[0]);
        storePeak(kSlots + 2, op[1]);
        return oboe::DataCallbackResult::Continue;
    }

    void applySlot(Slot& s) {
        if (!s.font) return;
        tsf_channel_set_presetindex(s.font, 0, s.preset);
        tsf_channel_set_volume(s.font, 0, 1.0f);
        tsf_channel_set_pan(s.font, 0, s.pan);
    }
};

static Engine* gEngine = nullptr;

static Slot* slotAt(int i) {
    if (!gEngine || i < 0 || i >= kSlots) return nullptr;
    return &gEngine->slots[i];
}

extern "C" {

JNIEXPORT jboolean JNICALL
Java_com_rarodoce_sf2player_Engine_nativeStart(JNIEnv*, jobject) {
    if (!gEngine) gEngine = new Engine();
    return gEngine->start();
}

JNIEXPORT void JNICALL
Java_com_rarodoce_sf2player_Engine_nativeStop(JNIEnv*, jobject) {
    if (gEngine) gEngine->stop();
}

JNIEXPORT jobjectArray JNICALL
Java_com_rarodoce_sf2player_Engine_nativeLoad(JNIEnv* env, jobject, jint idx, jstring jpath, jint preset) {
    if (!slotAt(idx)) return nullptr;
    const char* path = env->GetStringUTFChars(jpath, nullptr);
    tsf* f = tsf_load_filename(path);
    env->ReleaseStringUTFChars(jpath, path);
    if (!f) return nullptr;

    tsf_set_output(f, TSF_STEREO_INTERLEAVED, gEngine->sampleRate, 0.0f);
    tsf_set_max_voices(f, kMaxVoices);
    int count = tsf_get_presetcount(f);
    if (preset < 0 || preset >= count) preset = 0;
    tsf_channel_set_presetindex(f, 0, preset);
    tsf_channel_set_pitchrange(f, 0, 2.0f);

    jobjectArray names = env->NewObjectArray(count, env->FindClass("java/lang/String"), nullptr);
    for (int i = 0; i < count; ++i) {
        const char* n = tsf_get_presetname(f, i);
        env->SetObjectArrayElement(names, i, env->NewStringUTF(n ? n : "?"));
    }

    tsf* old;
    {
        std::lock_guard<std::mutex> g(gEngine->lock);
        Slot& s = gEngine->slots[idx];
        old = s.font;
        s.font = f;
        s.preset = preset;
        std::memset(s.active, -1, sizeof(s.active));
        s.lp.reset(); s.low.reset(); s.mid.reset(); s.high.reset();
        gEngine->applySlot(s);
    }
    if (old) tsf_close(old);
    return names;
}

JNIEXPORT void JNICALL
Java_com_rarodoce_sf2player_Engine_nativeUnload(JNIEnv*, jobject, jint idx) {
    if (!slotAt(idx)) return;
    tsf* old;
    {
        std::lock_guard<std::mutex> g(gEngine->lock);
        old = gEngine->slots[idx].font;
        gEngine->slots[idx].font = nullptr;
    }
    if (old) tsf_close(old);
}

// p = [ganho, pan, release×, velocity, grave dB, médio dB, agudo dB, cutoff Hz, compressor, envio reverb, saída (0 L+R, 1 L, 2 R)]
JNIEXPORT void JNICALL
Java_com_rarodoce_sf2player_Engine_nativeSetSlot(JNIEnv* env, jobject, jint idx, jboolean enabled, jint preset,
        jint transpose, jint lowKey, jint highKey, jint channel, jfloatArray jp) {
    Slot* s = slotAt(idx);
    if (!s || env->GetArrayLength(jp) < 11) return;
    float p[11];
    env->GetFloatArrayRegion(jp, 0, 11, p);

    std::lock_guard<std::mutex> g(gEngine->lock);
    bool wasEnabled = s->enabled;
    s->enabled = enabled;
    s->gain = std::clamp(p[0], 0.f, 4.f);
    s->pan = std::clamp(p[1], 0.f, 1.f);
    s->releaseMul = std::clamp(p[2], 0.05f, 16.f);
    s->velSens = std::clamp(p[3], 0.f, 1.f);
    s->eqLow = std::clamp(p[4], -15.f, 15.f);
    s->eqMid = std::clamp(p[5], -15.f, 15.f);
    s->eqHigh = std::clamp(p[6], -15.f, 15.f);
    s->cutoff = std::clamp(p[7], 40.f, 20000.f);
    s->comp = std::clamp(p[8], 0.f, 1.f);
    s->reverbSend = std::clamp(p[9], 0.f, 1.f);
    s->output = std::clamp((int)p[10], 0, 2);
    s->transpose = std::clamp((int)transpose, -48, 48);
    s->lowKey = std::clamp((int)lowKey, 0, 127);
    s->highKey = std::clamp((int)highKey, 0, 127);
    s->midiChannel = std::clamp((int)channel, -1, 15);
    s->updateDsp((float)gEngine->sampleRate);
    if (s->font) {
        if (wasEnabled && !enabled) {
            tsf_channel_note_off_all(s->font, 0);
            std::memset(s->active, -1, sizeof(s->active));
        }
        if (preset != s->preset && preset >= 0 && preset < tsf_get_presetcount(s->font)) {
            tsf_channel_sounds_off_all(s->font, 0);
            std::memset(s->active, -1, sizeof(s->active));
            s->preset = preset;
        }
        gEngine->applySlot(*s);
    }
}

JNIEXPORT void JNICALL
Java_com_rarodoce_sf2player_Engine_nativeSetMaster(JNIEnv*, jobject, jfloat v) {
    if (!gEngine) return;
    std::lock_guard<std::mutex> g(gEngine->lock);
    gEngine->master = std::clamp((float)v, 0.0f, 4.f);
}

JNIEXPORT void JNICALL
Java_com_rarodoce_sf2player_Engine_nativeSetReverb(JNIEnv*, jobject, jfloat size, jfloat tone, jfloat level) {
    if (!gEngine) return;
    std::lock_guard<std::mutex> g(gEngine->lock);
    gEngine->revSize = std::clamp((float)size, 0.f, 1.f);
    gEngine->revTone = std::clamp((float)tone, 0.f, 1.f);
    gEngine->revLevel = std::clamp((float)level, 0.f, 1.f);
    gEngine->reverb.set(gEngine->revSize, gEngine->revTone, gEngine->revLevel);
}

JNIEXPORT void JNICALL
Java_com_rarodoce_sf2player_Engine_nativeSetOutputs(JNIEnv*, jobject, jboolean dual, jfloat gainL, jfloat gainR) {
    if (!gEngine) return;
    std::lock_guard<std::mutex> g(gEngine->lock);
    gEngine->dualMono = dual;
    gEngine->outGain[0] = std::clamp((float)gainL, 0.f, 4.f);
    gEngine->outGain[1] = std::clamp((float)gainR, 0.f, 4.f);
}

/** Troca a placa/saída de áudio (0 = padrão do Android) reabrindo o stream. */
JNIEXPORT jboolean JNICALL
Java_com_rarodoce_sf2player_Engine_nativeSetDevice(JNIEnv*, jobject, jint id) {
    if (!gEngine) return false;
    gEngine->deviceId = id;
    gEngine->stop();
    return gEngine->start();
}

/** [taxa de amostragem, burst, buffer em quadros, id da saída em uso] */
JNIEXPORT jintArray JNICALL
Java_com_rarodoce_sf2player_Engine_nativeStreamInfo(JNIEnv* env, jobject) {
    jint v[4] = {0, 0, 0, 0};
    if (gEngine && gEngine->stream) {
        auto st = gEngine->stream;
        v[0] = st->getSampleRate(); v[1] = st->getFramesPerBurst();
        v[2] = st->getBufferSizeInFrames(); v[3] = st->getDeviceId();
    }
    jintArray a = env->NewIntArray(4);
    env->SetIntArrayRegion(a, 0, 4, v);
    return a;
}

/** Lê e zera os picos: 8 canais, master, saída L, saída R. */
JNIEXPORT void JNICALL
Java_com_rarodoce_sf2player_Engine_nativeReadMeters(JNIEnv* env, jobject, jfloatArray out) {
    if (!gEngine) return;
    float v[kSlots + 3];
    for (int i = 0; i < kSlots + 3; ++i) v[i] = gEngine->peaks[i].exchange(0.f, std::memory_order_relaxed);
    env->SetFloatArrayRegion(out, 0, std::min((int)env->GetArrayLength(out), kSlots + 3), v);
}

JNIEXPORT void JNICALL
Java_com_rarodoce_sf2player_Engine_nativeNoteOn(JNIEnv*, jobject, jint ch, jint key, jint vel) {
    if (!gEngine || key < 0 || key > 127) return;
    if (vel == 0) gEngine->push({EV_NOTE_OFF, (uint8_t)ch, (uint8_t)key, 0, 0});
    else gEngine->push({EV_NOTE_ON, (uint8_t)ch, (uint8_t)key, (uint8_t)vel, 0});
}

JNIEXPORT void JNICALL
Java_com_rarodoce_sf2player_Engine_nativeNoteOff(JNIEnv*, jobject, jint ch, jint key) {
    if (!gEngine || key < 0 || key > 127) return;
    gEngine->push({EV_NOTE_OFF, (uint8_t)ch, (uint8_t)key, 0, 0});
}

JNIEXPORT void JNICALL
Java_com_rarodoce_sf2player_Engine_nativeControl(JNIEnv*, jobject, jint ch, jint cc, jint value) {
    if (!gEngine) return;
    gEngine->push({EV_CC, (uint8_t)ch, (uint8_t)cc, (uint8_t)value, 0});
}

JNIEXPORT void JNICALL
Java_com_rarodoce_sf2player_Engine_nativePitchBend(JNIEnv*, jobject, jint ch, jint value) {
    if (!gEngine) return;
    gEngine->push({EV_PITCH, (uint8_t)ch, 0, 0, (int16_t)value});
}

JNIEXPORT void JNICALL
Java_com_rarodoce_sf2player_Engine_nativePanic(JNIEnv*, jobject) {
    if (!gEngine) return;
    gEngine->push({EV_PANIC, 0, 0, 0, 0});
}

}
