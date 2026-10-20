package com.example.netraai.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = ElectricCyan,
    onPrimary = TextOnAccent,
    primaryContainer = DarkCard,
    onPrimaryContainer = ElectricCyan,
    secondary = ElectricAmber,
    onSecondary = TextOnAccent,
    background = DarkBackground,
    onBackground = TextPrimary,
    surface = DarkSurface,
    onSurface = TextPrimary,
    error = DangerRed,
    onError = Color.White
)

@Composable
fun NetraAITheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography,
        content = content
    )
}