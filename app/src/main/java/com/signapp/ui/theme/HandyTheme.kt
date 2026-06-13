package com.signapp.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

object HandyColors {
    val Background = Color(0xFF050505)
    val Surface = Color(0xFF0F1115)
    val SurfaceSecondary = Color(0xFF121826)
    val Accent = Color(0xFF4DA3FF)
    val TextPrimary = Color.White
    val TextSecondary = Color(0xFF9CA3AF)
    val Border = Color(0x0DFFFFFF)       // rgba(255,255,255,0.05)
    val ProgressTrack = Color(0x14FFFFFF) // rgba(255,255,255,0.08)
    val GradientStart = Color(0xFF111827)
    val GradientEnd = Color(0xFF0F1115)
}

@Composable
fun HandyTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            background = HandyColors.Background,
            surface = HandyColors.Surface,
            surfaceVariant = HandyColors.SurfaceSecondary,
            primary = HandyColors.Accent,
            onPrimary = Color.White,
            onBackground = HandyColors.TextPrimary,
            onSurface = HandyColors.TextPrimary,
            outline = HandyColors.Border,
        ),
        content = content
    )
}