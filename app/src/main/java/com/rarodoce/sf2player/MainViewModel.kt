package com.rarodoce.sf2player

import android.app.Application
import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.OpenableColumns
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import java.io.File
import java.util.concurrent.Executors

/** O que o próximo toque no teclado deve "aprender". */
sealed interface LearnMode {
    data class Control(val target: Target) : LearnMode
    data class KeyRange(val slot: Int, val low: Int? = null) : LearnMode
}

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = app.getSharedPreferences("sf2player", Context.MODE_PRIVATE)
    private val main = Handler(Looper.getMainLooper())
    private val io = Executors.newSingleThreadExecutor()
    private val fontsDir = File(app.filesDir, "soundfonts").apply { mkdirs() }

    val slots = mutableStateListOf<SlotState>().apply { addAll(slotsFromJson(prefs.getString("slots", null))) }
    val mappings = mutableStateListOf<Mapping>().apply { addAll(mappingsFromJson(prefs.getString("mappings", null))) }
    var master by mutableFloatStateOf(prefs.getFloat("master", 0.9f)); private set

    var sources by mutableStateOf<List<MidiSource>>(emptyList()); private set
    var connected by mutableStateOf<MidiSource?>(null); private set
    var learn by mutableStateOf<LearnMode?>(null); private set
    var lastEvent by mutableStateOf(""); private set
    var activity by mutableStateOf(0L); private set   // muda a cada mensagem: faz o LED piscar
    var audioOk by mutableStateOf(true); private set
    var toast by mutableStateOf<String?>(null)

    /** Mapeamentos lidos pela thread de MIDI: cópia imutável, trocada a cada mudança. */
    @Volatile private var mapSnapshot: List<Mapping> = mappings.toList()
    @Volatile private var learnSnapshot: LearnMode? = null

    private val midi = MidiHub(
        app,
        onSourcesChanged = { list -> main.post { sources = list } },
        onConnected = { src -> main.post { connected = src } },
        onMessage = ::onMidi,
    )

    init {
        audioOk = Engine.nativeStart()
        Engine.nativeSetMaster(master)
        midi.preferredKey = prefs.getString("midiSource", null)
        midi.start()
        // recarrega os instrumentos salvos
        slots.forEachIndexed { i, s ->
            if (s.path != null) {
                if (File(s.path).exists()) loadFromPath(i, s.path, s.fileName, s.preset)
                else slots[i] = SlotState()
            }
        }
    }

    // ---------------- MIDI ----------------

    private fun onMidi(status: Int, d1: Int, d2: Int) {
        val type = status and 0xF0
        val ch = status and 0x0F
        when (type) {
            0x90, 0x80 -> {
                val on = type == 0x90 && d2 > 0
                if (on) post("Nota ${noteName(d1)} · vel $d2 · canal ${ch + 1}")
                if (handleSpecial(SourceKind.NOTE, ch, d1, if (on) d2 else 0, on)) return
                if (on) Engine.nativeNoteOn(ch, d1, d2) else Engine.nativeNoteOff(ch, d1)
            }
            0xB0 -> {
                post("CC $d1 = $d2 · canal ${ch + 1}")
                if (handleSpecial(SourceKind.CC, ch, d1, d2, true)) return
                Engine.nativeControl(ch, d1, d2)
            }
            0xE0 -> Engine.nativePitchBend(ch, d1 or (d2 shl 7))
        }
    }

    private fun post(text: String) = main.post { lastEvent = text; activity = System.nanoTime() }

    /** Aprendizado e mapeamentos. Retorna true se a mensagem foi usada e não deve tocar som. */
    private fun handleSpecial(kind: SourceKind, ch: Int, n: Int, value: Int, isOn: Boolean): Boolean {
        when (val l = learnSnapshot) {
            is LearnMode.Control -> {
                if (!isOn || (kind == SourceKind.CC && value == 0 && !l.target.type.continuous)) return true
                main.post { finishLearn(Mapping(kind, ch, n, l.target)) }
                return true
            }
            is LearnMode.KeyRange -> if (kind == SourceKind.NOTE) {
                if (isOn) main.post { learnRangeKey(l, n) }
                return true
            }
            null -> Unit
        }
        val hits = mapSnapshot.filter { it.matches(kind, ch, n) }
        if (hits.isEmpty()) return false
        main.post { hits.forEach { applyMapping(it.target, value, isOn) } }
        return true
    }

    private fun applyMapping(t: Target, value: Int, isOn: Boolean) {
        val v = value / 127f
        val s = slots.getOrNull(t.slot)
        when (t.type) {
            TargetType.SLOT_VOLUME -> if (isOn && s != null) update(t.slot) { it.copy(volume = v) }
            TargetType.SLOT_PAN -> if (isOn && s != null) update(t.slot) { it.copy(pan = v) }
            TargetType.MASTER_VOLUME -> if (isOn) setMasterVolume(v * 1.2f)
            // botões: só reage ao apertar (valor > 0)
            TargetType.SLOT_TOGGLE -> if (isOn && value > 0 && s != null) update(t.slot) { it.copy(enabled = !it.enabled) }
            TargetType.SLOT_PRESET_NEXT -> if (isOn && value > 0 && s?.loaded == true) setPreset(t.slot, (s.preset + 1) % s.presets.size)
            TargetType.SLOT_PRESET_PREV -> if (isOn && value > 0 && s?.loaded == true) setPreset(t.slot, (s.preset - 1 + s.presets.size) % s.presets.size)
            TargetType.PANIC -> if (isOn && value > 0) panic()
        }
    }

    fun connect(src: MidiSource) {
        midi.preferredKey = src.key
        prefs.edit().putString("midiSource", src.key).apply()
        midi.connect(src)
    }

    // ---------------- aprendizado ----------------

    fun startLearn(target: Target) = setLearn(LearnMode.Control(target))
    fun startRangeLearn(slot: Int) = setLearn(LearnMode.KeyRange(slot))
    fun cancelLearn() = setLearn(null)

    private fun setLearn(l: LearnMode?) { learn = l; learnSnapshot = l }

    private fun finishLearn(m: Mapping) {
        // um controle físico faz uma coisa só; e cada função tem um controle só
        mappings.removeAll { (it.kind == m.kind && it.channel == m.channel && it.number == m.number) || it.target == m.target }
        mappings.add(m)
        saveMappings()
        setLearn(null)
        toast = "${m.target.describe()} → ${m.shortLabel()}"
    }

    private fun learnRangeKey(l: LearnMode.KeyRange, note: Int) {
        if (l.low == null) { setLearn(l.copy(low = note)); return }
        val lo = minOf(l.low, note); val hi = maxOf(l.low, note)
        update(l.slot) { it.copy(lowKey = lo, highKey = hi) }
        setLearn(null)
        toast = "Instrumento ${l.slot + 1}: ${noteName(lo)} a ${noteName(hi)}"
    }

    fun mappingFor(target: Target) = mappings.firstOrNull { it.target == target }

    fun removeMapping(m: Mapping) { mappings.remove(m); saveMappings() }

    private fun saveMappings() {
        mapSnapshot = mappings.toList()
        prefs.edit().putString("mappings", mappingsToJson(mappings)).apply()
    }

    // ---------------- instrumentos ----------------

    fun update(i: Int, f: (SlotState) -> SlotState) {
        val n = f(slots[i])
        slots[i] = n
        if (n.loaded) Engine.apply(i, n)
        scheduleSave()
    }

    fun setPreset(i: Int, p: Int) = update(i) { it.copy(preset = p) }

    fun setMasterVolume(v: Float) {
        master = v.coerceIn(0f, 1.2f)
        Engine.nativeSetMaster(master)
        scheduleSave()
    }

    fun panic() = Engine.nativePanic()

    /** Copia o .sf2 escolhido para dentro do app e carrega no slot. */
    fun importFont(i: Int, uri: Uri) {
        val ctx = getApplication<Application>()
        val name = ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { c -> if (c.moveToFirst()) c.getString(0) else null } ?: "instrumento.sf2"
        slots[i] = slots[i].copy(loading = true, error = null)
        io.execute {
            val dest = File(fontsDir, "slot${i}_${System.currentTimeMillis()}.sf2")
            val ok = runCatching {
                ctx.contentResolver.openInputStream(uri)!!.use { input -> dest.outputStream().use { input.copyTo(it, 1 shl 16) } }
            }.isSuccess
            if (!ok) {
                dest.delete()
                main.post { slots[i] = slots[i].copy(loading = false, error = "Não consegui ler o arquivo") }
                return@execute
            }
            val old = slots[i].path
            loadBlocking(i, dest.absolutePath, name, 0, keepSettings = true) { success ->
                if (success && old != null && old != dest.absolutePath) File(old).delete()
                if (!success) dest.delete()
            }
        }
    }

    private fun loadFromPath(i: Int, path: String, name: String, preset: Int) {
        slots[i] = slots[i].copy(loading = true)
        io.execute { loadBlocking(i, path, name, preset, keepSettings = true) {} }
    }

    private fun loadBlocking(i: Int, path: String, name: String, preset: Int, keepSettings: Boolean, done: (Boolean) -> Unit) {
        val names = Engine.nativeLoad(i, path, preset)
        main.post {
            if (names == null || names.isEmpty()) {
                slots[i] = slots[i].copy(loading = false, error = "Arquivo SF2 inválido ou corrompido")
                done(false)
            } else {
                val base = if (keepSettings) slots[i] else SlotState()
                val p = if (preset in names.indices) preset else 0
                update(i) { base.copy(path = path, fileName = name, presets = names.toList(), preset = p, loading = false, error = null) }
                done(true)
            }
        }
    }

    fun removeFont(i: Int) {
        val old = slots[i].path
        Engine.nativeUnload(i)
        slots[i] = SlotState()
        mappings.removeAll { it.target.slot == i }
        saveMappings()
        old?.let { io.execute { File(it).delete() } }
        scheduleSave()
    }

    private val saveTask = Runnable {
        prefs.edit()
            .putString("slots", slotsToJson(slots))
            .putFloat("master", master)
            .apply()
    }

    private fun scheduleSave() {
        main.removeCallbacks(saveTask)
        main.postDelayed(saveTask, 400)
    }

    override fun onCleared() {
        saveTask.run()
        midi.release()
        Engine.nativeStop()
        io.shutdown()
    }
}
