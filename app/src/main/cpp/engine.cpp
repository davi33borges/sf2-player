// Motor de áudio do SF2 Player: até 8 instrumentos SoundFont tocando em camadas/split.
// TinySoundFont faz a síntese, Oboe entrega o áudio com baixa latência.

#define TSF_IMPLEMENTATION
#include "tsf.h"

#include <jni.h>
#include <oboe/Oboe.h>
#include <android/log.h>
#include <mutex>
#include <vector>
#include <string>
#include <cstring>
#include <algorithm>
#include <memory>

#define LOG_TAG "SF2Engine"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

static constexpr int kSlots = 8;
static constexpr int kMaxVoices = 96;

struct Slot {
    tsf* font = nullptr;
    bool enabled = true;
    float volume = 0.8f;   // 0..1
    float pan = 0.5f;      // 0 = esquerda, 0.5 = centro, 1 = direita
    int transpose = 0;     // semitons
    int lowKey = 0;
    int highKey = 127;
    int midiChannel = -1;  // -1 = todos os canais
    int preset = 0;
    int8_t active[128];    // nota tocada (já transposta) para cada tecla física, -1 = nenhuma
    Slot() { std::memset(active, -1, sizeof(active)); }
};

enum EvType : uint8_t { EV_NOTE_ON, EV_NOTE_OFF, EV_CC, EV_PITCH, EV_PANIC };
struct Event { EvType type; uint8_t ch; uint8_t a; uint8_t b; int16_t value; };

class Engine : public oboe::AudioStreamDataCallback, public oboe::AudioStreamErrorCallback {
public:
    std::mutex lock;
    Slot slots[kSlots];
    std::vector<Event> pending;      // eventos aguardando o próximo bloco de áudio
    std::vector<Event> processing;
    float master = 0.9f;
    int sampleRate = 48000;
    std::shared_ptr<oboe::AudioStream> stream;

    Engine() { pending.reserve(1024); processing.reserve(1024); }

    bool start() {
        oboe::AudioStreamBuilder b;
        b.setDirection(oboe::Direction::Output)
         ->setPerformanceMode(oboe::PerformanceMode::LowLatency)
         ->setSharingMode(oboe::SharingMode::Exclusive)
         ->setFormat(oboe::AudioFormat::Float)
         ->setChannelCount(oboe::ChannelCount::Stereo)
         ->setUsage(oboe::Usage::Game)
         ->setDataCallback(this)
         ->setErrorCallback(this);
        oboe::Result r = b.openStream(stream);
        if (r != oboe::Result::OK) { LOGE("openStream: %s", oboe::convertToText(r)); return false; }
        // buffer de 2 bursts: menor atraso possível sem estalos
        stream->setBufferSizeInFrames(stream->getFramesPerBurst() * 2);
        {
            std::lock_guard<std::mutex> g(lock);
            sampleRate = stream->getSampleRate();
            for (auto& s : slots) if (s.font) tsf_set_output(s.font, TSF_STEREO_INTERLEAVED, sampleRate, 0.0f);
        }
        r = stream->requestStart();
        LOGI("stream: %d Hz, burst %d, %s", sampleRate, stream->getFramesPerBurst(), oboe::convertToText(r));
        return r == oboe::Result::OK;
    }

    void stop() {
        if (stream) { stream->stop(); stream->close(); stream.reset(); }
    }

    // Fone desconectado, troca de saída etc.: reabre o stream
    void onErrorAfterClose(oboe::AudioStream*, oboe::Result err) override {
        LOGI("stream fechado (%s), reabrindo", oboe::convertToText(err));
        stream.reset();
        start();
    }

    void push(const Event& e) {
        std::lock_guard<std::mutex> g(lock);
        if (pending.size() < 4096) pending.push_back(e);
    }

    // ---- processamento de eventos (chamado só dentro do callback de áudio, com lock) ----
    static bool chMatch(const Slot& s, int ch) { return s.midiChannel < 0 || s.midiChannel == ch; }

