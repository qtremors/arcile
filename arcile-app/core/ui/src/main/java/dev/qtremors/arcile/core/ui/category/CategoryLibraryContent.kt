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
internal fun CategoryPullRefreshPage(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    content: @Composable BoxScope.() -> Unit
) {
    val pullRefreshState = rememberPullToRefreshState()
    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        state = pullRefreshState,
        modifier = Modifier.fillMaxSize(),
        indicator = {
            ArcilePullRefreshIndicator(
                isRefreshing = isRefreshing,
                state = pullRefreshState
            )
        },
        content = content
    )
}
internal val CategoryLibraryPage.page: Int
    get() = ordinal

@Composable
internal fun CategoryFilesContent(
    files: List<FileModel>,
    selectedPaths: Set<String>,
    presentation: FileListingPreferences,
    grouping: CategoryGrouping,
    scrollbarEnabled: Boolean,
    topPadding: androidx.compose.ui.unit.Dp,
    bottomPadding: androidx.compose.ui.unit.Dp,
    onToggleSelection: (String) -> Unit,
    onSelectPaths: (Collection<String>) -> Unit,
    onOpenFile: (FileModel) -> Unit,
    fileItem: @Composable (
        FileModel,
        Boolean,
        Boolean,
        () -> Unit,
        () -> Unit,
        Modifier
    ) -> Unit
) {
    val selectionMode = selectedPaths.isNotEmpty()
    val groups = remember(files, grouping) { groupCategoryFiles(files, grouping) }
    val flatFiles = remember(files, groups, grouping) {
        if (grouping == CategoryGrouping.NONE) files else groups.values.flatten()
    }
    var lastInteractedIndex by rememberSaveable { mutableStateOf<Int?>(null) }
    val haptics = rememberArcileHaptics()
    LaunchedEffect(selectedPaths.isEmpty()) {
        if (selectedPaths.isEmpty()) lastInteractedIndex = null
    }
    fun click(file: FileModel) {
        if (selectionMode) {
            lastInteractedIndex = flatFiles.indexOf(file)
            onToggleSelection(file.absolutePath)
            haptics.selectionChanged()
        } else {
            onOpenFile(file)
        }
    }
    fun longClick(file: FileModel) {
        val index = flatFiles.indexOf(file)
        val previous = lastInteractedIndex
        if (selectionMode && previous != null && previous != index) {
            val start = minOf(previous, index)
            val end = maxOf(previous, index)
            onSelectPaths(flatFiles.subList(start, end + 1).map(FileModel::absolutePath))
            haptics.selectionChanged()
        } else {
            onToggleSelection(file.absolutePath)
            if (selectedPaths.isEmpty()) haptics.selectionStart() else haptics.selectionChanged()
        }
        lastInteractedIndex = index
    }
    val gridContentPadding = PaddingValues(
        start = 12.dp,
        top = topPadding + 8.dp,
        end = 12.dp,
        bottom = bottomPadding
    )
    val listContentPadding = PaddingValues(top = topPadding, bottom = bottomPadding)
    val gridState = rememberSaveable(saver = LazyGridState.Saver) { LazyGridState() }
    val listState = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }
    val scrollbarState: ScrollbarState =
        if (presentation.viewMode == FileViewMode.GRID) {
            LazyGridScrollbarState(gridState)
        } else {
            LazyListScrollbarState(listState)
        }
    Box(Modifier.fillMaxSize()) {
        if (presentation.viewMode == FileViewMode.GRID) {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(presentation.gridMinCellSize.dp),
                state = gridState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = gridContentPadding,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (grouping == CategoryGrouping.NONE) {
                    items(files, key = FileModel::absolutePath) { file ->
                        fileItem(
                            file,
                            file.absolutePath in selectedPaths,
                            selectionMode,
                            { click(file) },
                            { longClick(file) },
                            Modifier.animateItem()
                        )
                    }
                } else {
                    groups.forEach { (group, groupFiles) ->
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            CategorySectionHeader(group.label)
                        }
                        items(groupFiles, key = FileModel::absolutePath) { file ->
                            fileItem(
                                file,
                                file.absolutePath in selectedPaths,
                                selectionMode,
                                { click(file) },
                                { longClick(file) },
                                Modifier.animateItem()
                            )
                        }
                    }
                }
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = listContentPadding
            ) {
                if (grouping == CategoryGrouping.NONE) {
                    items(files, key = FileModel::absolutePath) { file ->
                        fileItem(
                            file,
                            file.absolutePath in selectedPaths,
                            selectionMode,
                            { click(file) },
                            { longClick(file) },
                            Modifier.animateItem()
                        )
                    }
                } else {
                    groups.forEach { (group, groupFiles) ->
                        item { CategorySectionHeader(group.label) }
                        items(groupFiles, key = FileModel::absolutePath) { file ->
                            fileItem(
                                file,
                                file.absolutePath in selectedPaths,
                                selectionMode,
                                { click(file) },
                                { longClick(file) },
                                Modifier.animateItem()
                            )
                        }
                    }
                }
            }
        }
        ArcileFastScrollbar(
            scrollbarState = scrollbarState,
            labelForIndex = { index ->
                categoryFileForLazyIndex(index, files, grouping, groups)?.name.orEmpty()
            },
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight(),
            contentPadding = if (presentation.viewMode == FileViewMode.GRID) {
                gridContentPadding
            } else {
                listContentPadding
            },
            enabled = scrollbarEnabled
        )
    }
}

