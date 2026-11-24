package com.rarodoce.sf2player

import android.content.Context
import android.media.midi.MidiDevice
import android.media.midi.MidiDeviceInfo
import android.media.midi.MidiManager
import android.media.midi.MidiOutputPort
import android.media.midi.MidiReceiver
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper

data class MidiSource(val info: MidiDeviceInfo, val port: Int, val portName: String) {
    val deviceName: String
        get() = info.properties.getString(MidiDeviceInfo.PROPERTY_NAME)
            ?: info.properties.getString(MidiDeviceInfo.PROPERTY_PRODUCT) ?: "Teclado MIDI"
    val label get() = if (portName.isBlank()) "$deviceName · porta ${port + 1}" else "$deviceName · $portName"
    val key get() = "${info.id}:$port"
}

/**
 * Conexão com teclados MIDI (USB ou Bluetooth já pareado).
 * [onMessage] é chamado na thread de MIDI, com status, dado1 e dado2 já separados.
 */
class MidiHub(
    context: Context,
    private val onSourcesChanged: (List<MidiSource>) -> Unit,
    private val onConnected: (MidiSource?) -> Unit,
    private val onMessage: (status: Int, d1: Int, d2: Int) -> Unit,
) {
    private val manager = context.getSystemService(Context.MIDI_SERVICE) as MidiManager?
    private val thread = HandlerThread("midi-in", android.os.Process.THREAD_PRIORITY_URGENT_AUDIO).apply { start() }
    private val midiHandler = Handler(thread.looper)
    private val main = Handler(Looper.getMainLooper())

    private var device: MidiDevice? = null
    private var port: MidiOutputPort? = null
    var current: MidiSource? = null; private set
    /** Fonte escolhida pelo usuário; se for nula, conecta automaticamente. */
    var preferredKey: String? = null

    val available get() = manager != null

    private val callback = object : MidiManager.DeviceCallback() {
        override fun onDeviceAdded(info: MidiDeviceInfo) = refresh(autoConnect = true)
        override fun onDeviceRemoved(info: MidiDeviceInfo) {
            if (current?.info?.id == info.id) disconnect()
            refresh(autoConnect = true)
        }
    }

    @Suppress("DEPRECATION")
    fun start() {
        val m = manager ?: return
        m.registerDeviceCallback(callback, main)
        refresh(autoConnect = true)
    }

    @Suppress("DEPRECATION")
    fun sources(): List<MidiSource> {
        val m = manager ?: return emptyList()
        return m.devices.flatMap { info ->
            info.ports.filter { it.type == MidiDeviceInfo.PortInfo.TYPE_OUTPUT }
                .map { MidiSource(info, it.portNumber, it.name ?: "") }
        }
    }

    private fun refresh(autoConnect: Boolean) {
        val list = sources()
        onSourcesChanged(list)
        if (!autoConnect || current != null || list.isEmpty()) return
        val pick = list.firstOrNull { it.key == preferredKey }
            ?: list.firstOrNull { it.deviceName.contains("launchkey", true) && it.port == 0 }
            ?: list.first()
        connect(pick)
    }

    fun connect(source: MidiSource) {
        val m = manager ?: return
        disconnect()
        m.openDevice(source.info, { dev ->
            if (dev == null) { onConnected(null); return@openDevice }
            val p = dev.openOutputPort(source.port)
            if (p == null) { dev.close(); onConnected(null); return@openDevice }
            p.connect(Parser())
            device = dev; port = p; current = source
            onConnected(source)
        }, midiHandler)
    }

    fun disconnect() {
        runCatching { port?.close() }
        runCatching { device?.close() }
        port = null; device = null
        if (current != null) { current = null; onConnected(null) }
    }

    fun release() {
        @Suppress("DEPRECATION")
        manager?.unregisterDeviceCallback(callback)
        disconnect()
        thread.quitSafely()
    }

    /** Separa o fluxo de bytes em mensagens (com running status, ignorando SysEx e clock). */
    private inner class Parser : MidiReceiver() {
        private var status = 0
        private var needed = 0
        private var count = 0
        private val data = IntArray(2)
        private var sysex = false

        override fun onSend(msg: ByteArray, offset: Int, length: Int, timestamp: Long) {
            for (i in offset until offset + length) {
                val b = msg[i].toInt() and 0xFF
                when {
                    b >= 0xF8 -> Unit                                   // tempo real (clock etc.)
                    b == 0xF0 -> { sysex = true; status = 0 }
                    b == 0xF7 -> sysex = false
                    b >= 0xF0 -> { status = 0; sysex = false }          // outras mensagens de sistema
                    b >= 0x80 -> {
                        sysex = false; status = b; count = 0
                        needed = if ((b and 0xF0) == 0xC0 || (b and 0xF0) == 0xD0) 1 else 2
                    }
                    sysex || status == 0 -> Unit
                    else -> {
                        data[count++] = b
                        if (count == needed) {
                            onMessage(status, data[0], if (needed == 2) data[1] else 0)
                            count = 0
                        }
                    }
                }
            }
        }
    }
}
