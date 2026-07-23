package dev.qtremors.arcile.core.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.qtremors.arcile.core.ui.R
import dev.qtremors.arcile.core.presentation.FolderTab
import dev.qtremors.arcile.core.presentation.formatFileSize

@Composable
fun FolderTabsRow(
    tabs: List<FolderTab>,
    selectedPath: String?,
    onSelectTab: (String?) -> Unit,
    modifier: Modifier = Modifier
) {
    if (tabs.size <= 1) return

    val selectedIndex = tabs.indexOfFirst { it.path == selectedPath }.takeIf { it >= 0 } ?: 0
    val listState = rememberLazyListState()
    val contentDescription = stringResource(R.string.folder_tabs_content_description)

    LaunchedEffect(selectedIndex, tabs.size) {
        listState.scrollToItem(selectedIndex)
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = Color.Transparent
    ) {
        LazyRow(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .semantics { this.contentDescription = contentDescription },
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            itemsIndexed(
                items = tabs,
                key = { index, tab -> tab.path ?: "all:$index" }
            ) { _, tab ->
                ExpressiveFilterChip(
                    selected = tab.path == selectedPath,
                    onClick = { onSelectTab(tab.path) },
                    modifier = Modifier.widthIn(max = 280.dp),
                    label = {
                        Text(
                            text = "${tab.label} (${tab.count}) • ${formatFileSize(tab.totalSizeBytes)}",
                            style = MaterialTheme.typography.labelLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                )
            }
        }
    }
}
