package com.rarodoce.sf2player.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Piano
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.StopCircle
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Usb
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.rarodoce.sf2player.LearnMode
import com.rarodoce.sf2player.MainViewModel
import com.rarodoce.sf2player.SLOT_COUNT
import com.rarodoce.sf2player.SlotState
import com.rarodoce.sf2player.Target
import com.rarodoce.sf2player.TargetType
import com.rarodoce.sf2player.cutoffLabel
import com.rarodoce.sf2player.faderDb
import com.rarodoce.sf2player.noteName
import com.rarodoce.sf2player.releaseMul
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

@Composable
fun MainScreen(vm: MainViewModel) {
    val snack = remember { SnackbarHostState() }
    var showMidi by remember { mutableStateOf(false) }
    var pickSlot by remember { mutableIntStateOf(-1) }
    var configSlot by remember { mutableIntStateOf(-1) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null && pickSlot >= 0) vm.importFont(pickSlot, uri)
        pickSlot = -1
    }
    val pick: (Int) -> Unit = { i -> pickSlot = i; picker.launch(arrayOf("*/*")) }

    LaunchedEffect(vm.toast) { vm.toast?.let { snack.showSnackbar(it); vm.toast = null } }
    // medidores: lê os níveis a cada quadro da tela
    LaunchedEffect(Unit) { while (true) { withFrameNanos { }; vm.pollMeters() } }

    Box(Modifier.fillMaxSize().background(Bg).safeDrawingPadding()) {
        Column(Modifier.fillMaxSize()) {
            TopBar(vm, onMidi = { showMidi = true })
            AnimatedVisibility(visible = vm.learn != null) { LearnBanner(vm) }
            Mixer(vm, Modifier.weight(1f), onConfig = { configSlot = it }, onPick = pick)
        }
        SnackbarHost(snack, Modifier.align(Alignment.BottomCenter).padding(12.dp))
    }

    if (showMidi) MidiDialog(vm) { showMidi = false }
    if (configSlot >= 0) {
        val i = configSlot
        if (vm.slots[i].loaded) ChannelConfig(vm, i, onClose = { configSlot = -1 }, onPick = { pick(i) })
        else LaunchedEffect(i) { configSlot = -1 }
    }
}

// ------------------------------------------------------------------ barra do topo

