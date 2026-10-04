package dev.qtremors.arcile.feature.browser

import androidx.lifecycle.SavedStateHandle
import dev.qtremors.arcile.core.operation.BulkFileOperationCoordinator
import dev.qtremors.arcile.core.presentation.ClipboardController
import dev.qtremors.arcile.core.storage.domain.ArchivePathResolver
import dev.qtremors.arcile.core.storage.domain.ActivityLogStore
import dev.qtremors.arcile.core.storage.domain.ArchiveRepository
import dev.qtremors.arcile.core.storage.domain.BrowserLocationPreferencesStore
import dev.qtremors.arcile.core.storage.domain.ClipboardRepository
import dev.qtremors.arcile.core.storage.domain.FileBrowserRepository
import dev.qtremors.arcile.core.storage.domain.FileMutationRepository
import dev.qtremors.arcile.core.storage.domain.SearchRepository
import dev.qtremors.arcile.core.storage.domain.TrashRepository
import dev.qtremors.arcile.core.storage.domain.VolumeRepository
import dev.qtremors.arcile.feature.browser.controller.BrowserArchiveController
import dev.qtremors.arcile.feature.browser.controller.BrowserArchiveWorkflowContext
import dev.qtremors.arcile.feature.browser.controller.BrowserArchiveWorkflowState
import dev.qtremors.arcile.feature.browser.controller.BrowserClipboardContext
import dev.qtremors.arcile.feature.browser.controller.BrowserClipboardController
import dev.qtremors.arcile.feature.browser.controller.BrowserConflictController
import dev.qtremors.arcile.feature.browser.controller.BrowserConflictOwner
import dev.qtremors.arcile.feature.browser.controller.BrowserConflictState
import dev.qtremors.arcile.feature.browser.controller.BrowserDeleteWorkflowState
import dev.qtremors.arcile.feature.browser.controller.BrowserMutationContext
import dev.qtremors.arcile.feature.browser.controller.BrowserMutationController
import dev.qtremors.arcile.feature.browser.navigation.BrowserNavigationController
import dev.qtremors.arcile.feature.browser.controller.BrowserOperationController
import dev.qtremors.arcile.feature.browser.controller.BrowserPropertiesContext
import dev.qtremors.arcile.feature.browser.controller.BrowserRevealController
import dev.qtremors.arcile.feature.browser.controller.BrowserBusySource
import dev.qtremors.arcile.feature.browser.controller.BrowserTransientController
import dev.qtremors.arcile.feature.browser.controller.BrowserRevealState
import dev.qtremors.arcile.feature.browser.controller.BrowserSearchContext
import dev.qtremors.arcile.feature.browser.controller.BrowserSelectionContext
import dev.qtremors.arcile.feature.browser.controller.PropertiesController
import dev.qtremors.arcile.feature.browser.controller.SearchController
import dev.qtremors.arcile.feature.browser.controller.SelectionController
import kotlinx.coroutines.CoroutineScope

internal data class BrowserControllerGraph(
    val navigation: BrowserNavigationController,
    val transient: BrowserTransientController,
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
    bulkFileCoordinator: BulkFileOperationCoordinator,
    activityLogStore: ActivityLogStore,
    operationOwnerId: String
): BrowserControllerGraph {
    lateinit var coordinator: BrowserCoordinator
    val transient = BrowserTransientController()
    val navigation = BrowserNavigationController(
        initialState = BrowserNavigationState(),
        viewModelScope = scope,
        fileBrowserRepository = fileBrowserRepository,
        archiveRepository = archiveRepository,
        searchRepository = searchRepository,
        browserPreferencesRepository = browserPreferencesRepository,
        savedStateHandle = savedStateHandle,
        onLocationChanged = { coordinator.onLocationChanged() }
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
        }
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
        onError = transient::reportError
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
        onSelectionChanged = properties::dismiss
    )
    val conflicts = BrowserConflictController(BrowserConflictState())
    val clipboardPresentation = ClipboardController(clipboardRepository)
    val operation = BrowserOperationController(
        initialState = BrowserOperationState(),
        scope = scope,
        trashRepository = trashRepository,
        fileMutationRepository = fileMutationRepository,
        clipboardRepository = clipboardRepository,
        clipboardController = clipboardPresentation,
        coordinator = bulkFileCoordinator,
        operationOwnerId = operationOwnerId,
        onBusyChange = { transient.setBusy(BrowserBusySource.OPERATION, it) },
        onError = transient::reportError,
        refreshAction = { coordinator.refreshAfterMutation() }
    )
    val clipboard = BrowserClipboardController(
        scope = scope,
        clipboardRepository = clipboardRepository,
        clipboardController = clipboardPresentation,
        operationCoordinator = bulkFileCoordinator,
        operationOwnerId = operationOwnerId,
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
        onBusyChange = { transient.setBusy(BrowserBusySource.CLIPBOARD, it) },
        onError = transient::reportError
    )
    val archive = BrowserArchiveController(
        initialState = BrowserArchiveWorkflowState(),
        scope = scope,
        archiveRepository = archiveRepository,
        archivePathResolver = archivePathResolver,
        operationCoordinator = bulkFileCoordinator,
        operationOwnerId = operationOwnerId,
        contextProvider = {
            val current = navigation.state.value
            BrowserArchiveWorkflowContext(
                archiveContext = current.archiveContext,
                currentPath = current.currentPath,
                selectedPaths = selection.state.value.selectedFiles
            )
        },
        clearSelection = selection::clear,
        onWorkflowChanged = { coordinator.onArchiveWorkflowChanged(it) },
        onConflicts = { conflicts.show(BrowserConflictOwner.ARCHIVE, it) },
        onDismissConflicts = conflicts::dismiss,
        onError = transient::reportError
    )
    val mutation = BrowserMutationController(
        initialState = BrowserDeleteWorkflowState(),
        scope = scope,
        fileBrowserRepository = fileBrowserRepository,
        fileMutationRepository = fileMutationRepository,
        volumeRepository = volumeRepository,
        operationCoordinator = bulkFileCoordinator,
        activityLogStore = activityLogStore,
        operationOwnerId = operationOwnerId,
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
        onBusyChange = { transient.setBusy(BrowserBusySource.MUTATION, it) },
        onError = transient::reportError,
        onMutationCompleted = { status, undo -> coordinator.onLocalMutationCompleted(status, undo) }
    )
    val reveal = BrowserRevealController(BrowserRevealState())
    coordinator = BrowserCoordinator(navigation, search, selection, archive, conflicts, operation)
    return BrowserControllerGraph(
        navigation,
        transient,
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
