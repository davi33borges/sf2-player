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
    val velocity: Float = 0.5f,        // 0 = fixa, 0,5 = normal, 1 = muito sensível
    val eqLow: Float = 0f,             // dB
    val eqMid: Float = 0f,
    val eqHigh: Float = 0f,
    val cutoff: Float = 1f,            // 0..1 (1 = aberto)
    val comp: Float = 0f,              // 0..1
    val reverb: Float = 0.2f,          // envio para o reverb 0..1
    val output: Int = 0,               // modo 2 saídas: 0 = L e R, 1 = só L, 2 = só R
    val delayTime: Float = 0.6f,       // 0..1 (≈ 400 ms)
    val delayMix: Float = 0f,          // intensidade 0..1 (0 = desligado)
    val poly: Int = 32,                // polifonia (vozes)
    val gain: Float = 0.6f,            // ganho do timbre: 0,6 = 0 dB (0 = -30 dB, 1 = +12 dB)
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
        put("release", release.toDouble()); put("velCurve", velocity.toDouble()); put("eqLow", eqLow.toDouble())
        put("eqMid", eqMid.toDouble()); put("eqHigh", eqHigh.toDouble()); put("cutoff", cutoff.toDouble()); put("comp", comp.toDouble())
        put("reverb", reverb.toDouble()); put("output", output)
        put("delayTime", delayTime.toDouble()); put("delayMix", delayMix.toDouble()); put("poly", poly); put("gain", gain.toDouble())
    }

    /** Volta o timbre ao som original do SF2 (mantém arquivo, preset e teclado). */
    fun resetSound() = copy(gain = 0.6f, release = 0f, velocity = 0.5f, delayMix = 0f, delayTime = 0.6f, eqLow = 0f, eqMid = 0f, eqHigh = 0f, cutoff = 1f, comp = 0f, reverb = 0.2f)

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
            velocity = o.optDouble("velCurve", 0.5).toFloat(),
            eqLow = o.optDouble("eqLow", 0.0).toFloat(),
            eqMid = o.optDouble("eqMid", 0.0).toFloat(),
            eqHigh = o.optDouble("eqHigh", 0.0).toFloat(),
            cutoff = o.optDouble("cutoff", 1.0).toFloat(),
            comp = o.optDouble("comp", 0.0).toFloat(),
            reverb = o.optDouble("reverb", 0.2).toFloat(),
            output = o.optInt("output", 0),
            delayTime = o.optDouble("delayTime", 0.6).toFloat(),
            delayMix = o.optDouble("delayMix", 0.0).toFloat(),
            poly = o.optInt("poly", 32),
            gain = o.optDouble("gain", 0.6).toFloat(),
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
    SLOT_REVERB("Reverb", true),
    SLOT_GAIN("Ganho", true),
    SLOT_DELAY_MIX("Delay (intensidade)", true),
    SLOT_DELAY_TIME("Delay (tempo)", true),
    SLOT_TOGGLE("Liga/desliga (mute)", false),
    SLOT_PRESET_NEXT("Próximo preset", false),
    SLOT_PRESET_PREV("Preset anterior", false),
    MASTER_VOLUME("Volume geral", true),
    REVERB_LEVEL("Volume do reverb", true),
    OUT_L_VOLUME("Volume da saída L", true),
    OUT_R_VOLUME("Volume da saída R", true),
    OUT_L_MUTE("Mute da saída L", false),
    OUT_R_MUTE("Mute da saída R", false),
    PANIC("Parar todos os sons", false),
    PAD_TRIGGER("Disparar pad", false),
    PADS_STOP("Parar todos os pads", false),
    PADS_VOLUME("Volume dos pads", true),
}

