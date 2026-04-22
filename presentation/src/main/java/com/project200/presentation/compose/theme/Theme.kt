package com.project200.presentation.compose.theme

import android.os.Build
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val LightColorScheme = ColorScheme(
    primary = ColorMain,
    onPrimary = ColorWhite300,
    primaryContainer = ColorMainBackground,
    onPrimaryContainer = ColorMain,
    inversePrimary = ColorWhite300,
    secondary = ColorGray200,
    onSecondary = ColorWhite300,
    secondaryContainer = ColorGray300,
    onSecondaryContainer = ColorGray100,
    tertiary = ColorMain,
    onTertiary = ColorWhite300,
    tertiaryContainer = ColorMainBackground,
    onTertiaryContainer = ColorMain,
    error = ColorErrorRed,
    onError = ColorWhite300,
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = ColorErrorRed,
    background = ColorBackground,
    onBackground = ColorBlack,
    surface = ColorSurface,
    onSurface = ColorBlack,
    surfaceVariant = ColorSurfaceVariant,
    onSurfaceVariant = ColorGray100,
    surfaceTint = ColorMain,
    inverseSurface = ColorBlack,
    inverseOnSurface = ColorWhite300,
    outline = ColorOutline,
    outlineVariant = ColorGray300,
    scrim = Color(0xFF000000)
)

@Composable
fun AppTheme(
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            dynamicLightColorScheme(context)
        }
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        shapes = AppShapes,
        content = content
    )
}
