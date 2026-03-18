package dev.qtremors.arcile.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.materialkolor.hct.Hct
import com.materialkolor.scheme.SchemeTonalSpot
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.ColorScheme

// Baseline dynamic fallbacks (default purple base)
val DarkColorScheme = darkColorScheme(
    primary = Color(0xFFD0BCFF),
    secondary = Color(0xFFCCC2DC),
    tertiary = Color(0xFFEFB8C8),
    background = Color(0xFF141318),
    surface = Color(0xFF141318),
    surfaceVariant = Color(0xFF49454F),
    surfaceContainerLowest = Color(0xFF0F0D13),
    surfaceContainerLow = Color(0xFF1D1B20),
    surfaceContainer = Color(0xFF211F26),
    surfaceContainerHigh = Color(0xFF2B2930),
    surfaceContainerHighest = Color(0xFF36343B),
    onSurface = Color(0xFFE6E0E9),
    onBackground = Color(0xFFE6E0E9),
    onSurfaceVariant = Color(0xFFCAC4D0),
    outline = Color(0xFF938F99),
    outlineVariant = Color(0xFF49454F)
)

val LightColorScheme = lightColorScheme(
    primary = Color(0xFF6750A4),
    secondary = Color(0xFF625B71),
    tertiary = Color(0xFF7D5260),
    background = Color(0xFFFEF7FF),
    surface = Color(0xFFFEF7FF),
    surfaceContainerHighest = Color(0xFFE2E2E6),
    onSurface = Color(0xFF1B1B1F),
    onBackground = Color(0xFF1B1B1F),
    onSurfaceVariant = Color(0xFF44474E),
    outline = Color(0xFF74777F),
    outlineVariant = Color(0xFFC4C6D0)
)

val OledColorScheme = darkColorScheme(
    primary = Color(0xFFD0BCFF),
    secondary = Color(0xFFCCC2DC),
    tertiary = Color(0xFFEFB8C8),
    background = Color.Black,
    surface = Color.Black,
    surfaceVariant = Color(0xFF121212),
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = Color(0xFF0A0A0A),
    surfaceContainer = Color(0xFF111111),
    surfaceContainerHigh = Color(0xFF1A1A1A),
    surfaceContainerHighest = Color(0xFF222222),
    onBackground = Color.White,
    onSurface = Color.White
)

// Fully tailored custom accent palettes

// Category Colors - Light
val CatImageLight = Color(0xFF2E6B30)
val CatVideoLight = Color(0xFFBC004B)
val CatAudioLight = Color(0xFF8B5000)
val CatDocLight = Color(0xFF0061A4)
val CatArchiveLight = Color(0xFF86279E)
val CatApkLight = Color(0xFF006874)

// Category Colors - Dark
val CatImageDark = Color(0xFF90D88D)
val CatVideoDark = Color(0xFFFFB2BF)
val CatAudioDark = Color(0xFFFFB870)
val CatDocDark = Color(0xFF9ECAFF)
val CatArchiveDark = Color(0xFFF3B2FF)
val CatApkDark = Color(0xFF4FD8EB)

// Accent Colors (Material 500)
val AccentRed = Color(0xFFF44336)
val AccentPink = Color(0xFFE91E63)
val AccentPurple = Color(0xFF9C27B0)
val AccentDeepPurple = Color(0xFF673AB7)
val AccentIndigo = Color(0xFF3F51B5)
val AccentBlue = Color(0xFF2196F3)
val AccentLightBlue = Color(0xFF03A9F4)
val AccentCyan = Color(0xFF00BCD4)
val AccentTeal = Color(0xFF009688)
val AccentGreen = Color(0xFF4CAF50)
val AccentLightGreen = Color(0xFF8BC34A)
val AccentLime = Color(0xFFCDDC39)
val AccentYellow = Color(0xFFFFEB3B)
val AccentAmber = Color(0xFFFFC107)
val AccentOrange = Color(0xFFFF9800)
val AccentDeepOrange = Color(0xFFFF5722)
val AccentBrown = Color(0xFF795548)
val AccentBlueGrey = Color(0xFF607D8B)
val AccentGrey = Color(0xFF9E9E9E)
val AccentBlack = Color(0xFF212121)
val AccentMonochrome = Color.Gray // Keeping as fallback or alias