@Composable
internal fun CategoryFoldersContent(
    folders: List<CategoryFolderSummary>,
    presentation: FileListingPreferences,
    scrollbarEnabled: Boolean,
    clipboardAvailable: Boolean,
    topPadding: androidx.compose.ui.unit.Dp,
    bottomPadding: androidx.compose.ui.unit.Dp,
    onOpenFolder: (CategoryFolderSummary) -> Unit,
    onPasteToFolder: (String) -> Unit,
    folderItem: @Composable (CategoryFolderSummary, () -> Unit, Modifier) -> Unit
) {
    val contentPadding = PaddingValues(
        start = 16.dp,
        top = topPadding + 8.dp,
        end = 16.dp,
        bottom = bottomPadding
    )
    val gridState = rememberSaveable(saver = LazyGridState.Saver) { LazyGridState() }
    val scrollbarState: ScrollbarState = LazyGridScrollbarState(gridState)
    Box(Modifier.fillMaxSize()) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(presentation.gridMinCellSize.dp),
            state = gridState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = contentPadding,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(folders, key = CategoryFolderSummary::path) { folder ->
                CategoryFolderWithPasteAction(
                    folder = folder,
                    clipboardAvailable = clipboardAvailable,
                    onOpenFolder = onOpenFolder,
                    onPasteToFolder = onPasteToFolder,
                    folderItem = folderItem,
                    modifier = Modifier.animateItem()
                )
            }
        }
        ArcileFastScrollbar(
            scrollbarState = scrollbarState,
            labelForIndex = { index -> folders.getOrNull(index)?.label.orEmpty() },
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight(),
            contentPadding = contentPadding,
            enabled = scrollbarEnabled
        )
    }
}

@Composable
private fun CategoryFolderWithPasteAction(
    folder: CategoryFolderSummary,
    clipboardAvailable: Boolean,
    onOpenFolder: (CategoryFolderSummary) -> Unit,
    onPasteToFolder: (String) -> Unit,
    folderItem: @Composable (CategoryFolderSummary, () -> Unit, Modifier) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxWidth()) {
        folderItem(folder, { onOpenFolder(folder) }, Modifier.fillMaxWidth())
        if (clipboardAvailable) {
            Surface(
                onClick = { onPasteToFolder(folder.path) },
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                tonalElevation = 3.dp,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.ContentPaste,
                    contentDescription = stringResource(R.string.action_paste_here),
                    modifier = Modifier.padding(9.dp).size(20.dp)
                )
            }
        }
    }
}

