package dev.qtremors.arcile.feature.browser

import androidx.lifecycle.SavedStateHandle
import dev.qtremors.arcile.core.operation.BulkFileOperationCoordinator
import dev.qtremors.arcile.core.presentation.ClipboardController
import dev.qtremors.arcile.core.storage.domain.ArchivePathResolver
import dev.qtremors.arcile.core.storage.domain.ArchiveRepository
import dev.qtremors.arcile.core.storage.domain.BrowserLocationPreferencesStore
import dev.qtremors.arcile.core.storage.domain.ClipboardRepository
import dev.qtremors.arcile.core.storage.domain.FileBrowserRepository
import dev.qtremors.arcile.core.storage.domain.FileMutationRepository
import dev.qtremors.arcile.core.storage.domain.SearchRepository
import dev.qtremors.arcile.core.storage.domain.TrashRepository
import dev.qtremors.arcile.core.storage.domain.VolumeRepository
import dev.qtremors.arcile.feature.browser.delegate.BrowserArchiveController
import dev.qtremors.arcile.feature.browser.delegate.BrowserArchiveWorkflowContext
import dev.qtremors.arcile.feature.browser.delegate.BrowserArchiveWorkflowState
import dev.qtremors.arcile.feature.browser.delegate.BrowserClipboardContext
import dev.qtremors.arcile.feature.browser.delegate.BrowserClipboardController
import dev.qtremors.arcile.feature.browser.delegate.BrowserConflictController
import dev.qtremors.arcile.feature.browser.delegate.BrowserConflictOwner
import dev.qtremors.arcile.feature.browser.delegate.BrowserConflictState
import dev.qtremors.arcile.feature.browser.delegate.BrowserDeleteWorkflowState
import dev.qtremors.arcile.feature.browser.delegate.BrowserMutationContext
import dev.qtremors.arcile.feature.browser.delegate.BrowserMutationController
import dev.qtremors.arcile.feature.browser.delegate.BrowserNavigationController
import dev.qtremors.arcile.feature.browser.delegate.BrowserOperationController
import dev.qtremors.arcile.feature.browser.delegate.BrowserPropertiesContext
import dev.qtremors.arcile.feature.browser.delegate.BrowserRevealController
import dev.qtremors.arcile.feature.browser.delegate.BrowserRevealState
import dev.qtremors.arcile.feature.browser.delegate.BrowserSearchContext
import dev.qtremors.arcile.feature.browser.delegate.BrowserSelectionContext
import dev.qtremors.arcile.feature.browser.delegate.PropertiesController
import dev.qtremors.arcile.feature.browser.delegate.SearchController
import dev.qtremors.arcile.feature.browser.delegate.SelectionController
import kotlinx.coroutines.CoroutineScope

internal data class BrowserControllerGraph(
    val navigation: BrowserNavigationController,
    val search: SearchController,
    val properties: PropertiesController,
    val selection: SelectionController,
    val conflicts: BrowserConflictController,
    val operation: BrowserOperationController,
    val clipboard: BrowserClipboardController,
    val archive: BrowserArchiveController,
    val mutation: BrowserMutationController,
    val reveal: BrowserRevealController,
    val coordinator: BrowserCoordinator
)

