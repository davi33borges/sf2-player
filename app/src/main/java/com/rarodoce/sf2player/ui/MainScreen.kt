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
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Piano
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.StopCircle
import androidx.compose.material.icons.rounded.Usb
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rarodoce.sf2player.LearnMode
import com.rarodoce.sf2player.MainViewModel
import com.rarodoce.sf2player.SlotState
import com.rarodoce.sf2player.Target
import com.rarodoce.sf2player.TargetType
import com.rarodoce.sf2player.noteName
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

private val Mono = FontFamily.Monospace

@Composable
fun MainScreen(vm: MainViewModel) {
    val snack = remember { SnackbarHostState() }
    var showMidi by remember { mutableStateOf(false) }
    var pickSlot by remember { mutableIntStateOf(-1) }
    var presetSlot by remember { mutableIntStateOf(-1) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null && pickSlot >= 0) vm.importFont(pickSlot, uri)
        pickSlot = -1
    }
    val pick: (Int) -> Unit = { i -> pickSlot = i; picker.launch(arrayOf("*/*")) }

    LaunchedEffect(vm.toast) {
        vm.toast?.let { snack.showSnackbar(it); vm.toast = null }
    }

    Box(Modifier.fillMaxSize().background(Bg).safeDrawingPadding()) {
        Column(Modifier.fillMaxSize()) {
            Header(vm, onMidi = { showMidi = true })
            AnimatedVisibility(visible = vm.learn != null) { LearnBanner(vm) }
            LazyVerticalGrid(
                columns = GridCells.Adaptive(340.dp),
                contentPadding = PaddingValues(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                item(span = { GridItemSpan(maxLineSpan) }) { MasterStrip(vm) }
                itemsIndexed(vm.slots) { i, s ->
                    if (s.loaded) SlotCard(vm, i, s, onPick = { pick(i) }, onPresets = { presetSlot = i })
                    else EmptySlot(i, s, onPick = { pick(i) })
                }
            }
        }
        SnackbarHost(snack, Modifier.align(Alignment.BottomCenter).padding(16.dp))
    }

    if (showMidi) MidiDialog(vm) { showMidi = false }
    if (presetSlot >= 0) PresetDialog(vm, presetSlot) { presetSlot = -1 }
}

// ------------------------------------------------------------------ topo

@Composable
private fun Header(vm: MainViewModel, onMidi: () -> Unit) {
    var lit by remember { mutableStateOf(false) }
    LaunchedEffect(vm.activity) { if (vm.activity != 0L) { lit = true; delay(90); lit = false } }
    val led by animateColorAsState(if (lit) Accent else if (vm.connected != null) Ok else InkDim, tween(80), label = "led")

    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Piano, null, tint = Accent, modifier = Modifier.size(26.dp))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text("SF2 Player", fontWeight = FontWeight.Black, fontSize = 20.sp, color = Ink)
            Text(
                if (vm.audioOk) vm.lastEvent.ifEmpty { "Toque no teclado para testar" } else "Erro ao abrir o áudio",
                color = if (vm.audioOk) InkDim else Color(0xFFFF5C5C), fontSize = 12.sp, fontFamily = Mono,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        Row(
            Modifier.clip(RoundedCornerShape(50)).background(Panel).border(1.dp, Line, RoundedCornerShape(50))
                .clickable(onClick = onMidi).padding(horizontal = 12.dp, vertical = 8.dp)
                .widthIn(max = 220.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(9.dp).clip(CircleShape).background(led))
            Spacer(Modifier.width(8.dp))
            Icon(Icons.Rounded.Usb, null, tint = InkDim, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text(
                vm.connected?.deviceName ?: "Sem teclado", fontSize = 13.sp, color = Ink,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        IconButton(onClick = vm::panic) {
            Icon(Icons.Rounded.StopCircle, "Parar todos os sons", tint = Color(0xFFFF5C5C))
        }
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
        Modifier.fillMaxWidth().background(Accent.copy(alpha = 0.16f * pulse + 0.06f))
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(Accent.copy(alpha = pulse)))
        Spacer(Modifier.width(10.dp))
        Text(text, color = Ink, fontSize = 14.sp, modifier = Modifier.weight(1f))
        TextButton(onClick = vm::cancelLearn) { Text("Cancelar") }
    }
}

@Composable
private fun MasterStrip(vm: MainViewModel) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Panel)
            .padding(horizontal = 14.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("GERAL", color = InkDim, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
        Spacer(Modifier.width(12.dp))
        Slider(
            value = vm.master, onValueChange = vm::setMasterVolume, valueRange = 0f..1.2f,
            colors = sliderColors(Ink), modifier = Modifier.weight(1f),
        )
        Text("${(vm.master * 100).roundToInt()}%", fontFamily = Mono, fontSize = 13.sp, color = Ink,
            modifier = Modifier.width(48.dp))
        LearnChip(vm, Target(TargetType.MASTER_VOLUME), Ink)
    }
}

