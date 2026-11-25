package com.rarodoce.sf2player.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rarodoce.sf2player.LearnMode
import com.rarodoce.sf2player.MainViewModel
import com.rarodoce.sf2player.Target
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sin

val Mono = FontFamily.Monospace

/** Posição do fader (0..1) para um valor em dB; 0 dB fica em 0,75. */
private fun dbToPos(db: Float) = 0.75f * 10f.pow(db / 40f)

/**
 * Fader vertical de mesa de som, com medidor de nível ao lado.
 * Arraste para mover; toque duplo volta para 0 dB.
 */
@Composable
fun VerticalFader(
    value: Float,
    onChange: (Float) -> Unit,
    color: Color,
    level: () -> Float,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    default: Float = 0.75f,
) {
    val v by rememberUpdatedState(value)
    val change by rememberUpdatedState(onChange)
    Box(
        modifier
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectVerticalDragGestures { c, dy ->
                    c.consume()
                    val usable = size.height - 40.dp.toPx()
                    change((v - dy / usable).coerceIn(0f, 1f))
                }
            }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectTapGestures(onDoubleTap = { change(default) })
            }
            .drawBehind {
                val capH = 22.dp.toPx(); val capW = 30.dp.toPx()
                val top = capH / 2 + 6.dp.toPx(); val bottom = size.height - capH / 2 - 6.dp.toPx()
                val span = bottom - top
                val cx = size.width * 0.42f
                fun y(p: Float) = bottom - p * span

                // escala em dB
                for (db in listOf(0f, -6f, -12f, -24f, -48f)) {
                    val yy = y(dbToPos(db))
                    val major = db == 0f
                    drawLine(if (major) InkDim else Line, Offset(cx - 16.dp.toPx(), yy), Offset(cx - 8.dp.toPx(), yy), 1.dp.toPx())
                    drawLine(if (major) InkDim else Line, Offset(cx + 8.dp.toPx(), yy), Offset(cx + 14.dp.toPx(), yy), 1.dp.toPx())
                }
                // trilho
                drawRoundRect(Color.Black.copy(alpha = 0.45f), Offset(cx - 2.5.dp.toPx(), top), Size(5.dp.toPx(), span), CornerRadius(3.dp.toPx()))
                if (enabled) drawRoundRect(
                    color.copy(alpha = 0.55f), Offset(cx - 1.5.dp.toPx(), y(v)), Size(3.dp.toPx(), bottom - y(v)), CornerRadius(2.dp.toPx()),
                )

                // medidor
                val mx = size.width - 7.dp.toPx(); val mw = 5.dp.toPx()
                drawRoundRect(Color.Black.copy(alpha = 0.35f), Offset(mx, top), Size(mw, span), CornerRadius(2.dp.toPx()))
                val lv = level()
                if (lv > 0.0005f) {
                    val db = 20f * log10(lv)
                    val frac = ((db + 48f) / 54f).coerceIn(0f, 1f)      // -48 dB até +6 dB
                    val h = frac * span
                    drawRoundRect(
                        Brush.verticalGradient(
                            0f to Color(0xFFFF4D4D), 0.12f to Color(0xFFFFD54F), 0.3f to Ok, 1f to Ok,
                            startY = top, endY = bottom,
                        ),
                        Offset(mx, bottom - h), Size(mw, h), CornerRadius(2.dp.toPx()),
                    )
                }

                // botão do fader
                val cy = y(v)
                val capColor = if (enabled) Color.White else Color.White.copy(alpha = 0.25f)
                drawRoundRect(Color.Black.copy(alpha = 0.5f), Offset(cx - capW / 2, cy - capH / 2 + 2.dp.toPx()), Size(capW, capH), CornerRadius(4.dp.toPx()))
                drawRoundRect(capColor, Offset(cx - capW / 2, cy - capH / 2), Size(capW, capH), CornerRadius(4.dp.toPx()))
                drawLine(if (enabled) color else Line, Offset(cx - capW / 2 + 3.dp.toPx(), cy), Offset(cx + capW / 2 - 3.dp.toPx(), cy), 3.dp.toPx())
            },
    )
}

