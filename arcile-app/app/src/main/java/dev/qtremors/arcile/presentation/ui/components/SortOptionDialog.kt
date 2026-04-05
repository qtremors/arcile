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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.qtremors.arcile.R
import dev.qtremors.arcile.domain.BrowserPresentationPreferences
import dev.qtremors.arcile.domain.BrowserViewMode
import dev.qtremors.arcile.presentation.FileSortOption
import kotlin.math.floor
import kotlin.math.max

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

    val sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp, top = 8.dp)
        ) {
            val liveColumnCount = max(
                1,
                floor(((maxWidth.value - 32f) / draftPreferences.gridMinCellSize).toDouble()).toInt()
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
            ) {
                Surface(
                    shape = MaterialTheme.shapes.extraLarge,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    tonalElevation = 2.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 18.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Surface(
                                shape = MaterialTheme.shapes.large,
                                color = MaterialTheme.colorScheme.secondaryContainer
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Tune,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.padding(10.dp)
                                )
                            }
                            Column {
                                Text(text = title, style = MaterialTheme.typography.titleLarge)
                                Text(
                                    text = stringResource(
                                        R.string.browser_layout_summary,
                                        sortLabel(draftPreferences.sortOption),
                                        viewModeLabel(draftPreferences.viewMode)
                                    ),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))
                Text(
                    text = stringResource(R.string.browser_layout_view_mode),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
                Spacer(modifier = Modifier.height(10.dp))
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    BrowserViewMode.entries.forEachIndexed { index, mode ->
                        SegmentedButton(
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = BrowserViewMode.entries.size),
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

                Spacer(modifier = Modifier.height(22.dp))
                Text(
                    text = stringResource(R.string.action_sort),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
                Spacer(modifier = Modifier.height(10.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(FileSortOption.entries) { option ->
                        FilterChip(
                            selected = draftPreferences.sortOption == option,
                            onClick = { draftPreferences = draftPreferences.copy(sortOption = option) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Sort,
                                    contentDescription = null
                                )
                            },
                            label = { Text(sortLabel(option)) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(22.dp))
                AnimatedContent(
                    targetState = draftPreferences.viewMode,
                    label = "browser_layout_controls"
                ) { mode ->
                    if (mode == BrowserViewMode.LIST) {
                        PresentationSliderCard(
                            icon = Icons.AutoMirrored.Filled.ViewList,
                            title = stringResource(R.string.browser_layout_list_zoom),
                            summary = stringResource(
                                R.string.browser_layout_list_zoom_value,
                                (draftPreferences.listZoom * 100).toInt()
                            )
                        ) {
                            Slider(
                                value = draftPreferences.listZoom,
                                onValueChange = {
                                    draftPreferences = draftPreferences.copy(listZoom = it)
                                },
                                valueRange = BrowserPresentationPreferences.MIN_LIST_ZOOM..BrowserPresentationPreferences.MAX_LIST_ZOOM
                            )
                        }
                    } else {
                        PresentationSliderCard(
                            icon = Icons.Default.Apps,
                            title = stringResource(R.string.browser_layout_grid_size),
                            summary = stringResource(R.string.browser_layout_grid_columns_value, liveColumnCount)
                        ) {
                            Slider(
                                value = draftPreferences.gridMinCellSize,
                                onValueChange = {
                                    draftPreferences = draftPreferences.copy(gridMinCellSize = it)
                                },
                                valueRange = BrowserPresentationPreferences.MIN_GRID_MIN_CELL_SIZE..BrowserPresentationPreferences.MAX_GRID_MIN_CELL_SIZE
                            )
                        }
                    }
                }

                if (showApplyToSubfolders) {
                    Spacer(modifier = Modifier.height(18.dp))
                    Surface(
                        shape = MaterialTheme.shapes.extraLarge,
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.extraLarge)
                            .clickable { applyToSubfolders = !applyToSubfolders }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(checked = applyToSubfolders, onCheckedChange = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = stringResource(R.string.apply_to_folder_and_subfolders),
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                Text(
                                    text = stringResource(R.string.browser_layout_persistence_hint),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.cancel))
                    }
                    FilledTonalButton(
                        onClick = {
                            onApply(draftPreferences.normalized(), applyToSubfolders)
                            onDismiss()
                        }
                    ) {
                        Text(stringResource(R.string.apply))
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun PresentationSliderCard(
    icon: ImageVector,
    title: String,
    summary: String,
    content: @Composable () -> Unit
) {
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 2.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            ListItem(
                leadingContent = { Icon(imageVector = icon, contentDescription = null) },
                headlineContent = { Text(title) },
                supportingContent = { Text(summary) },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent)
            )
            content()
        }
    }
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
