package dev.qtremors.arcile.feature.audio

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.qtremors.arcile.core.operation.BulkFileOperationCoordinator
import dev.qtremors.arcile.core.presentation.UiText
import dev.qtremors.arcile.core.storage.domain.ArchivePathResolver
import dev.qtremors.arcile.core.storage.domain.AudioLibraryRepository
import dev.qtremors.arcile.core.storage.domain.AudioLibraryPreferences
import dev.qtremors.arcile.core.storage.domain.CategoryLibraryPage
import dev.qtremors.arcile.core.storage.domain.AudioLibraryPreferencesStore
import dev.qtremors.arcile.core.storage.domain.ClipboardRepository
import dev.qtremors.arcile.core.storage.domain.ConflictResolution
import dev.qtremors.arcile.core.storage.domain.FileBrowserRepository
import dev.qtremors.arcile.core.storage.domain.FileListingPreferences
import dev.qtremors.arcile.core.storage.domain.FileMutationRepository
import dev.qtremors.arcile.core.storage.domain.CategoryGrouping
import dev.qtremors.arcile.core.storage.domain.StorageScope
import dev.qtremors.arcile.core.storage.domain.SearchFilters
import dev.qtremors.arcile.core.storage.domain.VolumeRepository
import dev.qtremors.arcile.core.ui.R
import kotlinx.coroutines.Job
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import java.util.UUID
import dev.qtremors.arcile.core.ui.ArcileFeedbackEvent

