package dev.qtremors.arcile.core.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.materialkolor.hct.Hct
import com.materialkolor.scheme.SchemeTonalSpot
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.staticCompositionLocalOf
import com.materialkolor.ktx.harmonize

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

// Dracula Custom Color Schemes
val DraculaDarkColorScheme = darkColorScheme(
    primary = Color(0xFFBD93F9),          // Purple
    onPrimary = Color(0xFF282A36),        // Background
    primaryContainer = Color(0xFF44475A), // Current Line
    onPrimaryContainer = Color(0xFFF8F8F2),
    secondary = Color(0xFFFF79C6),        // Pink
    onSecondary = Color(0xFF282A36),
    secondaryContainer = Color(0xFF44475A),
    onSecondaryContainer = Color(0xFFF8F8F2),
    tertiary = Color(0xFF8BE9FD),         // Cyan
    onTertiary = Color(0xFF282A36),
    tertiaryContainer = Color(0xFF44475A),
    onTertiaryContainer = Color(0xFFF8F8F2),
    background = Color(0xFF282A36),
    onBackground = Color(0xFFF8F8F2),
    surface = Color(0xFF282A36),
    onSurface = Color(0xFFF8F8F2),
    surfaceVariant = Color(0xFF343746),   // Selection / Slightly lighter variant
    onSurfaceVariant = Color(0xFFF8F8F2),
    surfaceContainerLowest = Color(0xFF1E1F29),
    surfaceContainerLow = Color(0xFF21222C),
    surfaceContainer = Color(0xFF282A36),
    surfaceContainerHigh = Color(0xFF343746),
    surfaceContainerHighest = Color(0xFF44475A),
    outline = Color(0xFF6272A4),          // Comment
    outlineVariant = Color(0xFF44475A),
    error = Color(0xFFFF5555),            // Red
    onError = Color(0xFF282A36)
)

val DraculaOledColorScheme = DraculaDarkColorScheme.copy(
    background = Color.Black,
    surface = Color.Black,
    surfaceVariant = Color(0xFF121212),
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = Color(0xFF0F0D13),
    surfaceContainer = Color(0xFF111111),
    surfaceContainerHigh = Color(0xFF1A1A1A),
    surfaceContainerHighest = Color(0xFF222222)
)

val DraculaLightColorScheme = lightColorScheme(
    primary = Color(0xFF6272A4),          // Comment color used as primary
    onPrimary = Color(0xFFF8F8F2),
    primaryContainer = Color(0xFFE2E2DC),
    onPrimaryContainer = Color(0xFF282A36),
    secondary = Color(0xFFFF5555),        // Red
    onSecondary = Color(0xFFF8F8F2),
    secondaryContainer = Color(0xFFE2E2DC),
    onSecondaryContainer = Color(0xFF282A36),
    tertiary = Color(0xFF50FA7B),         // Green
    onTertiary = Color(0xFF282A36),
    background = Color(0xFFF8F8F2),       // Light off-white foreground
    onBackground = Color(0xFF282A36),     // Dark background color
    surface = Color(0xFFF8F8F2),
    onSurface = Color(0xFF282A36),
    surfaceVariant = Color(0xFFE2E2DC),
    onSurfaceVariant = Color(0xFF282A36),
    outline = Color(0xFF6272A4),
    outlineVariant = Color(0xFFE2E2DC),
    error = Color(0xFFFF5555),
    onError = Color(0xFFF8F8F2)
)