// ------------------------------------------------------------------ instrumentos

@Composable
private fun EmptySlot(i: Int, s: SlotState, onPick: () -> Unit) {
    val color = SlotColors[i]
    Box(
        Modifier.fillMaxWidth().heightIn(min = 150.dp).clip(RoundedCornerShape(16.dp))
            .drawBehind {
                drawRoundRect(
                    color = Line, cornerRadius = CornerRadius(16.dp.toPx()),
                    style = Stroke(width = 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f))),
                )
            }
            .clickable(enabled = !s.loading, onClick = onPick),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (s.loading) {
                CircularProgressIndicator(color = color, strokeWidth = 3.dp, modifier = Modifier.size(32.dp))
                Spacer(Modifier.height(10.dp))
                Text("Carregando…", color = InkDim, fontSize = 13.sp)
            } else {
                Box(
                    Modifier.size(44.dp).clip(CircleShape).background(color.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Rounded.Add, null, tint = color) }
                Spacer(Modifier.height(10.dp))
                Text("Instrumento ${i + 1}", color = Ink, fontWeight = FontWeight.SemiBold)
                Text("Toque para adicionar um .sf2", color = InkDim, fontSize = 12.sp)
                s.error?.let { Text(it, color = Color(0xFFFF5C5C), fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp)) }
            }
        }
    }
}