/**
 * Knob giratório: arraste para cima/baixo. Toque duplo volta ao padrão.
 * [value] vai de 0 a 1; [bipolar] desenha o arco a partir do centro (ex.: pan, EQ).
 */
@Composable
fun Knob(
    value: Float,
    onChange: (Float) -> Unit,
    color: Color,
    size: Dp = 44.dp,
    bipolar: Boolean = false,
    default: Float = if (bipolar) 0.5f else 0f,
    enabled: Boolean = true,
) {
    val v by rememberUpdatedState(value)
    val change by rememberUpdatedState(onChange)
    Canvas(
        Modifier.size(size)
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectVerticalDragGestures { c, dy ->
                    c.consume()
                    change((v - dy / 180.dp.toPx()).coerceIn(0f, 1f))
                }
            }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectTapGestures(onDoubleTap = { change(default) })
            },
    ) {
        val stroke = 3.5.dp.toPx()
        val r = this.size.minDimension / 2 - stroke
        val tl = Offset(center.x - r, center.y - r)
        val sz = Size(r * 2, r * 2)
        drawArc(Line, 135f, 270f, false, tl, sz, style = Stroke(stroke, cap = StrokeCap.Round))
        val c = if (enabled) color else Line
        if (bipolar) {
            val start = 270f; val sweep = (v - 0.5f) * 270f
            if (kotlin.math.abs(sweep) > 0.5f) drawArc(c, start, sweep, false, tl, sz, style = Stroke(stroke, cap = StrokeCap.Round))
        } else if (v > 0.002f) {
            drawArc(c, 135f, v * 270f, false, tl, sz, style = Stroke(stroke, cap = StrokeCap.Round))
        }
        drawCircle(PanelHi, r * 0.72f)
        val ang = Math.toRadians((135f + v * 270f).toDouble())
        val p1 = Offset(center.x + (r * 0.2f) * cos(ang).toFloat(), center.y + (r * 0.2f) * sin(ang).toFloat())
        val p2 = Offset(center.x + (r * 0.62f) * cos(ang).toFloat(), center.y + (r * 0.62f) * sin(ang).toFloat())
        drawLine(if (enabled) Ink else InkDim, p1, p2, 2.5.dp.toPx(), cap = StrokeCap.Round)
    }
}

