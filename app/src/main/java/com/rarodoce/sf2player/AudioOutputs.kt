package com.rarodoce.sf2player

import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Handler
import android.os.Looper

data class AudioOut(val id: Int, val name: String, val type: Int, val channels: Int) {
    /** Identifica a placa entre conexões (o id muda a cada vez que ela é plugada). */
    val key get() = "$type:$name"
    val isExternal get() = type in EXTERNAL
    val typeLabel get() = when (type) {
        AudioDeviceInfo.TYPE_USB_DEVICE, AudioDeviceInfo.TYPE_USB_ACCESSORY -> "Placa USB"
        AudioDeviceInfo.TYPE_USB_HEADSET -> "Fone/placa USB"
        AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> "Alto-falante do aparelho"
        AudioDeviceInfo.TYPE_WIRED_HEADPHONES, AudioDeviceInfo.TYPE_WIRED_HEADSET -> "Saída P2 (fone)"
        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> "Bluetooth (com atraso)"
        AudioDeviceInfo.TYPE_HDMI -> "HDMI"
        AudioDeviceInfo.TYPE_LINE_ANALOG, AudioDeviceInfo.TYPE_LINE_DIGITAL -> "Saída de linha"
        else -> "Saída de áudio"
    }

    companion object {
        val EXTERNAL = setOf(AudioDeviceInfo.TYPE_USB_DEVICE, AudioDeviceInfo.TYPE_USB_HEADSET, AudioDeviceInfo.TYPE_USB_ACCESSORY)
        private val ALLOWED = EXTERNAL + setOf(
            AudioDeviceInfo.TYPE_BUILTIN_SPEAKER, AudioDeviceInfo.TYPE_WIRED_HEADPHONES, AudioDeviceInfo.TYPE_WIRED_HEADSET,
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, AudioDeviceInfo.TYPE_HDMI, AudioDeviceInfo.TYPE_LINE_ANALOG,
            AudioDeviceInfo.TYPE_LINE_DIGITAL,
        )
        fun from(d: AudioDeviceInfo): AudioOut? {
            if (d.type !in ALLOWED) return null
            val ch = d.channelCounts.maxOrNull() ?: 2
            return AudioOut(d.id, d.productName?.toString()?.ifBlank { null } ?: "Saída", d.type, ch)
        }
    }
}

/** Lista as saídas de áudio e avisa quando uma placa é plugada ou removida. */
class AudioOutputs(context: Context, private val onChange: (List<AudioOut>) -> Unit) {
    private val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val callback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(added: Array<out AudioDeviceInfo>) = onChange(list())
        override fun onAudioDevicesRemoved(removed: Array<out AudioDeviceInfo>) = onChange(list())
    }

    fun list(): List<AudioOut> = am.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        .mapNotNull(AudioOut::from).distinctBy { it.key }
        .sortedWith(compareByDescending<AudioOut> { it.isExternal }.thenBy { it.name })

    fun start() = am.registerAudioDeviceCallback(callback, Handler(Looper.getMainLooper()))
    fun stop() = am.unregisterAudioDeviceCallback(callback)
}
