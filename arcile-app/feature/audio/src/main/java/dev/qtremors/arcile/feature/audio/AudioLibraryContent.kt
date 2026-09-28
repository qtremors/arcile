@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package dev.qtremors.arcile.feature.audio

import dev.qtremors.arcile.core.storage.domain.CategoryLibraryPage
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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
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
import dev.qtremors.arcile.core.storage.domain.CategoryGrouping
import dev.qtremors.arcile.core.ui.ArcilePullRefreshIndicator
import dev.qtremors.arcile.core.ui.EmptyState
import dev.qtremors.arcile.core.ui.EmptyStateVariant
import dev.qtremors.arcile.core.ui.category.CategoryGridItem
import dev.qtremors.arcile.core.ui.category.CategoryItemInfo
import dev.qtremors.arcile.core.ui.category.CategoryListItem
import dev.qtremors.arcile.core.ui.category.CategorySectionHeader
import dev.qtremors.arcile.core.ui.rememberArcileHaptics
import dev.qtremors.arcile.core.ui.scrollbar.ArcileFastScrollbar
import dev.qtremors.arcile.core.ui.scrollbar.LazyGridScrollbarState
import dev.qtremors.arcile.core.ui.scrollbar.LazyListScrollbarState
import dev.qtremors.arcile.core.ui.scrollbar.ScrollbarState

