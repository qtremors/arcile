package dev.qtremors.arcile.core.ui.theme

data class UiPreferences(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val accentColor: AccentColor = AccentColor.DYNAMIC,
    val harmonizeColors: Boolean = true,
    val vibrationsEnabled: Boolean = true,
    val doubleLineFilenames: Boolean = false,
    val marqueeFilenames: Boolean = false,
    val landscapeDualPaneEnabled: Boolean = false,
    val folderIconsEnabled: Boolean = false,
    val themePreset: ThemePreset = ThemePreset.NONE,
    val customPrimaryColorHex: String = "#BD93F9",
    val customBackgroundColorHex: String = "#282A36"
)
