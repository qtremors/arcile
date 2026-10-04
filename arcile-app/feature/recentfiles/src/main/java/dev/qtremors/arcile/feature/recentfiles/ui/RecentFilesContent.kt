package dev.qtremors.arcile.feature.recentfiles.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed as gridItemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.qtremors.arcile.core.storage.domain.FileViewMode
import dev.qtremors.arcile.feature.recentfiles.RecentFilesState
import dev.qtremors.arcile.core.ui.lists.FileGrid
import dev.qtremors.arcile.core.ui.lists.FileItemRow
import dev.qtremors.arcile.core.ui.lists.FileList
import dev.qtremors.arcile.core.ui.lists.FileItemPresentation
import dev.qtremors.arcile.core.ui.rememberDateFormatter
import dev.qtremors.arcile.core.ui.rememberArcileHaptics
import dev.qtremors.arcile.core.ui.theme.spacing
import java.util.Date
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun RecentFilesContent(
    state: RecentFilesState,
    filesToDisplay: List<dev.qtremors.arcile.core.storage.domain.FileModel>,
    showSearchBar: Boolean,
    formatter: java.text.DateFormat,
    todayLabel: String,
    yesterdayLabel: String,
    contentPadding: PaddingValues,
    onOpenFile: (String) -> Unit,
    onToggleSelection: (String) -> Unit,
    onSelectMultiple: (List<String>) -> Unit,
    onLoadMore: () -> Unit
) {
    val isSelectionMode = state.selectedFiles.isNotEmpty()
    val haptics = rememberArcileHaptics()
    val visiblePaths = remember(filesToDisplay) { filesToDisplay.map { it.reference } }
    var selectionAnchorPath by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(visiblePaths) {
        if (selectionAnchorPath !in visiblePaths) selectionAnchorPath = null
    }
    val onGroupedClick: (String) -> Unit = { path ->
        if (isSelectionMode) {
            selectionAnchorPath = path
            onToggleSelection(path)
            haptics.selectionChanged()
        } else {
            onOpenFile(path)
        }
    }
    val onGroupedLongClick: (String) -> Unit = { path ->
        val range = recentSelectionRange(selectionAnchorPath, path, visiblePaths)
        if (isSelectionMode && range.size > 1) {
            onSelectMultiple(range)
            haptics.selectionChanged()
        } else {
            val wasEmpty = !isSelectionMode
            onToggleSelection(path)
            if (wasEmpty) haptics.selectionStart() else haptics.selectionChanged()
        }
        selectionAnchorPath = path
    }
    val topPadding = contentPadding.calculateTopPadding()
    val bottomPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + (if (isSelectionMode) MaterialTheme.spacing.toolbarBottomGap else MaterialTheme.spacing.screenGutter)

    val isGroupingEnabled = shouldGroupRecentFiles(showSearchBar, state.presentation)

    if (state.presentation.viewMode == FileViewMode.GRID) {
        val gridState = rememberLazyGridState()
        val shouldLoadMore by remember {
            derivedStateOf {
                val totalItems = gridState.layoutInfo.totalItemsCount
                val lastVisibleItem = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                totalItems > 0 && lastVisibleItem >= totalItems - 5
            }
        }
        LaunchedEffect(shouldLoadMore, state.isLoadingMore, state.hasMore, showSearchBar) {
            if (shouldLoadMore && !state.isLoadingMore && state.hasMore && !showSearchBar) {
                onLoadMore()
            }
        }
        
        if (isGroupingEnabled) {
            val currentYearFormat = rememberDateFormatter("EEEE, MMM d")
            val olderYearFormat = rememberDateFormatter("EEEE, MMM d, yyyy")
            val groupedFiles = remember(
                filesToDisplay,
                state.todayStart,
                state.yesterdayStart,
                currentYearFormat,
                olderYearFormat,
                todayLabel,
                yesterdayLabel
            ) {
                groupRecentFilesByCalendarDay(
                    files = filesToDisplay,
                    todayStart = state.todayStart,
                    yesterdayStart = state.yesterdayStart,
                    todayLabel = todayLabel,
                    yesterdayLabel = yesterdayLabel,
                    currentYearFormatter = currentYearFormat,
                    olderYearFormatter = olderYearFormat
                )
            }
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = state.presentation.gridMinCellSize.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = topPadding),
                state = gridState,
                contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = bottomPadding),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(16.dp),
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(16.dp)
            ) {
                groupedFiles.forEach { group ->
                    stickyHeader {
                        RecentDateHeaderPill(dateHeader = group.label)
                    }
                    gridItemsIndexed(
                        items = group.files,
                        key = { index, file ->
                            "${group.dayStartMillis}-$index-${file.reference}"
                        },
                        contentType = { _, file -> if (file.isDirectory) "directory" else "file" }
                    ) { _, file ->
                        dev.qtremors.arcile.core.ui.lists.FileGridItem(
                            modifier = Modifier.animateItem(),
                            file = file,
                            formattedDate = formatter.format(Date(file.lastModified)),
                            isSelected = state.selectedFiles.contains(file.reference),
                            isInSelectionMode = isSelectionMode,
                            presentation = FileItemPresentation(
                                showThumbnails = state.presentation.showThumbnails,
                                openFileFromThumbnailInSelectionMode = true
                            ),
                            onClick = { onGroupedClick(file.reference) },
                            onLongClick = { onGroupedLongClick(file.reference) },
                            onOpenDirectly = { onOpenFile(file.reference) }
                        )
                    }
                }
                if (state.isLoadingMore) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            LoadingIndicator()
                        }
                    }
                }
            }
        } else {
            FileGrid(
                files = filesToDisplay,
                selectedFiles = state.selectedFiles,
                onNavigateTo = {},
                onOpenFile = onOpenFile,
                onToggleSelection = onToggleSelection,
                onSelectMultiple = onSelectMultiple,
                gridState = gridState,
                minCellSize = state.presentation.gridMinCellSize.dp,
                presentation = FileItemPresentation(
                    showThumbnails = state.presentation.showThumbnails,
                    openFileFromThumbnailInSelectionMode = true
                ),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = topPadding),
                contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = bottomPadding)
            )
        }
    } else if (isGroupingEnabled) {
        val listState = rememberLazyListState()
        val currentYearFormat = rememberDateFormatter("EEEE, MMM d")
        val olderYearFormat = rememberDateFormatter("EEEE, MMM d, yyyy")
        val groupedFiles = remember(
            filesToDisplay,
            state.todayStart,
            state.yesterdayStart,
            currentYearFormat,
            olderYearFormat,
            todayLabel,
            yesterdayLabel
        ) {
            groupRecentFilesByCalendarDay(
                files = filesToDisplay,
                todayStart = state.todayStart,
                yesterdayStart = state.yesterdayStart,
                todayLabel = todayLabel,
                yesterdayLabel = yesterdayLabel,
                currentYearFormatter = currentYearFormat,
                olderYearFormatter = olderYearFormat
            )
        }
        val shouldLoadMore by remember {
            derivedStateOf {
                val totalItems = listState.layoutInfo.totalItemsCount
                val lastVisibleItem = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                totalItems > 0 && lastVisibleItem >= totalItems - 5
            }
        }
        LaunchedEffect(shouldLoadMore, state.isLoadingMore, state.hasMore, showSearchBar) {
            if (shouldLoadMore && !state.isLoadingMore && state.hasMore && !showSearchBar) {
                onLoadMore()
            }
        }
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = topPadding),
            state = listState,
            contentPadding = PaddingValues(bottom = bottomPadding)
        ) {
            groupedFiles.forEach { group ->
                stickyHeader {
                    RecentDateHeaderPill(dateHeader = group.label)
                }
                itemsIndexed(
                    items = group.files,
                    key = { index, file ->
                        "${group.dayStartMillis}-$index-${file.reference}"
                    },
                    contentType = { _, file -> if (file.isDirectory) "directory" else "file" }
                ) { _, file ->
                    FileItemRow(
                        file = file,
                        formattedDate = formatter.format(Date(file.lastModified)),
                        isSelected = state.selectedFiles.contains(file.reference),
                        isInSelectionMode = isSelectionMode,
                        presentation = FileItemPresentation(
                            zoom = state.presentation.listZoom,
                            showThumbnails = state.presentation.showThumbnails,
                            openFileFromThumbnailInSelectionMode = true
                        ),
                        onClick = { onGroupedClick(file.reference) },
                        onLongClick = { onGroupedLongClick(file.reference) },
                        onOpenDirectly = { onOpenFile(file.reference) }
                    )
                }
            }
            if (state.isLoadingMore) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        LoadingIndicator()
                    }
                }
            }
        }
    } else {
        val listState = rememberLazyListState()
        val shouldLoadMore by remember {
            derivedStateOf {
                val totalItems = listState.layoutInfo.totalItemsCount
                val lastVisibleItem = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                totalItems > 0 && lastVisibleItem >= totalItems - 5
            }
        }
        LaunchedEffect(shouldLoadMore, state.isLoadingMore, state.hasMore, showSearchBar) {
            if (shouldLoadMore && !state.isLoadingMore && state.hasMore && !showSearchBar) {
                onLoadMore()
            }
        }
        FileList(
            files = filesToDisplay,
            selectedFiles = state.selectedFiles,
            onNavigateTo = {},
            onOpenFile = onOpenFile,
            onToggleSelection = onToggleSelection,
            onSelectMultiple = onSelectMultiple,
            listState = listState,
            presentation = FileItemPresentation(
                zoom = state.presentation.listZoom,
                showThumbnails = state.presentation.showThumbnails,
                openFileFromThumbnailInSelectionMode = true
            ),
            modifier = Modifier
                .fillMaxSize()
                .padding(top = topPadding),
            contentPadding = PaddingValues(bottom = bottomPadding)
        )
    }
}

internal fun recentSelectionRange(
    anchorPath: String?,
    currentPath: String,
    visiblePaths: List<String>
): List<String> {
    val anchorIndex = anchorPath?.let(visiblePaths::indexOf) ?: return listOf(currentPath)
    val currentIndex = visiblePaths.indexOf(currentPath)
    if (anchorIndex < 0 || currentIndex < 0) return listOf(currentPath)
    val start = minOf(anchorIndex, currentIndex)
    val end = maxOf(anchorIndex, currentIndex)
    return visiblePaths.subList(start, end + 1).distinct()
}
