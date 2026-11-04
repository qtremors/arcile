package dev.qtremors.arcile.core.ui.theme

data class UiPreferences(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val accentColor: AccentColor = AccentColor.DYNAMIC,
    val harmonizeColors: Boolean = true,
    val vibrationsEnabled: Boolean = true,
    val doubleLineFilenames: Boolean = false,
    val marqueeFilenames: Boolean = false,
    val landscapeDualPaneEnabled: Boolean = false,
    /** Dedicated icons for well-known storage folders such as DCIM, Download, and Recordings. */
    val specialFolderIconsEnabled: Boolean = false,
    /** Icons of the app a folder belongs to, such as WhatsApp or the default camera app. */
    val appFolderIconsEnabled: Boolean = false,
    val fileDetails: FileDetailVisibility = FileDetailVisibility(),
    val themePreset: ThemePreset = ThemePreset.NONE,
    val customPrimaryColorHex: String = "#BD93F9",
    val customBackgroundColorHex: String = "#282A36"
)
