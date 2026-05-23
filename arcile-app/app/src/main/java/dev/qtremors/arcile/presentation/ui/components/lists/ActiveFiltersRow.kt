package dev.qtremors.arcile.presentation.ui.components.lists
import dev.qtremors.arcile.R
import androidx.compose.ui.res.stringResource

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.qtremors.arcile.domain.SearchFilters

@Composable
fun ActiveFiltersRow(
    filters: SearchFilters,
    onClearFilter: (SearchFilters) -> Unit
) {
    val activeChips = mutableListOf<Pair<String, SearchFilters>>()

    if (filters.itemType != null && filters.itemType != "Any") {
        activeChips.add(Pair(filters.itemType, filters.copy(itemType = null)))
    }

    if (filters.fileType != null) {
        activeChips.add(Pair(filters.fileType, filters.copy(fileType = null)))
    }
    if (filters.minSize != null || filters.maxSize != null) {
        val label = when {
            filters.minSize != null && filters.maxSize != null -> "Size: ${filters.minSize / (1024 * 1024)}MB - ${filters.maxSize / (1024 * 1024)}MB"
            filters.minSize != null -> "Size: >${filters.minSize / (1024 * 1024)}MB"
            else -> "Size: <${filters.maxSize!! / (1024 * 1024)}MB"
        }
        activeChips.add(Pair(label, filters.copy(minSize = null, maxSize = null)))
    }
    if (filters.minDateMillis != null || filters.maxDateMillis != null) {
        activeChips.add(Pair("Date Filter", filters.copy(minDateMillis = null, maxDateMillis = null)))
    }
    if (filters.extensions.isNotEmpty()) {
        activeChips.add(Pair("Ext: ${filters.extensions.joinToString(", ")}", filters.copy(extensions = emptySet())))
    }
    if (filters.includeHidden) {
        activeChips.add(Pair("Hidden", filters.copy(includeHidden = false)))
    }
    if (filters.storageVolumeId != null) {
        activeChips.add(Pair("Volume: ${filters.storageVolumeId}", filters.copy(storageVolumeId = null)))
    }
    if (filters.folderScopePath != null) {
        activeChips.add(Pair("Scope: ${filters.folderScopePath}", filters.copy(folderScopePath = null)))
    }
    if (filters.mimeType != null) {
        activeChips.add(Pair("MIME: ${filters.mimeType}", filters.copy(mimeType = null)))
    }
    if (filters.savedPresetName != null) {
        activeChips.add(Pair("Preset: ${filters.savedPresetName}", filters.copy(savedPresetName = null)))
    }

    if (activeChips.isNotEmpty()) {
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(activeChips) { (label, updatedFilter) ->
                InputChip(
                    selected = true,
                    onClick = { onClearFilter(updatedFilter) },
                    label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                    trailingIcon = {
                        Icon(
                            Icons.Default.Clear,
                            contentDescription = stringResource(R.string.action_clear),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                )
            }
        }
    }
}