@Composable
internal fun CategoryClipboardToolbar(
    state: CategoryFileActionState,
    canPaste: Boolean,
    onPaste: () -> Unit,
    onCancel: () -> Unit,
    onShowContents: () -> Unit
) {
    val clipboard = state.clipboardState
    val operation = state.activeOperation
    Row(
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        val rawProgress = operation?.let { active ->
            active.totalBytes
                ?.takeIf { it > 0L }
                ?.let { total ->
                    ((active.bytesCopied ?: 0L).toFloat() / total.toFloat()).coerceIn(0f, 1f)
                }
                ?: active.totalItems.takeIf { it > 0 }?.let { total ->
                    (active.completedItems.toFloat() / total.toFloat()).coerceIn(0f, 1f)
                }
        } ?: 0f
        val displayedProgress = if (operation?.terminalStatus != null) 1f else rawProgress
        val progressColor = when (operation?.terminalStatus) {
            OperationCompletionStatus.SUCCESS -> androidx.compose.ui.graphics.Color(0xFF4CAF50)
                .copy(alpha = 0.25f)
            OperationCompletionStatus.FAILED,
            OperationCompletionStatus.CANCELLED -> MaterialTheme.colorScheme.error.copy(alpha = 0.25f)
            null -> MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
        }
        Surface(
            onClick = {
                if (operation == null && clipboard != null) onShowContents()
            },
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 4.dp,
            shadowElevation = 2.dp,
            modifier = Modifier
                .height(56.dp)
                .padding(end = 8.dp)
                .width(192.dp)
                .animateContentSize()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .then(
                        if (operation != null) {
                            Modifier.drawBehind {
                                drawRect(
                                    color = progressColor,
                                    size = androidx.compose.ui.geometry.Size(
                                        size.width * displayedProgress,
                                        size.height
                                    )
                                )
                            }
                        } else {
                            Modifier
                        }
                    )
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = when {
                        operation?.type == BulkFileOperationType.MOVE ||
                            clipboard?.operation ==
                            dev.qtremors.arcile.core.storage.domain.ClipboardOperation.CUT ->
                            Icons.Default.ContentCut
                        operation?.type == BulkFileOperationType.CREATE_ARCHIVE ->
                            Icons.Default.FolderZip
                        else -> Icons.Default.ContentCopy
                    },
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Column {
                    val itemCount = operation?.totalItems ?: clipboard?.files?.size ?: 0
                    Text(
                        text = androidx.compose.ui.res.pluralStringResource(
                            if (operation == null) {
                                R.plurals.clipboard_items_ready_to_paste
                            } else {
                                R.plurals.clipboard_item_count
                            },
                            itemCount,
                            itemCount
                        ),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = operation?.totalBytes
                            ?.takeIf { it > 0L }
                            ?.let { total ->
                                formatFileSize(
                                    (total - (operation.bytesCopied ?: 0L)).coerceAtLeast(0L)
                                )
                            }
                            ?: if (operation != null) {
                                "${operation.completedItems}/${operation.totalItems}"
                            } else {
                                clipboard?.let { formatFileSize(it.totalSize) }.orEmpty()
                            },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        SplitButtonGroup(
            actions = when {
                operation != null && operation.terminalStatus == null -> listOf(
                    ToolbarAction(
                        icon = Icons.Default.Close,
                        contentDescription = stringResource(R.string.action_cancel_transfer),
                        containerColor = MaterialTheme.colorScheme.error,
                        tint = MaterialTheme.colorScheme.onError,
                        onClick = onCancel
                    )
                )
                operation == null && clipboard != null -> buildList {
                    if (canPaste) {
                        add(
                            ToolbarAction(
                                icon = Icons.Default.ContentPaste,
                                contentDescription = stringResource(R.string.action_paste_here),
                                onClick = onPaste
                            )
                        )
                    }
                    add(
                        ToolbarAction(
                            icon = Icons.Default.Close,
                            contentDescription = stringResource(R.string.action_cancel_transfer),
                            containerColor = MaterialTheme.colorScheme.error,
                            tint = MaterialTheme.colorScheme.onError,
                            onClick = onCancel
                        )
                    )
                }
                else -> emptyList()
            },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurface,
            height = 48.dp,
            minWidth = 48.dp,
            iconSize = 24.dp
        )
    }
}
