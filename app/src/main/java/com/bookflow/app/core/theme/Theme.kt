package com.bookflow.app.core.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightColorScheme = lightColorScheme(
    primary = BrandPurple,
    onPrimary = Color.White,
    primaryContainer = BrandPurpleSoft,
    onPrimaryContainer = BrandPurpleDark,
    secondary = AppTextSecondary,
    onSecondary = Color.White,
    background = AppBackground,
    onBackground = AppTextPrimary,
    surface = AppSurface,
    onSurface = AppTextPrimary,
    surfaceVariant = AppSurfaceVariant,
    onSurfaceVariant = AppTextSecondary,
    // outline: borders and disabled icons; outlineVariant: hairline dividers
    outline = Color(0xFFCBD5E1),
    outlineVariant = AppDivider
)

private val DarkColorScheme = darkColorScheme(
    primary = BrandPurpleLight,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF1E1B4B),
    onPrimaryContainer = Color(0xFFC7D2FE),
    secondary = Color(0xFF94A3B8),
    onSecondary = Color.Black,
    background = Color(0xFF0B1120),
    onBackground = Color(0xFFF1F5F9),
    surface = Color(0xFF151E2E),
    onSurface = Color(0xFFF1F5F9),
    surfaceVariant = Color(0xFF243044),
    onSurfaceVariant = Color(0xFFA3B1C6),
    outline = Color(0xFF475569),
    outlineVariant = Color(0xFF2A3547),
    surfaceContainerLow = Color(0xFF151E2E),
    surfaceContainer = Color(0xFF1A2436),
    surfaceContainerHigh = Color(0xFF1E293B),
    inverseSurface = Color(0xFFF1F5F9),
    inverseOnSurface = Color(0xFF0F172A)
)

@Composable
fun BookFlowTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val view = LocalView.current

    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                // Edge-to-edge: screen headers (BookFlowTopBar) paint their own color behind the status bar
                window.statusBarColor = Color.Transparent.toArgb()
                window.navigationBarColor = colorScheme.surface.toArgb()
                WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
                WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