// Tokyo Night Custom Color Schemes
val TokyoNightDarkColorScheme = darkColorScheme(
    primary = Color(0xFF7AA2F7),          // Blue
    onPrimary = Color(0xFF1A1B26),
    primaryContainer = Color(0xFF24283B),
    onPrimaryContainer = Color(0xFFC0CAF5),
    secondary = Color(0xFFBB9AF3),        // Purple
    onSecondary = Color(0xFF1A1B26),
    secondaryContainer = Color(0xFF24283B),
    onSecondaryContainer = Color(0xFFC0CAF5),
    tertiary = Color(0xFF7DCFFF),         // Cyan
    onTertiary = Color(0xFF1A1B26),
    tertiaryContainer = Color(0xFF24283B),
    onTertiaryContainer = Color(0xFFC0CAF5),
    background = Color(0xFF1A1B26),
    onBackground = Color(0xFFC0CAF5),
    surface = Color(0xFF1A1B26),
    onSurface = Color(0xFFC0CAF5),
    surfaceVariant = Color(0xFF24283B),
    onSurfaceVariant = Color(0xFFC0CAF5),
    surfaceContainerLowest = Color(0xFF16161E),
    surfaceContainerLow = Color(0xFF1F202E),
    surfaceContainer = Color(0xFF24283B),
    surfaceContainerHigh = Color(0xFF2F3549),
    surfaceContainerHighest = Color(0xFF383E56),
    outline = Color(0xFF565F89),
    outlineVariant = Color(0xFF24283B),
    error = Color(0xFFF7768E),            // Tokyo Night Red
    onError = Color(0xFF1A1B26)
)

val TokyoNightOledColorScheme = TokyoNightDarkColorScheme.copy(
    background = Color.Black,
    surface = Color.Black,
    surfaceVariant = Color(0xFF121212),
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = Color(0xFF0F0D13),
    surfaceContainer = Color(0xFF111111),
    surfaceContainerHigh = Color(0xFF1A1A1A),
    surfaceContainerHighest = Color(0xFF222222)
)