data class Target(val type: TargetType, val slot: Int = -1) {
    val usesSlot get() = type.name.startsWith("SLOT_")
    val isPad get() = type == TargetType.PAD_TRIGGER
    fun describe() = when {
        usesSlot -> "${type.label} · Instrumento ${slot + 1}"
        isPad -> "Pad ${slot + 1}"
        else -> type.label
    }
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

data class ReverbState(val size: Float = 0.6f, val tone: Float = 0.6f, val level: Float = 0.35f) {
    fun toJson() = JSONObject().apply { put("size", size.toDouble()); put("tone", tone.toDouble()); put("level", level.toDouble()) }
    companion object {
        fun fromJson(o: JSONObject?) = if (o == null) ReverbState() else ReverbState(
            o.optDouble("size", 0.6).toFloat(), o.optDouble("tone", 0.6).toFloat(), o.optDouble("level", 0.35).toFloat(),
        )
    }
}

const val PAD_COUNT = 8

/** Pad de sample (estilo octapad). */
data class PadState(
    val path: String? = null,
    val fileName: String = "",
    val name: String = "",
    val color: Int = 0,
    val volume: Float = 0.75f,         // posição de fader (0,75 = 0 dB)
    val pitch: Int = 0,                // semitons
    val mode: Int = 0,                 // 0 toca até o fim, 1 enquanto segura, 2 loop
    val velocity: Boolean = true,
    val output: Int = 0,
    val seconds: Float = 0f,
    val loading: Boolean = false,
    val error: String? = null,
) {
    val loaded get() = path != null && seconds > 0f
    val label get() = name.ifBlank { fileName.substringBeforeLast('.') }
    fun toJson() = JSONObject().apply {
        put("path", path ?: JSONObject.NULL); put("fileName", fileName); put("name", name); put("color", color)
        put("volume", volume.toDouble()); put("pitch", pitch); put("mode", mode); put("velocity", velocity); put("output", output)
    }
    companion object {
        fun fromJson(o: JSONObject) = PadState(
            path = if (o.isNull("path")) null else o.optString("path"), fileName = o.optString("fileName"),
            name = o.optString("name"), color = o.optInt("color"), volume = o.optDouble("volume", 0.75).toFloat(),
            pitch = o.optInt("pitch"), mode = o.optInt("mode"), velocity = o.optBoolean("velocity", true), output = o.optInt("output"),
        )
    }
}

fun padsFromJson(s: String?): List<PadState> {
    val list = MutableList(PAD_COUNT) { PadState(color = it) }
    listFromJson(s, PadState::fromJson).take(PAD_COUNT).forEachIndexed { i, p -> list[i] = p }
    return list
}

/** Preset geral: todos os canais, master, reverb e mapeamentos. */
data class Scene(
    val name: String,
    val savedAt: Long,
    val slots: List<SlotState>,
    val master: Float,
    val reverb: ReverbState,
    val mappings: List<Mapping>,
    val pads: List<PadState> = emptyList(),
    val padsVolume: Float = 0.75f,
) {
    val instrumentCount get() = slots.count { it.path != null }
    fun toJson() = JSONObject().apply {
        put("name", name); put("savedAt", savedAt); put("master", master.toDouble())
        put("slots", JSONArray(slotsToJson(slots))); put("reverb", reverb.toJson())
        put("mappings", JSONArray(mappingsToJson(mappings)))
        put("pads", JSONArray(listToJson(pads) { it.toJson() })); put("padsVolume", padsVolume.toDouble())
    }
    companion object {
        fun fromJson(o: JSONObject) = Scene(
            o.optString("name", "Sem nome"), o.optLong("savedAt"),
            slotsFromJson(o.optJSONArray("slots")?.toString()),
            o.optDouble("master", 0.75).toFloat(),
            ReverbState.fromJson(o.optJSONObject("reverb")),
            mappingsFromJson(o.optJSONArray("mappings")?.toString()),
            o.optJSONArray("pads")?.let { padsFromJson(it.toString()) } ?: emptyList(),
            o.optDouble("padsVolume", 0.75).toFloat(),
        )
    }
}

/** Timbre salvo: um .sf2 com preset e todos os ajustes de som de um canal. */
data class Timbre(val name: String, val savedAt: Long, val slot: SlotState) {
    fun toJson() = JSONObject().apply { put("name", name); put("savedAt", savedAt); put("slot", slot.toJson()) }
    companion object {
        fun fromJson(o: JSONObject) = Timbre(o.optString("name", "Timbre"), o.optLong("savedAt"), SlotState.fromJson(o.getJSONObject("slot")))
    }
}

fun <T> listToJson(items: List<T>, f: (T) -> JSONObject) = JSONArray().apply { items.forEach { put(f(it)) } }.toString()
fun <T> listFromJson(s: String?, f: (JSONObject) -> T): List<T> {
    if (s.isNullOrBlank()) return emptyList()
    return runCatching { val a = JSONArray(s); (0 until a.length()).mapNotNull { runCatching { f(a.getJSONObject(it)) }.getOrNull() } }
        .getOrDefault(emptyList())
}

/** Saídas da placa de áudio: estéreo normal ou L e R independentes, cada uma com volume e mute. */
data class OutputState(
    val dual: Boolean = false,
    val volL: Float = 0.75f, val volR: Float = 0.75f,
    val muteL: Boolean = false, val muteR: Boolean = false,
    val deviceKey: String? = null,      // placa escolhida (null = automática)
) {
    fun toJson() = JSONObject().apply {
        put("dual", dual); put("volL", volL.toDouble()); put("volR", volR.toDouble())
        put("muteL", muteL); put("muteR", muteR); put("deviceKey", deviceKey ?: JSONObject.NULL)
    }
    companion object {
        fun fromJson(o: JSONObject?) = if (o == null) OutputState() else OutputState(
            o.optBoolean("dual"), o.optDouble("volL", 0.75).toFloat(), o.optDouble("volR", 0.75).toFloat(),
            o.optBoolean("muteL"), o.optBoolean("muteR"), if (o.isNull("deviceKey")) null else o.optString("deviceKey"),
        )
    }
}
