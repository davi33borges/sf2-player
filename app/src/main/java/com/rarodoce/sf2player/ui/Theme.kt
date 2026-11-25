package com.rarodoce.sf2player.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// Visual "vidro sobre degradê": fundo magenta → roxo → azul, painéis translúcidos e texto branco.
val Bg = Color(0xFF1A0E2E)                          // texto sobre cores fortes
val Well = Color.Black.copy(alpha = 0.18f)           // áreas internas mais escuras
val Panel = Color.White.copy(alpha = 0.10f)          // vidro
val PanelHi = Color.White.copy(alpha = 0.17f)
val Line = Color.White.copy(alpha = 0.20f)
val Ink = Color.White
val InkDim = Color.White.copy(alpha = 0.66f)
val Accent = Color(0xFFFFD166)
val Ok = Color(0xFF5EE6A8)
val Danger = Color(0xFFFF6B81)
val Sheet = Color(0xF5301852)                        // painéis que abrem por cima

val AppGradient = Brush.linearGradient(
    0f to Color(0xFFE3245C),
    0.38f to Color(0xFFB12A8E),
    0.72f to Color(0xFF6A2FB6),
    1f to Color(0xFF3A3FC8),
)

/** Uma cor por instrumento/pad, como nos pads do print. */
val SlotColors = listOf(
    Color(0xFFFF3D6E), Color(0xFFFF7A6B), Color(0xFF9B6BFF), Color(0xFFC9C6D6),
    Color(0xFF4FD1FF), Color(0xFFFFD166), Color(0xFF5EE6A8), Color(0xFFFF9F43),
)

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Color.White, onPrimary = Bg,
            secondary = Ok, background = Color(0xFF2A1548), onBackground = Ink,
            surface = Color(0xFF2E1850), onSurface = Ink, surfaceVariant = Color(0xFF3A2160), onSurfaceVariant = InkDim,
            outline = Line, surfaceContainerHigh = Color(0xFF341B5A), surfaceContainer = Color(0xFF2E1850),
            surfaceContainerHighest = Color(0xFF3D2266), surfaceContainerLow = Color(0xFF2A1548),
        ),
        content = content,
    )
}