@Composable
private fun TopBar(vm: MainViewModel, onMidi: () -> Unit) {
    var lit by remember { mutableStateOf(false) }
    LaunchedEffect(vm.activity) { if (vm.activity != 0L) { lit = true; delay(90); lit = false } }
    val led by animateColorAsState(if (lit) Accent else if (vm.connected != null) Ok else InkDim, tween(80), label = "led")

    Row(
        Modifier.fillMaxWidth().height(44.dp).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Piano, null, tint = Accent, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(8.dp))
        Text("SF2 Player", fontWeight = FontWeight.Black, fontSize = 17.sp, color = Ink)
        Spacer(Modifier.width(14.dp))
        Text(
            if (vm.audioOk) vm.lastEvent.ifEmpty { "Toque no teclado para testar" } else "Erro ao abrir o áudio",
            color = if (vm.audioOk) InkDim else Color(0xFFFF5C5C), fontSize = 12.sp, fontFamily = Mono,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
        )
        Row(
            Modifier.clip(RoundedCornerShape(50)).background(Panel).border(1.dp, Line, RoundedCornerShape(50))
                .clickable(onClick = onMidi).padding(horizontal = 12.dp, vertical = 6.dp).widthIn(max = 240.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(9.dp).clip(CircleShape).background(led))
            Spacer(Modifier.width(8.dp))
            Icon(Icons.Rounded.Usb, null, tint = InkDim, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text(vm.connected?.deviceName ?: "Sem teclado", fontSize = 13.sp, color = Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        IconButton(onClick = vm::panic) { Icon(Icons.Rounded.StopCircle, "Parar todos os sons", tint = Color(0xFFFF5C5C)) }
    }
}

@Composable
private fun LearnBanner(vm: MainViewModel) {
    val l = vm.learn ?: return
    val pulse by rememberInfiniteTransition(label = "p").animateFloat(
        0.55f, 1f, infiniteRepeatable(tween(600), RepeatMode.Reverse), label = "a",
    )
    val text = when (l) {
        is LearnMode.Control -> "Mexa ou aperte o controle do teclado para: ${l.target.describe()}"
        is LearnMode.KeyRange ->
            if (l.low == null) "Instrumento ${l.slot + 1}: toque a nota MAIS GRAVE da faixa"
            else "Agora toque a nota MAIS AGUDA (a primeira foi ${noteName(l.low)})"
    }
    Row(
        Modifier.fillMaxWidth().background(Accent.copy(alpha = 0.16f * pulse + 0.06f)).padding(horizontal = 16.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(Accent.copy(alpha = pulse)))
        Spacer(Modifier.width(10.dp))
        Text(text, color = Ink, fontSize = 13.sp, modifier = Modifier.weight(1f))
        TextButton(onClick = vm::cancelLearn) { Text("Cancelar") }
    }
}

// ------------------------------------------------------------------ mesa

@Composable
private fun Mixer(vm: MainViewModel, modifier: Modifier, onConfig: (Int) -> Unit, onPick: (Int) -> Unit) {
    BoxWithConstraints(modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, bottom = 8.dp)) {
        val masterW = 88.dp
        val gap = 6.dp
        val stripW = maxOf(84.dp, (maxWidth - masterW - gap * SLOT_COUNT - 4.dp) / SLOT_COUNT)
        Row(Modifier.fillMaxSize()) {
            Row(
                Modifier.weight(1f).fillMaxHeight().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(gap),
            ) {
                vm.slots.forEachIndexed { i, s ->
                    ChannelStrip(vm, i, s, Modifier.width(stripW).fillMaxHeight(), onConfig = { onConfig(i) }, onPick = { onPick(i) })
                }
            }
            Spacer(Modifier.width(gap))
            MasterStrip(vm, Modifier.width(masterW).fillMaxHeight())
        }
    }
}

@Composable
private fun ChannelStrip(vm: MainViewModel, i: Int, s: SlotState, modifier: Modifier, onConfig: () -> Unit, onPick: () -> Unit) {
    val color = SlotColors[i]
    val on = s.loaded && s.enabled
    Column(
        modifier.clip(RoundedCornerShape(12.dp)).background(Panel)
            .border(1.dp, if (on) color.copy(alpha = 0.35f) else Line, RoundedCornerShape(12.dp)),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // cabeçalho: toque para configurar o timbre (ou adicionar um .sf2)
        Column(
            Modifier.fillMaxWidth().background(if (s.loaded) color.copy(alpha = if (on) 0.2f else 0.07f) else PanelHi)
                .clickable(enabled = !s.loading) { if (s.loaded) onConfig() else onPick() }
                .padding(horizontal = 6.dp, vertical = 5.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${i + 1}", color = if (s.loaded) color else InkDim, fontWeight = FontWeight.Black, fontFamily = Mono, fontSize = 13.sp)
                if (s.loaded) {
                    Spacer(Modifier.width(4.dp))
                    Icon(Icons.Rounded.Tune, "Configurar timbre", tint = Ink.copy(alpha = 0.75f), modifier = Modifier.size(13.dp))
                }
            }
            when {
                s.loading -> CircularProgressIndicator(color = color, strokeWidth = 2.dp, modifier = Modifier.padding(top = 2.dp).size(16.dp))
                s.loaded -> {
                    Text(s.displayName, color = Ink, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1,
                        overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                    Text(s.presetName, color = InkDim, fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                }
                else -> {
                    Icon(Icons.Rounded.Add, null, tint = InkDim, modifier = Modifier.size(16.dp))
                    Text(s.error ?: "Adicionar .sf2", color = if (s.error != null) Color(0xFFFF5C5C) else InkDim,
                        fontSize = 9.sp, maxLines = 2, textAlign = TextAlign.Center)
                }
            }
        }

        Column(
            Modifier.weight(1f).fillMaxWidth().alpha(if (on) 1f else 0.45f).padding(horizontal = 4.dp, vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Knob(s.pan, { v -> vm.update(i) { it.copy(pan = if (kotlin.math.abs(v - 0.5f) < 0.02f) 0.5f else v) } },
                color, size = 30.dp, bipolar = true, enabled = s.loaded)
            Text(panLabel(s.pan), color = InkDim, fontSize = 9.sp, fontFamily = Mono)
            VerticalFader(
                s.volume, { v -> vm.update(i) { it.copy(volume = v) } }, color, level = { vm.meters[i] },
                modifier = Modifier.weight(1f).fillMaxWidth(), enabled = s.loaded,
            )
            Text(if (s.loaded) faderDb(s.volume) else "", color = Ink, fontSize = 10.sp, fontFamily = Mono)
        }

        Row(
            Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, bottom = 5.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            SmallButton(
                if (s.enabled) "ON" else "MUTE", if (s.enabled) color else Color(0xFFFF5C5C),
                Modifier.weight(1f), filled = s.loaded && s.enabled,
            ) { if (s.loaded) vm.update(i) { it.copy(enabled = !it.enabled) } }
            if (s.loaded) LearnChip(vm, Target(TargetType.SLOT_VOLUME, i), color, Modifier.weight(1f))
        }
    }
}

@Composable
private fun MasterStrip(vm: MainViewModel, modifier: Modifier) {
    Column(
        modifier.clip(RoundedCornerShape(12.dp)).background(PanelHi).border(1.dp, Line, RoundedCornerShape(12.dp)),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("MASTER", color = Ink, fontWeight = FontWeight.Black, fontSize = 12.sp, letterSpacing = 1.5.sp,
            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))
        VerticalFader(
            vm.master, vm::setMasterVolume, Ink, level = { vm.meters[SLOT_COUNT] },
            modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 6.dp),
        )
        Text(faderDb(vm.master), color = Ink, fontSize = 10.sp, fontFamily = Mono)
        LearnChip(vm, Target(TargetType.MASTER_VOLUME), Ink, Modifier.padding(start = 6.dp, end = 6.dp, top = 4.dp, bottom = 6.dp).fillMaxWidth())
    }
}

private fun panLabel(p: Float): String {
    val v = ((p - 0.5f) * 200).roundToInt()
    return when { v == 0 -> "C"; v < 0 -> "E${-v}"; else -> "D$v" }
}

private fun dbLabel(db: Float) = (if (db > 0.05f) "+" else "") + "%.1f dB".format(db)

// ------------------------------------------------------------------ configurar timbre

@Composable
private fun ChannelConfig(vm: MainViewModel, i: Int, onClose: () -> Unit, onPick: () -> Unit) {
    val s = vm.slots[i]
    val color = SlotColors[i]
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier.fillMaxSize().padding(10.dp).clip(RoundedCornerShape(16.dp)).background(Panel)
                .border(1.dp, color.copy(alpha = 0.4f), RoundedCornerShape(16.dp)),
        ) {
            Row(
                Modifier.fillMaxWidth().background(color.copy(alpha = 0.14f)).padding(start = 14.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(28.dp).clip(RoundedCornerShape(8.dp)).background(color), contentAlignment = Alignment.Center) {
                    Text("${i + 1}", color = Bg, fontWeight = FontWeight.Black, fontFamily = Mono)
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(s.displayName, color = Ink, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("Timbre e configurações do canal", color = InkDim, fontSize = 11.sp)
                }
                SmallButton("Trocar .sf2", Ink) { onClose(); onPick() }
                Spacer(Modifier.width(6.dp))
                SmallButton("Remover", Color(0xFFFF5C5C)) { onClose(); vm.removeFont(i) }
                IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, "Fechar", tint = Ink) }
            }

            Row(Modifier.weight(1f).fillMaxWidth().padding(10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PresetColumn(vm, i, s, color, Modifier.weight(1f).fillMaxHeight())
                SoundColumn(vm, i, s, color, Modifier.weight(1.5f).fillMaxHeight())
                KeyboardColumn(vm, i, s, color, Modifier.weight(1.15f).fillMaxHeight())
            }
        }
    }
}

