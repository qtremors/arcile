package dev.qtremors.arcile.presentation.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.qtremors.arcile.R
import dev.qtremors.arcile.presentation.FolderTab
import dev.qtremors.arcile.utils.formatFileSize

@Composable
fun FolderTabsRow(
    tabs: List<FolderTab>,
    selectedPath: String?,
    onSelectTab: (String?) -> Unit,
    modifier: Modifier = Modifier
) {
    if (tabs.size <= 1) return

    val selectedIndex = tabs.indexOfFirst { it.path == selectedPath }.takeIf { it >= 0 } ?: 0
    val contentDescription = stringResource(R.string.folder_tabs_content_description)
    PrimaryScrollableTabRow(
        selectedTabIndex = selectedIndex,
        modifier = modifier
            .fillMaxWidth()
            .semantics { this.contentDescription = contentDescription },
        edgePadding = 16.dp
    ) {
        tabs.forEach { tab ->
            Tab(
                selected = tab.path == selectedPath,
                onClick = { onSelectTab(tab.path) },
                text = {
                    Text(
                        text = "${tab.label} (${tab.count}) • ${formatFileSize(tab.totalSizeBytes)}",
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1
                    )
                }
            )
        }
    }
}