@Composable
internal fun AudioLibraryPage(
    state: AudioLibraryState,
    tab: CategoryLibraryPage,
    activeGridSize: Float,
    contentPadding: PaddingValues,
    currentMediaId: String?,
    onGridSizeChange: (Float) -> Unit,
    onGridSizeFinalized: (Float) -> Unit,
    onRefresh: () -> Unit,
    onPlay: (String) -> Unit,
    onSelectFolder: (AudioFolder) -> Unit,
    onCreatePlaylist: (String) -> Unit,
    onAddSelectionToPlaylist: (String) -> Unit,
    onRenamePlaylist: (String, String) -> Unit,
    onDeletePlaylist: (String) -> Unit,
    onSetPlaylistTracks: (String, List<String>) -> Unit,
    onToggleSelection: (String) -> Unit,
    onSelectPaths: (Collection<String>) -> Unit,
    onTogglePaths: (Collection<String>) -> Unit,
    onPasteToFolder: (String) -> Unit,
    onTogglePinnedFolder: (AudioFolder) -> Unit,
    onChooseFolderCover: (AudioFolder) -> Unit,
    onResetFolderCover: (AudioFolder) -> Unit,
    modifier: Modifier = Modifier
) {
    val pullRefreshState = androidx.compose.material3.pulltorefresh.rememberPullToRefreshState()
    PullToRefreshBox(
        isRefreshing = state.isRefreshing,
        onRefresh = onRefresh,
        state = pullRefreshState,
        modifier = modifier.fillMaxSize(),
        indicator = {
            ArcilePullRefreshIndicator(
                isRefreshing = state.isRefreshing,
                state = pullRefreshState
            )
        }
    ) {
        val showingTracks =
            tab == CategoryLibraryPage.ITEMS || state.folderFilter != null
        val isEmpty = if (showingTracks) {
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
            isEmpty && !state.isLoading && showingTracks && state.folderFilter == null -> AudioEmptyState(
                hasFilter = state.query.isNotBlank() ||
                    state.searchFilters.hasActiveFilters ||
                    state.folderFilter != null
            )
            showingTracks -> AudioTracksContent(
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
                onCreatePlaylist = onCreatePlaylist,
                onAddSelectionToPlaylist = onAddSelectionToPlaylist,
                onRenamePlaylist = onRenamePlaylist,
                onDeletePlaylist = onDeletePlaylist,
                onSetPlaylistTracks = onSetPlaylistTracks,
                onSelectPaths = onSelectPaths,
                onTogglePaths = onTogglePaths,
                onPasteToFolder = onPasteToFolder,
                onTogglePinnedFolder = onTogglePinnedFolder,
                onChooseFolderCover = onChooseFolderCover,
                onResetFolderCover = onResetFolderCover
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
    val effectiveGrouping = if (state.folderFilter?.kind == AudioFolderKind.Playlist ||
        state.folderFilter?.kind == AudioFolderKind.Album
    ) CategoryGrouping.NONE else state.grouping
    val groups = remember(state.visibleTracks, effectiveGrouping) {
        groupAudioTracks(state.visibleTracks, effectiveGrouping)
    }
    val flatTracks = remember(groups, state.visibleTracks, effectiveGrouping) {
        if (effectiveGrouping == CategoryGrouping.NONE) {
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
    val gridContentPadding = PaddingValues(
        start = 12.dp,
        top = contentPadding.calculateTopPadding() + 8.dp,
        end = 12.dp,
        bottom = contentPadding.calculateBottomPadding()
    )
    val listContentPadding = PaddingValues(
        top = contentPadding.calculateTopPadding(),
        bottom = contentPadding.calculateBottomPadding()
    )
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
            contentPadding = gridContentPadding,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            state.folderFilter?.let { folder ->
                item(span = { GridItemSpan(maxLineSpan) }, key = "collection-header") {
                    AudioCollectionDetailHeader(
                        folder = folder,
                        onPlayAll = { state.visibleTracks.firstOrNull()?.let {
                            onPlay(it.file.absolutePath)
                        } },
                        onSelectAll = {
                            onSelectPaths(state.visibleTracks.map { it.file.absolutePath })
                        }
                    )
                }
            }
            if (effectiveGrouping == CategoryGrouping.NONE) {
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
            contentPadding = listContentPadding
        ) {
            state.folderFilter?.let { folder ->
                item(key = "collection-header") {
                    AudioCollectionDetailHeader(
                        folder = folder,
                        onPlayAll = { state.visibleTracks.firstOrNull()?.let {
                            onPlay(it.file.absolutePath)
                        } },
                        onSelectAll = {
                            onSelectPaths(state.visibleTracks.map { it.file.absolutePath })
                        }
                    )
                }
            }
            if (effectiveGrouping == CategoryGrouping.NONE) {
                items(state.visibleTracks, key = { it.file.absolutePath }) { track ->
                    AudioTrackListItem(
                        track = track,
                        zoom = state.audioPresentation.listZoom,
                        isCurrent = currentMediaId == track.file.absolutePath,
                        isSelected = track.file.absolutePath in state.selectedPaths,
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
                if (state.folderFilter != null && index == 0) {
                    state.folderFilter.title
                } else audioTrackForLazyIndex(
                    index - if (state.folderFilter != null) 1 else 0,
                    state.visibleTracks, effectiveGrouping, groups
                )
                    ?.displayTitle
                    .orEmpty()
            },
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight(),
            contentPadding = if (state.audioPresentation.viewMode == FileViewMode.GRID) {
                gridContentPadding
            } else {
                listContentPadding
            },
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
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    CategoryListItem(
        info = track.categoryItemInfo(context, showDetails = true),
        selected = isSelected,
        highlighted = isCurrent,
        zoom = zoom,
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier
    ) {
        AudioArtwork(track, Modifier.fillMaxSize())
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
    val context = androidx.compose.ui.platform.LocalContext.current
    CategoryGridItem(
        info = track.categoryItemInfo(context, showDetails = true),
        selected = isSelected,
        highlighted = isCurrent,
        showInfo = showDetails,
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier
    ) {
        AudioArtwork(track = track, modifier = Modifier.fillMaxSize())
    }
}

private fun AudioTrack.categoryItemInfo(context: android.content.Context, showDetails: Boolean): CategoryItemInfo =
    CategoryItemInfo(
        title = displayTitle,
        detailLines = buildList {
            add(buildTrackSubtitle(this@categoryItemInfo))
            if (showDetails) {
                add("${formatAudioDuration(durationMs)} • ${formatFileSize(context, file.size)}")
            }
        }
    )

@Composable
private fun AudioFoldersContent(
    state: AudioLibraryState,
    gridSize: Float,
    contentPadding: PaddingValues,
    onGridSizeChange: (Float) -> Unit,
    onGridSizeFinalized: (Float) -> Unit,
    onSelectFolder: (AudioFolder) -> Unit,
    onCreatePlaylist: (String) -> Unit,
    onAddSelectionToPlaylist: (String) -> Unit,
    onRenamePlaylist: (String, String) -> Unit,
    onDeletePlaylist: (String) -> Unit,
    onSetPlaylistTracks: (String, List<String>) -> Unit,
    onSelectPaths: (Collection<String>) -> Unit,
    onTogglePaths: (Collection<String>) -> Unit,
    onPasteToFolder: (String) -> Unit,
    onTogglePinnedFolder: (AudioFolder) -> Unit,
    onChooseFolderCover: (AudioFolder) -> Unit,
    onResetFolderCover: (AudioFolder) -> Unit
) {
    val haptics = rememberArcileHaptics()
    var showCreatePlaylist by rememberSaveable { mutableStateOf(false) }
    var playlistName by rememberSaveable { mutableStateOf("") }
    var showPlaylistPicker by rememberSaveable { mutableStateOf(false) }
    var renamePlaylistId by rememberSaveable { mutableStateOf<String?>(null) }
    var deletePlaylistId by rememberSaveable { mutableStateOf<String?>(null) }
    var editPlaylistId by rememberSaveable { mutableStateOf<String?>(null) }
    val gridState = rememberSaveable(saver = LazyGridState.Saver) { LazyGridState() }
    val scrollbarState: ScrollbarState = LazyGridScrollbarState(gridState)
    val listState = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }
    val folderContentPadding = PaddingValues(
        start = 16.dp,
        top = contentPadding.calculateTopPadding() + 8.dp,
        end = 16.dp,
        bottom = contentPadding.calculateBottomPadding()
    )
    val favoritesTitle = stringResource(R.string.audio_favorites)
    val folderLabels = remember(state.folders, favoritesTitle) {
        state.folders.map { it.displayTitle(favoritesTitle) }
    }
    if (state.presentationFor(state.collectionKind).viewMode == FileViewMode.LIST) {
        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = folderContentPadding,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (state.collectionKind == AudioCollectionKind.PLAYLISTS) {
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { showCreatePlaylist = true }) {
                                Text(stringResource(R.string.audio_new_playlist))
                            }
                            if (state.selectedPaths.isNotEmpty() && state.playlists.isNotEmpty()) {
                                Box {
                                    TextButton(onClick = { showPlaylistPicker = true }) {
                                        Text(stringResource(R.string.audio_add_selected))
                                    }
                                    DropdownMenu(
                                        expanded = showPlaylistPicker,
                                        onDismissRequest = { showPlaylistPicker = false }
                                    ) {
                                        state.playlists.forEach { playlist ->
                                            DropdownMenuItem(
                                                text = { Text(playlist.name) },
                                                onClick = {
                                                    showPlaylistPicker = false
                                                    onAddSelectionToPlaylist(playlist.id)
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                if (state.folders.isEmpty()) {
                    item {
                        Text(
                            if (state.collectionKind == AudioCollectionKind.PLAYLISTS) {
                                stringResource(R.string.audio_playlist_empty)
                            } else stringResource(R.string.audio_collection_empty),
                            modifier = Modifier.padding(24.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                items(state.folders, key = AudioFolder::key) { folder ->
                    val paths = folder.tracks.map { it.file.absolutePath }
                    val selected = paths.isNotEmpty() && paths.all(state.selectedPaths::contains)
                    AudioFolderListItem(
                        folder = folder,
                        zoom = state.presentationFor(state.collectionKind).listZoom,
                        isSelected = selected,
                        showDetails = state.showFileDetails,
                        canPaste = state.clipboardState != null && folder.isDirectory,
                        onClick = {
                            if (state.selectedPaths.isEmpty()) onSelectFolder(folder)
                            else onTogglePaths(paths)
                        },
                        onLongClick = { onSelectPaths(paths) },
                        onPaste = { onPasteToFolder(folder.key) },
                        onTogglePin = { onTogglePinnedFolder(folder) },
                        onChooseCover = { onChooseFolderCover(folder) },
                        onResetCover = { onResetFolderCover(folder) },
                        onRenamePlaylist = {
                            playlistName = folder.title
                            renamePlaylistId = folder.key
                        },
                        onDeletePlaylist = { deletePlaylistId = folder.key },
                        onEditPlaylist = { editPlaylistId = folder.key }
                    )
                }
            }
            ArcileFastScrollbar(
                scrollbarState = LazyListScrollbarState(listState),
                labelForIndex = { index -> folderLabels.getOrNull(index).orEmpty() },
                modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                contentPadding = folderContentPadding,
                enabled = state.scrollbarEnabled
            )
        }
    } else Box(modifier = Modifier.fillMaxSize()) {
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
            contentPadding = folderContentPadding,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (state.collectionKind == AudioCollectionKind.PLAYLISTS) {
                        Button(onClick = { showCreatePlaylist = true }) {
                            Text(stringResource(R.string.audio_new_playlist))
                        }
                        if (state.selectedPaths.isNotEmpty() && state.playlists.isNotEmpty()) {
                            Box {
                                TextButton(onClick = { showPlaylistPicker = true }) {
                                    Text(stringResource(R.string.audio_add_selected))
                                }
                                DropdownMenu(
                                    expanded = showPlaylistPicker,
                                    onDismissRequest = { showPlaylistPicker = false }
                                ) {
                                    state.playlists.forEach { playlist ->
                                        DropdownMenuItem(
                                            text = { Text(playlist.name) },
                                            onClick = {
                                                showPlaylistPicker = false
                                                onAddSelectionToPlaylist(playlist.id)
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
            if (state.folders.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        if (state.collectionKind == AudioCollectionKind.PLAYLISTS) {
                            stringResource(R.string.audio_playlist_empty)
                        } else {
                            stringResource(R.string.audio_collection_empty)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(24.dp)
                    )
                }
            }
            items(state.folders, key = AudioFolder::key) { folder ->
                val folderPaths = folder.tracks.map { it.file.absolutePath }
                val isSelected = folderPaths.isNotEmpty() &&
                    folderPaths.all(state.selectedPaths::contains)
                AudioFolderGridItem(
                    folder = folder,
                    isSelected = isSelected,
                    canPaste = state.clipboardState != null && folder.isDirectory,
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
                    onTogglePin = { onTogglePinnedFolder(folder) },
                    onChooseCover = { onChooseFolderCover(folder) },
                    onResetCover = { onResetFolderCover(folder) },
                    onRenamePlaylist = {
                        playlistName = folder.title
                        renamePlaylistId = folder.key
                    },
                    onDeletePlaylist = { deletePlaylistId = folder.key },
                    onEditPlaylist = { editPlaylistId = folder.key },
                    modifier = Modifier.animateItem()
                )
            }
        }
        ArcileFastScrollbar(
            scrollbarState = scrollbarState,
            labelForIndex = { index -> folderLabels.getOrNull(index).orEmpty() },
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight(),
            contentPadding = folderContentPadding,
            enabled = state.scrollbarEnabled
        )
    }
    if (showCreatePlaylist) {
        AlertDialog(
            onDismissRequest = { showCreatePlaylist = false },
            title = { Text(stringResource(R.string.audio_new_playlist)) },
            text = {
                OutlinedTextField(
                    value = playlistName,
                    onValueChange = { playlistName = it },
                    label = { Text(stringResource(R.string.audio_playlist_name)) },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onCreatePlaylist(playlistName)
                        playlistName = ""
                        showCreatePlaylist = false
                    },
                    enabled = playlistName.isNotBlank()
                ) { Text(stringResource(R.string.audio_create)) }
            },
            dismissButton = {
                TextButton(onClick = { showCreatePlaylist = false }) {
                    Text(stringResource(dev.qtremors.arcile.core.ui.R.string.cancel))
                }
            }
        )
    }
    renamePlaylistId?.let { id ->
        AlertDialog(
            onDismissRequest = { renamePlaylistId = null },
            title = { Text(stringResource(R.string.audio_rename_playlist)) },
            text = {
                OutlinedTextField(
                    value = playlistName,
                    onValueChange = { playlistName = it },
                    label = { Text(stringResource(R.string.audio_playlist_name)) },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onRenamePlaylist(id, playlistName)
                        renamePlaylistId = null
                        playlistName = ""
                    },
                    enabled = playlistName.isNotBlank()
                ) { Text(stringResource(R.string.audio_save)) }
            },
            dismissButton = {
                TextButton(onClick = { renamePlaylistId = null }) {
                    Text(stringResource(dev.qtremors.arcile.core.ui.R.string.cancel))
                }
            }
        )
    }
    deletePlaylistId?.let { id ->
        AlertDialog(
            onDismissRequest = { deletePlaylistId = null },
            title = { Text(stringResource(R.string.audio_delete_playlist_question)) },
            text = { Text(stringResource(R.string.audio_delete_playlist_note)) },
            confirmButton = {
                TextButton(onClick = {
                    onDeletePlaylist(id)
                    deletePlaylistId = null
                }) { Text(stringResource(R.string.audio_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { deletePlaylistId = null }) {
                    Text(stringResource(dev.qtremors.arcile.core.ui.R.string.cancel))
                }
            }
        )
    }
    editPlaylistId?.let { id ->
        state.playlists.firstOrNull { it.id == id }?.let { playlist ->
            AudioPlaylistEditorDialog(
                playlist = playlist,
                tracks = state.tracks,
                onSave = { trackIds ->
                    onSetPlaylistTracks(id, trackIds)
                    editPlaylistId = null
                },
                onDismiss = { editPlaylistId = null }
            )
        }
    }
}

@Composable
private fun AudioSectionHeader(title: String) {
    CategorySectionHeader(title)
}

private fun buildTrackSubtitle(track: AudioTrack): String =
    listOfNotNull(track.artist, track.album).joinToString(" • ")

internal fun audioTrackForLazyIndex(
    index: Int,
    tracks: List<AudioTrack>,
    grouping: CategoryGrouping,
    groups: Map<AudioGroupKey, List<AudioTrack>>
): AudioTrack? {
    if (grouping == CategoryGrouping.NONE) return tracks.getOrNull(index)
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
