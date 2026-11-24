package com.rarodoce.sf2player

/** Ponte para o motor de áudio em C++ (TinySoundFont + Oboe). */
object Engine {
    init { System.loadLibrary("sf2engine") }

    external fun nativeStart(): Boolean
    external fun nativeStop()
    /** Carrega o .sf2 no slot e devolve os nomes dos presets (null = arquivo inválido). */
    external fun nativeLoad(slot: Int, path: String, preset: Int): Array<String>?
    external fun nativeUnload(slot: Int)
    /** p = [ganho, pan, release×, velocity, grave dB, médio dB, agudo dB, cutoff Hz, compressor] */
    external fun nativeSetSlot(
        slot: Int, enabled: Boolean, preset: Int, transpose: Int,
        lowKey: Int, highKey: Int, channel: Int, p: FloatArray,
    )
    /** Picos de nível desde a última leitura: 8 canais + master. */
    external fun nativeReadMeters(out: FloatArray)
    external fun nativeSetMaster(volume: Float)
    external fun nativeNoteOn(channel: Int, key: Int, velocity: Int)
    external fun nativeNoteOff(channel: Int, key: Int)
    external fun nativeControl(channel: Int, cc: Int, value: Int)
    external fun nativePitchBend(channel: Int, value: Int)
    external fun nativePanic()

    fun apply(index: Int, s: SlotState) = nativeSetSlot(
        index, s.enabled, s.preset, s.transpose, s.lowKey, s.highKey, s.channel,
        floatArrayOf(
            faderGain(s.volume), s.pan, releaseMul(s.release), s.velocity,
            s.eqLow, s.eqMid, s.eqHigh, cutoffHz(s.cutoff), s.comp,
        ),
    )
}

/** Fader: posição 0..1 -> ganho. Na posição 0,75 o ganho é 1 (0 dB); no topo, cerca de +6 dB. */
fun faderGain(pos: Float): Float = if (pos <= 0f) 0f else (pos / 0.75f).let { it * it }
fun faderDb(pos: Float): String {
    val g = faderGain(pos)
    if (g < 0.0005f) return "-∞"
    val db = 20 * kotlin.math.log10(g)
    return (if (db > 0.05f) "+" else "") + "%.1f".format(db)
}
/** Release: -1..1 -> 1/8× até 8× o release original do SF2. */
fun releaseMul(r: Float): Float = Math.pow(2.0, r * 3.0).toFloat()
/** Cutoff: 0..1 -> 60 Hz até 20 kHz (escala logarítmica). */
fun cutoffHz(c: Float): Float = (60.0 * Math.pow(20000.0 / 60.0, c.toDouble())).toFloat()
fun cutoffLabel(c: Float): String {
    val hz = cutoffHz(c)
    return if (c >= 0.999f) "Aberto" else if (hz >= 1000) "%.1fk".format(hz / 1000) else "${hz.toInt()}"
}