internal fun createBrowserControllerGraph(
    scope: CoroutineScope,
    fileBrowserRepository: FileBrowserRepository,
    fileMutationRepository: FileMutationRepository,
    searchRepository: SearchRepository,
    clipboardRepository: ClipboardRepository,
    trashRepository: TrashRepository,
    archiveRepository: ArchiveRepository,
    archivePathResolver: ArchivePathResolver,
    volumeRepository: VolumeRepository,
    browserPreferencesRepository: BrowserLocationPreferencesStore,
    savedStateHandle: SavedStateHandle,
    bulkFileCoordinator: BulkFileOperationCoordinator
): BrowserControllerGraph {
    lateinit var coordinator: BrowserCoordinator
    val navigation = BrowserNavigationController(
        initialState = BrowserNavigationState(),
        viewModelScope = scope,
        fileBrowserRepository = fileBrowserRepository,
        archiveRepository = archiveRepository,
        searchRepository = searchRepository,
        browserPreferencesRepository = browserPreferencesRepository,
        savedStateHandle = savedStateHandle,
        onLocationChanged = { coordinator.onLocationChanged() },
        onStateChange = {}
    )
    val search = SearchController(
        initialState = BrowserSearchState(),
        scope = scope,
        repository = searchRepository,
        contextProvider = {
            val current = navigation.state.value
            BrowserSearchContext(
                currentPath = current.currentPath,
                currentVolumeId = current.currentVolumeId,
                isVolumeRootScreen = current.isVolumeRootScreen,
                isCategoryScreen = current.isCategoryScreen,
                activeCategoryName = current.activeCategoryName,
                archiveFiles = current.files.takeIf { current.archiveContext != null }
            )
        },
        onStateChange = {},
        onError = navigation::setError
    )
    lateinit var selection: SelectionController
    val properties = PropertiesController(
        initialState = BrowserPropertiesState(),
        scope = scope,
        fileBrowserRepository = fileBrowserRepository,
        archiveRepository = archiveRepository,
        contextProvider = {
            val current = navigation.state.value
            BrowserPropertiesContext(
                selectedPaths = selection.state.value.selectedFiles.toList(),
                files = current.files,
                archiveContext = current.archiveContext
            )
        },
        onStateChange = {},
        onError = navigation::setError
    )
    selection = SelectionController(
        initialState = BrowserSelectionState(),
        contextProvider = {
            val current = navigation.state.value
            BrowserSelectionContext(
                isVolumeRootScreen = current.isVolumeRootScreen,
                files = current.files,
                folderStats = current.folderStatsByPath
            )
        },
        onStateChange = {},
        onSelectionChanged = properties::dismiss
    )
    val conflicts = BrowserConflictController(BrowserConflictState(), onStateChange = {})
    val clipboardPresentation = ClipboardController(clipboardRepository)
    val operation = BrowserOperationController(
        initialState = BrowserOperationState(),
        scope = scope,
        trashRepository = trashRepository,
        fileMutationRepository = fileMutationRepository,
        clipboardRepository = clipboardRepository,
        clipboardController = clipboardPresentation,
        coordinator = bulkFileCoordinator,
        onStateChange = {},
        onBusyChange = navigation::setBusy,
        onError = navigation::setError,
        refreshAction = { coordinator.refreshAfterMutation() }
    )
    val clipboard = BrowserClipboardController(
        scope = scope,
        clipboardRepository = clipboardRepository,
        clipboardController = clipboardPresentation,
        operationCoordinator = bulkFileCoordinator,
        contextProvider = {
            val current = navigation.state.value
            BrowserClipboardContext(
                archiveContext = current.archiveContext,
                currentPath = current.currentPath,
                clipboardState = operation.state.value.clipboardState,
                selectedPaths = selection.state.value.selectedFiles,
                files = current.files,
                folderStats = current.folderStatsByPath
            )
        },
        clearSelection = selection::clear,
        onConflicts = { conflicts.show(BrowserConflictOwner.PASTE, it) },
        onDismissConflicts = conflicts::dismiss,
        onBusyChange = navigation::setBusy,
        onError = navigation::setError
    )
    val archive = BrowserArchiveController(
        initialState = BrowserArchiveWorkflowState(),
        scope = scope,
        archiveRepository = archiveRepository,
        archivePathResolver = archivePathResolver,
        operationCoordinator = bulkFileCoordinator,
        contextProvider = {
            val current = navigation.state.value
            BrowserArchiveWorkflowContext(
                archiveContext = current.archiveContext,
                currentPath = current.currentPath,
                selectedPaths = selection.state.value.selectedFiles
            )
        },
        clearSelection = selection::clear,
        onStateChange = { coordinator.onArchiveWorkflowChanged(it) },
        onConflicts = { conflicts.show(BrowserConflictOwner.ARCHIVE, it) },
        onDismissConflicts = conflicts::dismiss,
        onError = navigation::setError
    )
    val mutation = BrowserMutationController(
        initialState = BrowserDeleteWorkflowState(),
        scope = scope,
        fileBrowserRepository = fileBrowserRepository,
        fileMutationRepository = fileMutationRepository,
        volumeRepository = volumeRepository,
        operationCoordinator = bulkFileCoordinator,
        contextProvider = {
            val current = navigation.state.value
            BrowserMutationContext(
                currentPath = current.currentPath,
                isVolumeRootScreen = current.isVolumeRootScreen,
                isArchive = current.archiveContext != null,
                selectedPaths = selection.state.value.selectedFiles.toList()
            )
        },
        clearSelection = selection::clear,
        onStateChange = {},
        onBusyChange = navigation::setBusy,
        onError = navigation::setError,
        onMutationCompleted = { status, undo -> coordinator.onLocalMutationCompleted(status, undo) }
    )
    val reveal = BrowserRevealController(BrowserRevealState(), onStateChange = {})
    coordinator = BrowserCoordinator(navigation, search, selection, archive, conflicts, operation)
    return BrowserControllerGraph(
        navigation,
        search,
        properties,
        selection,
        conflicts,
        operation,
        clipboard,
        archive,
        mutation,
        reveal,
        coordinator
    )
}
