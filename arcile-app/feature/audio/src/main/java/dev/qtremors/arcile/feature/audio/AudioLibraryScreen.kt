package dev.qtremors.arcile.feature.audio

import dev.qtremors.arcile.core.storage.domain.CategoryLibraryPage
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import dev.qtremors.arcile.core.storage.domain.AudioTrack
import dev.qtremors.arcile.core.storage.domain.ConflictResolution
import dev.qtremors.arcile.core.storage.domain.FileListingPreferences
import dev.qtremors.arcile.core.storage.domain.CategoryGrouping
import dev.qtremors.arcile.core.storage.domain.SearchFilters
import dev.qtremors.arcile.core.presentation.UiText
import dev.qtremors.arcile.core.ui.ArcileFeedbackEvent
import dev.qtremors.arcile.core.ui.ArcileFeedbackSeverity
import dev.qtremors.arcile.core.ui.PasteConflictDialog
import dev.qtremors.arcile.core.ui.category.CategoryLibraryShell
import dev.qtremors.arcile.core.ui.category.rememberCategoryLibraryShellState
import dev.qtremors.arcile.core.ui.dialogs.ClipboardContentsDialog
import dev.qtremors.arcile.core.ui.dialogs.DeleteConfirmationDialog
import dev.qtremors.arcile.core.ui.dialogs.PropertiesDialog
import dev.qtremors.arcile.core.ui.dialogs.RenameDialog
import dev.qtremors.arcile.core.ui.rememberArcileHaptics
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

private enum class AudioBackAction {
    CLEAR_SELECTION,
    CLOSE_SEARCH,
    CLOSE_COLLECTION,
    NAVIGATE_BACK
}