@Composable
private fun SlotCard(vm: MainViewModel, i: Int, s: SlotState, onPick: () -> Unit, onPresets: () -> Unit) {
    val color = SlotColors[i]
    var menu by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Panel)
            .border(1.dp, if (s.enabled) color.copy(alpha = 0.45f) else Line, RoundedCornerShape(16.dp)),
    ) {
        // faixa colorida no topo
        Box(Modifier.fillMaxWidth().height(4.dp).background(if (s.enabled) color else Line))

        Column(Modifier.padding(start = 14.dp, end = 6.dp, top = 10.dp, bottom = 10.dp)) {
            // cabeçalho
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(28.dp).clip(RoundedCornerShape(8.dp)).background(color.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center,
                ) { Text("${i + 1}", color = color, fontWeight = FontWeight.Black, fontFamily = Mono) }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(s.displayName, color = Ink, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${s.presets.size} presets", color = InkDim, fontSize = 11.sp)
                }
                LearnChip(vm, Target(TargetType.SLOT_TOGGLE, i), color)
                Switch(
                    checked = s.enabled, onCheckedChange = { v -> vm.update(i) { it.copy(enabled = v) } },
                    colors = SwitchDefaults.colors(checkedTrackColor = color, checkedThumbColor = Bg),
                )
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, "Mais opções", tint = InkDim) }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("Trocar arquivo .sf2") }, onClick = { menu = false; onPick() })
                        DropdownMenuItem(text = { Text("Mapear: próximo preset") },
                            onClick = { menu = false; vm.startLearn(Target(TargetType.SLOT_PRESET_NEXT, i)) })
                        DropdownMenuItem(text = { Text("Mapear: preset anterior") },
                            onClick = { menu = false; vm.startLearn(Target(TargetType.SLOT_PRESET_PREV, i)) })
                        HorizontalDivider()
                        DropdownMenuItem(text = { Text("Remover instrumento", color = Color(0xFFFF5C5C)) },
                            onClick = { menu = false; vm.removeFont(i) })
                    }
                }
            }

            Column(Modifier.alpha(if (s.enabled) 1f else 0.5f)) {
                // preset
                Row(
                    Modifier.padding(top = 8.dp, end = 8.dp).fillMaxWidth().clip(RoundedCornerShape(10.dp))
                        .background(PanelHi),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = { vm.setPreset(i, (s.preset - 1 + s.presets.size) % s.presets.size) }) {
                        Icon(Icons.Rounded.ChevronLeft, "Preset anterior", tint = Ink)
                    }
                    Column(
                        Modifier.weight(1f).clickable(onClick = onPresets).padding(vertical = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(s.presetName, color = Ink, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${s.preset + 1} / ${s.presets.size} · toque para ver todos", color = InkDim, fontSize = 10.sp)
                    }
                    IconButton(onClick = { vm.setPreset(i, (s.preset + 1) % s.presets.size) }) {
                        Icon(Icons.Rounded.ChevronRight, "Próximo preset", tint = Ink)
                    }
                }

                ParamRow("VOL", "${(s.volume * 100).roundToInt()}%") {
                    Slider(s.volume, { v -> vm.update(i) { it.copy(volume = v) } }, colors = sliderColors(color), modifier = Modifier.weight(1f))
                    LearnChip(vm, Target(TargetType.SLOT_VOLUME, i), color)
                }
                ParamRow("PAN", panLabel(s.pan)) {
                    Slider(s.pan, { v -> vm.update(i) { it.copy(pan = if (kotlin.math.abs(v - 0.5f) < 0.03f) 0.5f else v) } },
                        colors = sliderColors(color), modifier = Modifier.weight(1f))
                    LearnChip(vm, Target(TargetType.SLOT_PAN, i), color)
                }

                // faixa de teclas (split)
                Row(Modifier.padding(top = 6.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("TECLAS", color = InkDim, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp,
                        modifier = Modifier.width(52.dp))
                    Text("${noteName(s.lowKey)} – ${noteName(s.highKey)}", fontFamily = Mono, color = Ink, fontSize = 13.sp,
                        modifier = Modifier.weight(1f))
                    SmallButton("Tocar p/ definir", color) { vm.startRangeLearn(i) }
                    Spacer(Modifier.width(6.dp))
                    SmallButton("Todas", InkDim) { vm.update(i) { it.copy(lowKey = 0, highKey = 127) } }
                }
                KeyRangeBar(s.lowKey, s.highKey, color, Modifier.padding(top = 6.dp, end = 8.dp))
                RangeSlider(
                    value = s.lowKey.toFloat()..s.highKey.toFloat(),
                    onValueChange = { r -> vm.update(i) { it.copy(lowKey = r.start.roundToInt(), highKey = r.endInclusive.roundToInt()) } },
                    valueRange = 0f..127f, colors = sliderColors(color), modifier = Modifier.padding(end = 8.dp).height(32.dp),
                )

                // transposição e canal
                Row(Modifier.padding(top = 2.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("TOM", color = InkDim, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp,
                        modifier = Modifier.width(52.dp))
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
                    Spacer(Modifier.weight(1f))
                    ChannelPicker(s.channel) { ch -> vm.update(i) { it.copy(channel = ch) } }
                }
            }
        }
    }
}

@Composable
private fun ParamRow(label: String, value: String, content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    Row(Modifier.padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = InkDim, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, modifier = Modifier.width(36.dp))
        Text(value, fontFamily = Mono, color = Ink, fontSize = 12.sp, modifier = Modifier.width(44.dp))
        content()
    }
}

private fun panLabel(p: Float): String {
    val v = ((p - 0.5f) * 200).roundToInt()
    return when { v == 0 -> "C"; v < 0 -> "E${-v}"; else -> "D$v" }
}

@Composable
private fun sliderColors(c: Color) = SliderDefaults.colors(
    thumbColor = c, activeTrackColor = c, inactiveTrackColor = Line,
    activeTickColor = Color.Transparent, inactiveTickColor = Color.Transparent,
)

