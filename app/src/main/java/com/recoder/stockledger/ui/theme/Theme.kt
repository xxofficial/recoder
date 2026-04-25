package com.recoder.stockledger.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColors = lightColorScheme(
    primary = SurfaceInverse,
    onPrimary = BackgroundPrimary,
    background = BackgroundPrimary,
    onBackground = ForegroundPrimary,
    surface = BackgroundPrimary,
    onSurface = ForegroundPrimary,
    surfaceVariant = SurfaceSecondary,
    outline = BorderSubtle,
)

private val DarkColors = darkColorScheme(
    primary = BackgroundPrimary,
    onPrimary = SurfaceInverse,
    background = SurfaceInverse,
    onBackground = BackgroundPrimary,
    surface = SurfaceInverse,
    onSurface = BackgroundPrimary,
)

@Composable
fun StockLedgerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = Typography,
        content = content,
    )
}

