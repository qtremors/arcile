package dev.qtremors.arcile.presentation.ui.components.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import dev.qtremors.arcile.presentation.ui.components.ArcileSectionHeader
import dev.qtremors.arcile.presentation.ui.components.ArcileListSurface

@Composable
fun SettingsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ArcileSectionHeader(text = title)
        ArcileListSurface(content = content)
    }
}
