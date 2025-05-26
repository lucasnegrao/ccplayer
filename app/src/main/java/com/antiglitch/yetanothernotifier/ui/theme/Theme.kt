package com.antiglitch.yetanothernotifier.ui.theme

import androidx.compose.runtime.Composable
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme

/**
 * Custom dark color scheme optimized for TV interfaces
 */
@OptIn(ExperimentalTvMaterial3Api::class)
private val TvDarkColorScheme = darkColorScheme(
    // Primary colors
    primary = Purple80,
    onPrimary = Purple20,
    primaryContainer = Purple30,
    onPrimaryContainer = Purple90,

    // Secondary colors
    secondary = Orange80,
    onSecondary = Orange20,
    secondaryContainer = Orange30,
    onSecondaryContainer = Orange90,

    // Background colors - ensure these are dark
    background = DarkGray,
    onBackground = LightGray,

    // Surface colors for cards and other components
    surface = DarkGrayVariant,
    onSurface = White,
    surfaceVariant = DarkGrayVariant.copy(alpha = 0.7f),
    onSurfaceVariant = LightGrayVariant,

    // Error colors
    error = Red80,
    onError = Red20
)

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun YetAnotherNotifierTheme(
    // Always use dark theme by default for TV interfaces
    content: @Composable () -> Unit
) {
    // Force our custom dark theme
    MaterialTheme(
        colorScheme = TvDarkColorScheme,
        content = content
    )
}