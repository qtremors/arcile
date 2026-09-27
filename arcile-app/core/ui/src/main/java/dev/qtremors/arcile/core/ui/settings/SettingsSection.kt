package dev.qtremors.arcile.core.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ListItemDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import dev.qtremors.arcile.core.ui.ArcileSectionHeader

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SettingsSection(
    title: String,
    showTitle: Boolean = true,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (showTitle) ArcileSectionHeader(text = title)
        Column(
            verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
            content = content
        )
    }
}
