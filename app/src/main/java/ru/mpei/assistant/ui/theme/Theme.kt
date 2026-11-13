package ru.mpei.assistant.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import ru.mpei.assistant.data.local.SessionStorage

val LocalThemeIsDark = compositionLocalOf { false }

@Composable
fun isAppInDarkTheme(): Boolean = LocalThemeIsDark.current

private val DarkColorScheme = darkColorScheme(
    primary = AppleBlueDark,
    onPrimary = Color.White,
    secondary = AppleBlueDark,
    secondaryContainer = Color.Transparent,
    tertiary = AppleGreenDark,
    background = AppleSystemBackgroundDark,
    onBackground = Color.White,
    surface = AppleSecondaryBackgroundDark,
    onSurface = Color.White,
    surfaceVariant = AppleTertiaryBackgroundDark,
    onSurfaceVariant = Color(0xFF8E8E93),
    outlineVariant = Color(0x2EFFFFFF),
    surfaceTint = Color.Transparent
)

private val LightColorScheme = lightColorScheme(
    primary = AppleBlue,
    onPrimary = Color.White,
    secondary = AppleBlue,
    secondaryContainer = Color.Transparent,
    tertiary = AppleGreen,
    background = AppleSystemBackgroundLight,
    onBackground = Color.Black,
    surface = AppleSecondaryBackgroundLight,
    onSurface = Color.Black,
    surfaceVariant = Color(0xFFE5E5EA),
    onSurfaceVariant = Color(0xFF8E8E93),
    outlineVariant = Color(0x14000000),
    surfaceTint = Color.Transparent
)

@Composable
fun MpeiTheme(
    themeMode: String = SessionStorage.THEME_SYSTEM,
    darkTheme: Boolean = when (themeMode) {
        SessionStorage.THEME_LIGHT -> false
        SessionStorage.THEME_DARK -> true
        else -> isSystemInDarkTheme()
    },
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                val insetsController = WindowCompat.getInsetsController(window, view)
                insetsController.isAppearanceLightStatusBars = !darkTheme
                insetsController.isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    CompositionLocalProvider(LocalThemeIsDark provides darkTheme) {
        MaterialTheme(
            colorScheme = colorScheme,
            content = content
        )
    }
}
