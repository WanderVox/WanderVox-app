package com.wandervox.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// La guía de estilo define solo la variante clara; sin dynamic color para respetar la marca.
private val WanderVoxColorScheme = lightColorScheme(
    primary = Forest,
    onPrimary = Color.White,
    secondary = Nature,
    onSecondary = Color.White,
    tertiary = Marigold,
    onTertiary = TextPrimary,
    background = Neutral,
    onBackground = TextPrimary,
    surface = Neutral,
    onSurface = TextPrimary,
    surfaceVariant = Color.White,
    onSurfaceVariant = TextSecondary,
    outline = TextSecondary
)

@Composable
fun WanderVoxTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = WanderVoxColorScheme,
        typography = WanderVoxTypography,
        content = content
    )
}
