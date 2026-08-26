@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package dev.qtremors.arcile.core.ui.category

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.qtremors.arcile.core.presentation.formatFileSize
import dev.qtremors.arcile.core.operation.BulkFileOperationType
import dev.qtremors.arcile.core.operation.OperationCompletionStatus
import dev.qtremors.arcile.core.storage.domain.FileListingPreferences
import dev.qtremors.arcile.core.storage.domain.FileModel
import dev.qtremors.arcile.core.storage.domain.FileSortOption
import dev.qtremors.arcile.core.storage.domain.FileViewMode
import dev.qtremors.arcile.core.storage.domain.CategoryGrouping
import dev.qtremors.arcile.core.storage.domain.SearchFilters
import dev.qtremors.arcile.core.storage.domain.ConflictResolution
import dev.qtremors.arcile.core.storage.domain.CategoryLibraryPage
import dev.qtremors.arcile.core.ui.ArcileDropdownMenu
import dev.qtremors.arcile.core.ui.ArcileDropdownMenuItem
import dev.qtremors.arcile.core.ui.EmptyState
import dev.qtremors.arcile.core.ui.EmptyStateVariant
import dev.qtremors.arcile.core.ui.R
import dev.qtremors.arcile.core.ui.ArcileSnackbarHost
import dev.qtremors.arcile.core.ui.ExpressiveFilterChip
import dev.qtremors.arcile.core.ui.ExpressiveSegmentedRow
import dev.qtremors.arcile.core.ui.SplitButtonGroup
import dev.qtremors.arcile.core.ui.ToolbarAction
import dev.qtremors.arcile.core.ui.rememberArcileHaptics
import dev.qtremors.arcile.core.ui.ArcilePullRefreshIndicator
import dev.qtremors.arcile.core.ui.PasteConflictDialog
import dev.qtremors.arcile.core.ui.asString
import dev.qtremors.arcile.core.ui.dialogs.ClipboardContentsDialog
import dev.qtremors.arcile.core.ui.dialogs.DeleteConfirmationDialog
import dev.qtremors.arcile.core.ui.dialogs.PropertiesDialog
import dev.qtremors.arcile.core.ui.dialogs.RenameDialog
import dev.qtremors.arcile.core.ui.theme.ExpressiveShapes
import dev.qtremors.arcile.core.ui.scrollbar.ArcileFastScrollbar
import dev.qtremors.arcile.core.ui.scrollbar.LazyGridScrollbarState
import dev.qtremors.arcile.core.ui.scrollbar.LazyListScrollbarState
import dev.qtremors.arcile.core.ui.scrollbar.ScrollbarState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.roundToInt