/** Pílula "MIDI": toque para aprender, toque longo para apagar o mapeamento. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LearnChip(vm: MainViewModel, target: Target, color: Color, modifier: Modifier = Modifier) {
    val mapping = vm.mappingFor(target)
    val learning = (vm.learn as? LearnMode.Control)?.target == target
    val pulse by rememberInfiniteTransition(label = "chip").animateFloat(
        0.3f, 1f, infiniteRepeatable(tween(500), RepeatMode.Reverse), label = "c",
    )
    val bg = when { learning -> Accent.copy(alpha = pulse); mapping != null -> color.copy(alpha = 0.2f); else -> Color.Transparent }
    Text(
        text = when { learning -> "•••"; mapping != null -> mapping.shortLabel(); else -> "MIDI" },
        color = when { learning -> Bg; mapping != null -> color; else -> InkDim },
        fontSize = 9.sp, fontFamily = Mono, fontWeight = FontWeight.Bold, maxLines = 1,
        textAlign = TextAlign.Center,
        modifier = modifier.widthIn(min = 40.dp).clip(RoundedCornerShape(5.dp)).background(bg)
            .border(1.dp, if (mapping != null || learning) Color.Transparent else Line, RoundedCornerShape(5.dp))
            .combinedClickable(
                onClick = { if (learning) vm.cancelLearn() else vm.startLearn(target) },
                onLongClick = { mapping?.let(vm::removeMapping) },
            )
            .padding(horizontal = 5.dp, vertical = 4.dp),
    )
}

@Composable
fun SmallButton(text: String, color: Color, modifier: Modifier = Modifier, filled: Boolean = false, onClick: () -> Unit) {
    Text(
        text, color = if (filled) Bg else color, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1,
        textAlign = TextAlign.Center,
        modifier = modifier.clip(RoundedCornerShape(7.dp))
            .background(if (filled) color else Color.Transparent)
            .border(1.dp, color.copy(alpha = if (filled) 0f else 0.45f), RoundedCornerShape(7.dp))
            .clickable(onClick = onClick).padding(horizontal = 8.dp, vertical = 6.dp),
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Stepper(label: String, onMinus: () -> Unit, onPlus: () -> Unit, onMinusLong: () -> Unit, onPlusLong: () -> Unit) {
    Row(Modifier.clip(RoundedCornerShape(9.dp)).background(PanelHi), verticalAlignment = Alignment.CenterVertically) {
        Text("−", color = Ink, fontSize = 18.sp,
            modifier = Modifier.combinedClickable(onClick = onMinus, onLongClick = onMinusLong).padding(horizontal = 12.dp, vertical = 3.dp))
        Text(label, fontFamily = Mono, color = Ink, fontSize = 13.sp, modifier = Modifier.widthIn(min = 56.dp), textAlign = TextAlign.Center)
        Text("+", color = Ink, fontSize = 18.sp,
            modifier = Modifier.combinedClickable(onClick = onPlus, onLongClick = onPlusLong).padding(horizontal = 12.dp, vertical = 3.dp))
    }
}

@Composable
fun ChannelPicker(channel: Int, onChange: (Int) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        SmallButton(if (channel < 0) "Canal MIDI: todos" else "Canal MIDI ${channel + 1}", InkDim) { open = true }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text("Todos os canais") }, onClick = { open = false; onChange(-1) })
            (0 until 16).forEach { c ->
                DropdownMenuItem(text = { Text("Canal ${c + 1}") }, onClick = { open = false; onChange(c) })
            }
        }
    }
}

/** Mini teclado mostrando quais teclas o instrumento toca. */
@Composable
fun KeyRangeBar(low: Int, high: Int, color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier.fillMaxWidth().height(20.dp).clip(RoundedCornerShape(4.dp)).drawBehind {
            val whites = (21..108).filter { it % 12 !in intArrayOf(1, 3, 6, 8, 10) }
            val w = size.width / whites.size
            whites.forEachIndexed { idx, n ->
                drawRect(
                    if (n in low..high) color.copy(alpha = 0.9f) else Color.White.copy(alpha = 0.14f),
                    topLeft = Offset(idx * w + 0.5f, 0f), size = Size(w - 1f, size.height),
                )
                if (n % 12 == 0) drawRect(Color.Black.copy(alpha = 0.5f), Offset(idx * w, size.height - 3f), Size(w, 3f))
            }
        },
    )
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, color = InkDim, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp, modifier = modifier)
}

/** Knob com nome, valor e pílula de mapeamento MIDI. */
@Composable
fun KnobCell(
    vm: MainViewModel, label: String, valueText: String, value: Float, onChange: (Float) -> Unit,
    color: Color, target: Target, bipolar: Boolean = false, default: Float = if (bipolar) 0.5f else 0f,
    modifier: Modifier = Modifier, showLearn: Boolean = true,
) {
    Column(modifier.padding(vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = Ink, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        Knob(value, onChange, color, size = 48.dp, bipolar = bipolar, default = default)
        Text(valueText, color = InkDim, fontSize = 10.sp, fontFamily = Mono, maxLines = 1)
        if (showLearn) LearnChip(vm, target, color, Modifier.padding(top = 3.dp))
    }
}

@Composable
fun Placeholder(modifier: Modifier = Modifier) = Box(modifier.fillMaxHeight())
