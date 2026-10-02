package dev.qtremors.arcile.core.ui.theme

import android.app.Activity
import android.provider.Settings
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
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.graphics.toColorInt
import androidx.core.view.WindowCompat

// Baseline schemes moved to Color.kt

val LocalHapticsEnabled = staticCompositionLocalOf { true }
val LocalDoubleLineFilenames = staticCompositionLocalOf { false }
val LocalMarqueeFilenames = staticCompositionLocalOf { false }
val LocalFolderIconsEnabled = staticCompositionLocalOf { false }

@Composable
fun ArcileTheme(
    uiPreferences: UiPreferences,
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val isSystemDark = darkTheme
    
    val effectivelyDark = when (uiPreferences.themeMode) {
        ThemeMode.SYSTEM -> isSystemDark
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.OLED -> true
    }

    val colorScheme = when (uiPreferences.themePreset) {
        ThemePreset.DRACULA -> {
            if (effectivelyDark) {
                if (uiPreferences.themeMode == ThemeMode.OLED) DraculaOledColorScheme else DraculaDarkColorScheme
            } else {
                DraculaLightColorScheme
            }
        }
        ThemePreset.TOKYO_NIGHT -> {
            if (effectivelyDark) {
                if (uiPreferences.themeMode == ThemeMode.OLED) TokyoNightOledColorScheme else TokyoNightDarkColorScheme
            } else {
                TokyoNightLightColorScheme
            }
        }
        ThemePreset.CUSTOM -> {
            val primaryColor = parseColor(uiPreferences.customPrimaryColorHex, Color(0xFFBD93F9))
            val rawBg = parseColor(uiPreferences.customBackgroundColorHex, Color(0xFF282A36))
            val bg = if (uiPreferences.themeMode == ThemeMode.OLED) Color.Black else rawBg
            val fg = getContrastColor(bg)
            val scheme = buildScheme(primaryColor, effectivelyDark)
            val surfaceVar = if (fg == Color.White) {
                Color.White.copy(alpha = 0.08f).compositeOver(bg)
            } else {
                Color.Black.copy(alpha = 0.08f).compositeOver(bg)
            }
            scheme.copy(
                primary = primaryColor,
                background = bg,
                surface = bg,
                onBackground = fg,
                onSurface = fg,
                surfaceVariant = surfaceVar,
                onSurfaceVariant = fg
            )
        }
        ThemePreset.NONE -> when {
            uiPreferences.accentColor == AccentColor.MONOCHROME -> {
                buildMonochromeScheme(
                    isDark = effectivelyDark,
                    isOled = uiPreferences.themeMode == ThemeMode.OLED
                )
            }

            // 1. Dynamic Wallpaper Colors (Android 12+)
            uiPreferences.accentColor == AccentColor.DYNAMIC && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                if (effectivelyDark) {
                    if (uiPreferences.themeMode == ThemeMode.OLED) {
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
            uiPreferences.accentColor != AccentColor.DYNAMIC -> {
                val primaryColor = uiPreferences.accentColor.color ?: AccentBlue
                val scheme = buildScheme(primaryColor, effectivelyDark)

                 if (uiPreferences.themeMode == ThemeMode.OLED) {
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
            uiPreferences.themeMode == ThemeMode.OLED -> OledColorScheme
            effectivelyDark -> DarkColorScheme
            else -> LightColorScheme
        }
    }

    val baseCategoryColors = if (effectivelyDark) DarkCategoryColors else LightCategoryColors
    val baseSemanticColors = if (effectivelyDark) DarkSemanticColors else LightSemanticColors

    val categoryColors = if (uiPreferences.harmonizeColors) {
        baseCategoryColors.harmonizeWith(colorScheme.primary)
    } else {
        baseCategoryColors
    }

    val semanticColors = if (uiPreferences.harmonizeColors) {
        baseSemanticColors.harmonizeWith(colorScheme.primary)
    } else {
        baseSemanticColors
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !effectivelyDark
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !effectivelyDark
        }
    }

    val currentHapticFeedback = LocalHapticFeedback.current
    val customHapticFeedback = remember(currentHapticFeedback, uiPreferences.vibrationsEnabled) {
        object : HapticFeedback {
            override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) {
                if (uiPreferences.vibrationsEnabled) {
                    currentHapticFeedback.performHapticFeedback(hapticFeedbackType)
                }
            }
        }
    }

    val reducedMotionEnabled = remember(context) {
        try {
            Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1.0f
            ) == 0f
        } catch (e: Exception) {
            false
        }
    }

    CompositionLocalProvider(
        LocalCategoryColors provides categoryColors,
        LocalSemanticColors provides semanticColors,
        LocalSpacing provides Spacing(),
        LocalHapticsEnabled provides uiPreferences.vibrationsEnabled,
        LocalDoubleLineFilenames provides uiPreferences.doubleLineFilenames,
        LocalMarqueeFilenames provides uiPreferences.marqueeFilenames,
        LocalFolderIconsEnabled provides uiPreferences.folderIconsEnabled,
        LocalHapticFeedback provides customHapticFeedback,
        LocalReducedMotionEnabled provides reducedMotionEnabled
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            shapes = ExpressiveShapes,
            content = content
        )
    }
}

private fun parseColor(hex: String, fallback: Color): Color {
    return try {
        Color(hex.toColorInt())
    } catch (e: Exception) {
        fallback
    }
}

private fun getContrastColor(backgroundColor: Color): Color {
    val luminance = backgroundColor.red * 0.299f + backgroundColor.green * 0.587f + backgroundColor.blue * 0.114f
    return if (luminance > 0.5f) Color.Black else Color.White
}
