package dev.qtremors.arcile.shared.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import dev.qtremors.arcile.shared.ui.ArcileSectionHeader
import dev.qtremors.arcile.shared.ui.ArcileListSurface

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