@Composable
private fun PresetColumn(vm: MainViewModel, i: Int, s: SlotState, color: Color, modifier: Modifier) {
    var query by remember { mutableStateOf("") }
    val list = s.presets.withIndex().filter { query.isBlank() || it.value.contains(query, true) }
    Column(modifier.clip(RoundedCornerShape(12.dp)).background(Bg).padding(8.dp)) {
        SectionTitle("PRESET · ${s.preset + 1}/${s.presets.size}")
        Row(
            Modifier.padding(vertical = 6.dp).fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(PanelHi)
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.Search, null, tint = InkDim, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Box(Modifier.weight(1f)) {
                if (query.isEmpty()) Text("Buscar", color = InkDim, fontSize = 13.sp)
                BasicTextField(query, { query = it }, singleLine = true, textStyle = TextStyle(color = Ink, fontSize = 13.sp),
                    cursorBrush = SolidColor(color), modifier = Modifier.fillMaxWidth())
            }
        }
        LazyColumn(Modifier.weight(1f)) {
            itemsIndexed(list) { _, p ->
                val sel = p.index == s.preset
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(7.dp))
                        .background(if (sel) color.copy(alpha = 0.2f) else Color.Transparent)
                        .clickable { vm.setPreset(i, p.index) }.padding(horizontal = 8.dp, vertical = 8.dp),
                ) {
                    Text("${p.index + 1}".padStart(3), fontFamily = Mono, color = InkDim, fontSize = 11.sp)
                    Spacer(Modifier.width(8.dp))
                    Text(p.value, color = if (sel) color else Ink, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal)
                }
            }
        }
        Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Preset − / +", color = InkDim, fontSize = 10.sp, modifier = Modifier.weight(1f))
            LearnChip(vm, Target(TargetType.SLOT_PRESET_PREV, i), color)
            Spacer(Modifier.width(4.dp))
            LearnChip(vm, Target(TargetType.SLOT_PRESET_NEXT, i), color)
        }
    }
}

