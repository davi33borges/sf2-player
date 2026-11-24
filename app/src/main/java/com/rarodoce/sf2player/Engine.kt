package com.rarodoce.sf2player

/** Ponte para o motor de áudio em C++ (TinySoundFont + Oboe). */
object Engine {
    init { System.loadLibrary("sf2engine") }

    external fun nativeStart(): Boolean
    external fun nativeStop()
    /** Carrega o .sf2 no slot e devolve os nomes dos presets (null = arquivo inválido). */
    external fun nativeLoad(slot: Int, path: String, preset: Int): Array<String>?
    external fun nativeUnload(slot: Int)
    external fun nativeSetSlot(
        slot: Int, enabled: Boolean, volume: Float, pan: Float, transpose: Int,
        lowKey: Int, highKey: Int, channel: Int, preset: Int,
    )
    external fun nativeSetMaster(volume: Float)
    external fun nativeNoteOn(channel: Int, key: Int, velocity: Int)
    external fun nativeNoteOff(channel: Int, key: Int)
    external fun nativeControl(channel: Int, cc: Int, value: Int)
    external fun nativePitchBend(channel: Int, value: Int)
    external fun nativePanic()

    fun apply(index: Int, s: SlotState) = nativeSetSlot(
        index, s.enabled, s.volume, s.pan, s.transpose, s.lowKey, s.highKey, s.channel, s.preset,
    )
}
