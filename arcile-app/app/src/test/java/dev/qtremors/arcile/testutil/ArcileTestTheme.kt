package dev.qtremors.arcile.testutil

import androidx.compose.runtime.Composable
import dev.qtremors.arcile.ui.theme.AccentColor
import dev.qtremors.arcile.ui.theme.FileManagerTheme
import dev.qtremors.arcile.ui.theme.ThemeMode
import dev.qtremors.arcile.ui.theme.ThemeState

@Composable
fun ArcileTestTheme(content: @Composable () -> Unit) {
    FileManagerTheme(
        themeState = ThemeState(
            themeMode = ThemeMode.LIGHT,
            accentColor = AccentColor.BLUE
        ),
        darkTheme = false,
        content = content
    )
}
