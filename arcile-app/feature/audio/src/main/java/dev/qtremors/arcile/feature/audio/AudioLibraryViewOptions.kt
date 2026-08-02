package dev.qtremors.arcile.feature.audio

import dev.qtremors.arcile.core.storage.domain.CategoryLibraryPage
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.qtremors.arcile.core.storage.domain.FileListingPreferences
import dev.qtremors.arcile.core.storage.domain.FileSortOption
import dev.qtremors.arcile.core.storage.domain.FileViewMode
import dev.qtremors.arcile.core.storage.domain.CategoryGrouping
import dev.qtremors.arcile.core.ui.ExpressiveFilterChip
import dev.qtremors.arcile.core.ui.ExpressiveSegmentedRow
import dev.qtremors.arcile.core.ui.rememberArcileHaptics
import dev.qtremors.arcile.core.ui.theme.ExpressiveShapes
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AudioViewOptionsDialog(
    tab: CategoryLibraryPage,
    presentation: FileListingPreferences,
    grouping: CategoryGrouping,
    showFileDetails: Boolean,
    onApply: (FileListingPreferences, CategoryGrouping, Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    val haptics = rememberArcileHaptics()
    var draftPresentation by remember(presentation, tab) {
        mutableStateOf(
            presentation.normalized().let {
                if (tab == CategoryLibraryPage.FOLDERS) {
                    it.copy(viewMode = FileViewMode.GRID)
                } else {
                    it
                }
            }
        )
    }
    var draftGrouping by remember(grouping) { mutableStateOf(grouping) }
    var draftDetails by remember(showFileDetails) { mutableStateOf(showFileDetails) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                Text(
                    text = stringResource(
                        if (tab == CategoryLibraryPage.ITEMS) {
                            R.string.audio_view_sort_title
                        } else {
                            R.string.audio_folder_view_sort_title
                        }
                    ),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )
                if (tab == CategoryLibraryPage.ITEMS) {
                    AudioViewModeSection(
                        selected = draftPresentation.viewMode,
                        onSelected = {
                            draftPresentation = draftPresentation.copy(viewMode = it)
                        }
                    )
                }
                AudioSizeSection(
                    presentation = draftPresentation,
                    availableWidth = this@BoxWithConstraints.maxWidth,
                    onChange = { draftPresentation = it }
                )
                AudioSortSection(
                    selected = draftPresentation.sortOption,
                    onSelected = {
                        draftPresentation = draftPresentation.copy(sortOption = it)
                    }
                )
                if (tab == CategoryLibraryPage.ITEMS) {
                    AudioGroupingSection(draftGrouping) { draftGrouping = it }
                }
                if (tab == CategoryLibraryPage.ITEMS) {
                    AudioDetailsSection(draftDetails) { draftDetails = it }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = onDismiss,
                        shape = ExpressiveShapes.medium
                    ) {
                        Text(stringResource(dev.qtremors.arcile.core.ui.R.string.cancel))
                    }
                    Spacer(Modifier.width(8.dp))
                    FilledTonalButton(
                        onClick = {
                            haptics.selectionChanged()
                            onApply(
                                draftPresentation.normalized().let {
                                    if (tab == CategoryLibraryPage.FOLDERS) {
                                        it.copy(viewMode = FileViewMode.GRID)
                                    } else {
                                        it
                                    }
                                },
                                draftGrouping,
                                draftDetails
                            )
                            onDismiss()
                        },
                        shape = ExpressiveShapes.medium
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(dev.qtremors.arcile.core.ui.R.string.apply))
                    }
                }
            }
        }
    }
}

@Composable
private fun AudioViewModeSection(
    selected: FileViewMode,
    onSelected: (FileViewMode) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        AudioOptionTitle(stringResource(dev.qtremors.arcile.core.ui.R.string.browser_layout_view_mode))
        ExpressiveSegmentedRow(
            options = FileViewMode.entries,
            selectedOption = selected,
            onOptionSelected = onSelected,
            modifier = Modifier.fillMaxWidth()
        ) { mode ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    if (mode == FileViewMode.LIST) {
                        Icons.AutoMirrored.Filled.ViewList
                    } else {
                        Icons.Default.GridView
                    },
                    contentDescription = null
                )
                Text(
                    stringResource(
                        if (mode == FileViewMode.LIST) {
                            dev.qtremors.arcile.core.ui.R.string.list_view
                        } else {
                            dev.qtremors.arcile.core.ui.R.string.grid_view
                        }
                    )
                )
            }
        }
    }
}

