package com.lyape.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LyapeLightColors = lightColorScheme(
    primary = Grape,
    onPrimary = CreamSurface,
    primaryContainer = SoftLavender,
    onPrimaryContainer = Ink,
    secondary = Lavender,
    onSecondary = Ink,
    secondaryContainer = SoftLavender,
    onSecondaryContainer = Ink,
    tertiary = Grape,
    onTertiary = CreamSurface,
    background = Cream,
    onBackground = Ink,
    surface = CreamSurface,
    onSurface = Ink,
    surfaceVariant = SurfaceMuted,
    onSurfaceVariant = MutedInk,
    outline = InteractiveOutline,
    outlineVariant = OutlineSoft,
    error = ErrorRed,
    onError = Color.White,
)

/**
 * Tema único de LYape. El diseño aprobado "Bodega cálida" es claro; no hay variante oscura.
 */
@Composable
fun LyapeTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LyapeLightColors,
        typography = LyapeTypography,
        shapes = LyapeShapes,
        content = content,
    )
}
