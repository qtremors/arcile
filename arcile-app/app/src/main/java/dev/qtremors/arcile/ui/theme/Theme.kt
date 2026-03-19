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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// Baseline schemes moved to Color.kt

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
                         surfaceVariant = OledSurfaceVariant
                     )
                } else {
                    dynamicDarkColorScheme(context)
                }
            } else {
                dynamicLightColorScheme(context)
            }
        }
        
        // 2. Custom Seed Color chosen
        themeState.accentColor != AccentColor.DYNAMIC -> {
            val primaryColor = themeState.accentColor.color ?: AccentBlue
            val scheme = buildScheme(primaryColor, effectivelyDark)

             if (themeState.themeMode == ThemeMode.OLED) {
                  scheme.copy(
                      background = Color.Black,
                      surface = Color.Black,
                      surfaceVariant = OledSurfaceVariant,
                      surfaceContainerLowest = OledContainerLowest,
                      surfaceContainerLow = OledContainerLow
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

    val categoryColors = if (effectivelyDark) DarkCategoryColors else LightCategoryColors

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !effectivelyDark
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !effectivelyDark
        }
    }

    CompositionLocalProvider(
        LocalCategoryColors provides categoryColors,
        LocalSpacing provides Spacing()
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            shapes = ExpressiveShapes,
            content = content
        )
    }
}