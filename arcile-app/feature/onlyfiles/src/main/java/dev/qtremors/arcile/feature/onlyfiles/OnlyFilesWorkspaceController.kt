package dev.qtremors.arcile.feature.onlyfiles

import dev.qtremors.arcile.core.storage.domain.SearchFilters
import dev.qtremors.arcile.core.vault.domain.DirectoryId
import dev.qtremors.arcile.core.vault.domain.VAULT_MAX_WORKSPACE_TABS
import dev.qtremors.arcile.core.vault.domain.VaultFailure
import dev.qtremors.arcile.core.vault.domain.VaultFileSystem
import dev.qtremors.arcile.core.vault.domain.VaultId
import dev.qtremors.arcile.core.vault.domain.VaultPath
import dev.qtremors.arcile.core.vault.domain.VaultSecurityPreferences
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal const val PRIMARY_VAULT_TAB_ID = 1

internal data class VaultWorkspaceLocation(
    val stack: List<VaultDirectoryCrumb> = listOf(VaultDirectoryCrumb(DirectoryId.Root, "", VaultPath.Root)),
    val query: String = "",
    val filters: SearchFilters = SearchFilters(),
    val scope: List<VaultDirectoryCrumb>? = null
) {
    val directory get() = stack.last()
}

internal data class VaultWorkspaceTab(
    val id: Int,
    val location: VaultWorkspaceLocation = VaultWorkspaceLocation(),
    val pinned: Boolean = false
) {
    val primary get() = id == PRIMARY_VAULT_TAB_ID
    val canClose get() = !primary && !pinned
}

internal data class VaultWorkspace(
    val tabs: List<VaultWorkspaceTab> = emptyList(),
    val activeTabId: Int = PRIMARY_VAULT_TAB_ID,
    val nextTabId: Int = PRIMARY_VAULT_TAB_ID + 1,
    val switching: Boolean = false,
    val restoringPins: Boolean = false,
    val pinsLoaded: Boolean = false
) {
    fun capture(state: OnlyFilesUiState): VaultWorkspace {
        if (state.currentDirectory == null) return this
        return copy(tabs = tabs.map { tab ->
            if (tab.id == activeTabId) tab.copy(
                location = VaultWorkspaceLocation(state.directoryStack, state.searchQuery, state.searchFilters, state.searchScope)
            ) else tab
        })
    }

    fun moveIndex(tabId: Int, offset: Int): Int? {
        val index = tabs.indexOfFirst { it.id == tabId }
        val target = index + offset
        return target.takeIf {
            offset in listOf(-1, 1) && index > 0 && target in 1..tabs.lastIndex &&
                tabs[index].pinned == tabs[target].pinned
        }
    }

    fun withoutOtherTabs(tabId: Int) = tabs.filter { it.id == tabId || !it.canClose }

    companion object {
        fun mounted() = VaultWorkspace(tabs = listOf(VaultWorkspaceTab(PRIMARY_VAULT_TAB_ID)))
    }
}

