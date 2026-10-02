package dev.qtremors.arcile.feature.audio

import dev.qtremors.arcile.core.storage.domain.AudioLibraryPreferencesStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

internal class AudioLibraryCollectionActions(
    private val scope: CoroutineScope,
    private val state: MutableStateFlow<AudioLibraryState>,
    private val musicStore: AudioCollectionStore,
    private val listeningStore: AudioListeningStore,
    private val preferencesStore: AudioLibraryPreferencesStore,
    private val clearSelection: () -> Unit,
    private val rebuildPresentation: ((AudioLibraryState) -> AudioLibraryState) -> Unit,
    private val onError: (Throwable) -> Unit
) {
    fun createPlaylist(name: String) {
        scope.launch {
            runCatching { musicStore.createPlaylist(name) }.onFailure(onError)
        }
    }

    fun createPlaylistFromSelection(name: String) {
        val paths = state.value.selectedPaths.toList()
        if (paths.isEmpty()) return
        scope.launch {
            runCatching { musicStore.createPlaylist(name, paths) }
                .onSuccess { clearSelection() }
                .onFailure(onError)
        }
    }

    fun addSelectionToPlaylist(id: String) {
        val paths = state.value.selectedPaths.toList()
        if (paths.isEmpty()) return
        scope.launch {
            runCatching { musicStore.appendPlaylistTracks(id, paths) }
                .onSuccess { clearSelection() }
                .onFailure(onError)
        }
    }

    fun renamePlaylist(id: String, name: String) {
        scope.launch {
            runCatching { musicStore.renamePlaylist(id, name) }.onFailure(onError)
        }
    }

    fun deletePlaylist(id: String) {
        scope.launch {
            runCatching { musicStore.deletePlaylist(id) }.onFailure(onError)
        }
    }

    fun setPlaylistTracks(id: String, paths: List<String>) {
        scope.launch {
            runCatching { musicStore.setPlaylistTracks(id, paths) }.onFailure(onError)
        }
    }

    fun toggleFavoriteSelection() {
        val selected = state.value.selectedPaths
        if (selected.isEmpty()) return
        val makeFavorite = !selected.all(state.value.favoritePaths::contains)
        scope.launch {
            runCatching { listeningStore.setFavorites(selected, makeFavorite) }
                .onFailure(onError)
        }
        rebuildPresentation { current ->
            current.copy(
                favoritePaths = if (makeFavorite) {
                    current.favoritePaths + selected
                } else {
                    current.favoritePaths - selected
                }
            )
        }
    }

    fun togglePinnedFolder(folder: AudioCollection) {
        if (folder.isFavorites) return
        val makePinned = folder.key !in state.value.pinnedFolderPaths
        scope.launch {
            preferencesStore.updatePinnedFolder(folder.key, makePinned)
        }
        rebuildPresentation { current ->
            current.copy(
                pinnedFolderPaths = if (makePinned) {
                    current.pinnedFolderPaths + folder.key
                } else {
                    current.pinnedFolderPaths - folder.key
                }
            )
        }
    }

    fun updateFolderCover(folder: AudioCollection, trackPath: String?) {
        if (folder.isFavorites) return
        val validPath = trackPath?.takeIf { candidate ->
            folder.tracks.any { it.file.reference == candidate }
        }
        scope.launch {
            preferencesStore.updateFolderCover(folder.key, validPath)
        }
        rebuildPresentation { current ->
            current.copy(
                folderCoverPaths = if (validPath == null) {
                    current.folderCoverPaths - folder.key
                } else {
                    current.folderCoverPaths + (folder.key to validPath)
                }
            )
        }
    }

    fun toggleFavoriteTrack(path: String) {
        val favorite = path !in state.value.favoritePaths
        scope.launch {
            runCatching { listeningStore.setFavorite(path, favorite) }.onFailure(onError)
        }
    }

    fun clearListeningHistory() {
        scope.launch {
            runCatching { listeningStore.clearHistory() }.onFailure(onError)
        }
    }
}
