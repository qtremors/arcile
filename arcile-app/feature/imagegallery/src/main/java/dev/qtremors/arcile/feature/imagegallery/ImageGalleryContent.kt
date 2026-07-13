@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package dev.qtremors.arcile.feature.imagegallery

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.qtremors.arcile.core.storage.domain.FileModel
import dev.qtremors.arcile.core.storage.domain.FileViewMode
import dev.qtremors.arcile.core.storage.domain.ImageGalleryGrouping
import dev.qtremors.arcile.core.ui.R
import dev.qtremors.arcile.core.ui.ArcilePullRefreshIndicator
import dev.qtremors.arcile.core.ui.EmptyState
import dev.qtremors.arcile.core.ui.EmptyStateVariant
import dev.qtremors.arcile.core.ui.rememberArcileHaptics
import dev.qtremors.arcile.core.ui.scrollbar.ArcileFastScrollbar
import dev.qtremors.arcile.core.ui.scrollbar.LazyGridScrollbarState
import dev.qtremors.arcile.core.ui.scrollbar.LazyListScrollbarState
import dev.qtremors.arcile.core.ui.scrollbar.LazyStaggeredGridScrollbarState
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

@Composable
internal fun ImageGalleryContent(
    state: ImageGalleryState,
    gridMinCellSize: Float,
    onPhotosGridCellSizeChange: (Float) -> Unit,
    onPhotosGridCellSizeFinalized: (Float) -> Unit,
    contentPadding: PaddingValues,
    onOpenFile: (String, List<FileModel>) -> Unit,
    onToggleSelection: (String) -> Unit,
    onSelectMultiple: (List<String>) -> Unit,
    onSelectAlbum: (String?) -> Unit,
    onRefresh: () -> Unit
) {
    val pullRefreshState = androidx.compose.material3.pulltorefresh.rememberPullToRefreshState()
    val albumScrollKey = state.selectedAlbumPath ?: "__all__"

    val bottomPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 96.dp

    val groupedFiles = remember(state.displayedFiles, state.imageGalleryGrouping) {
        if (state.imageGalleryGrouping != ImageGalleryGrouping.NONE) {
            val dayFormatter = java.text.SimpleDateFormat("MMMM d, yyyy", java.util.Locale.getDefault())
            val monthFormatter = java.text.SimpleDateFormat("MMMM yyyy", java.util.Locale.getDefault())

            state.displayedFiles.groupBy { file ->
                val lastMod = file.lastModified
                val label = when (state.imageGalleryGrouping) {
                    ImageGalleryGrouping.DAY -> dayFormatter.format(java.util.Date(lastMod))
                    ImageGalleryGrouping.WEEK -> getWeekLabel(lastMod)
                    ImageGalleryGrouping.MONTH -> monthFormatter.format(java.util.Date(lastMod))
                    ImageGalleryGrouping.NONE -> ""
                }

                val cal = java.util.Calendar.getInstance()
                cal.timeInMillis = lastMod
                when (state.imageGalleryGrouping) {
                    ImageGalleryGrouping.DAY -> {
                        cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
                        cal.set(java.util.Calendar.MINUTE, 0)
                        cal.set(java.util.Calendar.SECOND, 0)
                        cal.set(java.util.Calendar.MILLISECOND, 0)
                    }
                    ImageGalleryGrouping.WEEK -> {
                        cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
                        cal.set(java.util.Calendar.MINUTE, 0)
                        cal.set(java.util.Calendar.SECOND, 0)
                        cal.set(java.util.Calendar.MILLISECOND, 0)
                        val firstDayOfWeek = cal.firstDayOfWeek
                        while (cal.get(java.util.Calendar.DAY_OF_WEEK) != firstDayOfWeek) {
                            cal.add(java.util.Calendar.DAY_OF_MONTH, -1)
                        }
                    }
                    ImageGalleryGrouping.MONTH -> {
                        cal.set(java.util.Calendar.DAY_OF_MONTH, 1)
                        cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
                        cal.set(java.util.Calendar.MINUTE, 0)
                        cal.set(java.util.Calendar.SECOND, 0)
                        cal.set(java.util.Calendar.MILLISECOND, 0)
                    }
                    else -> {}
                }
                GroupKey(label, cal.timeInMillis)
            }.toSortedMap()
        } else {
            emptyMap()
        }
    }

    val flatUiFiles = remember(state.displayedFiles, state.imageGalleryGrouping, groupedFiles) {
        if (state.imageGalleryGrouping != ImageGalleryGrouping.NONE) {
            groupedFiles.values.flatten()
        } else {
            state.displayedFiles
        }
    }

    var lastInteractedIndex by rememberSaveable { mutableStateOf<Int?>(null) }
    LaunchedEffect(state.selectedFiles.isEmpty()) {
        if (state.selectedFiles.isEmpty()) {
            lastInteractedIndex = null
        }
    }

    val haptics = rememberArcileHaptics()

    val onClickItem = remember(flatUiFiles, state.selectedFiles, onOpenFile, onToggleSelection) {
        { file: FileModel ->
            val index = flatUiFiles.indexOf(file)
            if (state.selectedFiles.isNotEmpty()) {
                lastInteractedIndex = index
                onToggleSelection(file.absolutePath)
                haptics.selectionChanged()
            } else {
                onOpenFile(file.absolutePath, flatUiFiles)
            }
        }
    }

    val onLongClickItem = remember(flatUiFiles, state.selectedFiles, onToggleSelection, onSelectMultiple) {
        { file: FileModel ->
            val index = flatUiFiles.indexOf(file)
            if (state.selectedFiles.isNotEmpty() && lastInteractedIndex != null && lastInteractedIndex != index) {
                val start = minOf(lastInteractedIndex!!, index)
                val end = maxOf(lastInteractedIndex!!, index)
                val rangePaths = flatUiFiles.subList(start, end + 1).map { it.absolutePath }
                onSelectMultiple(rangePaths)
                haptics.selectionChanged()
            } else {
                val wasEmpty = state.selectedFiles.isEmpty()
                onToggleSelection(file.absolutePath)
                if (wasEmpty) haptics.selectionStart() else haptics.selectionChanged()
            }
            lastInteractedIndex = index
        }
    }

    androidx.compose.material3.pulltorefresh.PullToRefreshBox(
        isRefreshing = state.isRefreshing,
        onRefresh = onRefresh,
        state = pullRefreshState,
        modifier = Modifier.fillMaxSize(),
        indicator = {
            ArcilePullRefreshIndicator(
                isRefreshing = state.isRefreshing,
                state = pullRefreshState
            )
        }
    ) {
        when {
            state.isLoading && state.files.isEmpty() -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    LoadingIndicator()
                }
            }
            state.files.isEmpty() && state.searchQuery.isBlank() -> {
                EmptyState(
                    variant = EmptyStateVariant.Search,
                    title = stringResource(R.string.image_gallery_empty_title),
                    description = stringResource(R.string.image_gallery_empty_description),
                    modifier = Modifier.fillMaxSize()
                )
            }
            state.displayedFiles.isEmpty() -> {
                EmptyState(
                    variant = EmptyStateVariant.Search,
                    title = stringResource(R.string.no_results_found),
                    description = stringResource(R.string.no_results_description, state.searchQuery),
                    modifier = Modifier.fillMaxSize()
                )
            }
            else -> {
                val scrollbarState = when {
                    state.presentation.viewMode == FileViewMode.GRID && state.isAspectRatio -> {
                        val staggeredGridState = rememberSaveable(
                            albumScrollKey,
                            saver = LazyStaggeredGridState.Saver
                        ) {
                            LazyStaggeredGridState()
                        }
                        LazyStaggeredGridScrollbarState(staggeredGridState)
                    }
                    state.presentation.viewMode == FileViewMode.GRID -> {
                        val gridState = rememberSaveable(
                            albumScrollKey,
                            saver = LazyGridState.Saver
                        ) {
                            LazyGridState()
                        }
                        LazyGridScrollbarState(gridState)
                    }
                    else -> {
                        val listState = rememberSaveable(
                            albumScrollKey,
                            saver = LazyListState.Saver
                        ) {
                            LazyListState()
                        }
                        LazyListScrollbarState(listState)
                    }
                }
                var restoredViewerPath by rememberSaveable(albumScrollKey) { mutableStateOf<String?>(null) }
                LaunchedEffect(state.viewerReturnPath, state.displayedFiles, state.imageGalleryGrouping, groupedFiles) {
                    val viewerPath = state.viewerReturnPath ?: return@LaunchedEffect
                    if (viewerPath == restoredViewerPath || state.displayedFiles.isEmpty()) return@LaunchedEffect
                    val targetIndex = galleryLazyIndexForPath(
                        path = viewerPath,
                        displayedFiles = state.displayedFiles,
                        imageGalleryGrouping = state.imageGalleryGrouping,
                        groupedFiles = groupedFiles
                    ) ?: return@LaunchedEffect
                    scrollbarState.scrollToItem(targetIndex)
                    restoredViewerPath = viewerPath
                }
                val scrollbarLabelFormatter = remember { java.text.SimpleDateFormat("MMMM yyyy", java.util.Locale.getDefault()) }

                Box(modifier = Modifier.fillMaxSize()) {
                    if (state.presentation.viewMode == FileViewMode.GRID) {
                        if (state.isAspectRatio) {
                            val staggeredGridState = (scrollbarState as LazyStaggeredGridScrollbarState).state
                            LazyVerticalStaggeredGrid(
                                columns = StaggeredGridCells.Adaptive(minSize = gridMinCellSize.dp),
                                state = staggeredGridState,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .pinchToResize(
                                        currentCellSize = gridMinCellSize,
                                        onSizeChanged = onPhotosGridCellSizeChange,
                                        onSizeFinalized = onPhotosGridCellSizeFinalized
                                    ),
                                contentPadding = PaddingValues(
                                    start = 12.dp,
                                    top = contentPadding.calculateTopPadding() + 8.dp,
                                    end = 12.dp,
                                    bottom = bottomPadding
                                ),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalItemSpacing = 8.dp
                            ) {
                                if (state.imageGalleryGrouping != ImageGalleryGrouping.NONE) {
                                    groupedFiles.forEach { (section, filesInSection) ->
                                        if (filesInSection.isNotEmpty()) {
                                            item(span = StaggeredGridItemSpan.FullLine) {
                                                GallerySectionHeader(section.label)
                                            }
                                            items(filesInSection, key = { it.absolutePath }) { file ->
                                                GalleryImageItem(
                                                    file = file,
                                                    isSelected = file.absolutePath in state.selectedFiles,
                                                    isSelectionMode = state.selectedFiles.isNotEmpty(),
                                                    aspectRatio = state.aspectRatios[file.absolutePath] ?: 1f,
                                                    showDetails = state.showFileDetails,
                                                    onClick = { onClickItem(file) },
                                                    onLongClick = { onLongClickItem(file) },
                                                    onOpenDirectly = { onOpenFile(file.absolutePath, flatUiFiles) },
                                                    modifier = Modifier.animateItem()
                                                )
                                            }
                                        }
                                    }
                                } else {
                                    items(state.displayedFiles, key = { it.absolutePath }) { file ->
                                        GalleryImageItem(
                                            file = file,
                                            isSelected = file.absolutePath in state.selectedFiles,
                                            isSelectionMode = state.selectedFiles.isNotEmpty(),
                                            aspectRatio = state.aspectRatios[file.absolutePath] ?: 1f,
                                            showDetails = state.showFileDetails,
                                            onClick = { onClickItem(file) },
                                            onLongClick = { onLongClickItem(file) },
                                            onOpenDirectly = { onOpenFile(file.absolutePath, flatUiFiles) },
                                            modifier = Modifier.animateItem()
                                        )
                                    }
                                }
                            }
                        } else {
                            val gridState = (scrollbarState as LazyGridScrollbarState).state
                            LazyVerticalGrid(
                                columns = GridCells.Adaptive(minSize = gridMinCellSize.dp),
                                state = gridState,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .pinchToResize(
                                        currentCellSize = gridMinCellSize,
                                        onSizeChanged = onPhotosGridCellSizeChange,
                                        onSizeFinalized = onPhotosGridCellSizeFinalized
                                    ),
                                contentPadding = PaddingValues(
                                    start = 12.dp,
                                    top = contentPadding.calculateTopPadding() + 8.dp,
                                    end = 12.dp,
                                    bottom = bottomPadding
                                ),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                if (state.imageGalleryGrouping != ImageGalleryGrouping.NONE) {
                                    groupedFiles.forEach { (section, filesInSection) ->
                                        if (filesInSection.isNotEmpty()) {
                                            item(span = { GridItemSpan(maxLineSpan) }) {
                                                GallerySectionHeader(section.label)
                                            }
                                            items(filesInSection, key = { it.absolutePath }) { file ->
                                                GalleryImageItem(
                                                    file = file,
                                                    isSelected = file.absolutePath in state.selectedFiles,
                                                    isSelectionMode = state.selectedFiles.isNotEmpty(),
                                                    aspectRatio = 1f,
                                                    showDetails = state.showFileDetails,
                                                    onClick = { onClickItem(file) },
                                                    onLongClick = { onLongClickItem(file) },
                                                    onOpenDirectly = { onOpenFile(file.absolutePath, flatUiFiles) },
                                                    modifier = Modifier.animateItem()
                                                )
                                            }
                                        }
                                    }
                                } else {
                                    items(state.displayedFiles, key = { it.absolutePath }) { file ->
                                        GalleryImageItem(
                                            file = file,
                                            isSelected = file.absolutePath in state.selectedFiles,
                                            isSelectionMode = state.selectedFiles.isNotEmpty(),
                                            aspectRatio = 1f,
                                            showDetails = state.showFileDetails,
                                            onClick = { onClickItem(file) },
                                            onLongClick = { onLongClickItem(file) },
                                            onOpenDirectly = { onOpenFile(file.absolutePath, flatUiFiles) },
                                            modifier = Modifier.animateItem()
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        val listState = (scrollbarState as LazyListScrollbarState).state
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(
                                top = contentPadding.calculateTopPadding(),
                                bottom = bottomPadding
                            )
                        ) {
                            if (state.imageGalleryGrouping != ImageGalleryGrouping.NONE) {
                                groupedFiles.forEach { (section, filesInSection) ->
                                    if (filesInSection.isNotEmpty()) {
                                        item {
                                            GallerySectionHeader(
                                                title = section.label
                                            )
                                        }
                                        items(filesInSection, key = { it.absolutePath }) { file ->
                                            GalleryImageListItem(
                                                file = file,
                                                isSelected = file.absolutePath in state.selectedFiles,
                                                isSelectionMode = state.selectedFiles.isNotEmpty(),
                                                zoom = state.presentation.listZoom,
                                                onClick = { onClickItem(file) },
                                                onLongClick = { onLongClickItem(file) },
                                                onOpenDirectly = { onOpenFile(file.absolutePath, flatUiFiles) },
                                                modifier = Modifier.animateItem()
                                            )
                                        }
                                    }
                                }
                            } else {
                                items(state.displayedFiles, key = { it.absolutePath }) { file ->
                                    GalleryImageListItem(
                                        file = file,
                                        isSelected = file.absolutePath in state.selectedFiles,
                                        isSelectionMode = state.selectedFiles.isNotEmpty(),
                                        zoom = state.presentation.listZoom,
                                        onClick = { onClickItem(file) },
                                        onLongClick = { onLongClickItem(file) },
                                        onOpenDirectly = { onOpenFile(file.absolutePath, flatUiFiles) },
                                        modifier = Modifier.animateItem()
                                    )
                                }
                            }
                        }
                    }

                    ArcileFastScrollbar(
                        scrollbarState = scrollbarState,
                        labelForIndex = { index ->
                            galleryFileForLazyIndex(
                                index = index,
                                displayedFiles = state.displayedFiles,
                                imageGalleryGrouping = state.imageGalleryGrouping,
                                groupedFiles = groupedFiles
                            )?.let { scrollbarLabelFormatter.format(java.util.Date(it.lastModified)) }.orEmpty()
                        },
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .fillMaxHeight(),
                        contentPadding = PaddingValues(
                            top = contentPadding.calculateTopPadding() + 8.dp,
                            bottom = bottomPadding + 16.dp
                        ),
                        enabled = state.galleryScrollbarEnabled
                    )
                }
            }
        }
    }
}

internal fun galleryLazyIndexForPath(
    path: String,
    displayedFiles: List<FileModel>,
    imageGalleryGrouping: ImageGalleryGrouping,
    groupedFiles: Map<GroupKey, List<FileModel>>
): Int? {
    if (imageGalleryGrouping == ImageGalleryGrouping.NONE) {
        return displayedFiles.indexOfFirst { it.absolutePath == path }.takeIf { it >= 0 }
    }
    var lazyIndex = 0
    groupedFiles.values.forEach { filesInSection ->
        if (filesInSection.isEmpty()) return@forEach
        lazyIndex += 1
        val fileIndex = filesInSection.indexOfFirst { it.absolutePath == path }
        if (fileIndex >= 0) return lazyIndex + fileIndex
        lazyIndex += filesInSection.size
    }
    return null
}

private fun galleryFileForLazyIndex(
    index: Int,
    displayedFiles: List<FileModel>,
    imageGalleryGrouping: ImageGalleryGrouping,
    groupedFiles: Map<GroupKey, List<FileModel>>
): FileModel? {
    if (imageGalleryGrouping == ImageGalleryGrouping.NONE) return displayedFiles.getOrNull(index)
    var lazyIndex = 0
    groupedFiles.values.forEach { filesInSection ->
        if (filesInSection.isEmpty()) return@forEach
        if (index == lazyIndex) return filesInSection.firstOrNull()
        lazyIndex += 1
        val fileIndex = index - lazyIndex
        if (fileIndex in filesInSection.indices) return filesInSection[fileIndex]
        lazyIndex += filesInSection.size
    }
    return null
}
