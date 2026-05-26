package dev.qtremors.arcile.presentation.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.qtremors.arcile.R
import dev.qtremors.arcile.core.storage.domain.BrowserPresentationPreferences
import dev.qtremors.arcile.core.storage.domain.BrowserViewMode
import dev.qtremors.arcile.core.storage.domain.FileSortOption
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SortOptionDialog(
    title: String,
    selectedPreferences: BrowserPresentationPreferences,
    showApplyToSubfolders: Boolean,
    onDismiss: () -> Unit,
    onApply: (BrowserPresentationPreferences, Boolean) -> Unit
) {
    var applyToSubfolders by remember { mutableStateOf(false) }
    var draftPreferences by remember(selectedPreferences) {
        mutableStateOf(selectedPreferences.normalized())
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 16.dp)
        ) {
            val liveColumnCount = max(
                1,
                floor(((maxWidth.value - 32f) / draftPreferences.gridMinCellSize).toDouble()).toInt()
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                // Header (Simplified)
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge
                )

                // Layout & Density Section
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(
                        text = stringResource(R.string.browser_layout_view_mode),
                        style = MaterialTheme.typography.titleMedium
                    )

                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        BrowserViewMode.entries.forEachIndexed { index, mode ->
                            SegmentedButton(
                                shape = SegmentedButtonDefaults.itemShape(
                                    index = index,
                                    count = BrowserViewMode.entries.size
                                ),
                                colors = SegmentedButtonDefaults.colors(),
                                onClick = { draftPreferences = draftPreferences.copy(viewMode = mode) },
                                selected = draftPreferences.viewMode == mode,
                                icon = {
                                    Icon(
                                        imageVector = if (mode == BrowserViewMode.LIST) {
                                            Icons.AutoMirrored.Filled.ViewList
                                        } else {
                                            Icons.Default.GridView
                                        },
                                        contentDescription = null
                                    )
                                },
                                label = { Text(viewModeLabel(mode)) }
                            )
                        }
                    }

                    AnimatedContent(
                        targetState = draftPreferences.viewMode,
                        label = "browser_layout_controls"
                    ) { mode ->
                        if (mode == BrowserViewMode.LIST) {
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = stringResource(R.string.browser_layout_list_zoom),
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    Text(
                                        text = stringResource(
                                            R.string.browser_layout_list_zoom_value,
                                            (draftPreferences.listZoom * 100).roundToInt()
                                        ),
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                Slider(
                                    value = draftPreferences.listZoom,
                                    onValueChange = {
                                        draftPreferences = draftPreferences.copy(listZoom = it)
                                    },
                                    valueRange = BrowserPresentationPreferences.MIN_LIST_ZOOM..BrowserPresentationPreferences.MAX_LIST_ZOOM,
                                    steps = 7
                                )
                            }
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = stringResource(R.string.browser_layout_grid_size),
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    Text(
                                        text = stringResource(
                                            R.string.browser_layout_grid_columns_value,
                                            liveColumnCount
                                        ),
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                Slider(
                                    value = draftPreferences.gridMinCellSize,
                                    onValueChange = {
                                        draftPreferences = draftPreferences.copy(gridMinCellSize = it)
                                    },
                                    valueRange = BrowserPresentationPreferences.MIN_GRID_MIN_CELL_SIZE..BrowserPresentationPreferences.MAX_GRID_MIN_CELL_SIZE,
                                    steps = 1
                                )
                            }
                        }
                    }
                }

                // Sort Section (Grid Layout to prevent horizontal scroll)
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = stringResource(R.string.action_sort),
                        style = MaterialTheme.typography.titleMedium
                    )
                    
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            SortOptionChip(FileSortOption.NAME_ASC, draftPreferences, Modifier.weight(1f)) {
                                draftPreferences = draftPreferences.copy(sortOption = it)
                            }
                            SortOptionChip(FileSortOption.NAME_DESC, draftPreferences, Modifier.weight(1f)) {
                                draftPreferences = draftPreferences.copy(sortOption = it)
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            SortOptionChip(FileSortOption.DATE_NEWEST, draftPreferences, Modifier.weight(1f)) {
                                draftPreferences = draftPreferences.copy(sortOption = it)
                            }
                            SortOptionChip(FileSortOption.DATE_OLDEST, draftPreferences, Modifier.weight(1f)) {
                                draftPreferences = draftPreferences.copy(sortOption = it)
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            SortOptionChip(FileSortOption.SIZE_LARGEST, draftPreferences, Modifier.weight(1f)) {
                                draftPreferences = draftPreferences.copy(sortOption = it)
                            }
                            SortOptionChip(FileSortOption.SIZE_SMALLEST, draftPreferences, Modifier.weight(1f)) {
                                draftPreferences = draftPreferences.copy(sortOption = it)
                            }
                        }
                    }
                }

                // Scope Section
                if (showApplyToSubfolders) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.large)
                            .clickable { applyToSubfolders = !applyToSubfolders }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.apply_to_folder_and_subfolders),
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = stringResource(R.string.browser_layout_persistence_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Switch(
                            checked = applyToSubfolders,
                            onCheckedChange = null // Handled by Row click
                        )
                    }
                }

                // Actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.cancel))
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    FilledTonalButton(
                        onClick = {
                            onApply(draftPreferences.normalized(), applyToSubfolders)
                            onDismiss()
                        }
                    ) {
                        Text(stringResource(R.string.apply))
                    }
                }
            }
        }
    }
}

@Composable
private fun SortOptionChip(
    option: FileSortOption,
    preferences: BrowserPresentationPreferences,
    modifier: Modifier = Modifier,
    onSelect: (FileSortOption) -> Unit
) {
    FilterChip(
        selected = preferences.sortOption == option,
        onClick = { onSelect(option) },
        label = { 
            Text(
                text = sortLabel(option),
                modifier = Modifier.fillMaxWidth(),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            ) 
        },
        modifier = modifier
    )
}

@Composable
private fun sortLabel(option: FileSortOption): String {
    val labelRes = when (option) {
        FileSortOption.NAME_ASC -> R.string.sort_name_asc
        FileSortOption.NAME_DESC -> R.string.sort_name_desc
        FileSortOption.DATE_NEWEST -> R.string.sort_date_newest
        FileSortOption.DATE_OLDEST -> R.string.sort_date_oldest
        FileSortOption.SIZE_LARGEST -> R.string.sort_size_largest
        FileSortOption.SIZE_SMALLEST -> R.string.sort_size_smallest
    }
    return stringResource(labelRes)
}

@Composable
private fun viewModeLabel(mode: BrowserViewMode): String {
    return stringResource(
        if (mode == BrowserViewMode.LIST) R.string.list_view else R.string.grid_view
    )
}