@HiltViewModel
@OptIn(FlowPreview::class)
internal class AudioLibraryViewModel @Inject constructor(
    private val mediaObserver: AudioMediaObserver,
    private val repository: AudioLibraryRepository,
    private val musicStore: AudioCollectionStore,
    private val listeningStore: AudioListeningStore,
    internal val tagEditor: AudioTagEditor,
    private val preferencesStore: AudioLibraryPreferencesStore,
    private val clipboardRepository: ClipboardRepository,
    private val fileBrowserRepository: FileBrowserRepository,
    private val fileMutationRepository: FileMutationRepository,
    private val volumeRepository: VolumeRepository,
    private val archivePathResolver: ArchivePathResolver,
    private val operationCoordinator: BulkFileOperationCoordinator,
    internal val playback: AudioPlaybackController,
    savedStateHandle: SavedStateHandle
) : ViewModel() {
    private val volumeId = savedStateHandle.get<String>("volumeId")?.takeIf(String::isNotBlank)
    private var loadJob: Job? = null
    private var presentationJob: Job? = null
    private var pendingPresentationState: AudioLibraryState? = null
    private var searchJob: Job? = null
    private var presentationGeneration = 0L
    private var preferencesApplied = false
    private val _state = MutableStateFlow(AudioLibraryState(musicOnly = musicStore.musicOnly()))
    val state: StateFlow<AudioLibraryState> = _state.asStateFlow()
    private val _feedbackEvents = MutableSharedFlow<ArcileFeedbackEvent>(extraBufferCapacity = 8)
    val feedbackEvents = _feedbackEvents.asSharedFlow()
    private val operationOwnerId = savedStateHandle.get<String>(OPERATION_OWNER_ID_KEY)
        ?: "audio:${UUID.randomUUID()}".also { savedStateHandle[OPERATION_OWNER_ID_KEY] = it }
    private val fileActions = AudioLibraryFileActions(
        scope = viewModelScope,
        state = _state,
        clipboardRepository = clipboardRepository,
        fileBrowserRepository = fileBrowserRepository,
        fileMutationRepository = fileMutationRepository,
        volumeRepository = volumeRepository,
        archivePathResolver = archivePathResolver,
        operationCoordinator = operationCoordinator,
        operationOwnerId = operationOwnerId,
        playback = playback,
        reload = { load(refresh = true) },
        onFileRenamed = { oldPath, newPath ->
            musicStore.replaceTrackPath(oldPath, newPath)
            listeningStore.replacePath(oldPath, newPath)
        },
        onOperationFeedback = _feedbackEvents::tryEmit,
        rebuildPresentation = ::rebuildPresentation
    )

    private val collectionActions = AudioLibraryCollectionActions(
        scope = viewModelScope,
        state = _state,
        musicStore = musicStore,
        listeningStore = listeningStore,
        preferencesStore = preferencesStore,
        clearSelection = fileActions::clearSelection,
        rebuildPresentation = ::rebuildPresentation,
        onError = ::showMusicError
    )

    init {
        viewModelScope.launch {
            musicStore.playlists.collectLatest { playlists ->
                rebuildPresentation { it.copy(playlists = playlists) }
            }
        }
        viewModelScope.launch {
            listeningStore.favoritePaths.collectLatest { favorites ->
                if (_state.value.favoritePaths != favorites) {
                    rebuildPresentation { it.copy(favoritePaths = favorites) }
                }
            }
        }
        viewModelScope.launch {
            listeningStore.trackRecords.collectLatest { records ->
                val counts = records.filter { it.playCount > 0 }.associate { it.path to it.playCount }
                val recent = records.filter { it.lastPlayedAt > 0L }
                    .associate { it.path to it.lastPlayedAt }
                if (_state.value.playCounts != counts || _state.value.lastPlayedAt != recent) {
                    rebuildPresentation { it.copy(playCounts = counts, lastPlayedAt = recent) }
                }
            }
        }
        viewModelScope.launch {
            merge(musicStore.mediaChanges, mediaObserver.changes)
                .debounce(500L)
                .collectLatest { load(refresh = true) }
        }
        viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            operationCoordinator.activeRequest.collectLatest(fileActions::syncActiveRequest)
        }
        viewModelScope.launch {
            clipboardRepository.clipboardState.collectLatest { clipboard ->
                _state.update { it.copy(clipboardState = clipboard) }
            }
        }
        viewModelScope.launch {
            operationCoordinator.events.collect(fileActions::handleOperationEvent)
        }
        viewModelScope.launch {
            val preferencesFlow = preferencesStore.audioLibraryPreferencesFlow
            applyPreferences(preferencesFlow.first())
            load()
            preferencesFlow.drop(1).collectLatest(::applyPreferences)
        }
    }

    private fun applyPreferences(preferences: AudioLibraryPreferences) {
        rebuildPresentation { current ->
            val defaultPage = preferences.defaultPage
            val defaultSection = musicStore.defaultSection(
                if (defaultPage == CategoryLibraryPage.ITEMS) AudioCollectionKind.SONGS
                else AudioCollectionKind.FOLDERS
            )
            current.copy(
                audioPresentation = preferences.audioPresentation,
                collectionPresentation = preferences.collectionPresentation,
                sectionPresentations = AudioCollectionKind.entries
                    .filterNot { it == AudioCollectionKind.SONGS || it == AudioCollectionKind.FOLDERS }
                    .associateWith(musicStore::presentation),
                grouping = preferences.grouping,
                defaultPage = defaultPage,
                defaultSection = defaultSection,
                tab = if (preferencesApplied) current.tab else {
                    if (defaultSection == AudioCollectionKind.SONGS) CategoryLibraryPage.ITEMS
                    else CategoryLibraryPage.FOLDERS
                },
                collectionKind = if (preferencesApplied) current.collectionKind else defaultSection,
                showFileDetails = preferences.showFileDetails,
                scrollbarEnabled = preferences.scrollbarEnabled,
                pinnedFolderPaths = preferences.pinnedFolders,
                folderCoverPaths = preferences.folderCovers
            )
        }
        preferencesApplied = true
    }

    fun load(refresh: Boolean = false) {
        loadJob?.cancel()
        _state.update {
            it.copy(
                isLoading = !refresh && it.tracks.isEmpty(),
                isRefreshing = refresh,
                error = null
            )
        }
        loadJob = viewModelScope.launch {
            val scope = volumeId?.let(StorageScope::Volume) ?: StorageScope.AllStorage
            (if (_state.value.musicOnly) repository.getMusicTracks(scope)
                else repository.getTracks(scope))
                .onSuccess { tracks ->
                    val generation = ++presentationGeneration
                    presentationJob?.cancel()
                    val current = (pendingPresentationState ?: _state.value).copy(
                        isLoading = false,
                        isRefreshing = false,
                        error = null,
                        selectedPaths = _state.value.selectedPaths.intersect(
                            tracks.mapTo(mutableSetOf()) { it.file.reference }
                        )
                    )
                    pendingPresentationState = current.copy(tracks = tracks)
                    val presented = withContext(Dispatchers.Default) {
                        buildAudioLibraryState(current, tracks)
                    }
                    if (generation == presentationGeneration) {
                        pendingPresentationState = null
                        _state.update { live ->
                            presented.copy(
                                selectedPaths = live.selectedPaths.intersect(
                                    tracks.mapTo(mutableSetOf()) { it.file.reference }
                                ),
                                clipboardState = live.clipboardState,
                                activeFileOperation = live.activeFileOperation,
                                error = live.error
                            )
                        }
                    } else {
                        rebuildPresentation { it.copy(tracks = tracks, isLoading = false, isRefreshing = false) }
                    }
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            isLoading = false,
                            isRefreshing = false,
                            error = error.localizedMessage
                                ?.takeIf(String::isNotBlank)
                                ?.let(UiText::Dynamic)
                                ?: UiText.StringResource(R.string.error_load_category_failed)
                        )
                    }
                }
        }
    }

    fun updateQuery(query: String) {
        _state.update { it.copy(query = query) }
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(180L)
            rebuildPresentation { it }
        }
    }

    fun updateFavoriteSearchAliases(aliases: Set<String>) {
        val normalizedAliases = aliases
            .mapTo(linkedSetOf()) { it.trim() }
            .filterTo(linkedSetOf(), String::isNotEmpty)
        if (_state.value.favoriteSearchAliases == normalizedAliases) return
        rebuildPresentation { it.copy(favoriteSearchAliases = normalizedAliases) }
    }

    fun updateSearchFilters(filters: SearchFilters) {
        rebuildPresentation { it.copy(searchFilters = filters) }
    }

    fun selectTab(tab: CategoryLibraryPage) {
        rebuildPresentation {
            it.copy(tab = tab, collectionFilter = null,
                collectionKind = if (tab == CategoryLibraryPage.ITEMS) {
                    AudioCollectionKind.SONGS
                } else AudioCollectionKind.FOLDERS)
                .withPresentedVisibleTracks()
        }
    }

    fun selectCollection(section: AudioCollectionKind) {
        rebuildPresentation {
            it.copy(
                collectionKind = section,
                tab = if (section == AudioCollectionKind.SONGS) {
                    CategoryLibraryPage.ITEMS
                } else CategoryLibraryPage.FOLDERS,
                collectionFilter = null,
                selectedPaths = emptySet()
            )
        }
    }

    fun selectSongFilter(filter: AudioSongFilter) {
        if ((pendingPresentationState ?: _state.value).songFilter == filter) return
        rebuildPresentation { it.copy(songFilter = filter) }
    }

    fun openCollection(collection: AudioCollection) {
        _state.update { it.copy(query = "") }
        rebuildPresentation {
            it.copy(
                tab = CategoryLibraryPage.FOLDERS,
                collectionFilter = collection,
                query = ""
            ).withPresentedVisibleTracks()
        }
    }

    fun clearCollectionFilter() {
        rebuildPresentation {
            it.copy(collectionFilter = null)
                .withPresentedVisibleTracks()
        }
    }

    fun updatePresentation(tab: CategoryLibraryPage, presentation: FileListingPreferences) {
        viewModelScope.launch {
            when (tab) {
                CategoryLibraryPage.ITEMS ->
                    preferencesStore.updateAudioPresentation(presentation.normalized())
                CategoryLibraryPage.FOLDERS ->
                    preferencesStore.updateAudioCollectionPresentation(presentation.normalized())
            }
        }
        rebuildPresentation { current ->
            when (tab) {
                CategoryLibraryPage.ITEMS -> current.copy(
                    audioPresentation = presentation.normalized()
                )
                CategoryLibraryPage.FOLDERS -> current.copy(
                    collectionPresentation = presentation.normalized()
                )
            }
        }
    }

    fun updateSectionPresentation(
        section: AudioCollectionKind,
        presentation: FileListingPreferences
    ) {
        when (section) {
            AudioCollectionKind.SONGS -> updatePresentation(CategoryLibraryPage.ITEMS, presentation)
            AudioCollectionKind.FOLDERS -> updatePresentation(CategoryLibraryPage.FOLDERS, presentation)
            else -> {
                viewModelScope.launch {
                    runCatching { musicStore.savePresentation(section, presentation) }
                        .onFailure(::showMusicError)
                }
                rebuildPresentation {
                    it.copy(sectionPresentations = it.sectionPresentations +
                        (section to presentation.normalized()))
                }
            }
        }
    }

    fun createPlaylist(name: String) = collectionActions.createPlaylist(name)

    fun createPlaylistFromSelection(name: String) = collectionActions.createPlaylistFromSelection(name)

    fun addSelectionToPlaylist(id: String) = collectionActions.addSelectionToPlaylist(id)

    fun renamePlaylist(id: String, name: String) = collectionActions.renamePlaylist(id, name)

    fun deletePlaylist(id: String) = collectionActions.deletePlaylist(id)

    fun setPlaylistTracks(id: String, paths: List<String>) = collectionActions.setPlaylistTracks(id, paths)

    fun applyTagEdit(updated: dev.qtremors.arcile.core.storage.domain.AudioTrack) {
        rebuildPresentation { current ->
            current.copy(tracks = current.tracks.map { track ->
                if (track.file.reference == updated.file.reference) updated else track
            })
        }
    }

    private fun showMusicError(error: Throwable) {
        _state.update { it.copy(error = UiText.Dynamic(error.message.orEmpty())) }
    }

    fun updateGrouping(grouping: CategoryGrouping) {
        viewModelScope.launch { preferencesStore.updateAudioGrouping(grouping) }
        _state.update { it.copy(grouping = grouping) }
    }

    fun updateShowFileDetails(show: Boolean) {
        viewModelScope.launch { preferencesStore.updateAudioShowFileDetails(show) }
        _state.update { it.copy(showFileDetails = show) }
    }

    fun updateMusicOnly(enabled: Boolean) {
        if (_state.value.musicOnly == enabled) return
        _state.update { it.copy(musicOnly = enabled) }
        viewModelScope.launch {
            runCatching { musicStore.saveMusicOnly(enabled) }.onFailure(::showMusicError)
        }
        load(refresh = true)
    }

    fun updateDefaultPage(tab: CategoryLibraryPage) {
        viewModelScope.launch { preferencesStore.updateAudioDefaultPage(tab) }
        _state.update { it.copy(defaultPage = tab) }
    }

    fun updateDefaultSection(section: AudioCollectionKind) {
        viewModelScope.launch {
            runCatching { musicStore.saveDefaultSection(section) }.onFailure(::showMusicError)
        }
        _state.update { it.copy(defaultSection = section) }
    }

    fun toggleFavoriteSelection() = collectionActions.toggleFavoriteSelection()

    fun togglePinnedFolder(folder: AudioCollection) = collectionActions.togglePinnedFolder(folder)

    fun updateFolderCover(folder: AudioCollection, trackPath: String?) = collectionActions.updateFolderCover(folder, trackPath)

    fun toggleSelection(path: String) = fileActions.toggleSelection(path)
    fun selectPaths(paths: Collection<String>) = fileActions.selectPaths(paths)
    fun togglePaths(paths: Collection<String>) = fileActions.togglePaths(paths)
    fun selectAllVisible() = fileActions.selectAllVisible()
    fun invertSelection() = fileActions.invertSelection()
    fun clearSelection() = fileActions.clearSelection()
    fun copySelection(): Int = fileActions.copySelection()
    fun cutSelection(): Int = fileActions.cutSelection()
    fun removeFromClipboard(path: String) = fileActions.removeFromClipboard(path)
    fun requestDeleteSelected() = fileActions.requestDeleteSelected()
    fun confirmDeleteSelected() = fileActions.confirmDeleteSelected()
    fun dismissDeleteConfirmation() = fileActions.dismissDeleteConfirmation()
    fun togglePermanentDelete() = fileActions.togglePermanentDelete()
    fun toggleShred() = fileActions.toggleShred()
    fun openPropertiesForSelection() = fileActions.openPropertiesForSelection()
    fun dismissProperties() = fileActions.dismissProperties()
    fun pasteToCurrentFolder() = fileActions.pasteToCurrentFolder()
    fun pasteToFolder(destination: String) = fileActions.pasteToFolder(destination)
    fun resolvePasteConflicts(resolutions: Map<String, ConflictResolution>) =
        fileActions.resolvePasteConflicts(resolutions)
    fun dismissPasteConflictDialog() = fileActions.dismissPasteConflictDialog()
    fun cancelClipboard() = fileActions.cancelClipboard()
    fun clearActiveFileOperation() = fileActions.clearActiveFileOperation()
    fun renameSelected(newName: String) = fileActions.renameSelected(newName)
    fun createZipFromSelection() = fileActions.createZipFromSelection()

    fun clearError() {
        _state.update { it.copy(error = null) }
    }

    fun toggleFavoriteTrack(path: String) = collectionActions.toggleFavoriteTrack(path)

    fun clearListeningHistory() = collectionActions.clearListeningHistory()

    private fun rebuildPresentation(
        transform: (AudioLibraryState) -> AudioLibraryState
    ) {
        val generation = ++presentationGeneration
        val live = _state.value
        val base = (pendingPresentationState ?: live).copy(
            query = live.query,
            selectedPaths = live.selectedPaths,
            clipboardState = live.clipboardState,
            activeFileOperation = live.activeFileOperation,
            error = live.error
        )
        val snapshot = transform(base)
        pendingPresentationState = snapshot
        presentationJob?.cancel()
        presentationJob = viewModelScope.launch {
            val presented = withContext(Dispatchers.Default) {
                buildAudioLibraryState(snapshot)
            }
            if (generation == presentationGeneration) {
                pendingPresentationState = null
                _state.update { live ->
                    presented.copy(
                        selectedPaths = live.selectedPaths,
                        clipboardState = live.clipboardState,
                        activeFileOperation = live.activeFileOperation,
                        pasteConflicts = live.pasteConflicts,
                        pasteDestinationPath = live.pasteDestinationPath,
                        showPasteConflictDialog = live.showPasteConflictDialog,
                        error = live.error
                    )
                }
            }
        }
    }

    private companion object {
        const val OPERATION_OWNER_ID_KEY = "audioOperationOwnerId"
    }
}

internal fun AudioLibraryState.visibleSelectionPaths(): List<String> =
    if (tab == CategoryLibraryPage.ITEMS || collectionFilter != null) {
        visibleTracks.map { it.file.reference }
    } else {
        collections.flatMap { collection ->
            collection.tracks.map { it.file.reference }
        }
    }
