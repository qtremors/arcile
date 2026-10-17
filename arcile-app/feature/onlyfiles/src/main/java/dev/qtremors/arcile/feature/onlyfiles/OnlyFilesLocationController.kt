package dev.qtremors.arcile.feature.onlyfiles

import dev.qtremors.arcile.core.storage.domain.SearchFilters
import dev.qtremors.arcile.core.vault.domain.DirectoryId
import dev.qtremors.arcile.core.vault.domain.VaultDirectoryLocation
import dev.qtremors.arcile.core.vault.domain.VaultFailure
import dev.qtremors.arcile.core.vault.domain.VaultFileSystem
import dev.qtremors.arcile.core.vault.domain.VaultId
import dev.qtremors.arcile.core.vault.domain.VaultListOptions
import dev.qtremors.arcile.core.vault.domain.VaultNodeMetadata
import dev.qtremors.arcile.core.vault.domain.VaultPath
import dev.qtremors.arcile.core.vault.domain.VaultSecurityPreferences
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal enum class OnlyFilesLocationMode { SEARCH_SCOPE, PINNED, ARCHIVE_DESTINATION }

internal data class OnlyFilesLocationPickerState(
    val mode: OnlyFilesLocationMode,
    val stack: List<VaultDirectoryCrumb>,
    val folders: List<VaultNodeMetadata> = emptyList(),
    val shortcuts: List<List<VaultDirectoryCrumb>> = emptyList(),
    val nextPageToken: String? = null,
    val generation: Long? = null,
    val loading: Boolean = true,
    val failed: Boolean = false
)

