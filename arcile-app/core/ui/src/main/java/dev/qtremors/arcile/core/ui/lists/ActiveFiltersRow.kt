package dev.qtremors.arcile.core.ui.lists


import dev.qtremors.arcile.core.ui.R
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.qtremors.arcile.core.storage.domain.SearchFilters
import dev.qtremors.arcile.core.ui.ExpressiveFilterChip

@Composable
fun ActiveFiltersRow(
    filters: SearchFilters,
    onClearFilter: (SearchFilters) -> Unit
) {
    val activeChips = mutableListOf<Pair<String, SearchFilters>>()

    val itemType = filters.itemType
    if (itemType != null && itemType != "Any") {
        activeChips.add(Pair(itemType, filters.copy(itemType = null)))
    }

    filters.fileType?.let { fileType ->
        activeChips.add(Pair(fileType, filters.copy(fileType = null)))
    }
    val minSize = filters.minSize
    val maxSize = filters.maxSize
    if (minSize != null || maxSize != null) {
        val label = when {
            minSize != null && maxSize != null -> stringResource(
                R.string.filter_chip_size_range,
                minSize / (1024 * 1024),
                maxSize / (1024 * 1024)
            )
            minSize != null -> stringResource(R.string.filter_chip_size_min, minSize / (1024 * 1024))
            else -> stringResource(R.string.filter_chip_size_max, maxSize!! / (1024 * 1024))
        }
        activeChips.add(Pair(label, filters.copy(minSize = null, maxSize = null)))
    }
    if (filters.minDateMillis != null || filters.maxDateMillis != null) {
        activeChips.add(Pair(stringResource(R.string.filter_chip_date), filters.copy(minDateMillis = null, maxDateMillis = null)))
    }
    if (filters.extensions.isNotEmpty()) {
        activeChips.add(Pair(stringResource(R.string.filter_chip_extensions, filters.extensions.joinToString(", ")), filters.copy(extensions = emptySet())))
    }
    if (filters.includeHidden) {
        activeChips.add(Pair(stringResource(R.string.filter_chip_hidden), filters.copy(includeHidden = false)))
    }
    filters.storageVolumeId?.let { storageVolumeId ->
        activeChips.add(Pair(stringResource(R.string.filter_chip_volume, storageVolumeId), filters.copy(storageVolumeId = null)))
    }
    filters.folderScopePath?.let { folderScopePath ->
        activeChips.add(Pair(stringResource(R.string.filter_chip_scope, folderScopePath), filters.copy(folderScopePath = null)))
    }
    filters.mimeType?.let { mimeType ->
        activeChips.add(Pair(stringResource(R.string.filter_chip_mime, mimeType), filters.copy(mimeType = null)))
    }
    filters.savedPresetName?.let { savedPresetName ->
        activeChips.add(Pair(stringResource(R.string.filter_chip_preset, savedPresetName), filters.copy(savedPresetName = null)))
    }

    if (activeChips.isNotEmpty()) {
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(activeChips) { (label, updatedFilter) ->
                ExpressiveFilterChip(
                    selected = true,
                    onClick = { onClearFilter(updatedFilter) },
                    label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                    leadingIcon = {
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
