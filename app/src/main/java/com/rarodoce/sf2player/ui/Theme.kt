package com.rarodoce.sf2player.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// Visual "vidro sobre degradê": fundo em tons de azul médio, painéis translúcidos e texto branco.
val Bg = Color(0xFF0F1E3D)                          // texto sobre cores fortes
val Well = Color.Black.copy(alpha = 0.18f)           // áreas internas mais escuras
val Panel = Color.White.copy(alpha = 0.10f)          // vidro
val PanelHi = Color.White.copy(alpha = 0.17f)
val Line = Color.White.copy(alpha = 0.20f)
val Ink = Color.White
val InkDim = Color.White.copy(alpha = 0.66f)
val Accent = Color(0xFFFFD166)
val Ok = Color(0xFF5EE6A8)
val Danger = Color(0xFFFF6B81)
val Sheet = Color(0xF5234A8C)                        // painéis que abrem por cima

val AppGradient = Brush.linearGradient(
    0f to Color(0xFF3F86DE),
    0.45f to Color(0xFF3270CC),
    1f to Color(0xFF2A5BB5),
)

/** Cor única de todas as faixas da mesa. */
val StripColor = Color(0xFF4FD1FF)

/** Cores que dá para escolher em cada pad, como nos pads do print. */
val SlotColors = listOf(
    Color(0xFFFF3D6E), Color(0xFFFF7A6B), Color(0xFF9B6BFF), Color(0xFFC9C6D6),
    Color(0xFF4FD1FF), Color(0xFFFFD166), Color(0xFF5EE6A8), Color(0xFFFF9F43),
)

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Color.White, onPrimary = Bg,
            secondary = Ok, background = Color(0xFF244E96), onBackground = Ink,
            surface = Color(0xFF26519A), onSurface = Ink, surfaceVariant = Color(0xFF2E5CA8), onSurfaceVariant = InkDim,
            outline = Line, surfaceContainerHigh = Color(0xFF2A56A2), surfaceContainer = Color(0xFF26519A),
            surfaceContainerHighest = Color(0xFF3062B0), surfaceContainerLow = Color(0xFF244E96),
        ),
        content = content,
    )
}