internal class OnlyFilesLocationController(
    private val fileSystem: VaultFileSystem,
    private val preferences: VaultSecurityPreferences,
    private val state: MutableStateFlow<OnlyFilesUiState>,
    private val scope: CoroutineScope,
    private val reload: () -> Unit,
    private val refreshSearch: () -> Unit,
    private val showError: (Throwable) -> Unit,
    private val onLocationChanged: () -> Unit = {}
) {
    private var pickerJob: Job? = null
    private var navigationJob: Job? = null

    fun restore(directory: DirectoryId?) {
        if (directory == null || directory == DirectoryId.Root) reload()
        else navigate(directory, focusNodeId = null, restore = true)
    }

    fun navigate(directory: DirectoryId, focusNodeId: String? = null, restore: Boolean = false) {
        navigationJob?.cancel()
        val snapshot = state.value
        val vault = snapshot.selectedVaultId ?: return
        state.update { it.copy(isListing = true) }
        navigationJob = scope.launch {
            val result = fileSystem.directoryTrail(vault, directory)
            ensureActive()
            if (!isMounted(vault, snapshot.mountGeneration)) return@launch
            result.fold(
                onSuccess = { trail -> changeDirectory(trail.toCrumbs(), focusNodeId) },
                onFailure = { error ->
                    if (restore && error is VaultFailure.InvalidPath) rememberDirectory(vault, DirectoryId.Root)
                    state.update { it.copy(isListing = false) }
                    if (restore || state.value.nodes.isEmpty()) reload()
                    if (!restore) showError(error)
                }
            )
        }
    }

    fun changeDirectory(stack: List<VaultDirectoryCrumb>, focusNodeId: String? = null) {
        val directory = stack.lastOrNull() ?: return
        val vault = state.value.selectedVaultId ?: return
        cancelNavigation()
        closePicker()
        state.update {
            it.copy(
                directoryStack = stack, nodes = emptyList(), nextPageToken = null,
                viewer = null, viewerQueue = emptyList(), selectedNodeIds = emptySet(),
                searchQuery = "", searchFilters = SearchFilters(),
                searchHits = emptyList(), searchScope = null, revealNodeId = focusNodeId, revealHiddenNodeId = focusNodeId
            )
        }
        rememberDirectory(vault, directory.id)
        onLocationChanged()
        reload()
    }

    fun togglePin(directory: DirectoryId) {
        val snapshot = state.value
        val vault = snapshot.selectedVaultId ?: return
        val pinned = directory in snapshot.pinnedDirectoryIds
        savePreference {
            preferences.setDirectoryPinned(vault, directory, !pinned)
        }
    }

    fun resetCurrentFolderView() {
        val snapshot = state.value
        val vault = snapshot.selectedVaultId ?: return
        val directory = snapshot.currentDirectory?.id ?: return
        savePreference { preferences.resetFolderViewOptions(vault, directory) }
    }

    fun openScopePicker() {
        val snapshot = state.value
        val stack = snapshot.searchScope ?: snapshot.directoryStack
        if (stack.isEmpty()) return
        loadPicker(OnlyFilesLocationPickerState(OnlyFilesLocationMode.SEARCH_SCOPE, stack))
    }

    fun openArchiveDestination() {
        val stack = state.value.directoryStack
        if (stack.isEmpty()) return
        loadPicker(OnlyFilesLocationPickerState(OnlyFilesLocationMode.ARCHIVE_DESTINATION, stack))
    }

    fun openPinnedFolders() {
        closePicker()
        val snapshot = state.value
        val vault = snapshot.selectedVaultId ?: return
        state.update { it.copy(locationPicker = OnlyFilesLocationPickerState(OnlyFilesLocationMode.PINNED, emptyList())) }
        pickerJob = scope.launch {
            val trails = mutableListOf<List<VaultDirectoryCrumb>>()
            for (id in snapshot.pinnedDirectoryIds) {
                val result = fileSystem.directoryTrail(vault, id)
                ensureActive()
                if (!isMounted(vault, snapshot.mountGeneration)) return@launch
                result.fold(
                    onSuccess = { trails.add(it.toCrumbs()) },
                    onFailure = { error ->
                        if (error is VaultFailure.InvalidPath) {
                            savePreference { preferences.setDirectoryPinned(vault, id, false) }
                        }
                    }
                )
            }
            state.update { current ->
                current.copy(locationPicker = current.locationPicker?.copy(
                    shortcuts = trails.sortedBy { it.last().name.lowercase() }, loading = false
                ))
            }
        }
    }

    fun openPickerFolder(folder: VaultNodeMetadata) {
        val picker = state.value.locationPicker ?: return
        if (picker.mode == OnlyFilesLocationMode.PINNED) return
        val id = folder.ref.directoryId ?: return
        val path = picker.stack.last().path.resolve(folder.name)
        loadPicker(picker.copy(
            stack = picker.stack + VaultDirectoryCrumb(id, folder.name, path),
            folders = emptyList(), nextPageToken = null
        ))
    }

    fun pickerToDirectory(id: DirectoryId) {
        val picker = state.value.locationPicker ?: return
        val index = picker.stack.indexOfFirst { it.id == id }
        if (index !in 0 until picker.stack.lastIndex) return
        loadPicker(picker.copy(stack = picker.stack.take(index + 1), folders = emptyList(), nextPageToken = null))
    }

    fun loadMoreFolders() {
        val picker = state.value.locationPicker ?: return
        if (picker.loading || picker.nextPageToken == null) return
        loadPicker(picker, append = true)
    }

    fun retryPicker() {
        val picker = state.value.locationPicker ?: return
        loadPicker(picker.copy(folders = emptyList(), nextPageToken = null))
    }

    fun applyScope() {
        val stack = state.value.locationPicker?.stack ?: return
        if (stack.isEmpty()) return
        closePicker()
        state.update { it.copy(searchScope = stack) }
        refreshSearch()
    }

    fun clearScope() {
        state.update { it.copy(searchScope = null) }
        refreshSearch()
    }

    fun closePicker() {
        pickerJob?.cancel()
        pickerJob = null
        state.update { it.copy(locationPicker = null) }
    }

    fun cancelNavigation() {
        navigationJob?.cancel()
        navigationJob = null
    }

    private fun loadPicker(picker: OnlyFilesLocationPickerState, append: Boolean = false) {
        pickerJob?.cancel()
        val snapshot = state.value
        val vault = snapshot.selectedVaultId ?: return
        val directory = picker.stack.lastOrNull() ?: return
        state.update { it.copy(locationPicker = picker.copy(loading = true, failed = false)) }
        pickerJob = scope.launch {
            val result = fileSystem.listDirectory(
                vault, directory.id, VaultListOptions(
                    pageToken = picker.nextPageToken.takeIf { append },
                    showHiddenFiles = snapshot.viewOptions.showHiddenFiles,
                    foldersOnly = true
                )
            )
            ensureActive()
            if (!isMounted(vault, snapshot.mountGeneration)) return@launch
            val next = result.fold(
                onSuccess = { page ->
                    if (append && picker.generation != page.generation) {
                        loadPicker(picker.copy(folders = emptyList(), nextPageToken = null))
                        return@launch
                    }
                    picker.copy(
                        folders = (picker.folders + page.items).distinctBy { it.ref.nodeId },
                        nextPageToken = page.nextPageToken,
                        generation = page.generation,
                        loading = false, failed = false
                    )
                },
                onFailure = { picker.copy(loading = false, failed = true) }
            )
            state.update { it.copy(locationPicker = next) }
        }
    }

    private fun isMounted(vault: VaultId, generation: Long): Boolean =
        state.value.let { it.selectedVaultId == vault && it.mountGeneration == generation }

    private fun rememberDirectory(vault: VaultId, directory: DirectoryId) =
        savePreference { preferences.setLastDirectory(vault, directory) }

    private fun savePreference(block: suspend () -> Unit) {
        scope.launch {
            try {
                block()
            } catch (error: IOException) {
                showError(error)
            }
        }
    }
}

internal fun List<VaultDirectoryLocation>.toCrumbs(): List<VaultDirectoryCrumb> {
    var path = VaultPath.Root
    return map { location ->
        if (location.id != DirectoryId.Root) path = path.resolve(location.name)
        VaultDirectoryCrumb(location.id, location.name, path)
    }
}
