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
    var master by mutableFloatStateOf(prefs.getFloat("masterFader", 0.75f)); private set
    var reverb by mutableStateOf(ReverbState.fromJson(prefs.getString("reverb", null)?.let { runCatching { org.json.JSONObject(it) }.getOrNull() })); private set

    private val scenesFile = File(app.filesDir, "presets.json")
    private val timbresFile = File(app.filesDir, "timbres.json")
    val scenes = mutableStateListOf<Scene>().apply { addAll(listFromJson(scenesFile.takeIf { it.exists() }?.readText(), Scene::fromJson)) }
    val timbres = mutableStateListOf<Timbre>().apply { addAll(listFromJson(timbresFile.takeIf { it.exists() }?.readText(), Timbre::fromJson)) }
    var currentScene by mutableStateOf(prefs.getString("currentScene", null)); private set

    /** Nível exibido nos medidores (0..1, já com queda suave): 8 canais + master. */
    val meters = mutableStateListOf(*Array(SLOT_COUNT + 3) { 0f })   // canais, master, saída L, saída R
    private val meterBuf = FloatArray(SLOT_COUNT + 3)

    var outputs by mutableStateOf(OutputState.fromJson(prefs.getString("outputs", null)?.let { runCatching { org.json.JSONObject(it) }.getOrNull() })); private set
    var audioDevices by mutableStateOf<List<AudioOut>>(emptyList()); private set
    /** [taxa, burst, buffer, id da saída em uso] */
    var streamInfo by mutableStateOf(IntArray(4)); private set
    private val audioOuts = AudioOutputs(app) { list -> audioDevices = list; routeToPreferredDevice() }

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
        Engine.nativeSetMaster(faderGain(master))
        Engine.nativeSetReverb(reverb.size, reverb.tone, reverb.level)
        applyOutputs()
        audioDevices = audioOuts.list()
        audioOuts.start()
        routeToPreferredDevice()
        midi.preferredKey = prefs.getString("midiSource", null)
        midi.start()
        // recarrega os instrumentos salvos
        slots.forEachIndexed { i, s ->
            if (s.path != null) loadSlotState(i, s)
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
            TargetType.SLOT_RELEASE -> if (isOn && s != null) update(t.slot) { it.copy(release = v * 2 - 1) }
            TargetType.SLOT_VELOCITY -> if (isOn && s != null) update(t.slot) { it.copy(velocity = v) }
            TargetType.SLOT_EQ_LOW -> if (isOn && s != null) update(t.slot) { it.copy(eqLow = v * 24 - 12) }
            TargetType.SLOT_EQ_MID -> if (isOn && s != null) update(t.slot) { it.copy(eqMid = v * 24 - 12) }
            TargetType.SLOT_EQ_HIGH -> if (isOn && s != null) update(t.slot) { it.copy(eqHigh = v * 24 - 12) }
            TargetType.SLOT_CUTOFF -> if (isOn && s != null) update(t.slot) { it.copy(cutoff = v) }
            TargetType.SLOT_COMP -> if (isOn && s != null) update(t.slot) { it.copy(comp = v) }
            TargetType.SLOT_REVERB -> if (isOn && s != null) update(t.slot) { it.copy(reverb = v) }
            TargetType.MASTER_VOLUME -> if (isOn) setMasterVolume(v)
            TargetType.OUT_L_VOLUME -> if (isOn) changeOutputs(outputs.copy(volL = v))
            TargetType.OUT_R_VOLUME -> if (isOn) changeOutputs(outputs.copy(volR = v))
            TargetType.OUT_L_MUTE -> if (isOn && value > 0) changeOutputs(outputs.copy(muteL = !outputs.muteL))
            TargetType.OUT_R_MUTE -> if (isOn && value > 0) changeOutputs(outputs.copy(muteR = !outputs.muteR))
            TargetType.REVERB_LEVEL -> if (isOn) changeReverb(reverb.copy(level = v))
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

    fun startLearn(target: Target) = changeLearn(LearnMode.Control(target))
    fun startRangeLearn(slot: Int) = changeLearn(LearnMode.KeyRange(slot))
    fun cancelLearn() = changeLearn(null)

    private fun changeLearn(l: LearnMode?) { learn = l; learnSnapshot = l }

    private fun finishLearn(m: Mapping) {
        // um controle físico faz uma coisa só; e cada função tem um controle só
        mappings.removeAll { (it.kind == m.kind && it.channel == m.channel && it.number == m.number) || it.target == m.target }
        mappings.add(m)
        saveMappings()
        changeLearn(null)
        toast = "${m.target.describe()} → ${m.shortLabel()}"
    }

    private fun learnRangeKey(l: LearnMode.KeyRange, note: Int) {
        if (l.low == null) { changeLearn(l.copy(low = note)); return }
        val lo = minOf(l.low, note); val hi = maxOf(l.low, note)
        update(l.slot) { it.copy(lowKey = lo, highKey = hi) }
        changeLearn(null)
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
        master = v.coerceIn(0f, 1f)
        Engine.nativeSetMaster(faderGain(master))
        scheduleSave()
    }

    /** Chamado pela tela a cada quadro: atualiza os medidores com queda suave. */
    fun pollMeters() {
        Engine.nativeReadMeters(meterBuf)
        for (i in meterBuf.indices) {
            val peak = meterBuf[i].coerceIn(0f, 2f)
            val shown = meters[i]
            val next = if (peak > shown) peak else shown * 0.88f
            if (kotlin.math.abs(next - shown) > 0.002f || (next == 0f && shown != 0f)) meters[i] = if (next < 0.002f) 0f else next
        }
    }

    fun panic() = Engine.nativePanic()

    // ---------------- placa de áudio e saídas ----------------

    fun changeOutputs(o: OutputState) {
        outputs = o
        applyOutputs()
        prefs.edit().putString("outputs", o.toJson().toString()).apply()
    }

    private fun applyOutputs() {
        val o = outputs
        Engine.nativeSetOutputs(o.dual, if (o.muteL) 0f else faderGain(o.volL), if (o.muteR) 0f else faderGain(o.volR))
    }

    /** Escolhe a placa (null = automático: o Android decide, normalmente a placa USB quando plugada). */
    fun selectDevice(d: AudioOut?) {
        changeOutputs(outputs.copy(deviceKey = d?.key))
        Engine.nativeSetDevice(d?.id ?: 0)
        refreshStreamInfo()
        toast = if (d == null) "Saída automática" else "Saída: ${d.name}"
    }

    private fun routeToPreferredDevice() {
        val key = outputs.deviceKey
        val want = audioDevices.firstOrNull { it.key == key }?.id ?: 0
        val current = Engine.nativeStreamInfo()[3]
        // automático: deixa o Android escolher; placa escolhida: reabre nela (ou na padrão, se ela sumiu)
        if (key != null && want != current) Engine.nativeSetDevice(want)
        refreshStreamInfo()
    }

    fun refreshStreamInfo() { streamInfo = Engine.nativeStreamInfo() }

    fun changeReverb(r: ReverbState) {
        reverb = r
        Engine.nativeSetReverb(r.size, r.tone, r.level)
        scheduleSave()
    }

    /**
     * Copia o .sf2 escolhido para a biblioteca do app (um arquivo por nome+tamanho, sem duplicar)
     * e carrega no canal, mantendo os ajustes de som do canal.
     */
    fun importFont(i: Int, uri: Uri) {
        val ctx = getApplication<Application>()
        var name = "instrumento.sf2"; var size = -1L
        ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
            if (c.moveToFirst()) { name = c.getString(0) ?: name; if (!c.isNull(1)) size = c.getLong(1) }
        }
        slots[i] = slots[i].copy(loading = true, error = null)
        io.execute {
            val base = name.substringBeforeLast('.').replace(Regex("[^A-Za-z0-9._ -]"), "_").take(60)
            var dest = File(fontsDir, "${base}_$size.sf2")
            if (size < 0 || !dest.exists() || dest.length() != size) {
                val tmp = File(fontsDir, "import_${System.nanoTime()}.tmp")
                val ok = runCatching {
                    ctx.contentResolver.openInputStream(uri)!!.use { input -> tmp.outputStream().use { input.copyTo(it, 1 shl 16) } }
                }.isSuccess
                if (!ok) {
                    tmp.delete()
                    main.post { slots[i] = slots[i].copy(loading = false, error = "Não consegui ler o arquivo") }
                    return@execute
                }
                dest = File(fontsDir, "${base}_${tmp.length()}.sf2")
                if (dest.exists()) tmp.delete() else tmp.renameTo(dest)
            }
            loadBlocking(i, dest.absolutePath, name, 0) { cleanupFonts() }
        }
    }

    /** Coloca um estado completo no canal, carregando o .sf2 se for outro arquivo. */
    private fun loadSlotState(i: Int, target: SlotState) {
        val path = target.path
        when {
            path == null -> { Engine.nativeUnload(i); slots[i] = SlotState(); scheduleSave() }
            !File(path).exists() -> {
                Engine.nativeUnload(i)
                slots[i] = SlotState(error = "Arquivo não encontrado: ${target.fileName}")
            }
            slots[i].path == path && slots[i].loaded -> update(i) { target.copy(presets = it.presets, loading = false, error = null) }
            else -> {
                slots[i] = target.copy(presets = emptyList(), loading = true, error = null)
                io.execute { loadBlocking(i, path, target.fileName, target.preset) {} }
            }
        }
    }

    private fun loadBlocking(i: Int, path: String, name: String, preset: Int, done: (Boolean) -> Unit) {
        val names = Engine.nativeLoad(i, path, preset)
        main.post {
            if (names == null || names.isEmpty()) {
                slots[i] = slots[i].copy(loading = false, presets = emptyList(), path = null, error = "Arquivo SF2 inválido ou corrompido")
                done(false)
            } else {
                val p = if (preset in names.indices) preset else 0
                update(i) { it.copy(path = path, fileName = name, presets = names.toList(), preset = p, loading = false, error = null) }
                done(true)
            }
        }
    }

    fun removeFont(i: Int) {
        Engine.nativeUnload(i)
        slots[i] = SlotState()
        mappings.removeAll { it.target.slot == i }
        saveMappings()
        scheduleSave()
        cleanupFonts()
    }

    /** Apaga da biblioteca os .sf2 que não estão em nenhum canal, preset geral ou timbre salvo. */
    private fun cleanupFonts() {
        val used = HashSet<String>()
        slots.mapNotNullTo(used) { it.path }
        scenes.forEach { sc -> sc.slots.mapNotNullTo(used) { it.path } }
        timbres.mapNotNullTo(used) { it.slot.path }
        io.execute {
            val recent = System.currentTimeMillis() - 120_000   // não apaga o que acabou de ser importado
            fontsDir.listFiles()?.forEach { f ->
                if (f.name.endsWith(".sf2") && f.absolutePath !in used && f.lastModified() < recent) f.delete()
            }
        }
    }

    // ---------------- presets gerais (cenas) ----------------

    fun saveScene(name: String) {
        val clean = name.trim().ifEmpty { "Preset ${scenes.size + 1}" }
        val scene = Scene(
            clean, System.currentTimeMillis(), slots.map { it.copy(loading = false, error = null) },
            master, reverb, mappings.toList(),
        )
        val idx = scenes.indexOfFirst { it.name.equals(clean, true) }
        if (idx >= 0) scenes[idx] = scene else scenes.add(scene)
        persistScenes()
        currentScene = clean; prefs.edit().putString("currentScene", clean).apply()
        toast = "Preset \"$clean\" salvo"
    }

    fun loadScene(scene: Scene) {
        Engine.nativePanic()
        for (i in 0 until SLOT_COUNT) loadSlotState(i, scene.slots.getOrElse(i) { SlotState() })
        setMasterVolume(scene.master)
        changeReverb(scene.reverb)
        mappings.clear(); mappings.addAll(scene.mappings); saveMappings()
        currentScene = scene.name; prefs.edit().putString("currentScene", scene.name).apply()
        toast = "Preset \"${scene.name}\" carregado"
    }

    fun deleteScene(scene: Scene) {
        scenes.remove(scene)
        persistScenes()
        if (currentScene == scene.name) { currentScene = null; prefs.edit().remove("currentScene").apply() }
        cleanupFonts()
    }

    private fun persistScenes() {
        val json = listToJson(scenes.toList()) { it.toJson() }
        io.execute { scenesFile.writeText(json) }
    }

    // ---------------- timbres salvos ----------------

    fun saveTimbre(i: Int, name: String) {
        val s = slots[i]
        if (!s.loaded) return
        val clean = name.trim().ifEmpty { s.presetName }
        val t = Timbre(clean, System.currentTimeMillis(), s.copy(loading = false, error = null, enabled = true))
        val idx = timbres.indexOfFirst { it.name.equals(clean, true) }
        if (idx >= 0) timbres[idx] = t else timbres.add(t)
        persistTimbres()
        toast = "Timbre \"$clean\" salvo"
    }

    fun loadTimbre(i: Int, t: Timbre) {
        val cur = slots[i]
        // mantém o que é do canal (faixa de teclas, canal MIDI, mute); traz o som do timbre
        loadSlotState(i, t.slot.copy(enabled = cur.enabled, lowKey = cur.lowKey, highKey = cur.highKey, channel = cur.channel)
            .let { if (cur.path == null) it.copy(lowKey = 0, highKey = 127, channel = -1, enabled = true) else it })
        toast = "Timbre \"${t.name}\" no canal ${i + 1}"
    }

    fun deleteTimbre(t: Timbre) {
        timbres.remove(t)
        persistTimbres()
        cleanupFonts()
    }

    private fun persistTimbres() {
        val json = listToJson(timbres.toList()) { it.toJson() }
        io.execute { timbresFile.writeText(json) }
    }

    private val saveTask = Runnable {
        prefs.edit()
            .putString("slots", slotsToJson(slots))
            .putFloat("masterFader", master)
            .putString("reverb", reverb.toJson().toString())
            .apply()
    }

    private fun scheduleSave() {
        main.removeCallbacks(saveTask)
        main.postDelayed(saveTask, 400)
    }

    override fun onCleared() {
        saveTask.run()
        midi.release()
        audioOuts.stop()
        Engine.nativeStop()
        io.shutdown()
    }
}
