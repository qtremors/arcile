package dev.qtremors.arcile.feature.onlyfiles

import dev.qtremors.arcile.core.storage.domain.SearchFilters
import dev.qtremors.arcile.core.storage.domain.matchesSearchFilters
import dev.qtremors.arcile.core.vault.domain.VaultFileSystem
import dev.qtremors.arcile.core.vault.domain.VaultId
import dev.qtremors.arcile.core.vault.domain.VaultListOptions
import dev.qtremors.arcile.core.vault.domain.VaultNodeMetadata
import dev.qtremors.arcile.core.vault.domain.VaultSearchQuery
import dev.qtremors.arcile.core.vault.domain.VaultSecuritySettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal class OnlyFilesBrowserController(
    private val fileSystem: VaultFileSystem,
    private val state: MutableStateFlow<OnlyFilesUiState>,
    private val scope: CoroutineScope,
    private val showError: (Throwable) -> Unit,
    private val runBusy: ((suspend () -> Unit) -> Unit)
) {
    private var searchJob: Job? = null
    private var directoryJob: Job? = null
    private var directoryGeneration: Long? = null

    fun refresh() = reload()

    fun loadNextPage() {
        val snapshot = state.value
        if (!snapshot.hasSearch) loadDirectoryPage(snapshot.nextPageToken, true)
        else scheduleSearchPage(snapshot.searchNextPageToken, true)
    }

    fun applyViewOptions(settings: VaultSecuritySettings) {
        val previous = state.value.viewOptions
        state.update { it.copy(defaultViewOptions = settings.viewOptions, folderViewOptions = settings.folderViewOptions) }
        val options = state.value.viewOptions
        if (previous.sortField != options.sortField || previous.sortDirection != options.sortDirection ||
            previous.showHiddenFiles != options.showHiddenFiles || previous.foldersFirst != options.foldersFirst
        ) {
            reload()
        } else if (previous.recursiveSearch != options.recursiveSearch && state.value.hasSearch) {
            updateSearch(state.value.searchQuery)
        }
    }

    fun updateSearch(
        query: String,
        filters: SearchFilters = state.value.searchFilters,
        debounceMillis: Long = 250L
    ) {
        searchJob?.cancel()
        state.update {
            it.copy(
                searchQuery = query, searchFilters = filters, searchHits = emptyList(), searchNextPageToken = null,
                selectedNodeIds = emptySet(), isSearching = false
            )
        }
        if (state.value.hasSearch) scheduleSearchPage(null, false, debounceMillis)
    }


    fun reload() {
        directoryJob?.cancel()
        searchJob?.cancel()
        directoryGeneration = null
        state.update {
            it.copy(
                nextPageToken = null, searchNextPageToken = null, isListing = false, isSearching = false,
                listingRevision = it.listingRevision + 1L
            )
        }
        loadDirectoryPage(null, false)
        if (state.value.hasSearch) scheduleSearchPage(null, false)
    }

    fun cancel() {
        searchJob?.cancel()
        searchJob = null
        directoryJob?.cancel()
        directoryJob = null
        directoryGeneration = null
    }

    fun createFolder(name: String) = currentMutation { vaultId, directoryId ->
        fileSystem.createDirectory(vaultId, directoryId, name)
    }

    fun createEmptyFile(name: String) = currentMutation { vaultId, directoryId ->
        fileSystem.createEmptyFile(vaultId, directoryId, name)
    }

    fun rename(node: VaultNodeMetadata, name: String) = runBusy {
        fileSystem.rename(node.ref, name).fold(onSuccess = { reload() }, onFailure = showError)
    }

    fun delete(nodes: List<VaultNodeMetadata>) = runBusy {
        val mountGeneration = state.value.mountGeneration
        val distinctNodes = nodes.distinctBy { it.ref.nodeId }
        val deletedIds = mutableSetOf<String>()
        var failed = 0
        state.update {
            it.copy(
                batchProgress = OnlyFilesBatchProgress(0, distinctNodes.size, distinctNodes.firstOrNull()?.name)
            )
        }
        distinctNodes.forEach { node ->
            state.update {
                if (it.mountGeneration != mountGeneration) it else it.copy(
                    batchProgress = OnlyFilesBatchProgress(
                        completedItems = deletedIds.size + failed,
                        totalItems = distinctNodes.size,
                        currentName = node.name
                    )
                )
            }
            fileSystem.deletePermanently(node.ref).fold({ deletedIds += node.ref.nodeId.value }, { failed++ })
            state.update {
                if (it.mountGeneration != mountGeneration) it else it.copy(
                    batchProgress = OnlyFilesBatchProgress(
                        completedItems = deletedIds.size + failed,
                        totalItems = distinctNodes.size,
                        currentName = node.name
                    )
                )
            }
        }
        val completed = deletedIds.size
        state.update {
            if (it.mountGeneration != mountGeneration) it else it.copy(
                selectedNodeIds = emptySet(),
                viewer = it.viewer?.takeUnless { viewer -> viewer.ref.nodeId.value in deletedIds },
                viewerQueue = it.viewerQueue.filterNot { node -> node.ref.nodeId.value in deletedIds },
                batchProgress = null,
                message = if (failed == 0) "$completed item(s) permanently deleted" else "$completed deleted; $failed failed"
            )
        }
        if (state.value.mountGeneration == mountGeneration) reload()
    }

    private fun currentMutation(block: suspend (VaultId, dev.qtremors.arcile.core.vault.domain.DirectoryId) -> Result<*>) {
        val snapshot = state.value
        val vaultId = snapshot.selectedVaultId ?: return
        val directoryId = snapshot.currentDirectory?.id ?: return
        runBusy { block(vaultId, directoryId).fold(onSuccess = { reload() }, onFailure = showError) }
    }

    private fun loadDirectoryPage(pageToken: String?, append: Boolean) {
        val snapshot = state.value
        val vaultId = snapshot.selectedVaultId ?: return
        val directoryId = snapshot.currentDirectory?.id ?: return
        if (snapshot.isListing || append && pageToken == null) return
        state.update { it.copy(isListing = true) }
        directoryJob = scope.launch {
            val result = fileSystem.listDirectory(
                vaultId, directoryId,
                VaultListOptions(
                    snapshot.sortField, snapshot.sortDirection, PAGE_SIZE, pageToken,
                    showHiddenFiles = snapshot.viewOptions.showHiddenFiles,
                    foldersFirst = snapshot.viewOptions.foldersFirst
                )
            )
            ensureActive()
            result.fold(
                onSuccess = { page ->
                    if (append && directoryGeneration != page.generation) {
                        state.update { it.copy(isListing = false) }
                        loadDirectoryPage(null, false)
                        return@fold
                    }
                    directoryGeneration = page.generation
                    state.update { current ->
                        val combined = (if (append) current.nodes + page.items else page.items).distinctBy { it.ref.nodeId }
                        val selection = if (current.hasSearch) current.selectedNodeIds
                        else current.selectedNodeIds.intersect(combined.map { it.ref.nodeId.value }.toSet())
                        current.copy(
                            nodes = combined, nextPageToken = page.nextPageToken,
                            selectedNodeIds = selection, isListing = false
                        )
                    }
                },
                onFailure = { error -> state.update { it.copy(isListing = false) }; showError(error) }
            )
        }
    }

    private fun scheduleSearchPage(pageToken: String?, append: Boolean, debounceMillis: Long = 0L) {
        val snapshot = state.value
        val vaultId = snapshot.selectedVaultId ?: return
        val directoryId = snapshot.currentDirectory?.id ?: return
        val query = snapshot.searchQuery.trim()
        val filters = snapshot.searchFilters
        if (!snapshot.hasSearch || snapshot.isSearching || append && pageToken == null) return
        searchJob?.cancel()
        state.update { it.copy(isSearching = true) }
        searchJob = scope.launch {
            if (debounceMillis > 0L) delay(debounceMillis)
            val result = fileSystem.search(
                vaultId, directoryId,
                VaultSearchQuery(
                    query, snapshot.recursiveSearch, PAGE_SIZE, pageToken, filters.includeHidden,
                    sortField = snapshot.sortField,
                    direction = snapshot.sortDirection,
                    foldersFirst = snapshot.viewOptions.foldersFirst
                ) { node ->
                    !filters.hasActiveFilters || node.toSharedFileModel().matchesSearchFilters(filters)
                }
            )
            ensureActive()
            result.fold(
                onSuccess = { page ->
                    if (state.value.searchQuery.trim() == query && state.value.currentDirectory?.id == directoryId) {
                        state.update { current ->
                            val combined = (if (append) current.searchHits + page.items else page.items)
                                .distinctBy { it.metadata.ref.nodeId }
                            current.copy(
                                searchHits = combined, searchNextPageToken = page.nextPageToken,
                                selectedNodeIds = current.selectedNodeIds.intersect(
                                    combined.map { it.metadata.ref.nodeId.value }.toSet()
                                ),
                                isSearching = false
                            )
                        }
                    }
                },
                onFailure = { error ->
                    if (state.value.searchQuery.trim() == query) {
                        state.update { it.copy(isSearching = false) }
                        showError(error)
                    }
                }
            )
        }
    }

    private companion object { const val PAGE_SIZE = 256 }
}
