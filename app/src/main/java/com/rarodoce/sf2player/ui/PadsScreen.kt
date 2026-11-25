package com.rarodoce.sf2player.ui

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Loop
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rarodoce.sf2player.MainViewModel
import com.rarodoce.sf2player.PadState
import com.rarodoce.sf2player.SLOT_COUNT
import com.rarodoce.sf2player.Target
import com.rarodoce.sf2player.TargetType
import com.rarodoce.sf2player.faderDb

private val ModeLabels = listOf("Toca até o fim", "Enquanto segura", "Loop (liga/desliga)")

/** Aba Pads: 8 pads para disparar samples, estilo octapad. */
@Composable
fun PadsScreen(vm: MainViewModel, modifier: Modifier) {
    var editing by remember { mutableStateOf(false) }
    var configPad by remember { mutableIntStateOf(-1) }
    var pickPad by remember { mutableIntStateOf(-1) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null && pickPad >= 0) vm.importSample(pickPad, uri)
        pickPad = -1
    }
    val pick: (Int) -> Unit = { i -> pickPad = i; picker.launch(arrayOf("audio/*", "application/octet-stream")) }

    Box(modifier) {
        Row(Modifier.fillMaxSize().padding(start = 8.dp, end = 8.dp, bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PadsSide(vm, editing, onEdit = { editing = !editing }, Modifier.width(96.dp).fillMaxHeight())
            Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                for (row in 0 until 2) {
                    Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        for (col in 0 until 4) {
                            val i = row * 4 + col
                            PadTile(
                                vm, i, vm.pads[i], editing, Modifier.weight(1f).fillMaxHeight(),
                                onConfig = { configPad = i }, onPick = { pick(i) },
                            )
                        }
                    }
                }
            }
        }
        if (configPad >= 0) {
            BackHandler { configPad = -1 }
            PadConfig(vm, configPad, onClose = { configPad = -1 }, onPick = { pick(configPad) })
        }
    }
}

