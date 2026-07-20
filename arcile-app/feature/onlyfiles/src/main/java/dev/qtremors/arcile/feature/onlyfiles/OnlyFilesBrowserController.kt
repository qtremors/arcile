package dev.qtremors.arcile.feature.onlyfiles

import dev.qtremors.arcile.core.vault.domain.VaultFileSystem
import dev.qtremors.arcile.core.vault.domain.VaultId
import dev.qtremors.arcile.core.vault.domain.VaultListOptions
import dev.qtremors.arcile.core.vault.domain.VaultNodeMetadata
import dev.qtremors.arcile.core.vault.domain.VaultSearchQuery
import dev.qtremors.arcile.core.vault.domain.VaultSortDirection
import dev.qtremors.arcile.core.vault.domain.VaultSortField
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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

    fun refresh() = if (state.value.searchQuery.isBlank()) reload() else runSearch()

    fun loadNextPage() {
        val snapshot = state.value
        if (snapshot.searchQuery.isBlank()) loadDirectoryPage(snapshot.nextPageToken, true)
        else scheduleSearchPage(snapshot.searchNextPageToken, true)
    }

    fun setSort(field: VaultSortField, direction: VaultSortDirection) {
        state.update { it.copy(sortField = field, sortDirection = direction, selectedNodeIds = emptySet()) }
        reload()
    }

    fun updateSearch(query: String) {
        searchJob?.cancel()
        state.update {
            it.copy(
                searchQuery = query, searchHits = emptyList(), searchNextPageToken = null,
                selectedNodeIds = emptySet(), isSearching = false
            )
        }
        if (query.isNotBlank()) scheduleSearchPage(null, false, 250L)
    }

    fun toggleRecursiveSearch() {
        state.update { it.copy(recursiveSearch = !it.recursiveSearch) }
        if (state.value.searchQuery.isNotBlank()) runSearch()
    }

    fun reload() {
        searchJob?.cancel()
        state.update {
            it.copy(nodes = emptyList(), nextPageToken = null, searchHits = emptyList(), searchNextPageToken = null, isSearching = false)
        }
        loadDirectoryPage(null, false)
    }

    fun cancel() {
        searchJob?.cancel()
        searchJob = null
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
        var completed = 0
        var failed = 0
        nodes.distinctBy { it.ref.nodeId }.forEach { node ->
            fileSystem.deletePermanently(node.ref).fold({ completed++ }, { failed++ })
        }
        state.update {
            it.copy(
                selectedNodeIds = emptySet(),
                viewer = it.viewer?.takeUnless { viewer -> nodes.any { node -> node.ref.nodeId == viewer.ref.nodeId } },
                message = if (failed == 0) "$completed item(s) permanently deleted" else "$completed deleted; $failed failed"
            )
        }
        reload()
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
        if (snapshot.isSearching || append && pageToken == null) return
        scope.launch {
            state.update { it.copy(isSearching = true) }
            fileSystem.listDirectory(
                vaultId, directoryId,
                VaultListOptions(snapshot.sortField, snapshot.sortDirection, PAGE_SIZE, pageToken)
            ).fold(
                onSuccess = { page ->
                    if (state.value.selectedVaultId == vaultId && state.value.currentDirectory?.id == directoryId) {
                        state.update { current ->
                            val combined = if (append) current.nodes + page.items else page.items
                            current.copy(
                                nodes = combined.distinctBy { it.ref.nodeId }, nextPageToken = page.nextPageToken,
                                selectedNodeIds = current.selectedNodeIds.intersect(combined.map { it.ref.nodeId.value }.toSet()),
                                isSearching = false
                            )
                        }
                    }
                },
                onFailure = { error -> state.update { it.copy(isSearching = false) }; showError(error) }
            )
        }
    }

    private fun runSearch() {
        searchJob?.cancel()
        state.update { it.copy(searchHits = emptyList(), searchNextPageToken = null, isSearching = false) }
        scheduleSearchPage(null, false)
    }

    private fun scheduleSearchPage(pageToken: String?, append: Boolean, debounceMillis: Long = 0L) {
        val snapshot = state.value
        val vaultId = snapshot.selectedVaultId ?: return
        val directoryId = snapshot.currentDirectory?.id ?: return
        val query = snapshot.searchQuery.trim()
        if (query.isEmpty() || snapshot.isSearching || append && pageToken == null) return
        searchJob?.cancel()
        searchJob = scope.launch {
            if (debounceMillis > 0L) delay(debounceMillis)
            state.update { it.copy(isSearching = true) }
            fileSystem.search(vaultId, directoryId, VaultSearchQuery(query, snapshot.recursiveSearch, PAGE_SIZE, pageToken)).fold(
                onSuccess = { page ->
                    if (state.value.searchQuery.trim() == query && state.value.currentDirectory?.id == directoryId) {
                        state.update { current ->
                            val combined = if (append) current.searchHits + page.items else page.items
                            current.copy(
                                searchHits = combined.distinctBy { it.metadata.ref.nodeId },
                                searchNextPageToken = page.nextPageToken, selectedNodeIds = emptySet(), isSearching = false
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