@Composable
private fun AudioSizeSection(
    presentation: FileListingPreferences,
    availableWidth: Dp,
    onChange: (FileListingPreferences) -> Unit
) {
    AnimatedContent(presentation.viewMode, label = "audioLayoutControls") { mode ->
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    stringResource(
                        if (mode == FileViewMode.LIST) {
                            dev.qtremors.arcile.core.ui.R.string.browser_layout_list_zoom
                        } else {
                            dev.qtremors.arcile.core.ui.R.string.browser_layout_grid_size
                        }
                    )
                )
                Text(
                    text = if (mode == FileViewMode.LIST) {
                        stringResource(
                            dev.qtremors.arcile.core.ui.R.string.browser_layout_list_zoom_value,
                            (presentation.listZoom * 100).roundToInt()
                        )
                    } else {
                        val columns = max(
                            1,
                            floor(
                                ((availableWidth.value - 32f) / presentation.gridMinCellSize).toDouble()
                            ).toInt()
                        )
                        stringResource(
                            dev.qtremors.arcile.core.ui.R.string.browser_layout_grid_columns_value,
                            columns
                        )
                    },
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Slider(
                value = if (mode == FileViewMode.LIST) {
                    presentation.listZoom
                } else {
                    presentation.gridMinCellSize
                },
                onValueChange = {
                    onChange(
                        if (mode == FileViewMode.LIST) {
                            presentation.copy(listZoom = it)
                        } else {
                            presentation.copy(gridMinCellSize = it)
                        }
                    )
                },
                valueRange = if (mode == FileViewMode.LIST) {
                    FileListingPreferences.MIN_LIST_ZOOM..FileListingPreferences.MAX_LIST_ZOOM
                } else {
                    FileListingPreferences.MIN_GRID_MIN_CELL_SIZE..
                        FileListingPreferences.MAX_GRID_MIN_CELL_SIZE
                },
                steps = if (mode == FileViewMode.LIST) 7 else 1
            )
        }
    }
}

@Composable
private fun AudioSortSection(
    selected: FileSortOption,
    onSelected: (FileSortOption) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        AudioOptionTitle(stringResource(dev.qtremors.arcile.core.ui.R.string.action_sort))
        FileSortOption.entries.chunked(2).forEach { rowOptions ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                rowOptions.forEach { option ->
                    ExpressiveFilterChip(
                        selected = selected == option,
                        onClick = { onSelected(option) },
                        label = {
                            Text(
                                stringResource(
                                    when (option) {
                                        FileSortOption.NAME_ASC ->
                                            dev.qtremors.arcile.core.ui.R.string.sort_name_asc
                                        FileSortOption.NAME_DESC ->
                                            dev.qtremors.arcile.core.ui.R.string.sort_name_desc
                                        FileSortOption.DATE_NEWEST ->
                                            dev.qtremors.arcile.core.ui.R.string.sort_date_newest
                                        FileSortOption.DATE_OLDEST ->
                                            dev.qtremors.arcile.core.ui.R.string.sort_date_oldest
                                        FileSortOption.SIZE_LARGEST ->
                                            dev.qtremors.arcile.core.ui.R.string.sort_size_largest
                                        FileSortOption.SIZE_SMALLEST ->
                                            dev.qtremors.arcile.core.ui.R.string.sort_size_smallest
                                    }
                                )
                            )
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun AudioGroupingSection(
    grouping: CategoryGrouping,
    onSelected: (CategoryGrouping) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        AudioOptionTitle(stringResource(R.string.audio_grouping))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            CategoryGrouping.entries.forEach { option ->
                ExpressiveFilterChip(
                    selected = grouping == option,
                    onClick = { onSelected(option) },
                    label = {
                        Text(
                            stringResource(
                                when (option) {
                                    CategoryGrouping.NONE -> R.string.audio_group_none
                                    CategoryGrouping.DAY -> R.string.audio_group_day
                                    CategoryGrouping.WEEK -> R.string.audio_group_week
                                    CategoryGrouping.MONTH -> R.string.audio_group_month
                                }
                            )
                        )
                    }
                )
            }
        }
    }
}

@Composable
private fun AudioDetailsSection(
    showDetails: Boolean,
    onChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(stringResource(R.string.audio_show_file_details))
            Text(
                stringResource(R.string.audio_show_file_details_description),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = showDetails, onCheckedChange = onChange)
    }
}

@Composable
private fun AudioOptionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Medium
    )
}
