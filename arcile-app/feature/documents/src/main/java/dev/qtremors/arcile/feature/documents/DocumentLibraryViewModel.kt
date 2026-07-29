package dev.qtremors.arcile.feature.documents

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.qtremors.arcile.core.operation.BulkFileOperationCoordinator
import dev.qtremors.arcile.core.storage.domain.ArchivePathResolver
import dev.qtremors.arcile.core.storage.domain.BrowserLocationPreferencesStore
import dev.qtremors.arcile.core.storage.domain.ClipboardRepository
import dev.qtremors.arcile.core.storage.domain.FileCategories
import dev.qtremors.arcile.core.storage.domain.FileListingPreferences
import dev.qtremors.arcile.core.storage.domain.FileBrowserRepository
import dev.qtremors.arcile.core.storage.domain.FileMutationRepository
import dev.qtremors.arcile.core.storage.domain.FileViewMode
import dev.qtremors.arcile.core.storage.domain.SearchRepository
import dev.qtremors.arcile.core.storage.domain.SearchFilters
import dev.qtremors.arcile.core.storage.domain.StorageScope
import dev.qtremors.arcile.core.storage.domain.VolumeRepository
import dev.qtremors.arcile.core.storage.domain.normalizeStoragePath
import dev.qtremors.arcile.core.ui.category.CategoryFileActionController
import dev.qtremors.arcile.core.storage.domain.CategoryGrouping
import dev.qtremors.arcile.core.ui.category.CategoryFolderSummary
import dev.qtremors.arcile.core.storage.domain.CategoryLibraryPage
import dev.qtremors.arcile.core.storage.domain.preferenceSuffix
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
internal class DocumentLibraryViewModel @Inject constructor(
    private val searchRepository: SearchRepository,
    private val preferencesStore: BrowserLocationPreferencesStore,
    clipboardRepository: ClipboardRepository,
    fileBrowserRepository: FileBrowserRepository,
    fileMutationRepository: FileMutationRepository,
    volumeRepository: VolumeRepository,
    archivePathResolver: ArchivePathResolver,
    operationCoordinator: BulkFileOperationCoordinator,
    savedStateHandle: SavedStateHandle
) : ViewModel() {
    private val volumeId = savedStateHandle.get<String>("volumeId")?.takeIf(String::isNotBlank)
    private val _state = MutableStateFlow(DocumentLibraryState())
    val state: StateFlow<DocumentLibraryState> = _state.asStateFlow()
    private val fileActions = CategoryFileActionController(
        scope = viewModelScope,
        state = _state,
        clipboardRepository = clipboardRepository,
        fileBrowserRepository = fileBrowserRepository,
        fileMutationRepository = fileMutationRepository,
        volumeRepository = volumeRepository,
        archivePathResolver = archivePathResolver,
        operationCoordinator = operationCoordinator,
        files = DocumentLibraryState::allFiles,
        selectedPaths = DocumentLibraryState::selectedPaths,
        actionState = DocumentLibraryState::fileActions,
        withSelection = { state, selection -> state.copy(selectedPaths = selection) },
        withActions = { state, actions -> state.copy(fileActions = actions) },
        withoutPaths = { state, removed ->
            presentDocuments(
                state.copy(
                    allFiles = state.allFiles.filterNot {
                        normalizeStoragePath(it.absolutePath) in removed
                    },
                    selectedPaths = state.selectedPaths.filterNot {
                        normalizeStoragePath(it) in removed
                    }.toSet()
                )
            )
        },
        reload = ::load
    )

    init {
        viewModelScope.launch {
            clipboardRepository.clipboardState.collectLatest(fileActions::syncClipboard)
        }
        viewModelScope.launch {
            operationCoordinator.events.collect(fileActions::handleOperationEvent)
        }
        viewModelScope.launch {
            val preferencesFlow = preferencesStore.locationPreferencesFlow
            applyPreferences(preferencesFlow.first())
            load()
            preferencesFlow.drop(1).collectLatest(::applyPreferences)
        }
    }

    private fun applyPreferences(
        preferences: dev.qtremors.arcile.core.storage.domain.BrowserLocationPreferences
    ) {
        val categoryId = FileCategories.Documents.id.value
        _state.update {
            presentDocuments(
                it.copy(
                    presentation = preferences
                        .getPresentationForCategory(categoryId, CategoryLibraryPage.ITEMS)
                        .normalized(),
                    folderPresentation = preferences
                        .getPresentationForCategory(categoryId, CategoryLibraryPage.FOLDERS)
                        .normalized()
                        .copy(viewMode = FileViewMode.GRID),
                    scrollbarEnabled = preferences.scrollbarEnabled,
                    grouping = preferences.getGroupingForCategory(categoryId),
                    defaultPage = preferences.getDefaultPageForCategory(categoryId),
                    showFileDetails = preferences.getShowFileDetailsForCategory(categoryId),
                    tab = if (it.preferencesLoaded) {
                        it.tab
                    } else {
                        preferences.getDefaultPageForCategory(categoryId)
                    },
                    preferencesLoaded = true
                )
            )
        }
    }

    fun load() {
        _state.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            searchRepository.getFilesByCategory(
                StorageScope.Category(volumeId, FileCategories.Documents.storageName),
                FileCategories.Documents.storageName
            ).onSuccess { files ->
                _state.update {
                    presentDocuments(
                        it.copy(
                            allFiles = files.distinctBy { file -> file.absolutePath },
                            isLoading = false,
                            error = null
                        )
                    )
                }
            }.onFailure { error ->
                _state.update { it.copy(isLoading = false, error = error.localizedMessage) }
            }
        }
    }

    fun updateQuery(query: String) = _state.update { presentDocuments(it.copy(query = query)) }

    fun updateSearchFilters(filters: SearchFilters) =
        _state.update { presentDocuments(it.copy(searchFilters = filters)) }

    fun selectTab(tab: CategoryLibraryPage) = _state.update {
        presentDocuments(it.copy(tab = tab, folderFilter = null))
    }

    fun openFolder(folder: CategoryFolderSummary) = _state.update {
        presentDocuments(it.copy(tab = CategoryLibraryPage.FOLDERS, folderFilter = folder, query = ""))
    }

    fun clearFolderFilter() = _state.update { presentDocuments(it.copy(folderFilter = null)) }

    fun toggleSelection(path: String) = _state.update {
        val selection = it.selectedPaths.toMutableSet().apply {
            if (!add(path)) remove(path)
        }
        it.copy(selectedPaths = selection)
    }

    fun selectPaths(paths: Collection<String>) = _state.update {
        it.copy(selectedPaths = it.selectedPaths + paths)
    }

    fun clearSelection() = _state.update { it.copy(selectedPaths = emptySet()) }

    fun selectAll() = _state.update { it.copy(selectedPaths = it.files.mapTo(mutableSetOf()) { file -> file.absolutePath }) }

    fun invertSelection() = _state.update {
        val visible = it.files.mapTo(mutableSetOf()) { file -> file.absolutePath }
        it.copy(selectedPaths = visible - it.selectedPaths)
    }

    fun updatePresentation(page: CategoryLibraryPage, presentation: FileListingPreferences) {
        val normalized = presentation.normalized().let {
            if (page == CategoryLibraryPage.FOLDERS) {
                it.copy(viewMode = FileViewMode.GRID)
            } else {
                it
            }
        }
        _state.update {
            presentDocuments(
                when (page) {
                    CategoryLibraryPage.ITEMS -> it.copy(presentation = normalized)
                    CategoryLibraryPage.FOLDERS -> it.copy(folderPresentation = normalized)
                }
            )
        }
        viewModelScope.launch {
            preferencesStore.updatePathPresentation(
                path = "category_${FileCategories.Documents.id.value}_${page.preferenceSuffix}",
                presentation = normalized
            )
        }
    }

    fun updateDefaultPage(page: CategoryLibraryPage) {
        _state.update { it.copy(defaultPage = page) }
        viewModelScope.launch {
            preferencesStore.updateCategoryDefaultPage(FileCategories.Documents.id.value, page)
        }
    }

    fun updateGrouping(grouping: CategoryGrouping) {
        _state.update { it.copy(grouping = grouping) }
        viewModelScope.launch {
            preferencesStore.updateCategoryGrouping(
                FileCategories.Documents.id.value,
                grouping
            )
        }
    }

    fun updateShowFileDetails(show: Boolean) {
        _state.update { it.copy(showFileDetails = show) }
        viewModelScope.launch {
            preferencesStore.updateCategoryShowFileDetails(
                FileCategories.Documents.id.value,
                show
            )
        }
    }

    fun copySelection() = fileActions.copySelection()
    fun cutSelection() = fileActions.cutSelection()
    fun requestDelete() = fileActions.requestDelete()
    fun confirmDelete() = fileActions.confirmDelete()
    fun dismissDelete() = fileActions.dismissDelete()
    fun togglePermanentDelete() = fileActions.togglePermanentDelete()
    fun toggleShred() = fileActions.toggleShred()
    fun openProperties() = fileActions.openProperties()
    fun dismissProperties() = fileActions.dismissProperties()
    fun renameSelected(name: String) = fileActions.renameSelected(name)
    fun createZip() = fileActions.createZip()
    fun pasteToFolder(path: String) = fileActions.pasteToFolder(path)
    fun cancelClipboard() = fileActions.cancelClipboard()
    fun removeFromClipboard(path: String) = fileActions.removeFromClipboard(path)
    fun resolvePasteConflicts(resolutions: Map<String, dev.qtremors.arcile.core.storage.domain.ConflictResolution>) =
        fileActions.resolvePasteConflicts(resolutions)
    fun dismissPasteConflictDialog() = fileActions.dismissPasteConflictDialog()
    fun clearActiveOperation() = fileActions.clearActiveOperation()
    fun clearActionError() = fileActions.clearError()
    fun clearError() = _state.update { it.copy(error = null) }
}