val TokyoNightLightColorScheme = lightColorScheme(
    primary = Color(0xFF385898),
    onPrimary = Color(0xFFE1E2E7),
    primaryContainer = Color(0xFFCFD0D7),
    onPrimaryContainer = Color(0xFF3760BF),
    secondary = Color(0xFF9854F1),
    onSecondary = Color(0xFFE1E2E7),
    secondaryContainer = Color(0xFFCFD0D7),
    onSecondaryContainer = Color(0xFF3760BF),
    tertiary = Color(0xFF00668E),
    onTertiary = Color(0xFFE1E2E7),
    background = Color(0xFFE1E2E7),
    onBackground = Color(0xFF3760BF),
    surface = Color(0xFFE1E2E7),
    onSurface = Color(0xFF3760BF),
    surfaceVariant = Color(0xFFCFD0D7),
    onSurfaceVariant = Color(0xFF3760BF),
    outline = Color(0xFF8F93A2),
    outlineVariant = Color(0xFFCFD0D7),
    error = Color(0xFF8C4351),
    onError = Color(0xFFE1E2E7)
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
val AccentGrey = Color(0xFF757575)
val AccentBlack = Color(0xFF111111)

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

fun buildMonochromeScheme(isDark: Boolean, isOled: Boolean = false): ColorScheme {
    val background = when {
        isOled -> Color.Black
        isDark -> Color(0xFF121212)
        else -> Color(0xFFFAFAFA)
    }
    val surface = background
    return if (isDark || isOled) {
        darkColorScheme(
            primary = Color(0xFFE0E0E0),
            onPrimary = Color(0xFF1A1A1A),
            primaryContainer = Color(0xFF3A3A3A),
            onPrimaryContainer = Color(0xFFEDEDED),
            secondary = Color(0xFFCFCFCF),
            onSecondary = Color(0xFF1F1F1F),
            secondaryContainer = Color(0xFF333333),
            onSecondaryContainer = Color(0xFFE6E6E6),
            tertiary = Color(0xFFBDBDBD),
            onTertiary = Color(0xFF1F1F1F),
            tertiaryContainer = Color(0xFF2F2F2F),
            onTertiaryContainer = Color(0xFFE0E0E0),
            background = background,
            onBackground = Color(0xFFEDEDED),
            surface = surface,
            onSurface = Color(0xFFEDEDED),
            surfaceVariant = if (isOled) OledSurfaceVariant else Color(0xFF2C2C2C),
            onSurfaceVariant = Color(0xFFC7C7C7),
            surfaceContainerLowest = if (isOled) Color.Black else Color(0xFF0D0D0D),
            surfaceContainerLow = if (isOled) Color(0xFF0A0A0A) else Color(0xFF171717),
            surfaceContainer = Color(0xFF1F1F1F),
            surfaceContainerHigh = Color(0xFF2A2A2A),
            surfaceContainerHighest = Color(0xFF353535),
            outline = Color(0xFF8A8A8A),
            outlineVariant = Color(0xFF454545),
            error = Color(0xFFFFB4AB),
            onError = Color(0xFF690005)
        )
    } else {
        lightColorScheme(
            primary = Color(0xFF424242),
            onPrimary = Color.White,
            primaryContainer = Color(0xFFE0E0E0),
            onPrimaryContainer = Color(0xFF1B1B1B),
            secondary = Color(0xFF616161),
            onSecondary = Color.White,
            secondaryContainer = Color(0xFFE8E8E8),
            onSecondaryContainer = Color(0xFF202020),
            tertiary = Color(0xFF757575),
            onTertiary = Color.White,
            tertiaryContainer = Color(0xFFEDEDED),
            onTertiaryContainer = Color(0xFF242424),
            background = background,
            onBackground = Color(0xFF1B1B1B),
            surface = surface,
            onSurface = Color(0xFF1B1B1B),
            surfaceVariant = Color(0xFFE3E3E3),
            onSurfaceVariant = Color(0xFF464646),
            surfaceContainerLowest = Color.White,
            surfaceContainerLow = Color(0xFFF5F5F5),
            surfaceContainer = Color(0xFFEFEFEF),
            surfaceContainerHigh = Color(0xFFE8E8E8),
            surfaceContainerHighest = Color(0xFFE0E0E0),
            outline = Color(0xFF777777),
            outlineVariant = Color(0xFFC7C7C7),
            error = Color(0xFFBA1A1A),
            onError = Color.White
        )
    }
}

data class SemanticColors(
    val success: Color,
    val onSuccess: Color,
    val successContainer: Color,
    val warning: Color,
    val onWarning: Color,
    val warningContainer: Color,
    val operationProgress: Color,
    val hiddenBadge: Color
)

val LightSemanticColors = SemanticColors(
    success = Color(0xFF2E6C30),
    onSuccess = Color(0xFFFFFFFF),
    successContainer = Color(0xFFB1F1B5),
    warning = Color(0xFF8B5000),
    onWarning = Color(0xFFFFFFFF),
    warningContainer = Color(0xFFFFDCBE),
    operationProgress = Color(0xFF0061A4),
    hiddenBadge = Color(0xFF757575)
)

val DarkSemanticColors = SemanticColors(
    success = Color(0xFF96D799),
    onSuccess = Color(0xFF00390A),
    successContainer = Color(0xFF13531D),
    warning = Color(0xFFFFB870),
    onWarning = Color(0xFF4A2800),
    warningContainer = Color(0xFF693C00),
    operationProgress = Color(0xFF9ECAFF),
    hiddenBadge = Color(0xFF9E9E9E)
)

fun Color.harmonizeWith(keyColor: Color): Color {
    return this.harmonize(keyColor)
}

fun SemanticColors.harmonizeWith(keyColor: Color): SemanticColors {
    return SemanticColors(
        success = this.success.harmonizeWith(keyColor),
        onSuccess = this.onSuccess.harmonizeWith(keyColor),
        successContainer = this.successContainer.harmonizeWith(keyColor),
        warning = this.warning.harmonizeWith(keyColor),
        onWarning = this.onWarning.harmonizeWith(keyColor),
        warningContainer = this.warningContainer.harmonizeWith(keyColor),
        operationProgress = this.operationProgress.harmonizeWith(keyColor),
        hiddenBadge = this.hiddenBadge.harmonizeWith(keyColor)
    )
}

val LocalSemanticColors = staticCompositionLocalOf { LightSemanticColors }