@Composable
private fun SoundColumn(vm: MainViewModel, i: Int, s: SlotState, color: Color, modifier: Modifier) {
    val upd: ((SlotState) -> SlotState) -> Unit = { f -> vm.update(i, f) }
    Column(modifier.clip(RoundedCornerShape(12.dp)).background(Bg).padding(8.dp).verticalScroll(rememberScrollState())) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionTitle("TIMBRE", Modifier.weight(1f))
            SmallButton("Som original", InkDim) { upd { it.resetSound() } }
        }
        Text("Arraste os botões para cima ou para baixo. Toque duplo volta ao padrão.", color = InkDim, fontSize = 10.sp,
            modifier = Modifier.padding(top = 2.dp, bottom = 4.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            KnobCell(vm, "Release", "%.2f×".format(releaseMul(s.release)), (s.release + 1) / 2,
                { v -> upd { it.copy(release = v * 2 - 1) } }, color, Target(TargetType.SLOT_RELEASE, i), bipolar = true)
            KnobCell(vm, "Velocity", if (s.velocity < 0.01f) "Fixa" else "${(s.velocity * 100).roundToInt()}%", s.velocity,
                { v -> upd { it.copy(velocity = v) } }, color, Target(TargetType.SLOT_VELOCITY, i), default = 1f)
            KnobCell(vm, "Cutoff", cutoffLabel(s.cutoff), s.cutoff,
                { v -> upd { it.copy(cutoff = v) } }, color, Target(TargetType.SLOT_CUTOFF, i), default = 1f)
            KnobCell(vm, "Compressor", if (s.comp < 0.01f) "Desl." else "${(s.comp * 100).roundToInt()}%", s.comp,
                { v -> upd { it.copy(comp = v) } }, color, Target(TargetType.SLOT_COMP, i))
        }
        HorizontalDivider(color = Line, modifier = Modifier.padding(vertical = 6.dp))
        SectionTitle("EQUALIZADOR")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            KnobCell(vm, "Grave", dbLabel(s.eqLow), (s.eqLow + 12) / 24, { v -> upd { it.copy(eqLow = v * 24 - 12) } },
                color, Target(TargetType.SLOT_EQ_LOW, i), bipolar = true)
            KnobCell(vm, "Médio", dbLabel(s.eqMid), (s.eqMid + 12) / 24, { v -> upd { it.copy(eqMid = v * 24 - 12) } },
                color, Target(TargetType.SLOT_EQ_MID, i), bipolar = true)
            KnobCell(vm, "Agudo", dbLabel(s.eqHigh), (s.eqHigh + 12) / 24, { v -> upd { it.copy(eqHigh = v * 24 - 12) } },
                color, Target(TargetType.SLOT_EQ_HIGH, i), bipolar = true)
            KnobCell(vm, "Pan", panLabel(s.pan), s.pan, { v -> upd { it.copy(pan = v) } },
                color, Target(TargetType.SLOT_PAN, i), bipolar = true)
        }
    }
}

