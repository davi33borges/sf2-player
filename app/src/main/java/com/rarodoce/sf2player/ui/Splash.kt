package com.rarodoce.sf2player.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val LogoBg = Color(0xFF2A5BB5)
private val Cyan = Color(0xFF4FD1FF)

/**
 * Desenho da logo (4 pads + teclado) numa área 0..1.
 * [pads] = escala de cada pad (0 = escondido), [keys] = 0..1 do teclado subindo, [flash] = pad azul piscando.
 */
private fun DrawScope.drawLogo(pads: FloatArray, keys: Float, flash: Float) {
    val u = size.minDimension
    drawRoundRect(LogoBg, cornerRadius = CornerRadius(u * 0.24f))
    val pw = u * 0.30f; val ph = u * 0.26f; val r = CornerRadius(u * 0.07f)
    val pos = listOf(Offset(0.15f, 0.12f), Offset(0.53f, 0.12f), Offset(0.15f, 0.44f), Offset(0.53f, 0.44f))
    val colors = listOf(Color.White.copy(alpha = 0.9f), lerp(Cyan, Color.White, flash), Color.White.copy(alpha = 0.45f), Color.White.copy(alpha = 0.45f))
    pos.forEachIndexed { i, p ->
        val s = pads[i]
        if (s <= 0f) return@forEachIndexed
        val tl = Offset(p.x * u, p.y * u)
        scale(s, pivot = Offset(tl.x + pw / 2, tl.y + ph / 2)) {
            drawRoundRect(colors[i], tl, Size(pw, ph), r)
        }
    }
    if (keys > 0f) {
        translate(top = (1f - keys) * u * 0.12f) {
            val kx = u * 0.15f; val ky = u * 0.76f; val kw = u * 0.68f; val kh = u * 0.13f
            drawRoundRect(Color.White.copy(alpha = keys), Offset(kx, ky), Size(kw, kh), CornerRadius(u * 0.025f))
            val bw = u * 0.045f
            for (f in listOf(0.09f, 0.22f, 0.48f, 0.61f, 0.74f)) {
                drawRect(LogoBg.copy(alpha = keys), Offset(kx + kw * f, ky), Size(bw, kh * 0.58f))
            }
        }
    }
}

/** Logo parada, usada na barra do topo. */
@Composable
fun LogoMark(modifier: Modifier = Modifier) {
    Canvas(modifier) { drawLogo(floatArrayOf(1f, 1f, 1f, 1f), 1f, 0f) }
}

/** Abertura do app: pads acendem um a um, o teclado sobe, entra o nome e a frase. */
@Composable
fun SplashScreen(onDone: () -> Unit) {
    val pads = remember { List(4) { Animatable(0f) } }
    val keys = remember { Animatable(0f) }
    val name = remember { Animatable(0f) }
    val tag = remember { Animatable(0f) }
    val flash = remember { Animatable(0f) }
    val out = remember { Animatable(1f) }
    val pop = spring<Float>(dampingRatio = 0.45f, stiffness = Spring.StiffnessMediumLow)

    LaunchedEffect(Unit) {
        val delays = longArrayOf(300, 750, 450, 600)   // o pad azul entra por último
        pads.forEachIndexed { i, a -> launch { delay(delays[i]); a.animateTo(1f, pop) } }
        launch { delay(1050); keys.animateTo(1f, tween(400)) }
        launch { delay(1450); name.animateTo(1f, tween(450)) }
        launch { delay(1800); tag.animateTo(1f, tween(450)) }
        launch { delay(2200); flash.snapTo(1f); flash.animateTo(0f, tween(500)) }
        delay(2750)
        out.animateTo(0f, tween(350))
        onDone()
    }

    Box(
        Modifier.fillMaxSize().alpha(out.value).background(Color(0xFF3270CC)),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(22.dp)) {
            Canvas(Modifier.size(120.dp).clip(RoundedCornerShape(28.dp))) {
                drawLogo(FloatArray(4) { pads[it].value }, keys.value, flash.value)
            }
            Column {
                Text(
                    "SonarKeys", color = Color.White, fontSize = 40.sp, fontWeight = FontWeight.Black,
                    modifier = Modifier.graphicsLayer { alpha = name.value; translationX = (1f - name.value) * -40f },
                )
                Text(
                    "Tudo em um lugar só", color = Color(0xFFDCE8FA), fontSize = 16.sp,
                    modifier = Modifier.padding(top = 2.dp).alpha(tag.value),
                )
            }
        }
    }
}