// OLED Specific
val OledSurfaceVariant = Color(0xFF121212)
val OledContainerLow = Color(0xFF0F0D13)
val OledContainerLowest = Color.Black



/**
 * Generates a full Material 3 color scheme from a primary seed color using Material Color Utilities.
 * This provides mathematically accurate tonal palettes for a cohesive design.
 */
fun buildScheme(primary: Color, isDark: Boolean): ColorScheme {
    val hct = Hct.fromInt(primary.toArgb())
    val scheme = SchemeTonalSpot(hct, isDark, 0.0)
    
    return if (isDark) {
        darkColorScheme(
            primary = Color(scheme.primary),
            onPrimary = Color(scheme.onPrimary),
            primaryContainer = Color(scheme.primaryContainer),
            onPrimaryContainer = Color(scheme.onPrimaryContainer),
            secondary = Color(scheme.secondary),
            onSecondary = Color(scheme.onSecondary),
            secondaryContainer = Color(scheme.secondaryContainer),
            onSecondaryContainer = Color(scheme.onSecondaryContainer),
            tertiary = Color(scheme.tertiary),
            onTertiary = Color(scheme.onTertiary),
            tertiaryContainer = Color(scheme.tertiaryContainer),
            onTertiaryContainer = Color(scheme.onTertiaryContainer),
            error = Color(scheme.error),
            onError = Color(scheme.onError),
            errorContainer = Color(scheme.errorContainer),
            onErrorContainer = Color(scheme.onErrorContainer),
            background = Color(scheme.background),
            onBackground = Color(scheme.onBackground),
            surface = Color(scheme.surface),
            onSurface = Color(scheme.onSurface),
            surfaceVariant = Color(scheme.surfaceVariant),
            onSurfaceVariant = Color(scheme.onSurfaceVariant),
            outline = Color(scheme.outline),
            outlineVariant = Color(scheme.outlineVariant),
            scrim = Color(scheme.scrim),
            inverseSurface = Color(scheme.inverseSurface),
            inverseOnSurface = Color(scheme.inverseOnSurface),
            inversePrimary = Color(scheme.inversePrimary),
            surfaceContainerLowest = Color(scheme.surfaceContainerLowest),
            surfaceContainerLow = Color(scheme.surfaceContainerLow),
            surfaceContainer = Color(scheme.surfaceContainer),
            surfaceContainerHigh = Color(scheme.surfaceContainerHigh),
            surfaceContainerHighest = Color(scheme.surfaceContainerHighest)
        )
    } else {
        lightColorScheme(
            primary = Color(scheme.primary),
            onPrimary = Color(scheme.onPrimary),
            primaryContainer = Color(scheme.primaryContainer),
            onPrimaryContainer = Color(scheme.onPrimaryContainer),
            secondary = Color(scheme.secondary),
            onSecondary = Color(scheme.onSecondary),
            secondaryContainer = Color(scheme.secondaryContainer),
            onSecondaryContainer = Color(scheme.onSecondaryContainer),
            tertiary = Color(scheme.tertiary),
            onTertiary = Color(scheme.onTertiary),
            tertiaryContainer = Color(scheme.tertiaryContainer),
            onTertiaryContainer = Color(scheme.onTertiaryContainer),
            error = Color(scheme.error),
            onError = Color(scheme.onError),
            errorContainer = Color(scheme.errorContainer),
            onErrorContainer = Color(scheme.onErrorContainer),
            background = Color(scheme.background),
            onBackground = Color(scheme.onBackground),
            surface = Color(scheme.surface),
            onSurface = Color(scheme.onSurface),
            surfaceVariant = Color(scheme.surfaceVariant),
            onSurfaceVariant = Color(scheme.onSurfaceVariant),
            outline = Color(scheme.outline),
            outlineVariant = Color(scheme.outlineVariant),
            scrim = Color(scheme.scrim),
            inverseSurface = Color(scheme.inverseSurface),
            inverseOnSurface = Color(scheme.inverseOnSurface),
            inversePrimary = Color(scheme.inversePrimary),
            surfaceContainerLowest = Color(scheme.surfaceContainerLowest),
            surfaceContainerLow = Color(scheme.surfaceContainerLow),
            surfaceContainer = Color(scheme.surfaceContainer),
            surfaceContainerHigh = Color(scheme.surfaceContainerHigh),
            surfaceContainerHighest = Color(scheme.surfaceContainerHighest)
        )
    }
}