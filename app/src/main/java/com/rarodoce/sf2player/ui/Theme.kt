package com.rarodoce.sf2player.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Bg = Color(0xFF0D0E12)
val Panel = Color(0xFF16181F)
val PanelHi = Color(0xFF1E212A)
val Line = Color(0xFF2A2E3A)
val Ink = Color(0xFFF2EEE6)
val InkDim = Color(0xFF8E93A3)
val Accent = Color(0xFFFF8A3D)
val Ok = Color(0xFF3DDC97)

/** Uma cor por instrumento, para reconhecer cada camada de longe. */
val SlotColors = listOf(
    Color(0xFFFF8A3D), Color(0xFF4FC3F7), Color(0xFFB388FF), Color(0xFF3DDC97),
    Color(0xFFFF5C8A), Color(0xFFFFD54F), Color(0xFF64FFDA), Color(0xFFFF7043),
)

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Accent, onPrimary = Color(0xFF1A0E05),
            secondary = Ok, background = Bg, onBackground = Ink,
            surface = Panel, onSurface = Ink, surfaceVariant = PanelHi, onSurfaceVariant = InkDim,
            outline = Line, surfaceContainerHigh = PanelHi, surfaceContainer = Panel,
        ),
        content = content,
    )
}