internal class OnlyFilesWorkspaceController(
    private val fileSystem: VaultFileSystem,
    private val preferences: VaultSecurityPreferences,
    private val state: MutableStateFlow<OnlyFilesUiState>,
    private val scope: CoroutineScope,
    private val stopListing: () -> Unit,
    private val reload: () -> Unit,
    private val showError: (Throwable) -> Unit
) {
    private var switchJob: Job? = null
    private var restoreJob: Job? = null

    fun clear() {
        switchJob?.cancel()
        restoreJob?.cancel()
        switchJob = null
        restoreJob = null
    }

    fun setEnabled(enabled: Boolean) = writePreference { preferences.setWorkspaceTabsEnabled(enabled) }

    fun cancelSwitch() {
        if (!state.value.workspace.switching) return
        switchJob?.cancel()
        state.update { it.copy(workspace = it.workspace.copy(switching = false)) }
        reload()
    }

    fun restorePins(directories: List<DirectoryId>) {
        val snapshot = state.value
        val vault = snapshot.selectedVaultId ?: return
        val workspace = snapshot.workspace
        if (!snapshot.workspaceTabsEnabled || workspace.tabs.isEmpty() || workspace.pinsLoaded || workspace.restoringPins) return
        if (directories.isEmpty()) {
            state.update { it.copy(workspace = it.workspace.copy(pinsLoaded = true)) }
            return
        }
        state.update { it.copy(workspace = it.workspace.copy(restoringPins = true)) }
        restoreJob = scope.launch {
            val locations = mutableListOf<VaultWorkspaceLocation>()
            var failure: Throwable? = null
            for (directory in directories.take(VAULT_MAX_WORKSPACE_TABS - 1)) {
                val result = readStack(vault, directory)
                ensureActive()
                val error = result.exceptionOrNull()
                if (error != null && error !is VaultFailure.InvalidPath) {
                    failure = error
                    break
                }
                result.getOrNull()?.let { locations += VaultWorkspaceLocation(it) }
            }
            if (!sameMount(snapshot)) return@launch
            state.update { current ->
                val active = current.workspace
                val pinned = if (failure == null) locations.mapIndexed { index, location ->
                    VaultWorkspaceTab(active.nextTabId + index, location, pinned = true)
                } else emptyList()
                current.copy(workspace = active.copy(
                    tabs = active.tabs.take(1) + pinned + active.tabs.drop(1),
                    nextTabId = active.nextTabId + pinned.size,
                    pinsLoaded = true,
                    restoringPins = false
                ))
            }
            if (failure == null && locations.size != directories.size) persistPins()
            failure?.let(showError)
        }
    }

    fun addTab() = add(null)

    fun duplicateTab(tabId: Int) = add(tabId)

    private fun add(duplicateId: Int?) {
        val current = editableWorkspace() ?: return
        if (current.tabs.size >= VAULT_MAX_WORKSPACE_TABS) return
        val anchor = duplicateId ?: current.activeTabId
        val tab = current.tabs.firstOrNull { it.id == anchor } ?: return
        val location = if (duplicateId == null) VaultWorkspaceLocation()
        else VaultWorkspaceLocation(tab.location.stack)
        val nextTab = VaultWorkspaceTab(current.nextTabId, location)
        val index = if (tab.canClose) current.tabs.indexOf(tab) + 1 else {
            current.tabs.indexOfFirst { it.canClose }.takeIf { it >= 0 } ?: current.tabs.size
        }
        val tabs = current.tabs.toMutableList().apply { add(index, nextTab) }
        activate(current.copy(tabs = tabs, nextTabId = current.nextTabId + 1), nextTab.id)
    }

    fun selectTab(tabId: Int) {
        val current = editableWorkspace() ?: return
        if (tabId != current.activeTabId && current.tabs.any { it.id == tabId }) activate(current, tabId)
    }

    fun setPinned(tabId: Int, pinned: Boolean) {
        val current = editableWorkspace() ?: return
        if (current.tabs.none { it.id == tabId && !it.primary && it.pinned != pinned }) return
        val changed = current.tabs.map { if (it.id == tabId) it.copy(pinned = pinned) else it }
        val ordered = changed.filter { it.primary } + changed.filter { it.pinned } + changed.filter { it.canClose }
        updateTabs(current.copy(tabs = ordered))
        persistPins()
    }

    fun moveTab(tabId: Int, offset: Int) {
        val current = editableWorkspace() ?: return
        val target = current.moveIndex(tabId, offset) ?: return
        val tabs = current.tabs.toMutableList()
        val tab = tabs.removeAt(tabs.indexOfFirst { it.id == tabId })
        tabs.add(target, tab)
        updateTabs(current.copy(tabs = tabs))
        if (tab.pinned) persistPins()
    }

    fun closeTab(tabId: Int) {
        val current = editableWorkspace() ?: return
        val index = current.tabs.indexOfFirst { it.id == tabId && it.canClose }
        if (index < 0) return
        val remaining = current.tabs.filterNot { it.id == tabId }
        val next = current.copy(tabs = remaining)
        if (tabId == current.activeTabId) {
            activate(next, remaining[index.coerceAtMost(remaining.lastIndex)].id)
        } else updateTabs(next)
    }

    fun closeOtherTabs(tabId: Int) {
        val current = editableWorkspace() ?: return
        if (current.tabs.none { it.id == tabId }) return
        val remaining = current.withoutOtherTabs(tabId)
        if (remaining == current.tabs) return
        val next = current.copy(tabs = remaining)
        if (remaining.none { it.id == current.activeTabId }) activate(next, tabId)
        else updateTabs(next)
    }

    fun onDirectoryChanged() {
        val current = state.value.workspace
        if (current.tabs.any { it.id == current.activeTabId && it.pinned }) persistPins()
    }

    private fun activate(workspace: VaultWorkspace, tabId: Int) {
        val snapshot = state.value
        val vault = snapshot.selectedVaultId ?: return
        val target = workspace.tabs.first { it.id == tabId }
        stopListing()
        state.update { it.copy(workspace = it.workspace.copy(switching = true), selectedNodeIds = emptySet()) }
        switchJob = scope.launch {
            val result = resolveLocation(vault, target.location)
            ensureActive()
            if (!sameMount(snapshot)) return@launch
            result.fold(
                onSuccess = { location ->
                    val updated = workspace.copy(
                        tabs = workspace.tabs.map { if (it.id == tabId) it.copy(location = location) else it },
                        activeTabId = tabId,
                        switching = false
                    )
                    state.update { it.copy(
                        workspace = updated, directoryStack = location.stack,
                        nodes = emptyList(), nextPageToken = null, searchHits = emptyList(), searchNextPageToken = null,
                        searchQuery = location.query, searchFilters = location.filters, searchScope = location.scope,
                        revealNodeId = null, revealHiddenNodeId = null, selectedNodeIds = emptySet(),
                        properties = emptyList(), isListing = false, isSearching = false
                    ) }
                    writePreference { preferences.setLastDirectory(vault, location.directory.id) }
                    if (target.pinned && location.directory.id != target.location.directory.id) persistPins()
                },
                onFailure = {
                    state.update { current -> current.copy(workspace = current.workspace.copy(switching = false)) }
                    showError(it)
                }
            )
            reload()
        }
    }

    private suspend fun resolveLocation(vault: VaultId, location: VaultWorkspaceLocation): Result<VaultWorkspaceLocation> {
        for (crumb in location.stack.asReversed()) {
            val result = readStack(vault, crumb.id)
            val error = result.exceptionOrNull()
            if (error is VaultFailure.InvalidPath) continue
            if (error != null) return Result.failure(error)
            val stack = result.getOrThrow()
            val scopeId = location.scope?.lastOrNull()?.id
            val scope = when (scopeId) {
                null -> null
                crumb.id -> stack
                else -> {
                    val scoped = readStack(vault, scopeId)
                    val failure = scoped.exceptionOrNull()
                    if (failure != null && failure !is VaultFailure.InvalidPath) return Result.failure(failure)
                    scoped.getOrNull()
                }
            }
            return Result.success(location.copy(stack = stack, scope = scope))
        }
        return Result.success(VaultWorkspaceLocation())
    }

    private suspend fun readStack(vault: VaultId, directory: DirectoryId): Result<List<VaultDirectoryCrumb>> =
        if (directory == DirectoryId.Root) Result.success(VaultWorkspaceLocation().stack)
        else fileSystem.directoryTrail(vault, directory).mapCatching { trail ->
            if (trail.lastOrNull()?.id != directory) throw VaultFailure.InvalidPath("Folder is unavailable")
            trail.toCrumbs()
        }

    private fun editableWorkspace(): VaultWorkspace? {
        val current = state.value
        if (current.selectedVaultId == null || !current.workspaceTabsEnabled || current.busy ||
            current.isSelectingRows || current.workspace.switching || current.workspace.restoringPins
        ) return null
        return current.workspace.capture(current)
    }

    private fun sameMount(snapshot: OnlyFilesUiState): Boolean = state.value.let {
        it.selectedVaultId == snapshot.selectedVaultId && it.mountGeneration == snapshot.mountGeneration
    }

    private fun updateTabs(workspace: VaultWorkspace) = state.update { it.copy(workspace = workspace) }

    private fun persistPins() {
        val current = state.value
        val vault = current.selectedVaultId ?: return
        val directories = current.workspace.capture(current).tabs.filter(VaultWorkspaceTab::pinned)
            .map { it.location.directory.id }
        writePreference { preferences.setPinnedWorkspaceTabs(vault, directories) }
    }

    private fun writePreference(action: suspend () -> Unit) {
        val snapshot = state.value
        scope.launch {
            try {
                action()
            } catch (error: IOException) {
                if (sameMount(snapshot)) showError(error)
            }
        }
    }
}
