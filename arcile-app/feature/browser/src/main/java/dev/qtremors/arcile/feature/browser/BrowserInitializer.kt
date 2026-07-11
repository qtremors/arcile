package dev.qtremors.arcile.feature.browser

import dev.qtremors.arcile.core.presentation.UiText
import dev.qtremors.arcile.core.storage.domain.StorageBrowserLocation
import dev.qtremors.arcile.core.storage.domain.usecase.GetStorageVolumesUseCase
import dev.qtremors.arcile.core.ui.R
import dev.qtremors.arcile.feature.browser.delegate.BrowserNavigationController
import dev.qtremors.arcile.feature.browser.delegate.openArchive
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

internal class BrowserInitializer(
    private val scope: CoroutineScope,
    private val getStorageVolumes: GetStorageVolumesUseCase,
    private val navigation: BrowserNavigationController
) {
    private val _state = MutableStateFlow<BrowserInitializationState>(
        BrowserInitializationState.Uninitialized
    )
    val state: StateFlow<BrowserInitializationState> = _state

    private var hasObservedVolumes = false
    private var initializationRequested = false
    private var initializationStarted = false
    private var pendingInitialRequest: BrowserEntryRequest? = null
    private var queuedEntryRequest: BrowserEntryRequest? = null
    private var lastHandledEntryRequestId: Long? = null
    private var volumeObservationJob: Job? = null

    init {
        observeStorageVolumes()
    }

    fun initialize(entryRequest: BrowserEntryRequest?) {
        if (!initializationRequested) {
            initializationRequested = true
            pendingInitialRequest = entryRequest
            _state.value = BrowserInitializationState.Restoring
            tryStartInitialization()
            return
        }

        if (entryRequest == null || entryRequest.id == lastHandledEntryRequestId) return
        if (state.value == BrowserInitializationState.Ready) {
            handleEntryRequest(entryRequest)
        } else {
            queuedEntryRequest = entryRequest
        }
    }

    fun retry() {
        if (state.value !is BrowserInitializationState.Failed) return
        initializationStarted = false
        _state.value = BrowserInitializationState.Restoring
        if (!hasObservedVolumes) observeStorageVolumes()
        tryStartInitialization()
    }

    private fun observeStorageVolumes() {
        volumeObservationJob?.cancel()
        volumeObservationJob = scope.launch {
            try {
                getStorageVolumes().collectLatest { volumes ->
                    hasObservedVolumes = true
                    navigation.setStorageVolumes(volumes)
                    if (!initializationStarted) {
                        tryStartInitialization()
                    } else if (state.value == BrowserInitializationState.Ready) {
                        val currentVolumeId = navigation.state.value.currentVolumeId
                        if (currentVolumeId != null && volumes.none { it.id == currentVolumeId }) {
                            navigation.openVolumeRoots(
                                UiText.StringResource(R.string.error_selected_storage_removed)
                            )
                        } else if (navigation.state.value.isVolumeRootScreen) {
                            navigation.openVolumeRoots()
                        }
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                hasObservedVolumes = false
                initializationStarted = false
                if (initializationRequested) {
                    _state.value = BrowserInitializationState.Failed(
                        error.message?.let(UiText::Dynamic)
                            ?: UiText.StringResource(R.string.error_load_directory_failed)
                    )
                }
            }
        }
    }

    private fun tryStartInitialization() {
        if (!initializationRequested || !hasObservedVolumes || initializationStarted) return
        initializationStarted = true
        val request = pendingInitialRequest
        if (request != null) {
            lastHandledEntryRequestId = request.id
            openEntry(request.entry)
        } else {
            restoreSavedLocationOrDefault()
        }
        scope.launch {
            val restoredState = navigation.state.first { !it.isLoading }
            _state.value = restoredState.error?.let(BrowserInitializationState::Failed)
                ?: BrowserInitializationState.Ready
            if (state.value == BrowserInitializationState.Ready) {
                queuedEntryRequest?.also {
                    queuedEntryRequest = null
                    handleEntryRequest(it)
                }
            }
        }
    }

    private fun restoreSavedLocationOrDefault() {
        when (val location = navigation.restoreLocationFromState()) {
            StorageBrowserLocation.Roots -> navigation.openVolumeRoots()
            is StorageBrowserLocation.Directory -> navigation.navigateToSpecificFolder(
                location.pathScope.absolutePath,
                seedInitialPathHistory = false
            )
            is StorageBrowserLocation.Category -> navigation.navigateToCategory(
                location.categoryScope.categoryName,
                location.categoryScope.volumeId
            )
            is StorageBrowserLocation.Archive -> navigation.openArchive(
                archivePath = location.archivePath,
                entryPrefix = location.entryPrefix,
                seedHistory = false
            )
            null -> navigation.initializeFromArgs()
        }
    }

    private fun handleEntryRequest(request: BrowserEntryRequest) {
        lastHandledEntryRequestId = request.id
        openEntry(request.entry)
    }

    private fun openEntry(entry: BrowserEntry) {
        when (entry) {
            is BrowserEntry.Archive -> navigation.openArchive(entry.path)
            is BrowserEntry.Category -> navigation.navigateToCategory(entry.name, entry.volumeId)
            is BrowserEntry.Path -> navigation.navigateToSpecificFolder(
                entry.path,
                seedInitialPathHistory = entry.seedInitialPathHistory
            )
            is BrowserEntry.Root -> navigation.openFileBrowser(entry.restorePersistentLocation)
        }
    }
}