@Composable
private fun PadsSide(vm: MainViewModel, editing: Boolean, onEdit: () -> Unit, modifier: Modifier) {
    Column(
        modifier.clip(RoundedCornerShape(20.dp)).background(Panel).border(1.dp, Line, RoundedCornerShape(20.dp)).padding(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("PADS", color = Ink, fontWeight = FontWeight.Black, fontSize = 12.sp, letterSpacing = 1.5.sp)
        VerticalFader(
            vm.padsVolume, vm::changePadsVolume, Color.White, level = { vm.meters[SLOT_COUNT] },
            modifier = Modifier.weight(1f).fillMaxWidth().padding(vertical = 4.dp),
        )
        Text(faderDb(vm.padsVolume), color = Ink, fontSize = 10.sp, fontFamily = Mono)
        // fader do teclado que controla o volume geral dos pads
        Text("volume no teclado", color = InkDim, fontSize = 9.sp, modifier = Modifier.padding(top = 6.dp))
        LearnChip(vm, Target(TargetType.PADS_VOLUME), Color.White, Modifier.padding(top = 2.dp).fillMaxWidth())
        Spacer(Modifier.height(6.dp))
        SmallButton("Parar", Danger, Modifier.fillMaxWidth()) { vm.stopPads() }
        Text("parar no teclado", color = InkDim, fontSize = 9.sp, modifier = Modifier.padding(top = 4.dp))
        LearnChip(vm, Target(TargetType.PADS_STOP), Danger, Modifier.padding(top = 2.dp).fillMaxWidth())
        Spacer(Modifier.height(6.dp))
        SmallButton(if (editing) "Pronto" else "Editar", Ink, Modifier.fillMaxWidth(), filled = editing, onClick = onEdit)
    }
}

@Composable
private fun PadTile(
    vm: MainViewModel, i: Int, p: PadState, editing: Boolean, modifier: Modifier,
    onConfig: () -> Unit, onPick: () -> Unit,
) {
    val color = SlotColors[p.color % SlotColors.size]
    val flash = remember { Animatable(0f) }
    val hit = vm.padHit[i]
    LaunchedEffect(hit) {
        if (hit != 0L) { flash.snapTo(1f); flash.animateTo(0f, tween(260)) }
    }
    val playing = vm.padPlaying[i]
    val mapping = vm.mappingFor(Target(TargetType.PAD_TRIGGER, i))
    val shape = RoundedCornerShape(22.dp)

    Box(
        modifier.clip(shape)
            .background(
                if (p.loaded) Brush.linearGradient(listOf(color, color.copy(alpha = 0.72f)))
                else SolidColor(Color.White.copy(alpha = 0.09f)),
            )
            .border(if (playing) 3.dp else 1.dp, if (playing) Color.White else Line, shape)
            .drawBehind { if (flash.value > 0f) drawRect(Color.White.copy(alpha = 0.55f * flash.value)) }
            .pointerInput(p.loaded, p.loading, editing) {
                detectTapGestures(
                    onPress = {
                        when {
                            editing -> Unit   // no modo editar, usa os botões do próprio pad
                            !p.loaded -> if (!p.loading) onPick()
                            else -> {
                                vm.padDown(i)
                                tryAwaitRelease()
                                vm.padUp(i)
                            }
                        }
                    },
                    onLongPress = { if (p.loaded) onConfig() },
                )
            }
            .padding(12.dp),
    ) {
        when {
            p.loading -> CircularProgressIndicator(color = Ink, strokeWidth = 2.dp, modifier = Modifier.align(Alignment.Center).size(24.dp))
            !p.loaded -> Column(
                Modifier.align(Alignment.Center).clip(RoundedCornerShape(12.dp)).clickable(enabled = editing, onClick = onPick).padding(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(Icons.Rounded.Add, null, tint = InkDim, modifier = Modifier.size(26.dp))
                Text(p.error ?: "Adicionar sample", color = if (p.error != null) Danger else InkDim, fontSize = 11.sp)
            }
            else -> {
                // topo: modo e controle mapeado
                Row(Modifier.align(Alignment.TopEnd), verticalAlignment = Alignment.CenterVertically) {
                    if (p.mode == 2) Icon(Icons.Rounded.Loop, "Loop", tint = Color.White, modifier = Modifier.size(16.dp))
                    if (p.mode == 1) Icon(Icons.Rounded.TouchApp, "Enquanto segura", tint = Color.White, modifier = Modifier.size(16.dp))
                    if (mapping != null && !editing) {
                        Spacer(Modifier.width(4.dp))
                        Text(mapping.shortLabel(), color = Color.White, fontSize = 10.sp, fontFamily = Mono, fontWeight = FontWeight.Bold,
                            modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(Color.Black.copy(alpha = 0.22f)).padding(horizontal = 5.dp, vertical = 1.dp))
                    }
                }
                Text("${i + 1}", color = Color.White.copy(alpha = 0.75f), fontWeight = FontWeight.Black, fontSize = 13.sp,
                    modifier = Modifier.align(Alignment.TopStart))
                Text(p.label, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.align(if (editing) Alignment.TopStart else Alignment.BottomStart).padding(top = if (editing) 18.dp else 0.dp))
            }
        }
        if (editing) {
            // modo editar: mapear direto no pad, sem abrir as configurações
            Row(Modifier.align(Alignment.BottomEnd), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                LearnChip(vm, Target(TargetType.PAD_TRIGGER, i), Color.White,
                    Modifier.clip(RoundedCornerShape(6.dp)).background(Color.Black.copy(alpha = 0.3f)))
                if (p.loaded) Text("editar", color = Bg, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(Color.White).clickable(onClick = onConfig)
                        .padding(horizontal = 8.dp, vertical = 4.dp))
            }
        }
    }
}

// ------------------------------------------------------------------ configuração do pad

@Composable
private fun PadConfig(vm: MainViewModel, i: Int, onClose: () -> Unit, onPick: () -> Unit) {
    val p = vm.pads[i]
    val color = SlotColors[p.color % SlotColors.size]
    var menu by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().padding(horizontal = 8.dp).padding(bottom = 8.dp).clip(RoundedCornerShape(18.dp)).background(Sheet)
            .border(1.dp, Line, RoundedCornerShape(18.dp))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
    ) {
        Row(
            Modifier.fillMaxWidth().background(color.copy(alpha = 0.22f)).padding(start = 12.dp, end = 2.dp, top = 2.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(26.dp).clip(RoundedCornerShape(8.dp)).background(color), contentAlignment = Alignment.Center) {
                Text("${i + 1}", color = Bg, fontWeight = FontWeight.Black, fontFamily = Mono)
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(p.label.ifBlank { "Pad ${i + 1}" }, color = Ink, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(if (p.loaded) "%s · %.1f s".format(p.fileName, p.seconds) else "Sem sample", color = InkDim, fontSize = 11.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            SmallButton("Testar", Ink) { vm.padDown(i, 120) }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, "Mais opções", tint = Ink) }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Trocar sample") }, onClick = { menu = false; onClose(); onPick() })
                    HorizontalDivider()
                    DropdownMenuItem(text = { Text("Remover sample", color = Danger) }, onClick = { menu = false; onClose(); vm.removeSample(i) })
                }
            }
            IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, "Fechar", tint = Ink) }
        }

        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().padding(8.dp)) {
            val w1 = 250.dp; val w2 = 300.dp; val w3 = 250.dp; val gap = 8.dp
            val wide = maxWidth >= w1 + w2 + w3 + gap * 2
            Row(
                if (wide) Modifier.fillMaxSize() else Modifier.fillMaxSize().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(gap),
            ) {
                PadSampleColumn(vm, i, p, color, if (wide) Modifier.weight(1f).fillMaxHeight() else Modifier.width(w1).fillMaxHeight())
                PadSoundColumn(vm, i, p, color, if (wide) Modifier.weight(1.2f).fillMaxHeight() else Modifier.width(w2).fillMaxHeight())
                PadControlColumn(vm, i, p, color, if (wide) Modifier.weight(1f).fillMaxHeight() else Modifier.width(w3).fillMaxHeight())
            }
        }
    }
}