@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CategoryLibraryViewOptionsSheet(
    title: String,
    presentation: FileListingPreferences,
    grouping: CategoryGrouping,
    showFileDetails: Boolean,
    isFolderPage: Boolean,
    onApply: (FileListingPreferences, CategoryGrouping, Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    val haptics = rememberArcileHaptics()
    var draft by remember(presentation, isFolderPage) {
        mutableStateOf(
            presentation.normalized().let {
                if (isFolderPage) it.copy(viewMode = FileViewMode.GRID) else it
            }
        )
    }
    var draftGrouping by remember(grouping) { mutableStateOf(grouping) }
    var draftShowFileDetails by remember(showFileDetails) {
        mutableStateOf(showFileDetails)
    }
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
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )
                if (!isFolderPage) {
                    CategoryViewModeSection(
                        selected = draft.viewMode,
                        onSelected = { draft = draft.copy(viewMode = it) }
                    )
                }
                CategorySizeSection(
                    preferences = draft,
                    availableWidth = this@BoxWithConstraints.maxWidth,
                    onPreferencesChange = { draft = it }
                )
                CategorySortSection(
                    preferences = draft,
                    onSortChange = { draft = draft.copy(sortOption = it) }
                )
                if (!isFolderPage) {
                    CategoryGroupingSection(
                        selected = draftGrouping,
                        onSelected = { draftGrouping = it }
                    )
                    CategoryThumbnailSection(
                        showThumbnails = draft.showThumbnails,
                        onShowThumbnailsChange = { draft = draft.copy(showThumbnails = it) }
                    )
                    CategoryDetailsSection(
                        showFileDetails = draftShowFileDetails,
                        onShowFileDetailsChange = { draftShowFileDetails = it }
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = {
                            haptics.selectionChanged()
                            onDismiss()
                        },
                        shape = ExpressiveShapes.medium
                    ) {
                        Text(stringResource(R.string.cancel))
                    }
                    Spacer(Modifier.width(8.dp))
                    FilledTonalButton(
                        onClick = {
                            haptics.selectionChanged()
                            onApply(
                                draft.normalized().let {
                                    if (isFolderPage) {
                                        it.copy(viewMode = FileViewMode.GRID)
                                    } else {
                                        it
                                    }
                                },
                                draftGrouping,
                                draftShowFileDetails
                            )
                        },
                        shape = ExpressiveShapes.medium
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.apply))
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryGroupingSection(
    selected: CategoryGrouping,
    onSelected: (CategoryGrouping) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        CategorySectionTitle(stringResource(R.string.image_gallery_grouping))
        CategoryGrouping.entries.chunked(2).forEach { options ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                options.forEach { grouping ->
                    ExpressiveFilterChip(
                        selected = selected == grouping,
                        onClick = { onSelected(grouping) },
                        label = {
                            Text(
                                stringResource(
                                    when (grouping) {
                                        CategoryGrouping.NONE -> R.string.category_group_none
                                        CategoryGrouping.DAY -> R.string.category_group_day
                                        CategoryGrouping.WEEK -> R.string.category_group_week
                                        CategoryGrouping.MONTH -> R.string.category_group_month
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
private fun CategoryViewModeSection(
    selected: FileViewMode,
    onSelected: (FileViewMode) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        CategorySectionTitle(stringResource(R.string.browser_layout_view_mode))
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
private fun CategorySizeSection(
    preferences: FileListingPreferences,
    availableWidth: androidx.compose.ui.unit.Dp,
    onPreferencesChange: (FileListingPreferences) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(
                    if (preferences.viewMode == FileViewMode.LIST) {
                        R.string.browser_layout_list_zoom
                    } else {
                        R.string.browser_layout_grid_size
                    }
                ),
                style = MaterialTheme.typography.bodyMedium
            )
            val value = if (preferences.viewMode == FileViewMode.LIST) {
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
                androidx.compose.ui.res.pluralStringResource(R.plurals.browser_layout_grid_columns_value, columns, columns)
            }
            Text(
                text = value,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Slider(
            value = if (preferences.viewMode == FileViewMode.LIST) {
                preferences.listZoom
            } else {
                preferences.gridMinCellSize
            },
            onValueChange = {
                onPreferencesChange(
                    if (preferences.viewMode == FileViewMode.LIST) {
                        preferences.copy(listZoom = it)
                    } else {
                        preferences.copy(gridMinCellSize = it)
                    }
                )
            },
            valueRange = if (preferences.viewMode == FileViewMode.LIST) {
                FileListingPreferences.MIN_LIST_ZOOM..FileListingPreferences.MAX_LIST_ZOOM
            } else {
                FileListingPreferences.MIN_GRID_MIN_CELL_SIZE..
                    FileListingPreferences.MAX_GRID_MIN_CELL_SIZE
            },
            steps = if (preferences.viewMode == FileViewMode.LIST) 7 else 1
        )
    }
}

@Composable
private fun CategorySortSection(
    preferences: FileListingPreferences,
    onSortChange: (FileSortOption) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        CategorySectionTitle(stringResource(R.string.action_sort))
        FileSortOption.entries.chunked(2).forEach { options ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                options.forEach { option ->
                    ExpressiveFilterChip(
                        selected = preferences.sortOption == option,
                        onClick = { onSortChange(option) },
                        label = {
                            Text(
                                text = stringResource(categorySortLabelResource(option)),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
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
private fun CategoryThumbnailSection(
    showThumbnails: Boolean,
    onShowThumbnailsChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(R.string.settings_show_thumbnails),
            style = MaterialTheme.typography.bodyMedium
        )
        Switch(
            checked = showThumbnails,
            onCheckedChange = onShowThumbnailsChange
        )
    }
}

@Composable
private fun CategoryDetailsSection(
    showFileDetails: Boolean,
    onShowFileDetailsChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.category_show_file_details),
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = stringResource(R.string.category_show_file_details_description),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = showFileDetails,
            onCheckedChange = onShowFileDetailsChange
        )
    }
}

@Composable
private fun CategorySectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Medium
    )
}
