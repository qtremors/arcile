@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package dev.qtremors.arcile.feature.audio

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.qtremors.arcile.core.storage.domain.FileViewMode
import dev.qtremors.arcile.core.ui.rememberArcileHaptics
import dev.qtremors.arcile.core.ui.scrollbar.ArcileFastScrollbar
import dev.qtremors.arcile.core.ui.scrollbar.LazyGridScrollbarState
import dev.qtremors.arcile.core.ui.scrollbar.LazyListScrollbarState
import dev.qtremors.arcile.core.ui.scrollbar.ScrollbarState

@Composable
internal fun AudioCollectionsContent(
    state: AudioLibraryState,
    gridSize: Float,
    contentPadding: PaddingValues,
    onGridSizeChange: (Float) -> Unit,
    onGridSizeFinalized: (Float) -> Unit,
    onOpenCollection: (AudioCollection) -> Unit,
    onCreatePlaylist: (String) -> Unit,
    onAddSelectionToPlaylist: (String) -> Unit,
    onRenamePlaylist: (String, String) -> Unit,
    onDeletePlaylist: (String) -> Unit,
    onSetPlaylistTracks: (String, List<String>) -> Unit,
    onSelectPaths: (Collection<String>) -> Unit,
    onTogglePaths: (Collection<String>) -> Unit,
    onPasteToFolder: (String) -> Unit,
    onTogglePinnedFolder: (AudioCollection) -> Unit,
    onChooseFolderCover: (AudioCollection) -> Unit,
    onResetFolderCover: (AudioCollection) -> Unit
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
    val folderLabels = remember(state.collections, favoritesTitle) {
        state.collections.map { it.displayTitle(favoritesTitle) }
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
                if (state.collections.isEmpty()) {
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
                items(state.collections, key = AudioCollection::key) { collection ->
                    val paths = collection.tracks.map { it.file.reference }
                    val selected = paths.isNotEmpty() && paths.all(state.selectedPaths::contains)
                    AudioCollectionListItem(
                        collection = collection,
                        zoom = state.presentationFor(state.collectionKind).listZoom,
                        isSelected = selected,
                        showDetails = state.showFileDetails,
                        canPaste = state.clipboardState != null && collection.isDirectory,
                        onClick = {
                            if (state.selectedPaths.isEmpty()) onOpenCollection(collection)
                            else onTogglePaths(paths)
                        },
                        onLongClick = { onSelectPaths(paths) },
                        onPaste = { onPasteToFolder(collection.key) },
                        onTogglePin = { onTogglePinnedFolder(collection) },
                        onChooseCover = { onChooseFolderCover(collection) },
                        onResetCover = { onResetFolderCover(collection) },
                        onRenamePlaylist = {
                            playlistName = collection.title
                            renamePlaylistId = collection.key
                        },
                        onDeletePlaylist = { deletePlaylistId = collection.key },
                        onEditPlaylist = { editPlaylistId = collection.key }
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
            if (state.collections.isEmpty()) {
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
            items(state.collections, key = AudioCollection::key) { collection ->
                val folderPaths = collection.tracks.map { it.file.reference }
                val isSelected = folderPaths.isNotEmpty() &&
                    folderPaths.all(state.selectedPaths::contains)
                AudioCollectionGridItem(
                    collection = collection,
                    isSelected = isSelected,
                    canPaste = state.clipboardState != null && collection.isDirectory,
                    onClick = {
                        if (state.selectedPaths.isEmpty()) {
                            onOpenCollection(collection)
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
                    onPaste = { onPasteToFolder(collection.key) },
                    onTogglePin = { onTogglePinnedFolder(collection) },
                    onChooseCover = { onChooseFolderCover(collection) },
                    onResetCover = { onResetFolderCover(collection) },
                    onRenamePlaylist = {
                        playlistName = collection.title
                        renamePlaylistId = collection.key
                    },
                    onDeletePlaylist = { deletePlaylistId = collection.key },
                    onEditPlaylist = { editPlaylistId = collection.key },
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
