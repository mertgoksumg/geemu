package com.mertg.geemu.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF8BEA62),
    secondary = Color(0xFF48B6FF),
    tertiary = Color(0xFFFFB84D),
    background = Color(0xFF080A0D),
    surface = Color(0xFF12161C),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = Color(0xFFF7F8FA),
    onSurface = Color(0xFFF7F8FA)
)

@Composable
fun GeemuTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography,
        content = content
    )
}