@Composable
private fun PadSampleColumn(vm: MainViewModel, i: Int, p: PadState, color: Color, modifier: Modifier) {
    Column(modifier.clip(RoundedCornerShape(14.dp)).background(Well).padding(10.dp).verticalScroll(rememberScrollState())) {
        SectionTitle("NOME DO PAD")
        Box(
            Modifier.padding(top = 6.dp).fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(PanelHi).padding(horizontal = 10.dp, vertical = 8.dp),
        ) {
            if (p.name.isEmpty()) Text("Ex.: Grito, Virada, Efeito", color = InkDim, fontSize = 14.sp)
            BasicTextField(
                p.name, { v -> vm.updatePad(i) { it.copy(name = v.take(20)) } }, singleLine = true,
                textStyle = TextStyle(color = Ink, fontSize = 14.sp), cursorBrush = SolidColor(Color.White), modifier = Modifier.fillMaxWidth(),
            )
        }
        SectionTitle("COR", Modifier.padding(top = 12.dp))
        Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            SlotColors.forEachIndexed { idx, c ->
                Box(
                    Modifier.size(24.dp).clip(CircleShape).background(c)
                        .border(if (idx == p.color) 3.dp else 0.dp, Color.White, CircleShape)
                        .clickable { vm.updatePad(i) { it.copy(color = idx) } },
                )
            }
        }
        SectionTitle("ARQUIVO", Modifier.padding(top = 12.dp))
        Text(p.fileName.ifBlank { "—" }, color = Ink, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
        Text("WAV, MP3, OGG, FLAC ou M4A · até 90 s", color = InkDim, fontSize = 10.sp)
    }
}

@Composable
private fun PadSoundColumn(vm: MainViewModel, i: Int, p: PadState, color: Color, modifier: Modifier) {
    val none = Target(TargetType.PADS_STOP)
    Column(modifier.clip(RoundedCornerShape(14.dp)).background(Well).padding(10.dp).verticalScroll(rememberScrollState())) {
        SectionTitle("SOM")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            KnobCell(vm, "Volume", faderDb(p.volume) + " dB", p.volume, { v -> vm.updatePad(i) { it.copy(volume = v) } },
                color, none, default = 0.75f, showLearn = false)
            KnobCell(vm, "Afinação", (if (p.pitch > 0) "+" else "") + "${p.pitch} st", (p.pitch + 12) / 24f,
                { v -> vm.updatePad(i) { it.copy(pitch = Math.round(v * 24 - 12)) } }, color, none, bipolar = true, showLearn = false)
        }
        SectionTitle("MODO", Modifier.padding(top = 8.dp))
        Column(Modifier.padding(top = 6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            ModeLabels.forEachIndexed { idx, label ->
                SmallButton(label, color, Modifier.fillMaxWidth(), filled = p.mode == idx) { vm.updatePad(i) { it.copy(mode = idx) } }
            }
        }
        SectionTitle("FORÇA DA BATIDA", Modifier.padding(top = 10.dp))
        Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            SmallButton("Sensível", color, filled = p.velocity) { vm.updatePad(i) { it.copy(velocity = true) } }
            SmallButton("Volume fixo", color, filled = !p.velocity) { vm.updatePad(i) { it.copy(velocity = false) } }
        }
    }
}

@Composable
private fun PadControlColumn(vm: MainViewModel, i: Int, p: PadState, color: Color, modifier: Modifier) {
    Column(modifier.clip(RoundedCornerShape(14.dp)).background(Well).padding(10.dp).verticalScroll(rememberScrollState())) {
        SectionTitle("DISPARAR PELO TECLADO")
        Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Pad ou tecla", color = Ink, fontSize = 13.sp, modifier = Modifier.weight(1f))
            LearnChip(vm, Target(TargetType.PAD_TRIGGER, i), color)
        }
        Text("Toque em MIDI e bata no pad do Launchkey (ou numa tecla). Segure a pílula para apagar.",
            color = InkDim, fontSize = 10.sp, modifier = Modifier.padding(top = 4.dp))

        SectionTitle("SAÍDA DA PLACA", Modifier.padding(top = 14.dp))
        Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("L e R", "Só L", "Só R").forEachIndexed { idx, label ->
                SmallButton(label, color, filled = p.output == idx) { vm.updatePad(i) { it.copy(output = idx) } }
            }
        }
        Text(
            if (vm.outputs.dual) "Modo 2 saídas ligado no MASTER." else "Vale quando o MASTER está no modo \"2 saídas independentes\".",
            color = InkDim, fontSize = 10.sp, modifier = Modifier.padding(top = 4.dp),
        )
    }
}
