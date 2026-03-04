package dev.qtremors.arcile.ui.theme

import androidx.compose.ui.graphics.Color

enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK,
    OLED
}

enum class AccentColor(val color: Color?) {
    DYNAMIC(null), // Handled by Material3 dynamic colors
    MONOCHROME(Color.Gray),
    BLUE(Color(0xFF2196F3)),
    CYAN(Color(0xFF00BCD4)),
    GREEN(Color(0xFF4CAF50)),
    RED(Color(0xFFF44336)),
    PURPLE(Color(0xFF9C27B0))
}

data class ThemeState(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val accentColor: AccentColor = AccentColor.DYNAMIC
)
