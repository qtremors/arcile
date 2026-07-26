@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package dev.qtremors.arcile.feature.audio

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.qtremors.arcile.core.presentation.formatFileSize
import dev.qtremors.arcile.core.storage.domain.AudioTrack
import dev.qtremors.arcile.core.storage.domain.FileViewMode
import dev.qtremors.arcile.core.storage.domain.ImageGalleryGrouping
import dev.qtremors.arcile.core.ui.ArcilePullRefreshIndicator
import dev.qtremors.arcile.core.ui.EmptyState
import dev.qtremors.arcile.core.ui.EmptyStateVariant
import dev.qtremors.arcile.core.ui.rememberArcileHaptics
import dev.qtremors.arcile.core.ui.scrollbar.ArcileFastScrollbar
import dev.qtremors.arcile.core.ui.scrollbar.LazyGridScrollbarState
import dev.qtremors.arcile.core.ui.scrollbar.LazyListScrollbarState
import dev.qtremors.arcile.core.ui.scrollbar.ScrollbarState

@Composable
internal fun AudioLibraryPage(
    state: AudioLibraryState,
    tab: AudioLibraryTab,
    activeGridSize: Float,
    contentPadding: PaddingValues,
    currentMediaId: String?,
    onGridSizeChange: (Float) -> Unit,
    onGridSizeFinalized: (Float) -> Unit,
    onRefresh: () -> Unit,
    onPlay: (String) -> Unit,
    onSelectFolder: (AudioFolder) -> Unit,
    onToggleSelection: (String) -> Unit,
    onSelectPaths: (Collection<String>) -> Unit,
    onTogglePaths: (Collection<String>) -> Unit,
    onPasteToFolder: (String) -> Unit
) {
    val pullRefreshState = androidx.compose.material3.pulltorefresh.rememberPullToRefreshState()
    PullToRefreshBox(
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
        val isEmpty = if (tab == AudioLibraryTab.AUDIO) {
            state.visibleTracks.isEmpty()
        } else {
            state.folders.isEmpty()
        }
        when {
            state.isLoading && state.tracks.isEmpty() -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    LoadingIndicator()
                }
            }
            isEmpty && !state.isLoading -> AudioEmptyState(
                hasFilter = state.query.isNotBlank() || state.folderFilter != null
            )
            tab == AudioLibraryTab.AUDIO -> AudioTracksContent(
                state = state,
                gridSize = activeGridSize,
                contentPadding = contentPadding,
                currentMediaId = currentMediaId,
                onGridSizeChange = onGridSizeChange,
                onGridSizeFinalized = onGridSizeFinalized,
                onPlay = onPlay,
                onToggleSelection = onToggleSelection,
                onSelectPaths = onSelectPaths
            )
            else -> AudioFoldersContent(
                state = state,
                gridSize = activeGridSize,
                contentPadding = contentPadding,
                onGridSizeChange = onGridSizeChange,
                onGridSizeFinalized = onGridSizeFinalized,
                onSelectFolder = onSelectFolder,
                onSelectPaths = onSelectPaths,
                onTogglePaths = onTogglePaths,
                onPasteToFolder = onPasteToFolder
            )
        }
    }
}

@Composable
private fun AudioEmptyState(hasFilter: Boolean) {
    EmptyState(
        variant = EmptyStateVariant.Search,
        title = stringResource(
            if (hasFilter) R.string.audio_no_results else R.string.audio_no_tracks
        ),
        description = stringResource(
            if (hasFilter) {
                R.string.audio_no_results_description
            } else {
                R.string.audio_no_tracks_description
            }
        ),
        modifier = Modifier.fillMaxSize()
    )
}

