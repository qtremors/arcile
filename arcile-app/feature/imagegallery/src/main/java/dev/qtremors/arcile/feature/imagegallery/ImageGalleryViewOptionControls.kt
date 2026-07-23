package dev.qtremors.arcile.feature.imagegallery

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.qtremors.arcile.core.storage.domain.FileListingPreferences
import dev.qtremors.arcile.core.storage.domain.FileSortOption
import dev.qtremors.arcile.core.storage.domain.FileViewMode
import dev.qtremors.arcile.core.storage.domain.ImageGalleryGrouping
import dev.qtremors.arcile.core.ui.R
import dev.qtremors.arcile.core.ui.ExpressiveFilterChip
import dev.qtremors.arcile.core.ui.ExpressiveSegmentedRow
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.roundToInt

@Composable
internal fun GalleryPhotosViewOptions(
    preferences: FileListingPreferences,
    aspectRatio: Boolean,
    grouping: ImageGalleryGrouping,
    showDetails: Boolean,
    isVideoGallery: Boolean,
    availableWidth: Dp,
    onPreferencesChange: (FileListingPreferences) -> Unit,
    onAspectRatioChange: (Boolean) -> Unit,
    onGroupingChange: (ImageGalleryGrouping) -> Unit,
    onShowDetailsChange: (Boolean) -> Unit
) {
    GalleryViewModeSection(
        selected = preferences.viewMode,
        onSelected = { onPreferencesChange(preferences.copy(viewMode = it)) }
    )
    GallerySizeSection(
        preferences = preferences,
        availableWidth = availableWidth,
        onPreferencesChange = onPreferencesChange
    )
    if (preferences.viewMode == FileViewMode.GRID && !isVideoGallery) {
        GalleryAspectRatioSection(
            aspectRatio = aspectRatio,
            onAspectRatioChange = onAspectRatioChange
        )
    }
    GallerySortSection(
        preferences = preferences,
        onSortChange = { onPreferencesChange(preferences.copy(sortOption = it)) }
    )
    GalleryGroupingSection(grouping, onGroupingChange)
    GalleryDetailsSection(showDetails, isVideoGallery, onShowDetailsChange)
}

@Composable
internal fun GalleryAlbumViewOptions(
    preferences: FileListingPreferences,
    availableWidth: Dp,
    onPreferencesChange: (FileListingPreferences) -> Unit
) {
    GallerySizeSection(
        preferences = preferences.copy(viewMode = FileViewMode.GRID),
        availableWidth = availableWidth,
        onPreferencesChange = onPreferencesChange
    )
    GallerySortSection(
        preferences = preferences,
        onSortChange = { onPreferencesChange(preferences.copy(sortOption = it)) }
    )
}

@Composable
private fun GalleryViewModeSection(
    selected: FileViewMode,
    onSelected: (FileViewMode) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle(stringResource(R.string.browser_layout_view_mode))
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
                    imageVector = if (mode == FileViewMode.LIST) {
                        Icons.AutoMirrored.Filled.ViewList
                    } else {
                        Icons.Default.GridView
                    },
                    contentDescription = null
                )
                Text(
                    stringResource(
                        if (mode == FileViewMode.LIST) R.string.list_view else R.string.grid_view
                    )
                )
            }
        }
    }
}

