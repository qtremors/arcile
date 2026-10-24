package dev.qtremors.arcile.feature.onlyfiles

import dev.qtremors.arcile.core.storage.domain.SearchFilters
import dev.qtremors.arcile.core.storage.domain.matchesSearchFilters
import dev.qtremors.arcile.core.vault.domain.VaultFileSystem
import dev.qtremors.arcile.core.vault.domain.VaultId
import dev.qtremors.arcile.core.vault.domain.VaultListOptions
import dev.qtremors.arcile.core.vault.domain.VaultNodeMetadata
import dev.qtremors.arcile.core.vault.domain.VaultRenameItem
import dev.qtremors.arcile.core.vault.domain.VaultSearchHit
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
    private val runBusy: ((suspend () -> Unit) -> Unit),
    private val onFoldersDeleted: suspend (VaultId, Set<dev.qtremors.arcile.core.vault.domain.DirectoryId>) -> Unit = { _, _ -> }
) {
    private var searchJob: Job? = null
    private var directoryJob: Job? = null
    private var selectionJob: Job? = null
    private var directoryGeneration: Long? = null
    private var searchGeneration: Long? = null
    private var searchMatcher: Pair<SearchFilters, (VaultNodeMetadata) -> Boolean>? = null

    fun refresh() = reload()

    fun loadNextPage() {
        val snapshot = state.value
        if (snapshot.isSelectingRows) return
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
        cancelSelection()
        searchJob?.cancel()
        searchGeneration = null
        state.update {
            it.copy(
                searchQuery = query, searchFilters = filters, searchHits = emptyList(), searchNextPageToken = null,
                selectedNodeIds = emptySet(), isSearching = false
            )
        }
        if (state.value.hasSearch) scheduleSearchPage(null, false, debounceMillis)
    }


    fun reload() {
        cancelSelection()
        directoryJob?.cancel()
        searchJob?.cancel()
        directoryGeneration = null
        searchGeneration = null
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
        cancelSelection()
        searchJob?.cancel()
        searchJob = null
        directoryJob?.cancel()
        directoryJob = null
        directoryGeneration = null
        searchGeneration = null
    }

    fun cancelSelection() {
        selectionJob?.cancel()
        selectionJob = null
        state.update { if (it.isSelectingRows) it.copy(isSelectingRows = false) else it }
    }

    fun selectRows(invert: Boolean) {
        val snapshot = state.value
        if (snapshot.busy || snapshot.isSelectingRows || snapshot.isLoadingRows && !snapshot.hasMoreRows) return
        if (!snapshot.hasMoreRows) {
            state.update { it.copy(selectedNodeIds = selectionOf(it.displayedNodes, snapshot, invert)) }
            return
        }
        val vault = snapshot.selectedVaultId ?: return
        val directory = snapshot.currentDirectory?.id ?: return
        if (snapshot.hasSearch) {
            searchJob?.cancel()
            state.update { it.copy(isSelectingRows = true, isSearching = false) }
        } else {
            directoryJob?.cancel()
            state.update { it.copy(isSelectingRows = true, isListing = false) }
        }
        selectionJob = scope.launch {
            val result = if (snapshot.hasSearch) {
                val searchDirectory = snapshot.searchScope?.lastOrNull()?.id ?: directory
                fileSystem.selectSearchRows(vault, searchDirectory, snapshot.searchOptions(null)).map { page ->
                    SelectionRows(page.items.map(VaultSearchHit::metadata), page.items, page.generation)
                }
            } else {
                fileSystem.selectDirectoryRows(vault, directory, snapshot.listOptions(null)).map { page ->
                    SelectionRows(page.items, emptyList(), page.generation)
                }
            }
            ensureActive()
            if (state.value.mountGeneration != snapshot.mountGeneration) return@launch
            result.fold(
                onSuccess = { rows ->
                    state.update { current ->
                        val selection = selectionOf(rows.nodes, snapshot, invert)
                        if (snapshot.hasSearch) {
                            searchGeneration = rows.generation
                            current.copy(
                                searchHits = rows.hits, searchNextPageToken = null,
                                selectedNodeIds = selection, isSelectingRows = false
                            )
                        } else {
                            directoryGeneration = rows.generation
                            current.copy(
                                nodes = rows.nodes, nextPageToken = null,
                                selectedNodeIds = selection, isSelectingRows = false
                            )
                        }
                    }
                },
                onFailure = {
                    state.update { current -> current.copy(isSelectingRows = false) }
                    showError(it)
                }
            )
        }
    }

    private fun selectionOf(
        nodes: List<VaultNodeMetadata>,
        snapshot: OnlyFilesUiState,
        invert: Boolean
    ): Set<String> = nodes.mapTo(mutableSetOf()) { it.ref.nodeId.value }.let { ids ->
        if (invert) ids - snapshot.selectedNodeIds else ids
    }

    private data class SelectionRows(
        val nodes: List<VaultNodeMetadata>,
        val hits: List<VaultSearchHit>,
        val generation: Long
    )

    fun createFolder(name: String) = currentMutation { vaultId, directoryId ->
        fileSystem.createDirectory(vaultId, directoryId, name)
    }

    fun createEmptyFile(name: String) = currentMutation { vaultId, directoryId ->
        fileSystem.createEmptyFile(vaultId, directoryId, name)
    }

    fun createSyntheticFile(name: String, size: Long) = currentMutation { vaultId, directoryId ->
        fileSystem.createSyntheticFile(vaultId, directoryId, name, size)
    }

    fun rename(node: VaultNodeMetadata, name: String, onRenamed: (VaultNodeMetadata) -> Unit = {}) = runBusy {
        val mount = state.value.mountGeneration
        fileSystem.rename(node.ref, name).fold(
            onSuccess = { updated ->
                if (state.value.mountGeneration == mount) {
                    state.update { it.withUpdatedViewerNode(updated) }
                    onRenamed(updated)
                    reload()
                }
            },
            onFailure = showError
        )
    }

    fun batchRename(items: List<VaultRenameItem>) = runBusy {
        val mount = state.value.mountGeneration
        val result = fileSystem.batchRename(items)
        if (state.value.mountGeneration == mount) {
            result.fold(
                onSuccess = { updated ->
                    state.update { current ->
                        updated.fold(current) { next, node -> next.withUpdatedViewerNode(node) }
                            .copy(selectedNodeIds = emptySet())
                    }
                    reload()
                },
                onFailure = showError
            )
        }
    }

    fun delete(nodes: List<VaultNodeMetadata>, onDeleted: (Set<String>) -> Unit = {}) = runBusy {
        val mountGeneration = state.value.mountGeneration
        val distinctNodes = nodes.distinctBy { it.ref.nodeId }
        val deletedIds = mutableSetOf<String>()
        val deletedFolders = mutableMapOf<VaultId, MutableSet<dev.qtremors.arcile.core.vault.domain.DirectoryId>>()
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
            fileSystem.deleteAndReportFolders(node.ref).fold(
                onSuccess = { folders ->
                    deletedIds += node.ref.nodeId.value
                    if (folders.isNotEmpty()) deletedFolders.getOrPut(node.ref.vaultId) { mutableSetOf() }.addAll(folders)
                },
                onFailure = { failed++ }
            )
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
        for ((vault, folders) in deletedFolders) onFoldersDeleted(vault, folders)
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
        if (state.value.mountGeneration == mountGeneration) {
            onDeleted(deletedIds)
            reload()
        }
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
                snapshot.listOptions(pageToken)
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
                        ).withCurrentMediaQueue()
                    }
                },
                onFailure = { error ->
                    state.update { it.copy(isListing = false, revealNodeId = null) }
                    showError(error)
                }
            )
        }
    }

    private fun scheduleSearchPage(pageToken: String?, append: Boolean, debounceMillis: Long = 0L) {
        val snapshot = state.value
        val vaultId = snapshot.selectedVaultId ?: return
        val directoryId = (snapshot.searchScope?.lastOrNull() ?: snapshot.currentDirectory)?.id ?: return
        val query = snapshot.searchQuery.trim()
        if (!snapshot.hasSearch || snapshot.isSearching || append && pageToken == null) return
        searchJob?.cancel()
        state.update { it.copy(isSearching = true) }
        searchJob = scope.launch {
            if (debounceMillis > 0L) delay(debounceMillis)
            val result = fileSystem.search(
                vaultId, directoryId,
                snapshot.searchOptions(pageToken)
            )
            ensureActive()
            result.fold(
                onSuccess = { page ->
                    val current = state.value
                    val activeDirectory = (current.searchScope?.lastOrNull() ?: current.currentDirectory)?.id
                    if (current.searchQuery.trim() == query && activeDirectory == directoryId) {
                        if (append && searchGeneration != page.generation) {
                            state.update { it.copy(isSearching = false, searchNextPageToken = null) }
                            scheduleSearchPage(null, false)
                            return@fold
                        }
                        searchGeneration = page.generation
                        state.update { current ->
                            val combined = (if (append) current.searchHits + page.items else page.items)
                                .distinctBy { it.metadata.ref.nodeId }
                            current.copy(
                                searchHits = combined, searchNextPageToken = page.nextPageToken,
                                selectedNodeIds = current.selectedNodeIds.intersect(
                                    combined.map { it.metadata.ref.nodeId.value }.toSet()
                                ),
                                isSearching = false
                            ).withCurrentMediaQueue()
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

    private fun OnlyFilesUiState.listOptions(pageToken: String?) = VaultListOptions(
        sortField, sortDirection, pageToken = pageToken,
        showHiddenFiles = viewOptions.showHiddenFiles,
        foldersFirst = viewOptions.foldersFirst,
        revealHiddenNodeId = revealHiddenNodeId
    )

    private fun OnlyFilesUiState.searchOptions(pageToken: String?): VaultSearchQuery {
        val filters = searchFilters
        val matcher = if (!filters.hasActiveFilters) null else {
            val cached = searchMatcher
            if (cached?.first == filters) cached.second else {
                val predicate: (VaultNodeMetadata) -> Boolean = { it.toSharedFileModel().matchesSearchFilters(filters) }
                searchMatcher = filters to predicate
                predicate
            }
        }
        return VaultSearchQuery(
            searchQuery.trim(), recursiveSearch, pageToken = pageToken, includeHidden = filters.includeHidden,
            sortField = sortField, direction = sortDirection, foldersFirst = viewOptions.foldersFirst,
            reusePageWindow = true, matches = matcher
        )
    }
}
