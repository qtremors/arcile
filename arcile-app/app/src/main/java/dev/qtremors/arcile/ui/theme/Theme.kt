package dev.qtremors.arcile.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// Basic color schemes if dynamic is not available or overriden by a specific seed color.
// We will use the system's dynamic color generation if available and selected.
private val DarkColorScheme = darkColorScheme(
    primary = Purple80,
    secondary = PurpleGrey80,
    tertiary = Pink80,
    background = Color(0xFF1C1B1F),
    surface = Color(0xFF1C1B1F)
)

private val LightColorScheme = lightColorScheme(
    primary = Purple40,
    secondary = PurpleGrey40,
    tertiary = Pink40,
    background = Color(0xFFFFFBFE),
    surface = Color(0xFFFFFBFE)
)

private val OledColorScheme = darkColorScheme(
    primary = Purple80,
    secondary = PurpleGrey80,
    tertiary = Pink80,
    background = Color.Black,
    surface = Color.Black,
    surfaceVariant = Color(0xFF121212),
    onBackground = Color.White,
    onSurface = Color.White
)

/**
 * Returns a fallback color scheme mapped roughly from a base seed color.
 * In a fully robust app, you'd use Material Color Utilities to generate a full tonal palette.
 */
private fun generateColorSchemeFromSeed(seed: Color, isDark: Boolean): ColorScheme {
    // For a simple implementation, we adjust the primary colors based on the seed.
    // Real dynamic theming from a seed uses the m3color library.
    val baseScheme = if (isDark) DarkColorScheme else LightColorScheme
    return baseScheme.copy(
        primary = seed,
        // Approximate other tones relative to primary for MVP
        primaryContainer = seed.copy(alpha = 0.3f),
        onPrimaryContainer = seed
    )
}

@Composable
fun FileManagerTheme(
    themeState: ThemeState,
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val isSystemDark = darkTheme
    
    val effectivelyDark = when (themeState.themeMode) {
        ThemeMode.SYSTEM -> isSystemDark
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.OLED -> true
    }

    val colorScheme = when {
        // 1. Dynamic Wallpaper Colors (Android 12+)
        themeState.accentColor == AccentColor.DYNAMIC && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (effectivelyDark) {
                if (themeState.themeMode == ThemeMode.OLED) {
                     dynamicDarkColorScheme(context).copy(
                         background = Color.Black,
                         surface = Color.Black,
                         surfaceVariant = Color(0xFF121212)
                     )
                } else {
                    dynamicDarkColorScheme(context)
                }
            } else {
                dynamicLightColorScheme(context)
            }
        }
        
        // 2. Custom Seed Color chosen
        themeState.accentColor != AccentColor.DYNAMIC && themeState.accentColor.color != null -> {
            val scheme = generateColorSchemeFromSeed(themeState.accentColor.color, effectivelyDark)
            if (themeState.themeMode == ThemeMode.OLED) {
                 scheme.copy(
                     background = Color.Black,
                     surface = Color.Black,
                     surfaceVariant = Color(0xFF121212)
                 )
            } else {
                scheme
            }
        }
        
        // 3. Fallback standard themes
        themeState.themeMode == ThemeMode.OLED -> OledColorScheme
        effectivelyDark -> DarkColorScheme
        else -> LightColorScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            window.navigationBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !effectivelyDark
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !effectivelyDark
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}