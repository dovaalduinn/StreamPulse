package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColorScheme = lightColorScheme(
    primary = M3Primary,
    onPrimary = M3OnPrimary,
    primaryContainer = M3PrimaryContainer,
    onPrimaryContainer = M3OnPrimaryContainer,
    secondary = M3Secondary,
    onSecondary = M3OnSecondary,
    secondaryContainer = M3SecondaryContainer,
    onSecondaryContainer = M3OnSecondaryContainer,
    tertiary = M3Tertiary,
    onTertiary = M3OnTertiary,
    background = M3Background,
    onBackground = M3OnBackground,
    surface = M3Surface,
    onSurface = M3OnSurface,
    surfaceVariant = M3SurfaceVariant,
    onSurfaceVariant = M3OnSurfaceVariant,
    outline = M3Outline,
    outlineVariant = M3OutlineVariant
)

private val DarkColorScheme = darkColorScheme(
    primary = CinemaAccent,
    onPrimary = M3OnPrimaryContainer,
    primaryContainer = M3Primary,
    onPrimaryContainer = M3PrimaryContainer,
    secondary = M3SecondaryContainer,
    onSecondary = M3OnSecondaryContainer,
    background = CinemaBlack,
    onBackground = M3OnPrimary,
    surface = Color(0xFF1C1B1F),
    onSurface = M3OnPrimary,
    surfaceVariant = Color(0xFF49454F),
    onSurfaceVariant = M3SurfaceVariant
)

@Composable
fun StreamPulseTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
