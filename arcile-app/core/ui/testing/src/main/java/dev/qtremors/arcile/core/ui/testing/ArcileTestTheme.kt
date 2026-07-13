package dev.qtremors.arcile.core.ui.testing

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import dev.qtremors.arcile.core.ui.theme.AccentColor
import dev.qtremors.arcile.core.ui.theme.ArcileTheme
import dev.qtremors.arcile.core.ui.theme.LocalReducedMotionEnabled
import dev.qtremors.arcile.core.ui.theme.ThemeMode
import dev.qtremors.arcile.core.ui.theme.ThemeState

@Composable
fun ArcileTestTheme(content: @Composable () -> Unit) {
    ArcileTheme(
        themeState = ThemeState(
            themeMode = ThemeMode.LIGHT,
            accentColor = AccentColor.BLUE
        ),
        darkTheme = false,
        content = {
            CompositionLocalProvider(
                LocalReducedMotionEnabled provides true,
                content = content
            )
        }
    )
}
