package dev.qtremors.arcile.feature.audio

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import dev.qtremors.arcile.core.storage.domain.AudioTrack
import dev.qtremors.arcile.core.storage.domain.ConflictResolution
import dev.qtremors.arcile.core.storage.domain.FileListingPreferences
import dev.qtremors.arcile.core.storage.domain.ImageGalleryGrouping
import dev.qtremors.arcile.core.ui.PasteConflictDialog
import dev.qtremors.arcile.core.ui.dialogs.RenameDialog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

private enum class AudioBackAction {
    CLEAR_SELECTION,
    CLOSE_SEARCH,
    CLOSE_FOLDER,
    NAVIGATE_BACK
}

@Composable
internal fun AudioLibraryScreen(
    state: AudioLibraryState,
    playback: AudioPlaybackState,
    onNavigateBack: () -> Unit,
    onRefresh: () -> Unit,
    onQueryChange: (String) -> Unit,
    onSelectTab: (AudioLibraryTab) -> Unit,
    onSelectFolder: (AudioFolder) -> Unit,
    onClearFolderFilter: () -> Unit,
    onPresentationChange: (AudioLibraryTab, FileListingPreferences) -> Unit,
    onGroupingChange: (ImageGalleryGrouping) -> Unit,
    onShowFileDetailsChange: (Boolean) -> Unit,
    onDefaultTabChange: (AudioLibraryTab) -> Unit,
    onToggleSelection: (String) -> Unit,
    onSelectPaths: (Collection<String>) -> Unit,
    onSelectAll: () -> Unit,
    onInvertSelection: () -> Unit,
    onClearSelection: () -> Unit,
    onCopySelection: () -> Unit,
    onCutSelection: () -> Unit,
    onRenameSelection: (String) -> Unit,
    onPaste: () -> Unit,
    onCancelClipboard: () -> Unit,
    onResolvePasteConflicts: (Map<String, ConflictResolution>) -> Unit,
    onDismissPasteConflictDialog: () -> Unit,
    onClearActiveFileOperation: () -> Unit,
    onPlay: (String) -> Unit,
    onPlaySelection: (Collection<String>) -> Unit,
    onTogglePlayback: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onQueueTrack: (Int) -> Unit,
    onToggleRepeat: () -> Unit,
    onToggleShuffle: () -> Unit,
    onSeek: (Long) -> Unit,
    onExpandPlayer: () -> Unit,
    onCollapsePlayer: () -> Unit,
    onShareSelected: (List<AudioTrack>) -> Unit,
    onOpenWith: (AudioTrack) -> Unit,
    onShowContainingFolder: (AudioTrack) -> Unit,
    onClearError: () -> Unit
) {
    val currentTrack = state.tracks.firstOrNull {
        it.file.absolutePath == playback.currentMediaId
    }
    val queueTracks = remember(state.tracks, playback.queueMediaIds) {
        val byPath = state.tracks.associateBy { it.file.absolutePath }
        playback.queueMediaIds.mapNotNull(byPath::get)
    }
    val selectedTracks = remember(state.tracks, state.selectedPaths) {
        state.tracks.filter { it.file.absolutePath in state.selectedPaths }
    }
    val isSelectionMode = selectedTracks.isNotEmpty()
    var showSearchBar by rememberSaveable { mutableStateOf(state.query.isNotEmpty()) }
    var showPresentationSheet by rememberSaveable { mutableStateOf(false) }
    var showRenameDialog by rememberSaveable { mutableStateOf(false) }
    var isChromeVisible by rememberSaveable { mutableStateOf(true) }
    var backProgress by remember { mutableFloatStateOf(0f) }
    var backAction by remember { mutableStateOf<AudioBackAction?>(null) }
    val pagerState = rememberPagerState(
        initialPage = if (state.defaultTab == AudioLibraryTab.FOLDERS) 1 else 0,
        pageCount = { 2 }
    )
    val coroutineScope = rememberCoroutineScope()
    var activeAudioGridSize by remember(state.audioPresentation.gridMinCellSize) {
        mutableFloatStateOf(state.audioPresentation.gridMinCellSize)
    }
    var activeFolderGridSize by remember(state.folderPresentation.gridMinCellSize) {
        mutableFloatStateOf(state.folderPresentation.gridMinCellSize)
    }
    val nestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (available.y < -15f) {
                    isChromeVisible = false
                } else if (available.y > 15f) {
                    isChromeVisible = true
                }
                return Offset.Zero
            }
        }
    }

    LaunchedEffect(pagerState.currentPage) {
        val tab =
            if (pagerState.currentPage == 0) AudioLibraryTab.AUDIO else AudioLibraryTab.FOLDERS
        if (state.tab != tab) onSelectTab(tab)
    }
    LaunchedEffect(state.tab) {
        val page = if (state.tab == AudioLibraryTab.AUDIO) 0 else 1
        if (pagerState.currentPage != page) pagerState.animateScrollToPage(page)
    }
    LaunchedEffect(state.error, playback.error) {
        if (state.error != null || playback.error) onClearError()
    }

    PredictiveBackHandler(enabled = !state.playerExpanded) { progress ->
        backAction = when {
            isSelectionMode -> AudioBackAction.CLEAR_SELECTION
            showSearchBar -> AudioBackAction.CLOSE_SEARCH
            state.folderFilter != null -> AudioBackAction.CLOSE_FOLDER
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
                AudioBackAction.CLOSE_FOLDER -> onClearFolderFilter()
                AudioBackAction.NAVIGATE_BACK -> onNavigateBack()
            }
        } catch (_: CancellationException) {
            // Keep the current category state when a predictive gesture is cancelled.
        } finally {
            backProgress = 0f
            backAction = null
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(nestedScrollConnection)
    ) {
        val topPadding =
            WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 80.dp
        val bottomPadding =
            WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() +
                if (currentTrack == null) 104.dp else 176.dp
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    if (backAction == AudioBackAction.NAVIGATE_BACK) {
                        val scale = 1f - backProgress * 0.08f
                        scaleX = scale
                        scaleY = scale
                        translationX = backProgress * 100.dp.toPx()
                        alpha = 1f - backProgress * 0.4f
                    }
                },
            userScrollEnabled = !isSelectionMode
        ) { page ->
            val tab = if (page == 0) AudioLibraryTab.AUDIO else AudioLibraryTab.FOLDERS
            AudioLibraryPage(
                state = state,
                tab = tab,
                activeGridSize = if (tab == AudioLibraryTab.AUDIO) {
                    activeAudioGridSize
                } else {
                    activeFolderGridSize
                },
                contentPadding = PaddingValues(
                    start = 12.dp,
                    top = topPadding,
                    end = 12.dp,
                    bottom = bottomPadding
                ),
                currentMediaId = playback.currentMediaId,
                onGridSizeChange = { size ->
                    if (tab == AudioLibraryTab.AUDIO) {
                        activeAudioGridSize = size
                    } else {
                        activeFolderGridSize = size
                    }
                },
                onGridSizeFinalized = { size ->
                    val current = if (tab == AudioLibraryTab.AUDIO) {
                        state.audioPresentation
                    } else {
                        state.folderPresentation
                    }
                    onPresentationChange(tab, current.copy(gridMinCellSize = size))
                },
                onRefresh = onRefresh,
                onPlay = onPlay,
                onSelectFolder = { folder ->
                    onSelectFolder(folder)
                    coroutineScope.launch { pagerState.animateScrollToPage(0) }
                },
                onToggleSelection = onToggleSelection,
                onSelectPaths = onSelectPaths
            )
        }

        val chromeOffset by animateDpAsState(
            targetValue = if (isChromeVisible || isSelectionMode || showSearchBar) 0.dp else (-120).dp,
            animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
            label = "audioTopChromeOffset"
        )
        val chromeAlpha by animateFloatAsState(
            targetValue = if (isChromeVisible || isSelectionMode || showSearchBar) 1f else 0f,
            animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
            label = "audioTopChromeAlpha"
        )
        if (isSelectionMode) {
            AudioSelectionTopBar(
                selectedCount = selectedTracks.size,
                selectedSize = selectedTracks.sumOf { it.file.size },
                onClearSelection = onClearSelection,
                onSelectAll = onSelectAll,
                onInvertSelection = onInvertSelection,
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .graphicsLayer {
                        translationY = chromeOffset.toPx()
                        alpha = chromeAlpha
                    }
            )
        } else {
            AudioLibraryFloatingTopBar(
                state = state,
                currentTab = if (pagerState.currentPage == 0) {
                    AudioLibraryTab.AUDIO
                } else {
                    AudioLibraryTab.FOLDERS
                },
                showSearchBar = showSearchBar,
                onSearchClick = { showSearchBar = true },
                onCloseSearch = {
                    onQueryChange("")
                    showSearchBar = false
                },
                onQueryChange = onQueryChange,
                onViewSort = { showPresentationSheet = true },
                onDefaultTabChange = onDefaultTabChange,
                onSelectAll = onSelectAll,
                onNavigateBack = {
                    if (state.folderFilter != null) onClearFolderFilter() else onNavigateBack()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .graphicsLayer {
                        translationY = chromeOffset.toPx()
                        alpha = chromeAlpha
                    }
            )
        }
        AudioLibraryBottomBar(
            state = state,
            currentTab = if (pagerState.currentPage == 0) {
                AudioLibraryTab.AUDIO
            } else {
                AudioLibraryTab.FOLDERS
            },
            currentTrack = currentTrack,
            selectedTracks = selectedTracks,
            playback = playback,
            isChromeVisible = isChromeVisible,
            onSelectTab = { tab ->
                coroutineScope.launch {
                    pagerState.animateScrollToPage(
                        if (tab == AudioLibraryTab.AUDIO) 0 else 1
                    )
                }
            },
            onExpandPlayer = onExpandPlayer,
            onTogglePlayback = onTogglePlayback,
            onNext = onNext,
            onPlaySelected = {
                onPlaySelection(selectedTracks.map { it.file.absolutePath })
                onClearSelection()
            },
            onCopySelected = onCopySelection,
            onCutSelected = onCutSelection,
            onRenameSelected = { showRenameDialog = true },
            onShareSelected = {
                onShareSelected(selectedTracks)
                onClearSelection()
            },
            onOpenWith = {
                selectedTracks.singleOrNull()?.let(onOpenWith)
                onClearSelection()
            },
            onShowContainingFolder = {
                selectedTracks.singleOrNull()?.let(onShowContainingFolder)
                onClearSelection()
            },
            onPaste = onPaste,
            onCancelClipboard = onCancelClipboard,
            onClearActiveFileOperation = onClearActiveFileOperation,
            modifier = Modifier.align(Alignment.BottomCenter)
        )

        AnimatedVisibility(
            visible = state.playerExpanded && currentTrack != null,
            modifier = Modifier
                .fillMaxSize()
                .align(Alignment.Center),
            enter = fadeIn() +
                slideInVertically(initialOffsetY = { it / 3 }) +
                scaleIn(
                    initialScale = 0.86f,
                    transformOrigin = TransformOrigin(0.5f, 1f)
                ),
            exit = fadeOut() +
                slideOutVertically(targetOffsetY = { it / 3 }) +
                scaleOut(
                    targetScale = 0.86f,
                    transformOrigin = TransformOrigin(0.5f, 1f)
                )
        ) {
            currentTrack?.let { track ->
                AudioNowPlayingScreen(
                    track = track,
                    queue = queueTracks,
                    playback = playback,
                    predictiveBackEnabled = state.playerExpanded,
                    onCollapse = onCollapsePlayer,
                    onTogglePlayback = onTogglePlayback,
                    onPrevious = onPrevious,
                    onNext = onNext,
                    onQueueTrack = onQueueTrack,
                    onToggleRepeat = onToggleRepeat,
                    onToggleShuffle = onToggleShuffle,
                    onSeek = onSeek,
                    onShare = { onShareSelected(listOf(track)) },
                    onOpenWith = { onOpenWith(track) },
                    onShowContainingFolder = { onShowContainingFolder(track) }
                )
            }
        }
    }

    if (showPresentationSheet) {
        val currentTab =
            if (pagerState.currentPage == 0) AudioLibraryTab.AUDIO else AudioLibraryTab.FOLDERS
        AudioViewOptionsDialog(
            tab = currentTab,
            presentation = if (currentTab == AudioLibraryTab.AUDIO) {
                state.audioPresentation
            } else {
                state.folderPresentation
            },
            grouping = state.grouping,
            showFileDetails = state.showFileDetails,
            onApply = { presentation, grouping, showDetails ->
                onPresentationChange(currentTab, presentation)
                onGroupingChange(grouping)
                onShowFileDetailsChange(showDetails)
            },
            onDismiss = { showPresentationSheet = false }
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
}