    void handle(const Event& e) {
        switch (e.type) {
        case EV_NOTE_ON: {
            float vel = e.b / 127.0f;
            for (auto& s : slots) {
                if (!s.font || !s.enabled || !chMatch(s, e.ch)) continue;
                if (e.a < s.lowKey || e.a > s.highKey) continue;
                int k = e.a + s.transpose;
                if (k < 0 || k > 127) continue;
                if (s.active[e.a] >= 0) tsf_channel_note_off(s.font, 0, s.active[e.a]);
                tsf_channel_note_on(s.font, 0, k, vel);
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
            // volume (7) e pan (10) do teclado não mexem nos slots: esses são controlados pelo app
            if (e.a == 7 || e.a == 10) break;
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

    oboe::DataCallbackResult onAudioReady(oboe::AudioStream*, void* data, int32_t frames) override {
        float* out = static_cast<float*>(data);
        std::memset(out, 0, sizeof(float) * frames * 2);
        // nunca bloquear a thread de áudio: se a UI estiver segurando o lock, este bloco sai em silêncio
        std::unique_lock<std::mutex> g(lock, std::try_to_lock);
        if (!g.owns_lock()) return oboe::DataCallbackResult::Continue;

        processing.swap(pending);
        for (const auto& e : processing) handle(e);
        processing.clear();

        for (auto& s : slots)
            if (s.font) tsf_render_float(s.font, out, frames, 1);

        const float m = master;
        for (int i = 0; i < frames * 2; ++i) {
            float v = out[i] * m;
            // limitador suave para não distorcer quando várias camadas somam
            if (v > 1.0f) v = 1.0f; else if (v < -1.0f) v = -1.0f;
            out[i] = v;
        }
        return oboe::DataCallbackResult::Continue;
    }

    void applySlot(Slot& s) {
        if (!s.font) return;
        tsf_channel_set_presetindex(s.font, 0, s.preset);
        tsf_channel_set_volume(s.font, 0, s.volume);
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

// Carrega um .sf2 no slot. Retorna os nomes dos presets, ou null se o arquivo for inválido.
JNIEXPORT jobjectArray JNICALL
Java_com_rarodoce_sf2player_Engine_nativeLoad(JNIEnv* env, jobject, jint idx, jstring jpath, jint preset) {
    if (!slotAt(idx)) return nullptr;
    const char* path = env->GetStringUTFChars(jpath, nullptr);
    tsf* f = tsf_load_filename(path);   // pode demorar: fora do lock
    env->ReleaseStringUTFChars(jpath, path);
    if (!f) return nullptr;

    tsf_set_output(f, TSF_STEREO_INTERLEAVED, gEngine->sampleRate, 0.0f);
    tsf_set_max_voices(f, kMaxVoices);   // pré-aloca vozes: sem malloc na thread de áudio
    int count = tsf_get_presetcount(f);
    if (preset < 0 || preset >= count) preset = 0;
    tsf_channel_set_presetindex(f, 0, preset);  // aloca o canal 0 aqui, fora do áudio
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

JNIEXPORT void JNICALL
Java_com_rarodoce_sf2player_Engine_nativeSetSlot(JNIEnv*, jobject, jint idx, jboolean enabled,
        jfloat volume, jfloat pan, jint transpose, jint lowKey, jint highKey, jint channel, jint preset) {
    Slot* s = slotAt(idx);
    if (!s) return;
    std::lock_guard<std::mutex> g(gEngine->lock);
    bool wasEnabled = s->enabled;
    s->enabled = enabled;
    s->volume = std::clamp((float)volume, 0.0f, 1.0f);
    s->pan = std::clamp((float)pan, 0.0f, 1.0f);
    s->transpose = std::clamp((int)transpose, -48, 48);
    s->lowKey = std::clamp((int)lowKey, 0, 127);
    s->highKey = std::clamp((int)highKey, 0, 127);
    s->midiChannel = std::clamp((int)channel, -1, 15);
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
    gEngine->master = std::clamp((float)v, 0.0f, 1.5f);
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
