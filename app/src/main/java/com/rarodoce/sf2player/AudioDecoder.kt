package com.rarodoce.sf2player

import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import java.nio.ByteOrder

/** Decodifica WAV, MP3, OGG, FLAC, M4A… para estéreo float intercalado. */
object AudioDecoder {
    class Result(val data: FloatArray, val sampleRate: Int) {
        val seconds get() = data.size / 2f / sampleRate
    }

    const val MAX_SECONDS = 90

    fun decode(path: String): Result {
        val ex = MediaExtractor()
        ex.setDataSource(path)
        val track = (0 until ex.trackCount).firstOrNull {
            ex.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
        } ?: run { ex.release(); error("O arquivo não tem áudio") }
        ex.selectTrack(track)
        val inFmt = ex.getTrackFormat(track)
        var rate = inFmt.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        var channels = inFmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
        var pcmFloat = false

        val codec = MediaCodec.createDecoderByType(inFmt.getString(MediaFormat.KEY_MIME)!!)
        codec.configure(inFmt, null, null, 0)
        codec.start()

        var out = FloatArray(1 shl 18)
        var n = 0
        val maxSamples = MAX_SECONDS * 192_000 * 2
        val info = MediaCodec.BufferInfo()
        var inputDone = false
        var outputDone = false
        try {
            while (!outputDone) {
                if (!inputDone) {
                    val ii = codec.dequeueInputBuffer(10_000)
                    if (ii >= 0) {
                        val buf = codec.getInputBuffer(ii)!!
                        val size = ex.readSampleData(buf, 0)
                        if (size < 0) {
                            codec.queueInputBuffer(ii, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            codec.queueInputBuffer(ii, 0, size, ex.sampleTime, 0)
                            ex.advance()
                        }
                    }
                }
                val oi = codec.dequeueOutputBuffer(info, 10_000)
                when {
                    oi == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val f = codec.outputFormat
                        rate = f.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        channels = f.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                        pcmFloat = f.containsKey(MediaFormat.KEY_PCM_ENCODING) &&
                            f.getInteger(MediaFormat.KEY_PCM_ENCODING) == AudioFormat.ENCODING_PCM_FLOAT
                    }
                    oi >= 0 -> {
                        val buf = codec.getOutputBuffer(oi)!!.order(ByteOrder.nativeOrder())
                        buf.position(info.offset); buf.limit(info.offset + info.size)
                        val frames = if (pcmFloat) info.size / 4 / channels else info.size / 2 / channels
                        if (n + frames * 2 > out.size) out = out.copyOf(maxOf(out.size * 2, n + frames * 2))
                        if (pcmFloat) {
                            val fb = buf.asFloatBuffer()
                            for (f in 0 until frames) {
                                val l = fb.get(f * channels); val r = if (channels > 1) fb.get(f * channels + 1) else l
                                out[n++] = l; out[n++] = r
                            }
                        } else {
                            val sb = buf.asShortBuffer()
                            for (f in 0 until frames) {
                                val l = sb.get(f * channels) / 32768f
                                val r = if (channels > 1) sb.get(f * channels + 1) / 32768f else l
                                out[n++] = l; out[n++] = r
                            }
                        }
                        codec.releaseOutputBuffer(oi, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                        if (n >= maxSamples) outputDone = true
                    }
                }
            }
        } finally {
            runCatching { codec.stop() }; codec.release(); ex.release()
        }
        if (n < 4) error("Áudio vazio")
        val limit = minOf(n, MAX_SECONDS * rate * 2)
        return Result(out.copyOf(limit), rate)
    }
}