@Composable
private fun AudioTracksContent(
    state: AudioLibraryState,
    gridSize: Float,
    contentPadding: PaddingValues,
    currentMediaId: String?,
    onGridSizeChange: (Float) -> Unit,
    onGridSizeFinalized: (Float) -> Unit,
    onPlay: (String) -> Unit,
    onToggleSelection: (String) -> Unit,
    onSelectPaths: (Collection<String>) -> Unit
) {
    val groups = remember(state.visibleTracks, state.grouping) {
        groupAudioTracks(state.visibleTracks, state.grouping)
    }
    val flatTracks = remember(groups, state.visibleTracks, state.grouping) {
        if (state.grouping == ImageGalleryGrouping.NONE) {
            state.visibleTracks
        } else {
            groups.values.flatten()
        }
    }
    var lastInteractedIndex by rememberSaveable { mutableStateOf<Int?>(null) }
    val haptics = rememberArcileHaptics()
    LaunchedEffect(state.selectedPaths.isEmpty()) {
        if (state.selectedPaths.isEmpty()) lastInteractedIndex = null
    }
    fun click(track: AudioTrack) {
        val path = track.file.absolutePath
        if (state.selectedPaths.isNotEmpty()) {
            lastInteractedIndex = flatTracks.indexOf(track)
            onToggleSelection(path)
            haptics.selectionChanged()
        } else {
            onPlay(path)
        }
    }
    fun longClick(track: AudioTrack) {
        val index = flatTracks.indexOf(track)
        val previous = lastInteractedIndex
        if (state.selectedPaths.isNotEmpty() && previous != null && previous != index) {
            val start = minOf(previous, index)
            val end = maxOf(previous, index)
            onSelectPaths(flatTracks.subList(start, end + 1).map { it.file.absolutePath })
            haptics.selectionChanged()
        } else {
            onToggleSelection(track.file.absolutePath)
            if (state.selectedPaths.isEmpty()) haptics.selectionStart() else haptics.selectionChanged()
        }
        lastInteractedIndex = index
    }

    val gridState = rememberSaveable(saver = LazyGridState.Saver) { LazyGridState() }
    val listState = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }
    val scrollbarState: ScrollbarState =
        if (state.audioPresentation.viewMode == FileViewMode.GRID) {
            LazyGridScrollbarState(gridState)
        } else {
            LazyListScrollbarState(listState)
        }
    Box(modifier = Modifier.fillMaxSize()) {
    if (state.audioPresentation.viewMode == FileViewMode.GRID) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(gridSize.dp),
            state = gridState,
            modifier = Modifier
                .fillMaxSize()
                .audioPinchToResize(
                    currentCellSize = gridSize,
                    onSizeChanged = onGridSizeChange,
                    onSizeFinalized = onGridSizeFinalized
                ),
            contentPadding = contentPadding,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (state.grouping == ImageGalleryGrouping.NONE) {
                items(state.visibleTracks, key = { it.file.absolutePath }) { track ->
                    AudioTrackGridItem(
                        track = track,
                        isCurrent = currentMediaId == track.file.absolutePath,
                        isSelected = track.file.absolutePath in state.selectedPaths,
                        showDetails = state.showFileDetails,
                        onClick = { click(track) },
                        onLongClick = { longClick(track) },
                        modifier = Modifier.animateItem()
                    )
                }
            } else {
                groups.forEach { (group, tracks) ->
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        AudioSectionHeader(group.label)
                    }
                    items(tracks, key = { it.file.absolutePath }) { track ->
                        AudioTrackGridItem(
                            track = track,
                            isCurrent = currentMediaId == track.file.absolutePath,
                            isSelected = track.file.absolutePath in state.selectedPaths,
                            showDetails = state.showFileDetails,
                            onClick = { click(track) },
                            onLongClick = { longClick(track) },
                            modifier = Modifier.animateItem()
                        )
                    }
                }
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            contentPadding = contentPadding
        ) {
            if (state.grouping == ImageGalleryGrouping.NONE) {
                items(state.visibleTracks, key = { it.file.absolutePath }) { track ->
                    AudioTrackListItem(
                        track = track,
                        zoom = state.audioPresentation.listZoom,
                        isCurrent = currentMediaId == track.file.absolutePath,
                        isSelected = track.file.absolutePath in state.selectedPaths,
                        showDetails = state.showFileDetails,
                        onClick = { click(track) },
                        onLongClick = { longClick(track) },
                        modifier = Modifier.animateItem()
                    )
                }
            } else {
                groups.forEach { (group, tracks) ->
                    item { AudioSectionHeader(group.label) }
                    items(tracks, key = { it.file.absolutePath }) { track ->
                        AudioTrackListItem(
                            track = track,
                            zoom = state.audioPresentation.listZoom,
                            isCurrent = currentMediaId == track.file.absolutePath,
                            isSelected = track.file.absolutePath in state.selectedPaths,
                            showDetails = state.showFileDetails,
                            onClick = { click(track) },
                            onLongClick = { longClick(track) },
                            modifier = Modifier.animateItem()
                        )
                    }
                }
            }
        }
    }
        ArcileFastScrollbar(
            scrollbarState = scrollbarState,
            labelForIndex = { index ->
                audioTrackForLazyIndex(index, state.visibleTracks, state.grouping, groups)
                    ?.displayTitle
                    .orEmpty()
            },
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight(),
            contentPadding = contentPadding,
            enabled = state.scrollbarEnabled
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AudioTrackListItem(
    track: AudioTrack,
    zoom: Float,
    isCurrent: Boolean,
    isSelected: Boolean,
    showDetails: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 1.dp)
            .clip(MaterialTheme.shapes.extraLarge)
            .background(
                when {
                    isSelected -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                    isCurrent -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f)
                    else -> androidx.compose.ui.graphics.Color.Transparent
                }
            )
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AudioArtwork(track, Modifier.size((48f * zoom).dp))
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                track.displayTitle,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isCurrent || isSelected) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                buildTrackSubtitle(track),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (showDetails) {
                Text(
                    "${formatAudioDuration(track.durationMs)} • ${formatFileSize(track.file.size)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (isSelected) {
            Icon(
                Icons.Default.CheckCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .padding(start = 8.dp)
                    .size(22.dp)
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AudioTrackGridItem(
    track: AudioTrack,
    isCurrent: Boolean,
    isSelected: Boolean,
    showDetails: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = MaterialTheme.shapes.large
    val itemModifier = modifier
        .fillMaxWidth()
        .clip(shape)
        .combinedClickable(onClick = onClick, onLongClick = onLongClick)
    if (showDetails) {
        Surface(
            shape = shape,
            color = when {
                isSelected -> MaterialTheme.colorScheme.primaryContainer
                isCurrent -> MaterialTheme.colorScheme.secondaryContainer
                else -> MaterialTheme.colorScheme.surfaceContainerLow
            },
            modifier = itemModifier
        ) {
            Column {
                AudioArtwork(
                    track = track,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                )
                Column(modifier = Modifier.padding(8.dp)) {
                    Text(
                        track.displayTitle,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = if (isCurrent || isSelected) FontWeight.Bold else FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        buildTrackSubtitle(track),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        formatAudioDuration(track.durationMs),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    } else {
        Box(
            modifier = itemModifier.aspectRatio(1f)
        ) {
            AudioArtwork(track = track, modifier = Modifier.fillMaxSize())
            if (isSelected || isCurrent) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            if (isSelected) {
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                            } else {
                                MaterialTheme.colorScheme.secondary.copy(alpha = 0.22f)
                            }
                        )
                )
            }
        }
    }
}

@Composable
private fun AudioFoldersContent(
    state: AudioLibraryState,
    gridSize: Float,
    contentPadding: PaddingValues,
    onGridSizeChange: (Float) -> Unit,
    onGridSizeFinalized: (Float) -> Unit,
    onSelectFolder: (AudioFolder) -> Unit,
    onSelectPaths: (Collection<String>) -> Unit,
    onTogglePaths: (Collection<String>) -> Unit,
    onPasteToFolder: (String) -> Unit
) {
    val haptics = rememberArcileHaptics()
    val gridState = rememberSaveable(saver = LazyGridState.Saver) { LazyGridState() }
    val listState = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }
    val scrollbarState: ScrollbarState =
        if (state.folderPresentation.viewMode == FileViewMode.GRID) {
            LazyGridScrollbarState(gridState)
        } else {
            LazyListScrollbarState(listState)
        }
    Box(modifier = Modifier.fillMaxSize()) {
    if (state.folderPresentation.viewMode == FileViewMode.GRID) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(gridSize.dp),
            state = gridState,
            modifier = Modifier
                .fillMaxSize()
                .audioPinchToResize(
                    currentCellSize = gridSize,
                    onSizeChanged = onGridSizeChange,
                    onSizeFinalized = onGridSizeFinalized
                ),
            contentPadding = contentPadding,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(state.folders, key = AudioFolder::key) { folder ->
                val folderPaths = folder.tracks.map { it.file.absolutePath }
                val isSelected = folderPaths.all(state.selectedPaths::contains)
                AudioFolderGridItem(
                    folder = folder,
                    isSelected = isSelected,
                    showDetails = state.showFileDetails,
                    canPaste = state.clipboardState != null,
                    onClick = {
                        if (state.selectedPaths.isEmpty()) {
                            onSelectFolder(folder)
                        } else {
                            onTogglePaths(folderPaths)
                            haptics.selectionChanged()
                        }
                    },
                    onLongClick = {
                        onSelectPaths(folderPaths)
                        if (state.selectedPaths.isEmpty()) {
                            haptics.selectionStart()
                        } else {
                            haptics.selectionChanged()
                        }
                    },
                    onPaste = { onPasteToFolder(folder.key) },
                    modifier = Modifier.animateItem()
                )
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            contentPadding = contentPadding
        ) {
            items(state.folders, key = AudioFolder::key) { folder ->
                val folderPaths = folder.tracks.map { it.file.absolutePath }
                AudioFolderListItem(
                    folder = folder,
                    zoom = state.folderPresentation.listZoom,
                    isSelected = folder.tracks.all {
                        it.file.absolutePath in state.selectedPaths
                    },
                    showDetails = state.showFileDetails,
                    canPaste = state.clipboardState != null,
                    onClick = {
                        if (state.selectedPaths.isEmpty()) {
                            onSelectFolder(folder)
                        } else {
                            onTogglePaths(folderPaths)
                            haptics.selectionChanged()
                        }
                    },
                    onLongClick = {
                        onSelectPaths(folderPaths)
                        if (state.selectedPaths.isEmpty()) {
                            haptics.selectionStart()
                        } else {
                            haptics.selectionChanged()
                        }
                    },
                    onPaste = { onPasteToFolder(folder.key) },
                    modifier = Modifier.animateItem()
                )
            }
        }
    }
        ArcileFastScrollbar(
            scrollbarState = scrollbarState,
            labelForIndex = { index -> state.folders.getOrNull(index)?.title.orEmpty() },
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight(),
            contentPadding = contentPadding,
            enabled = state.scrollbarEnabled
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AudioFolderListItem(
    folder: AudioFolder,
    zoom: Float,
    isSelected: Boolean,
    showDetails: Boolean,
    canPaste: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onPaste: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 1.dp)
            .clip(MaterialTheme.shapes.extraLarge)
            .background(
                if (isSelected) {
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                } else {
                    androidx.compose.ui.graphics.Color.Transparent
                }
            )
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AudioArtwork(folder.coverTrack, Modifier.size((48f * zoom).dp))
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                folder.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                stringResource(R.string.audio_track_count, folder.tracks.size),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (showDetails) {
                Text(
                    "${folder.subtitle.orEmpty()} • ${formatFileSize(folder.totalSize)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (canPaste && !isSelected) {
            IconButton(onClick = onPaste) {
                Icon(
                    Icons.Default.ContentPaste,
                    contentDescription = stringResource(R.string.audio_paste_here),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        } else if (isSelected) {
            Icon(
                Icons.Default.CheckCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .padding(start = 8.dp)
                    .size(22.dp)
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AudioFolderGridItem(
    folder: AudioFolder,
    isSelected: Boolean,
    showDetails: Boolean,
    canPaste: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onPaste: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = MaterialTheme.shapes.large
    val itemModifier = Modifier
        .fillMaxWidth()
        .clip(shape)
        .combinedClickable(onClick = onClick, onLongClick = onLongClick)
    Box(modifier = modifier) {
        if (showDetails) {
            Surface(
                shape = shape,
                color = if (isSelected) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceContainerLow
                },
                modifier = itemModifier
            ) {
                Column {
                    AudioArtwork(
                        folder.coverTrack,
                        Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                    )
                    Column(modifier = Modifier.padding(8.dp)) {
                        Text(
                            folder.title,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            stringResource(R.string.audio_track_count, folder.tracks.size),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            formatFileSize(folder.totalSize),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            Box(modifier = itemModifier.aspectRatio(1f)) {
                AudioArtwork(folder.coverTrack, Modifier.fillMaxSize())
                if (isSelected) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
                    )
                }
            }
        }
        if (canPaste && !isSelected) {
            Surface(
                onClick = onPaste,
                shape = androidx.compose.foundation.shape.CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                shadowElevation = 4.dp,
                modifier = Modifier
                    .padding(8.dp)
                    .size(40.dp)
                    .align(Alignment.TopEnd)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.ContentPaste,
                        contentDescription = stringResource(R.string.audio_paste_here)
                    )
                }
            }
        } else if (isSelected) {
            Icon(
                Icons.Default.CheckCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .padding(8.dp)
                    .size(24.dp)
                    .align(Alignment.TopEnd)
            )
        }
    }
}

@Composable
private fun AudioSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
    )
}

private fun buildTrackSubtitle(track: AudioTrack): String =
    listOfNotNull(track.artist, track.album).joinToString(" • ")
        .ifBlank { track.file.name }

internal fun audioTrackForLazyIndex(
    index: Int,
    tracks: List<AudioTrack>,
    grouping: ImageGalleryGrouping,
    groups: Map<AudioGroupKey, List<AudioTrack>>
): AudioTrack? {
    if (grouping == ImageGalleryGrouping.NONE) return tracks.getOrNull(index)
    var lazyIndex = 0
    groups.values.forEach { groupTracks ->
        if (groupTracks.isEmpty()) return@forEach
        if (index == lazyIndex) return groupTracks.firstOrNull()
        lazyIndex += 1
        val trackIndex = index - lazyIndex
        if (trackIndex in groupTracks.indices) return groupTracks[trackIndex]
        lazyIndex += groupTracks.size
    }
    return null
}

private fun Modifier.audioPinchToResize(
    currentCellSize: Float,
    onSizeChanged: (Float) -> Unit,
    onSizeFinalized: (Float) -> Unit
): Modifier = pointerInput(currentCellSize) {
    var accumulatedScale: Float
    var startCellSize: Float
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false)
        accumulatedScale = 1f
        startCellSize = currentCellSize
        var isPinching = false
        do {
            val event = awaitPointerEvent()
            if (event.changes.size >= 2) {
                isPinching = true
                accumulatedScale *= event.calculateZoom()
                onSizeChanged(
                    (startCellSize * accumulatedScale).coerceIn(
                        dev.qtremors.arcile.core.storage.domain.FileListingPreferences
                            .MIN_GRID_MIN_CELL_SIZE,
                        dev.qtremors.arcile.core.storage.domain.FileListingPreferences
                            .MAX_GRID_MIN_CELL_SIZE
                    )
                )
                event.changes.forEach { it.consume() }
            }
        } while (event.changes.any { it.pressed })
        if (isPinching) {
            onSizeFinalized(
                (startCellSize * accumulatedScale).coerceIn(
                    dev.qtremors.arcile.core.storage.domain.FileListingPreferences
                        .MIN_GRID_MIN_CELL_SIZE,
                    dev.qtremors.arcile.core.storage.domain.FileListingPreferences
                        .MAX_GRID_MIN_CELL_SIZE
                )
            )
        }
    }
}