@Composable
private fun KeyboardColumn(vm: MainViewModel, i: Int, s: SlotState, color: Color, modifier: Modifier) {
    Column(modifier.clip(RoundedCornerShape(12.dp)).background(Bg).padding(8.dp).verticalScroll(rememberScrollState())) {
        SectionTitle("TECLAS (SPLIT)")
        Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("${noteName(s.lowKey)} – ${noteName(s.highKey)}", fontFamily = Mono, color = Ink, fontSize = 14.sp, modifier = Modifier.weight(1f))
            SmallButton("Tocar p/ definir", color) { vm.startRangeLearn(i) }
            Spacer(Modifier.width(4.dp))
            SmallButton("Todas", InkDim) { vm.update(i) { it.copy(lowKey = 0, highKey = 127) } }
        }
        KeyRangeBar(s.lowKey, s.highKey, color, Modifier.padding(top = 8.dp))
        RangeSlider(
            value = s.lowKey.toFloat()..s.highKey.toFloat(),
            onValueChange = { r -> vm.update(i) { it.copy(lowKey = r.start.roundToInt(), highKey = r.endInclusive.roundToInt()) } },
            valueRange = 0f..127f,
            colors = SliderDefaults.colors(thumbColor = color, activeTrackColor = color, inactiveTrackColor = Line),
            modifier = Modifier.height(36.dp),
        )

        SectionTitle("TOM", Modifier.padding(top = 6.dp))
        Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Stepper(
                label = when {
                    s.transpose == 0 -> "0"
                    s.transpose % 12 == 0 -> "${if (s.transpose > 0) "+" else ""}${s.transpose / 12} oit"
                    else -> "${if (s.transpose > 0) "+" else ""}${s.transpose} st"
                },
                onMinus = { vm.update(i) { it.copy(transpose = (it.transpose - 1).coerceAtLeast(-48)) } },
                onPlus = { vm.update(i) { it.copy(transpose = (it.transpose + 1).coerceAtMost(48)) } },
                onMinusLong = { vm.update(i) { it.copy(transpose = (it.transpose - 12).coerceAtLeast(-48)) } },
                onPlusLong = { vm.update(i) { it.copy(transpose = (it.transpose + 12).coerceAtMost(48)) } },
            )
            Spacer(Modifier.width(8.dp))
            Text("segure ± para oitava", color = InkDim, fontSize = 10.sp)
        }
        Row(Modifier.padding(top = 10.dp)) { ChannelPicker(s.channel) { ch -> vm.update(i) { it.copy(channel = ch) } } }

        SectionTitle("CONTROLES DO TECLADO", Modifier.padding(top = 12.dp))
        Text("Toque em MIDI e mexa o controle. Segure para apagar.", color = InkDim, fontSize = 10.sp)
        for ((label, t) in listOf("Fader" to TargetType.SLOT_VOLUME, "Liga/desliga (mute)" to TargetType.SLOT_TOGGLE)) {
            Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(label, color = Ink, fontSize = 13.sp, modifier = Modifier.weight(1f))
                LearnChip(vm, Target(t, i), color)
            }
        }
    }
}

// ------------------------------------------------------------------ teclado MIDI e mapeamentos

@Composable
private fun MidiDialog(vm: MainViewModel, onClose: () -> Unit) {
    AlertDialog(
        onDismissRequest = onClose,
        confirmButton = { TextButton(onClick = onClose) { Text("Fechar") } },
        title = { Text("Teclado e mapeamentos") },
        text = {
            LazyColumn(Modifier.heightIn(max = 420.dp)) {
                item {
                    SectionTitle("ENTRADA MIDI")
                    if (vm.sources.isEmpty()) Text(
                        "Nenhum teclado encontrado. Ligue o Launchkey com um cabo USB OTG.",
                        color = Ink, fontSize = 14.sp, modifier = Modifier.padding(vertical = 8.dp),
                    )
                }
                itemsIndexed(vm.sources) { _, src ->
                    Row(Modifier.fillMaxWidth().clickable { vm.connect(src) }, verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = vm.connected?.key == src.key, onClick = { vm.connect(src) })
                        Text(src.label, color = Ink, fontSize = 14.sp)
                    }
                }
                item {
                    Text("No Launchkey, use a porta 1 (MIDI). A porta DAW é dos programas de música.",
                        color = InkDim, fontSize = 12.sp, modifier = Modifier.padding(bottom = 12.dp))
                    HorizontalDivider()
                    Spacer(Modifier.height(12.dp))
                    SectionTitle("CONTROLES MAPEADOS")
                    if (vm.mappings.isEmpty()) Text(
                        "Nenhum ainda. Toque em \"MIDI\" embaixo de um fader ou knob e mexa o controle do teclado.",
                        color = Ink, fontSize = 14.sp, modifier = Modifier.padding(vertical = 8.dp),
                    )
                }
                itemsIndexed(vm.mappings.toList()) { _, m ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(8.dp).clip(CircleShape).background(if (m.target.slot >= 0) SlotColors[m.target.slot] else Ink))
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(m.target.describe(), color = Ink, fontSize = 14.sp)
                            Text(m.sourceLabel(), color = InkDim, fontSize = 12.sp, fontFamily = Mono)
                        }
                        IconButton(onClick = { vm.removeMapping(m) }) { Icon(Icons.Rounded.Delete, "Apagar", tint = InkDim) }
                    }
                }
                item {
                    TextButton(onClick = { onClose(); vm.startLearn(Target(TargetType.PANIC)) }) { Text("Mapear botão \"parar todos os sons\"") }
                }
            }
        },
    )
}