@Composable
internal fun AudioLibraryScreen(
    state: AudioLibraryState,
    playback: AudioPlaybackState,
    tagEditor: AudioTagEditor,
    onNavigateBack: () -> Unit,
    onRefresh: () -> Unit,
    onQueryChange: (String) -> Unit,
    onSearchFiltersChange: (SearchFilters) -> Unit,
    onSelectTab: (CategoryLibraryPage) -> Unit,
    onSelectCollection: (AudioCollectionKind) -> Unit,
    onSelectSongFilter: (AudioSongFilter) -> Unit,
    onClearListeningHistory: () -> Unit,
    onOpenCollection: (AudioCollection) -> Unit,
    onClearCollectionFilter: () -> Unit,
    onPresentationChange: (AudioCollectionKind, FileListingPreferences) -> Unit,
    onCreatePlaylist: (String) -> Unit,
    onCreatePlaylistFromSelection: (String) -> Unit,
    onAddSelectionToPlaylist: (String) -> Unit,
    onRenamePlaylist: (String, String) -> Unit,
    onDeletePlaylist: (String) -> Unit,
    onSetPlaylistTracks: (String, List<String>) -> Unit,
    onTagEdited: (AudioTrack) -> Unit,
    onGroupingChange: (CategoryGrouping) -> Unit,
    onShowFileDetailsChange: (Boolean) -> Unit,
    onMusicOnlyChange: (Boolean) -> Unit,
    onDefaultSectionChange: (AudioCollectionKind) -> Unit,
    onToggleFavoriteSelection: () -> Unit,
    onToggleFavoriteTrack: (String) -> Unit,
    onTogglePinnedFolder: (AudioCollection) -> Unit,
    onUpdateFolderCover: (AudioCollection, String?) -> Unit,
    onToggleSelection: (String) -> Unit,
    onSelectPaths: (Collection<String>) -> Unit,
    onTogglePaths: (Collection<String>) -> Unit,
    onSelectAll: () -> Unit,
    onInvertSelection: () -> Unit,
    onClearSelection: () -> Unit,
    onCopySelection: () -> Unit,
    onCutSelection: () -> Unit,
    onRenameSelection: (String) -> Unit,
    onDeleteSelection: () -> Unit,
    onConfirmDelete: () -> Unit,
    onDismissDelete: () -> Unit,
    onTogglePermanentDelete: () -> Unit,
    onToggleShred: () -> Unit,
    onOpenProperties: () -> Unit,
    onDismissProperties: () -> Unit,
    onCreateZip: () -> Unit,
    onPaste: () -> Unit,
    onPasteToFolder: (String) -> Unit,
    onCancelClipboard: () -> Unit,
    onRemoveFromClipboard: (String) -> Unit,
    onResolvePasteConflicts: (Map<String, ConflictResolution>) -> Unit,
    onDismissPasteConflictDialog: () -> Unit,
    onPlay: (String) -> Unit,
    onPlaySelection: (Collection<String>) -> Unit,
    onShareSelected: (List<AudioTrack>, () -> Unit) -> Unit,
    onEditSelected: (List<AudioTrack>) -> Unit,
    onOpenWith: (AudioTrack) -> Unit,
    onClearError: () -> Unit,
    onFeedback: (ArcileFeedbackEvent) -> Unit
) {
    val haptics = rememberArcileHaptics()
    val miniPlayerVisible = AudioFeatureEntryPoint.categoryMiniPlayerVisible()
    val miniPlayerClearance by animateDpAsState(
        targetValue = if (miniPlayerVisible) 88.dp else 8.dp,
        animationSpec = spring(),
        label = "audio mini player clearance"
    )
    val selectedTracks = remember(state.tracks, state.selectedPaths) {
        state.tracks.filter { it.file.reference in state.selectedPaths }
    }
    val isSelectionMode = selectedTracks.isNotEmpty()
    var showSearchBar by rememberSaveable {
        mutableStateOf(state.query.isNotEmpty() || state.searchFilters.hasActiveFilters)
    }
    var showPresentationSheet by rememberSaveable { mutableStateOf(false) }
    var showRenameDialog by rememberSaveable { mutableStateOf(false) }
    var showClipboardContents by rememberSaveable { mutableStateOf(false) }
    var showPlaylistPicker by rememberSaveable { mutableStateOf(false) }
    var tagEditTrack by remember { mutableStateOf<AudioTrack?>(null) }
    var optionsTrack by remember { mutableStateOf<AudioTrack?>(null) }
    var coverFolder by remember { mutableStateOf<AudioCollection?>(null) }
    val shellState = rememberCategoryLibraryShellState()
    var backProgress by remember { mutableFloatStateOf(0f) }
    var backAction by remember { mutableStateOf<AudioBackAction?>(null) }
    val pagerState = rememberPagerState(
        initialPage = AudioCollectionKind.entries.indexOf(state.defaultSection),
        pageCount = { AudioCollectionKind.entries.size }
    )
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(pagerState.settledPage) {
        val section = AudioCollectionKind.entries[pagerState.settledPage]
        if (state.collectionKind != section) onSelectCollection(section)
    }
    LaunchedEffect(state.collectionKind) {
        val page = AudioCollectionKind.entries.indexOf(state.collectionKind)
        if (pagerState.currentPage != page) pagerState.animateScrollToPage(page)
    }
    LaunchedEffect(state.error, playback.error) {
        val error = state.error ?: if (playback.error) {
            UiText.StringResource(R.string.audio_playback_failed)
        } else {
            null
        }
        error?.let {
            haptics.error()
            onFeedback(ArcileFeedbackEvent(it, ArcileFeedbackSeverity.Error))
            onClearError()
        }
    }

    PredictiveBackHandler { progress ->
        backAction = when {
            isSelectionMode -> AudioBackAction.CLEAR_SELECTION
            showSearchBar -> AudioBackAction.CLOSE_SEARCH
            state.collectionFilter != null -> AudioBackAction.CLOSE_COLLECTION
            else -> AudioBackAction.NAVIGATE_BACK
        }
        try {
            progress.collect { event -> backProgress = event.progress }
            when (requireNotNull(backAction)) {
                AudioBackAction.CLEAR_SELECTION -> onClearSelection()
                AudioBackAction.CLOSE_SEARCH -> {
                    showSearchBar = false
                    onQueryChange("")
                }
                AudioBackAction.CLOSE_COLLECTION -> onClearCollectionFilter()
                AudioBackAction.NAVIGATE_BACK -> onNavigateBack()
            }
        } catch (_: CancellationException) {
            // Keep the current category state when a predictive gesture is cancelled.
        } finally {
            backProgress = 0f
            backAction = null
        }
    }

    CategoryLibraryShell(
            state = shellState,
            selectionMode = isSelectionMode,
            searchVisible = showSearchBar,
            exitBackProgress = if (backAction == AudioBackAction.NAVIGATE_BACK) {
                backProgress
            } else {
                0f
            },
            chromeBackProgress = if (
                backAction == AudioBackAction.CLEAR_SELECTION ||
                backAction == AudioBackAction.CLOSE_SEARCH
            ) {
                backProgress
            } else {
                0f
            },
            extraBottomContentPadding = miniPlayerClearance,
            topChrome = {
                if (isSelectionMode) {
                    AudioSelectionTopBar(
                        selectedCount = selectedTracks.size,
                        selectedSize = selectedTracks.sumOf { it.file.size },
                        onClearSelection = onClearSelection,
                        onSelectAll = onSelectAll,
                        onInvertSelection = onInvertSelection,
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    AudioLibraryFloatingTopBar(
                        state = state,
                        currentTab = if (pagerState.currentPage == 0) {
                            CategoryLibraryPage.ITEMS
                        } else {
                            CategoryLibraryPage.FOLDERS
                        },
                        showSearchBar = showSearchBar,
                        onSearchClick = {
                            shellState.revealChrome()
                            showSearchBar = true
                        },
                        onCloseSearch = {
                            onQueryChange("")
                            showSearchBar = false
                        },
                        onQueryChange = onQueryChange,
                        onSearchFiltersChange = onSearchFiltersChange,
                        onViewSort = { showPresentationSheet = true },
                        onDefaultSectionChange = onDefaultSectionChange,
                        onSelectAll = onSelectAll,
                        onNavigateBack = {
                            if (state.collectionFilter != null) {
                                onClearCollectionFilter()
                            } else {
                                onNavigateBack()
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            bottomChrome = { isChromeVisible ->
                Column(modifier = Modifier.align(Alignment.BottomCenter)) {
                    AudioFeatureEntryPoint.CategoryMiniPlayer()
                    AudioLibraryBottomBar(
                    state = state,
                    currentSection = AudioCollectionKind.entries[pagerState.currentPage],
                    selectedTracks = selectedTracks,
                    isChromeVisible = isChromeVisible,
                    selectionBackProgress = if (
                        backAction == AudioBackAction.CLEAR_SELECTION
                    ) {
                        backProgress
                    } else {
                        0f
                    },
                    onSelectSection = { section ->
                        coroutineScope.launch {
                            pagerState.animateScrollToPage(
                                AudioCollectionKind.entries.indexOf(section)
                            )
                        }
                    },
                    onPlaySelected = {
                        onPlaySelection(selectedTracks.map { it.file.reference })
                        onClearSelection()
                    },
                    onCopySelected = onCopySelection,
                    onCutSelected = onCutSelection,
                    onRenameSelected = { showRenameDialog = true },
                    onDeleteSelected = onDeleteSelection,
                    onShareSelected = {
                        onShareSelected(selectedTracks, onClearSelection)
                    },
                    onOpenProperties = onOpenProperties,
                    onCreateZip = onCreateZip,
                    onOpenWith = {
                        selectedTracks.singleOrNull()?.let(onOpenWith)
                        onClearSelection()
                    },
                    onToggleFavorite = {
                        onToggleFavoriteSelection()
                        onClearSelection()
                    },
                    onAddToPlaylist = { showPlaylistPicker = true },
                    onEditTags = { tagEditTrack = selectedTracks.singleOrNull() },
                    onEditAudio = {
                        onEditSelected(selectedTracks)
                        onClearSelection()
                    },
                    onPaste = onPaste,
                    onCancelClipboard = onCancelClipboard,
                    onShowClipboardContents = { showClipboardContents = true },
                    modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        ) { contentPadding ->
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                userScrollEnabled = !isSelectionMode
            ) { page ->
                val section = AudioCollectionKind.entries[page]
                val pageState = if (section == state.collectionKind &&
                    state.presentedCollectionKind == section
                ) state else remember(state, section) {
                    buildAudioLibraryState(state.copy(
                        collectionKind = section,
                        collectionFilter = null,
                        selectedPaths = emptySet()
                    ))
                }
                val tab = if (section == AudioCollectionKind.SONGS) {
                    CategoryLibraryPage.ITEMS
                } else CategoryLibraryPage.FOLDERS
                val showingFolderContents =
                    section == state.collectionKind && state.collectionFilter != null
                val presentationSection = if (showingFolderContents) {
                    AudioCollectionKind.SONGS
                } else section
                var activeGridSize by remember(
                    presentationSection,
                    state.presentationFor(presentationSection).gridMinCellSize
                ) { mutableFloatStateOf(state.presentationFor(presentationSection).gridMinCellSize) }
                AudioLibraryPage(
                    state = pageState,
                    tab = tab,
                    activeGridSize = activeGridSize,
                    contentPadding = contentPadding,
                    currentMediaId = playback.currentMediaId,
                    onGridSizeChange = { size -> activeGridSize = size },
                    onGridSizeFinalized = { size ->
                        onPresentationChange(
                            presentationSection,
                            state.presentationFor(presentationSection).copy(gridMinCellSize = size)
                        )
                    },
                    onRefresh = onRefresh,
                    onPlay = onPlay,
                    onTrackOptions = { optionsTrack = it },
                    onSelectSongFilter = onSelectSongFilter,
                    onClearListeningHistory = onClearListeningHistory,
                    onOpenCollection = onOpenCollection,
                    onCreatePlaylist = onCreatePlaylist,
                    onAddSelectionToPlaylist = onAddSelectionToPlaylist,
                    onRenamePlaylist = onRenamePlaylist,
                    onDeletePlaylist = onDeletePlaylist,
                    onSetPlaylistTracks = onSetPlaylistTracks,
                    onToggleSelection = onToggleSelection,
                    onSelectPaths = onSelectPaths,
                    onTogglePaths = onTogglePaths,
                    onPasteToFolder = onPasteToFolder,
                    onTogglePinnedFolder = onTogglePinnedFolder,
                    onChooseFolderCover = { coverFolder = it },
                    onResetFolderCover = { onUpdateFolderCover(it, null) },
                    modifier = Modifier.graphicsLayer {
                        if (
                            backAction == AudioBackAction.CLOSE_COLLECTION &&
                            showingFolderContents
                        ) {
                            translationX = backProgress * 120.dp.toPx()
                            alpha = 1f - backProgress * 0.5f
                        }
                    }
                )
            }
        }

    if (showPresentationSheet) {
        val selectedSection = AudioCollectionKind.entries[pagerState.currentPage]
        val presentationSection = if (state.collectionFilter != null) {
            AudioCollectionKind.SONGS
        } else selectedSection
        AudioViewOptionsSheet(
            section = presentationSection,
            presentation = state.presentationFor(presentationSection),
            grouping = state.grouping,
            showFileDetails = state.showFileDetails,
            musicOnly = state.musicOnly,
            onApply = { presentation, grouping, showDetails, musicOnly ->
                onPresentationChange(presentationSection, presentation)
                onGroupingChange(grouping)
                onShowFileDetailsChange(showDetails)
                onMusicOnlyChange(musicOnly)
            },
            onDismiss = { showPresentationSheet = false }
        )
    }
    if (showPlaylistPicker) {
        AudioAddToPlaylistDialog(
            playlists = state.playlists,
            onAdd = { id ->
                onAddSelectionToPlaylist(id)
                showPlaylistPicker = false
            },
            onCreate = { name ->
                onCreatePlaylistFromSelection(name)
                showPlaylistPicker = false
            },
            onDismiss = { showPlaylistPicker = false }
        )
    }
    tagEditTrack?.let { selected ->
        AudioMetadataEditorSheet(
            track = selected,
            editor = tagEditor,
            onSaved = { updated ->
                onTagEdited(updated)
                onClearSelection()
                tagEditTrack = null
            },
            onDismiss = { tagEditTrack = null }
        )
    }
    optionsTrack?.let { selected ->
        AudioTrackActionsSheet(
            track = selected,
            isFavorite = selected.file.reference in state.favoritePaths,
            onDismiss = { optionsTrack = null },
            onToggleFavorite = {
                onToggleFavoriteTrack(selected.file.reference)
                optionsTrack = null
            },
            onEditTags = {
                optionsTrack = null
                tagEditTrack = selected
            },
            onEditAudio = {
                optionsTrack = null
                onEditSelected(listOf(selected))
            },
            onAddToPlaylist = {
                optionsTrack = null
                onSelectPaths(listOf(selected.file.reference))
                showPlaylistPicker = true
            }
        )
    }
    coverFolder?.let { folder ->
        AudioFolderCoverDialog(
            folder = folder,
            onSelect = { trackPath ->
                onUpdateFolderCover(folder, trackPath)
                coverFolder = null
            },
            onDismiss = { coverFolder = null }
        )
    }
    if (showRenameDialog && selectedTracks.size == 1) {
        RenameDialog(
            currentName = selectedTracks.single().file.name,
            onDismiss = {
                showRenameDialog = false
                onClearSelection()
            },
            onConfirm = { newName ->
                onRenameSelection(newName)
                showRenameDialog = false
            }
        )
    }
    if (state.showPasteConflictDialog && state.pasteConflicts.isNotEmpty()) {
        PasteConflictDialog(
            conflicts = state.pasteConflicts,
            onResolve = onResolvePasteConflicts,
            onDismiss = onDismissPasteConflictDialog
        )
    }
    if (
        state.showTrashConfirmation ||
        state.showPermanentDeleteConfirmation ||
        state.showMixedDeleteExplanation
    ) {
        DeleteConfirmationDialog(
            selectedCount = state.selectedPaths.size,
            isPermanentDeleteChecked =
                state.isPermanentDeleteChecked || state.showMixedDeleteExplanation,
            isPermanentDeleteToggleEnabled =
                state.isPermanentDeleteToggleEnabled && !state.showMixedDeleteExplanation,
            onConfirm = if (state.showMixedDeleteExplanation) ({}) else onConfirmDelete,
            onDismiss = onDismissDelete,
            onTogglePermanentDelete = onTogglePermanentDelete,
            decision = state.deleteDecision,
            isShredChecked = state.isShredChecked,
            onToggleShred = onToggleShred
        )
    }
    state.clipboardState?.let { clipboard ->
        if (showClipboardContents) {
            ClipboardContentsDialog(
                state = clipboard,
                onRemoveItem = onRemoveFromClipboard,
                onDismiss = { showClipboardContents = false }
            )
        }
    }
    if (state.isPropertiesVisible) {
        PropertiesDialog(
            properties = state.properties,
            isLoading = state.isPropertiesLoading,
            onDismiss = {
                onDismissProperties()
                onClearSelection()
            }
        )
    }
}
