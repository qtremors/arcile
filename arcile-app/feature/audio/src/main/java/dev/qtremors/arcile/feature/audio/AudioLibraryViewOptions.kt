package dev.qtremors.arcile.feature.audio

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
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberBottomSheetState
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
internal fun AudioViewOptionsSheet(
    section: AudioCollectionKind,
    presentation: FileListingPreferences,
    grouping: CategoryGrouping,
    showFileDetails: Boolean,
    musicOnly: Boolean,
    onApply: (FileListingPreferences, CategoryGrouping, Boolean, Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    val haptics = rememberArcileHaptics()
    var draftPresentation by remember(presentation, section) {
        mutableStateOf(presentation.normalized())
    }
    var draftGrouping by remember(grouping) { mutableStateOf(grouping) }
    var draftDetails by remember(showFileDetails) { mutableStateOf(showFileDetails) }
    var draftMusicOnly by remember(musicOnly) { mutableStateOf(musicOnly) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberBottomSheetState(
            initialValue = SheetValue.Hidden,
            enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded)
        ),
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
                    text = stringResource(R.string.audio_page_view_sort, section.displayName()),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )
                AudioViewModeSection(
                    selected = draftPresentation.viewMode,
                    onSelected = {
                        draftPresentation = draftPresentation.copy(viewMode = it)
                    }
                )
                AudioSizeSection(
                    presentation = draftPresentation,
                    availableWidth = this@BoxWithConstraints.maxWidth,
                    onChange = { draftPresentation = it }
                )
                AudioSortSection(
                    section = section,
                    selected = draftPresentation.sortOption,
                    onSelected = {
                        draftPresentation = draftPresentation.copy(sortOption = it)
                    }
                )
                if (section == AudioCollectionKind.SONGS) {
                    AudioGroupingSection(draftGrouping) { draftGrouping = it }
                }
                if (section == AudioCollectionKind.SONGS) {
                    AudioDetailsSection(draftDetails) { draftDetails = it }
                }
                AudioMusicOnlySection(draftMusicOnly) { draftMusicOnly = it }
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
                                draftPresentation.normalized(), draftGrouping,
                                draftDetails, draftMusicOnly
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
                        androidx.compose.ui.res.pluralStringResource(
                            dev.qtremors.arcile.core.ui.R.plurals.browser_layout_grid_columns_value,
                            columns,
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
    section: AudioCollectionKind,
    selected: FileSortOption,
    onSelected: (FileSortOption) -> Unit
) {
    val options = when (section) {
        AudioCollectionKind.SONGS, AudioCollectionKind.FOLDERS -> FileSortOption.entries
        AudioCollectionKind.ALBUMS -> listOf(
            FileSortOption.NAME_ASC, FileSortOption.NAME_DESC,
            FileSortOption.DATE_NEWEST, FileSortOption.DATE_OLDEST,
            FileSortOption.FILE_COUNT_HIGHEST, FileSortOption.FILE_COUNT_LOWEST
        )
        AudioCollectionKind.ARTISTS, AudioCollectionKind.GENRES -> listOf(
            FileSortOption.NAME_ASC, FileSortOption.NAME_DESC,
            FileSortOption.FILE_COUNT_HIGHEST, FileSortOption.FILE_COUNT_LOWEST
        )
        AudioCollectionKind.PLAYLISTS -> listOf(
            FileSortOption.NAME_ASC, FileSortOption.NAME_DESC,
            FileSortOption.DATE_NEWEST, FileSortOption.DATE_OLDEST,
            FileSortOption.FILE_COUNT_HIGHEST, FileSortOption.FILE_COUNT_LOWEST
        )
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        AudioOptionTitle(stringResource(dev.qtremors.arcile.core.ui.R.string.action_sort))
        options.chunked(2).forEach { rowOptions ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                rowOptions.forEach { option ->
                    ExpressiveFilterChip(
                        selected = selected == option,
                        onClick = { onSelected(option) },
                        label = {
                            Text(
                                text = if (section == AudioCollectionKind.ALBUMS &&
                                    option == FileSortOption.DATE_NEWEST
                                ) stringResource(R.string.audio_year_newest) else if (section == AudioCollectionKind.ALBUMS &&
                                    option == FileSortOption.DATE_OLDEST
                                ) stringResource(R.string.audio_year_oldest) else if (section == AudioCollectionKind.PLAYLISTS &&
                                    option == FileSortOption.DATE_NEWEST
                                ) stringResource(R.string.audio_recently_edited) else if (section == AudioCollectionKind.PLAYLISTS &&
                                    option == FileSortOption.DATE_OLDEST
                                ) stringResource(R.string.audio_oldest_edit) else stringResource(
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
                                        FileSortOption.FILE_COUNT_HIGHEST ->
                                            dev.qtremors.arcile.core.ui.R.string.sort_file_count_highest
                                        FileSortOption.FILE_COUNT_LOWEST ->
                                            dev.qtremors.arcile.core.ui.R.string.sort_file_count_lowest
                                    }
                                ),
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
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
private fun AudioMusicOnlySection(musicOnly: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(stringResource(R.string.audio_music_only))
            Text(
                stringResource(R.string.audio_music_only_description),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = musicOnly, onCheckedChange = onChange)
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
