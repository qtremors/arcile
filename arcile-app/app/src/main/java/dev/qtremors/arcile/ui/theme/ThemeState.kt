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
    
    // Row 1
    RED(AccentRed),
    PINK(AccentPink),
    PURPLE(AccentPurple),
    DEEP_PURPLE(AccentDeepPurple),
    
    // Row 2
    CYAN(AccentCyan),
    LIGHT_BLUE(AccentLightBlue),
    BLUE(AccentBlue),
    INDIGO(AccentIndigo),
    
    // Row 3
    TEAL(AccentTeal),
    GREEN(AccentGreen),
    LIGHT_GREEN(AccentLightGreen),
    LIME(AccentLime),
    
    // Row 4
    DEEP_ORANGE(AccentDeepOrange),
    ORANGE(AccentOrange),
    AMBER(AccentAmber),
    YELLOW(AccentYellow),
    
    // Row 5
    BROWN(AccentBrown),
    BLUE_GREY(AccentBlueGrey),
    GREY(AccentGrey),
    BLACK(AccentBlack),

    MONOCHROME(AccentMonochrome) // Keep as fallback or special case
}



data class ThemeState(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val accentColor: AccentColor = AccentColor.DYNAMIC
)