@Composable
private fun GallerySizeSection(
    preferences: FileListingPreferences,
    availableWidth: Dp,
    onPreferencesChange: (FileListingPreferences) -> Unit
) {
    AnimatedContent(
        targetState = preferences.viewMode,
        label = "gallery_layout_controls"
    ) { mode ->
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(
                        if (mode == FileViewMode.LIST) {
                            R.string.browser_layout_list_zoom
                        } else {
                            R.string.browser_layout_grid_size
                        }
                    ),
                    style = MaterialTheme.typography.bodyMedium
                )
                val value = if (mode == FileViewMode.LIST) {
                    stringResource(
                        R.string.browser_layout_list_zoom_value,
                        (preferences.listZoom * 100).roundToInt()
                    )
                } else {
                    val columns = max(
                        1,
                        floor(
                            ((availableWidth.value - 32f) / preferences.gridMinCellSize).toDouble()
                        ).toInt()
                    )
                    stringResource(R.string.browser_layout_grid_columns_value, columns)
                }
                Text(
                    text = value,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Slider(
                value = if (mode == FileViewMode.LIST) {
                    preferences.listZoom
                } else {
                    preferences.gridMinCellSize
                },
                onValueChange = {
                    onPreferencesChange(
                        if (mode == FileViewMode.LIST) {
                            preferences.copy(listZoom = it)
                        } else {
                            preferences.copy(gridMinCellSize = it)
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
private fun GalleryAspectRatioSection(
    aspectRatio: Boolean,
    onAspectRatioChange: (Boolean) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle(stringResource(R.string.image_gallery_grid_mode))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ExpressiveFilterChip(
                selected = !aspectRatio,
                onClick = { onAspectRatioChange(false) },
                label = { Text(stringResource(R.string.image_gallery_view_mode_square)) }
            )
            ExpressiveFilterChip(
                selected = aspectRatio,
                onClick = { onAspectRatioChange(true) },
                label = { Text(stringResource(R.string.image_gallery_view_mode_aspect)) }
            )
        }
    }
}

@Composable
private fun GallerySortSection(
    preferences: FileListingPreferences,
    onSortChange: (FileSortOption) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle(stringResource(R.string.action_sort))
        FileSortOption.entries.chunked(2).forEach { options ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                options.forEach { option ->
                    SortChip(
                        option = option,
                        preferences = preferences,
                        modifier = Modifier.weight(1f),
                        onSelect = onSortChange
                    )
                }
            }
        }
    }
}

@Composable
private fun GalleryGroupingSection(
    grouping: ImageGalleryGrouping,
    onGroupingChange: (ImageGalleryGrouping) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle(stringResource(R.string.image_gallery_grouping))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ImageGalleryGrouping.entries.forEach { mode ->
                ExpressiveFilterChip(
                    selected = grouping == mode,
                    onClick = { onGroupingChange(mode) },
                    label = {
                        Text(
                            when (mode) {
                                ImageGalleryGrouping.NONE -> "None"
                                ImageGalleryGrouping.DAY -> "Day"
                                ImageGalleryGrouping.WEEK -> "Week"
                                ImageGalleryGrouping.MONTH -> "Month"
                            }
                        )
                    }
                )
            }
        }
    }
}

@Composable
private fun GalleryDetailsSection(
    showDetails: Boolean,
    isVideoGallery: Boolean,
    onShowDetailsChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.image_gallery_show_file_details),
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = stringResource(
                    if (isVideoGallery) R.string.video_gallery_show_file_details_description
                    else R.string.image_gallery_show_file_details_description
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = showDetails, onCheckedChange = onShowDetailsChange)
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Medium
    )
}

@Composable
internal fun SortChip(
    option: FileSortOption,
    preferences: FileListingPreferences,
    modifier: Modifier = Modifier,
    onSelect: (FileSortOption) -> Unit
) {
    ExpressiveFilterChip(
        selected = preferences.sortOption == option,
        onClick = { onSelect(option) },
        label = {
            Text(
                stringResource(
                    when (option) {
                        FileSortOption.NAME_ASC -> R.string.sort_name_asc
                        FileSortOption.NAME_DESC -> R.string.sort_name_desc
                        FileSortOption.DATE_NEWEST -> R.string.sort_date_newest
                        FileSortOption.DATE_OLDEST -> R.string.sort_date_oldest
                        FileSortOption.SIZE_LARGEST -> R.string.sort_size_largest
                        FileSortOption.SIZE_SMALLEST -> R.string.sort_size_smallest
                    }
                )
            )
        },
        modifier = modifier
    )
}
