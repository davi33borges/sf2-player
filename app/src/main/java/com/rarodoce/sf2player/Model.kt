package com.rarodoce.sf2player

import org.json.JSONArray
import org.json.JSONObject

const val SLOT_COUNT = 8

data class SlotState(
    val path: String? = null,          // cópia do .sf2 dentro do app
    val fileName: String = "",
    val presets: List<String> = emptyList(),
    val preset: Int = 0,
    val enabled: Boolean = true,
    val volume: Float = 0.8f,
    val pan: Float = 0.5f,
    val transpose: Int = 0,
    val lowKey: Int = 0,
    val highKey: Int = 127,
    val channel: Int = -1,             // -1 = todos
    val release: Float = 0f,           // -1..1 (0 = release original do SF2)
    val velocity: Float = 1f,          // sensibilidade 0..1
    val eqLow: Float = 0f,             // dB
    val eqMid: Float = 0f,
    val eqHigh: Float = 0f,
    val cutoff: Float = 1f,            // 0..1 (1 = aberto)
    val comp: Float = 0f,              // 0..1
    val loading: Boolean = false,
    val error: String? = null,
) {
    val loaded get() = path != null && presets.isNotEmpty()
    val presetName get() = presets.getOrNull(preset) ?: "—"
    val displayName get() = fileName.removeSuffix(".sf2").removeSuffix(".SF2")

    fun toJson() = JSONObject().apply {
        put("path", path ?: JSONObject.NULL); put("fileName", fileName); put("preset", preset)
        put("enabled", enabled); put("volume", volume.toDouble()); put("pan", pan.toDouble())
        put("transpose", transpose); put("lowKey", lowKey); put("highKey", highKey); put("channel", channel)
        put("release", release.toDouble()); put("velocity", velocity.toDouble()); put("eqLow", eqLow.toDouble())
        put("eqMid", eqMid.toDouble()); put("eqHigh", eqHigh.toDouble()); put("cutoff", cutoff.toDouble()); put("comp", comp.toDouble())
    }

    /** Volta o timbre ao som original do SF2 (mantém arquivo, preset e teclado). */
    fun resetSound() = copy(release = 0f, velocity = 1f, eqLow = 0f, eqMid = 0f, eqHigh = 0f, cutoff = 1f, comp = 0f)

    companion object {
        fun fromJson(o: JSONObject) = SlotState(
            path = if (o.isNull("path")) null else o.optString("path"),
            fileName = o.optString("fileName"),
            preset = o.optInt("preset"),
            enabled = o.optBoolean("enabled", true),
            volume = o.optDouble("volume", 0.8).toFloat(),
            pan = o.optDouble("pan", 0.5).toFloat(),
            transpose = o.optInt("transpose"),
            lowKey = o.optInt("lowKey", 0),
            highKey = o.optInt("highKey", 127),
            channel = o.optInt("channel", -1),
            release = o.optDouble("release", 0.0).toFloat(),
            velocity = o.optDouble("velocity", 1.0).toFloat(),
            eqLow = o.optDouble("eqLow", 0.0).toFloat(),
            eqMid = o.optDouble("eqMid", 0.0).toFloat(),
            eqHigh = o.optDouble("eqHigh", 0.0).toFloat(),
            cutoff = o.optDouble("cutoff", 1.0).toFloat(),
            comp = o.optDouble("comp", 0.0).toFloat(),
        )
    }
}

/** O que um controle do teclado faz no app. */
enum class TargetType(val label: String, val continuous: Boolean) {
    SLOT_VOLUME("Fader", true),
    SLOT_PAN("Pan", true),
    SLOT_RELEASE("Release", true),
    SLOT_VELOCITY("Velocity", true),
    SLOT_EQ_LOW("Grave", true),
    SLOT_EQ_MID("Médio", true),
    SLOT_EQ_HIGH("Agudo", true),
    SLOT_CUTOFF("Cutoff", true),
    SLOT_COMP("Compressor", true),
    SLOT_TOGGLE("Liga/desliga (mute)", false),
    SLOT_PRESET_NEXT("Próximo preset", false),
    SLOT_PRESET_PREV("Preset anterior", false),
    MASTER_VOLUME("Volume geral", true),
    PANIC("Parar todos os sons", false),
}

data class Target(val type: TargetType, val slot: Int = -1) {
    val usesSlot get() = type.name.startsWith("SLOT_")
    fun describe() = if (usesSlot) "${type.label} · Instrumento ${slot + 1}" else type.label
}

enum class SourceKind { CC, NOTE }

data class Mapping(val kind: SourceKind, val channel: Int, val number: Int, val target: Target) {
    fun sourceLabel() = when (kind) {
        SourceKind.CC -> "CC $number · canal ${channel + 1}"
        SourceKind.NOTE -> "Nota ${noteName(number)} · canal ${channel + 1}"
    }
    fun shortLabel() = when (kind) {
        SourceKind.CC -> "CC $number"
        SourceKind.NOTE -> noteName(number)
    }
    fun matches(k: SourceKind, ch: Int, n: Int) = kind == k && channel == ch && number == n

    fun toJson() = JSONObject().apply {
        put("kind", kind.name); put("channel", channel); put("number", number)
        put("target", target.type.name); put("slot", target.slot)
    }

    companion object {
        fun fromJson(o: JSONObject): Mapping? = runCatching {
            Mapping(
                SourceKind.valueOf(o.getString("kind")), o.getInt("channel"), o.getInt("number"),
                Target(TargetType.valueOf(o.getString("target")), o.optInt("slot", -1)),
            )
        }.getOrNull()
    }
}

private val NOTE_NAMES = arrayOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")

/** Nome da nota no padrão em que o dó central (60) é C4. */
fun noteName(n: Int) = NOTE_NAMES[n.mod(12)] + (n / 12 - 1)

fun slotsToJson(slots: List<SlotState>) = JSONArray().apply { slots.forEach { put(it.toJson()) } }.toString()
fun mappingsToJson(m: List<Mapping>) = JSONArray().apply { m.forEach { put(it.toJson()) } }.toString()

fun slotsFromJson(s: String?): List<SlotState> {
    val list = MutableList(SLOT_COUNT) { SlotState() }
    if (s == null) return list
    runCatching {
        val a = JSONArray(s)
        for (i in 0 until minOf(a.length(), SLOT_COUNT)) list[i] = SlotState.fromJson(a.getJSONObject(i))
    }
    return list
}

fun mappingsFromJson(s: String?): List<Mapping> {
    if (s == null) return emptyList()
    return runCatching {
        val a = JSONArray(s)
        (0 until a.length()).mapNotNull { Mapping.fromJson(a.getJSONObject(it)) }
    }.getOrDefault(emptyList())
}