/** Mini teclado mostrando quais teclas este instrumento toca. */
@Composable
private fun KeyRangeBar(low: Int, high: Int, color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier.fillMaxWidth().height(18.dp).clip(RoundedCornerShape(4.dp)).drawBehind {
            // mostra só a extensão de um piano (A0 a C8)
            val first = 21; val last = 108
            val whites = (first..last).filter { it % 12 !in intArrayOf(1, 3, 6, 8, 10) }
            val w = size.width / whites.size
            whites.forEachIndexed { idx, n ->
                val on = n in low..high
                drawRect(
                    if (on) color.copy(alpha = 0.85f) else Color(0xFF2C303B),
                    topLeft = androidx.compose.ui.geometry.Offset(idx * w + 0.5f, 0f),
                    size = androidx.compose.ui.geometry.Size(w - 1f, size.height),
                )
                if (n % 12 == 0) {
                    drawRect(Color.Black.copy(alpha = 0.5f),
                        topLeft = androidx.compose.ui.geometry.Offset(idx * w, size.height - 3f),
                        size = androidx.compose.ui.geometry.Size(w, 3f))
                }
            }
        },
    )
}

@Composable
private fun SmallButton(text: String, color: Color, onClick: () -> Unit) {
    Text(
        text, color = color, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
        modifier = Modifier.clip(RoundedCornerShape(8.dp)).border(1.dp, color.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
            .clickable(onClick = onClick).padding(horizontal = 8.dp, vertical = 5.dp),
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Stepper(label: String, onMinus: () -> Unit, onPlus: () -> Unit, onMinusLong: () -> Unit, onPlusLong: () -> Unit) {
    Row(
        Modifier.clip(RoundedCornerShape(10.dp)).background(PanelHi),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("−", color = Ink, fontSize = 18.sp,
            modifier = Modifier.combinedClickable(onClick = onMinus, onLongClick = onMinusLong).padding(horizontal = 12.dp, vertical = 4.dp))
        Text(label, fontFamily = Mono, color = Ink, fontSize = 13.sp, modifier = Modifier.widthIn(min = 52.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        Text("+", color = Ink, fontSize = 18.sp,
            modifier = Modifier.combinedClickable(onClick = onPlus, onLongClick = onPlusLong).padding(horizontal = 12.dp, vertical = 4.dp))
    }
}

@Composable
private fun ChannelPicker(channel: Int, onChange: (Int) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        SmallButton(if (channel < 0) "Canal: todos" else "Canal ${channel + 1}", InkDim) { open = true }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text("Todos os canais") }, onClick = { open = false; onChange(-1) })
            (0 until 16).forEach { c ->
                DropdownMenuItem(text = { Text("Canal ${c + 1}") }, onClick = { open = false; onChange(c) })
            }
        }
    }
}

/** Pílula "MIDI": toque para aprender, toque longo para apagar o mapeamento. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LearnChip(vm: MainViewModel, target: Target, color: Color) {
    val mapping = vm.mappingFor(target)
    val learning = (vm.learn as? LearnMode.Control)?.target == target
    val pulse by rememberInfiniteTransition(label = "chip").animateFloat(
        0.3f, 1f, infiniteRepeatable(tween(500), RepeatMode.Reverse), label = "c",
    )
    val bg = when { learning -> Accent.copy(alpha = pulse); mapping != null -> color.copy(alpha = 0.2f); else -> Color.Transparent }
    Text(
        text = when { learning -> "…"; mapping != null -> mapping.shortLabel(); else -> "MIDI" },
        color = when { learning -> Bg; mapping != null -> color; else -> InkDim },
        fontSize = 10.sp, fontFamily = Mono, fontWeight = FontWeight.Bold, maxLines = 1,
        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        modifier = Modifier.padding(start = 4.dp).widthIn(min = 44.dp).clip(RoundedCornerShape(6.dp)).background(bg)
            .border(1.dp, if (mapping != null || learning) Color.Transparent else Line, RoundedCornerShape(6.dp))
            .combinedClickable(
                onClick = { if (learning) vm.cancelLearn() else vm.startLearn(target) },
                onLongClick = { mapping?.let(vm::removeMapping) },
            )
            .padding(horizontal = 6.dp, vertical = 5.dp),
    )
}

// ------------------------------------------------------------------ janelas

@Composable
private fun PresetDialog(vm: MainViewModel, i: Int, onClose: () -> Unit) {
    val s = vm.slots[i]
    var query by remember { mutableStateOf("") }
    val list = s.presets.withIndex().filter { query.isBlank() || it.value.contains(query, true) }
    AlertDialog(
        onDismissRequest = onClose,
        confirmButton = { TextButton(onClick = onClose) { Text("Fechar") } },
        title = { Text("Instrumento ${i + 1} · presets") },
        text = {
            Column {
                OutlinedTextField(
                    query, { query = it }, singleLine = true, placeholder = { Text("Buscar") },
                    leadingIcon = { Icon(Icons.Rounded.Search, null) }, modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                LazyColumn(Modifier.heightIn(max = 420.dp)) {
                    itemsIndexed(list) { _, p ->
                        val sel = p.index == s.preset
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                                .background(if (sel) SlotColors[i].copy(alpha = 0.18f) else Color.Transparent)
                                .clickable { vm.setPreset(i, p.index) }.padding(horizontal = 10.dp, vertical = 10.dp),
                        ) {
                            Text("${p.index + 1}".padStart(3), fontFamily = Mono, color = InkDim, fontSize = 12.sp)
                            Spacer(Modifier.width(10.dp))
                            Text(p.value, color = if (sel) SlotColors[i] else Ink, fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal)
                        }
                    }
                }
            }
        },
    )
}

@Composable
private fun MidiDialog(vm: MainViewModel, onClose: () -> Unit) {
    AlertDialog(
        onDismissRequest = onClose,
        confirmButton = { TextButton(onClick = onClose) { Text("Fechar") } },
        title = { Text("Teclado e mapeamentos") },
        text = {
            LazyColumn(Modifier.heightIn(max = 480.dp)) {
                item {
                    Text("ENTRADA MIDI", color = InkDim, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    if (vm.sources.isEmpty()) {
                        Text(
                            "Nenhum teclado encontrado. Ligue o Launchkey no celular/tablet com um cabo USB OTG.",
                            color = Ink, fontSize = 14.sp, modifier = Modifier.padding(vertical = 8.dp),
                        )
                    }
                }
                itemsIndexed(vm.sources) { _, src ->
                    Row(
                        Modifier.fillMaxWidth().clickable { vm.connect(src) },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = vm.connected?.key == src.key, onClick = { vm.connect(src) })
                        Text(src.label, color = Ink, fontSize = 14.sp)
                    }
                }
                item {
                    Text(
                        "No Launchkey, use a porta 1 (MIDI). A porta DAW é usada pelos programas de música.",
                        color = InkDim, fontSize = 12.sp, modifier = Modifier.padding(bottom = 12.dp),
                    )
                    HorizontalDivider()
                    Spacer(Modifier.height(12.dp))
                    Text("CONTROLES MAPEADOS", color = InkDim, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    if (vm.mappings.isEmpty()) {
                        Text(
                            "Nenhum ainda. Toque em \"MIDI\" ao lado de um volume, pan ou botão liga/desliga e mexa o fader, knob ou pad do teclado.",
                            color = Ink, fontSize = 14.sp, modifier = Modifier.padding(vertical = 8.dp),
                        )
                    }
                }
                itemsIndexed(vm.mappings.toList()) { _, m ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                        val c = if (m.target.slot >= 0) SlotColors[m.target.slot] else Ink
                        Box(Modifier.size(8.dp).clip(CircleShape).background(c))
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(m.target.describe(), color = Ink, fontSize = 14.sp)
                            Text(m.sourceLabel(), color = InkDim, fontSize = 12.sp, fontFamily = Mono)
                        }
                        IconButton(onClick = { vm.removeMapping(m) }) { Icon(Icons.Rounded.Delete, "Apagar", tint = InkDim) }
                    }
                }
                item {
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = { onClose(); vm.startLearn(Target(TargetType.PANIC)) }) {
                        Text("Mapear botão \"parar todos os sons\"")
                    }
                }
            }
        },
    )
}
