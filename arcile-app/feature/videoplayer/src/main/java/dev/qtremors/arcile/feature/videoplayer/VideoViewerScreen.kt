package dev.qtremors.arcile.feature.videoplayer

import android.net.Uri
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import dev.qtremors.arcile.core.storage.domain.FileModel
import dev.qtremors.arcile.core.ui.R
import dev.qtremors.arcile.core.ui.dialogs.DeleteConfirmationDialog
import dev.qtremors.arcile.core.ui.metadata.ImageMetadataDetailLabels
import dev.qtremors.arcile.core.ui.metadata.ImageMetadataSections
import dev.qtremors.arcile.core.ui.metadata.formatImageFileSize
import dev.qtremors.arcile.core.ui.rememberArcileHaptics
import dev.qtremors.arcile.core.ui.theme.LocalMarqueeFilenames
import dev.qtremors.arcile.core.ui.video.VideoPlaybackSession
import dev.qtremors.arcile.core.ui.video.VideoPlaybackItem
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import coil.request.ImageRequest
import java.io.File
import kotlin.math.max
import kotlin.math.min


internal enum class VideoViewerBackAction {
    DismissDelete,
    DismissMetadata,
    ExitViewer
}

@Composable
internal fun VideoViewerScreen(
    session: VideoPlaybackSession,
    viewModel: VideoViewerViewModel,
    selectionModeEnabled: Boolean = false,
    readOnly: Boolean = false,
    onNavigateBack: () -> Unit,
    onShareFile: (FileModel) -> Unit,
    onOpenWith: (FileModel) -> Unit
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val marqueeEnabled = LocalMarqueeFilenames.current

    val externalFiles = remember(session.files) { session.files.orEmpty().distinctBy(FileModel::absolutePath) }
    val initialPath = remember(session) { videoPlaybackInitialPath(session) }
    val viewerContext = remember(initialPath, externalFiles, state.displayedFiles, state.files) {
        val matchingExternalFile = externalFiles.find {
            videoReferencesMatch(it.absolutePath, initialPath)
        }
        val targetPath = matchingExternalFile?.absolutePath ?: initialPath

        if (state.isInitialized) {
            videoViewerFileContextAfterInitialization(
                initialPath = targetPath,
                displayedFiles = state.displayedFiles,
                allFiles = state.files
            )
        } else if (externalFiles.any { it.absolutePath == targetPath }) {
            videoViewerFileContextForInitialPath(targetPath, externalFiles, externalFiles)
        } else {
            videoViewerFileContextForInitialPath(targetPath, state.displayedFiles, state.files)
        }
    }
    val displayedFiles = viewerContext.files

    val haptics = rememberArcileHaptics()
    val isDeleteDialogVisible = state.showTrashConfirmation || state.showPermanentDeleteConfirmation || state.showMixedDeleteExplanation
    val showMetadataSheet = !readOnly && state.viewerMetadataPath != null

    var backProgress by remember { mutableFloatStateOf(0f) }
    var isBackPredicting by remember { mutableStateOf(false) }
    var backActionAtStart by remember { mutableStateOf<VideoViewerBackAction?>(null) }

    PredictiveBackHandler(enabled = true) { progressFlow ->
        backActionAtStart = when {
            isDeleteDialogVisible -> VideoViewerBackAction.DismissDelete
            showMetadataSheet -> VideoViewerBackAction.DismissMetadata
            else -> VideoViewerBackAction.ExitViewer
        }
        isBackPredicting = true
        try {
            progressFlow.collect { backEvent ->
                backProgress = backEvent.progress
            }
            when (requireNotNull(backActionAtStart)) {
                VideoViewerBackAction.DismissDelete -> viewModel.dismissDeleteConfirmation()
                VideoViewerBackAction.DismissMetadata -> viewModel.setViewerMetadataVisible(null, visible = false)
                VideoViewerBackAction.ExitViewer -> onNavigateBack()
            }
        } catch (e: Exception) {
            // Cancelled
        } finally {
            isBackPredicting = false
            backProgress = 0f
            backActionAtStart = null
        }
    }

    // Auto navigate back if dataset becomes empty (e.g. after deleting all files)
    LaunchedEffect(displayedFiles.size, state.isLoading, state.isInitialized) {
        if (displayedFiles.isEmpty() && state.isInitialized && !state.isLoading) {
            onNavigateBack()
        }
    }

    if (displayedFiles.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            if (!state.isInitialized || state.isLoading) {
                CircularProgressIndicator(color = Color.White)
            } else {
                Text(
                    text = stringResource(R.string.no_results_found),
                    color = Color.White,
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }
        return
    }

    val restoredPage = remember(
        initialPath,
        state.viewerSessionInitialPath,
        state.viewerCurrentPath,
        viewerContext
    ) {
        videoViewerInitialPageForSession(
            initialPath = initialPath,
            viewerSessionInitialPath = state.viewerSessionInitialPath,
            viewerCurrentPath = state.viewerCurrentPath,
            viewerContext = viewerContext
        )
    }

    val pagerState = rememberPagerState(
        initialPage = restoredPage,
        pageCount = { displayedFiles.size }
    )

    val displayedPaths = remember(displayedFiles) {
        displayedFiles.map(FileModel::absolutePath)
    }
    LaunchedEffect(displayedPaths) {
        if (displayedFiles.isEmpty()) return@LaunchedEffect
        val matchingExternalFile = externalFiles.find {
            videoReferencesMatch(it.absolutePath, initialPath)
        }
        val targetPath = matchingExternalFile?.absolutePath ?: initialPath
        val anchoredPath = state.viewerCurrentPath ?: targetPath
        val anchoredPage = videoViewerPageAfterDatasetChange(
            anchoredPath,
            pagerState.currentPage,
            displayedFiles
        )
        if (pagerState.currentPage != anchoredPage) pagerState.scrollToPage(anchoredPage)
        if (displayedFiles[anchoredPage].absolutePath != anchoredPath) {
            viewModel.setViewerCurrentPath(displayedFiles[anchoredPage].absolutePath)
        }
    }

    LaunchedEffect(initialPath, viewerContext.initialPage, displayedFiles.size) {
        val matchingExternalFile = externalFiles.find {
            videoReferencesMatch(it.absolutePath, initialPath)
        }
        val targetPath = matchingExternalFile?.absolutePath ?: initialPath
        if (displayedFiles.isNotEmpty() && state.viewerSessionInitialPath != targetPath) {
            viewModel.startViewerSession(targetPath)
            pagerState.scrollToPage(viewerContext.initialPage.coerceIn(0, displayedFiles.lastIndex))
        }
    }

    LaunchedEffect(pagerState.settledPage, state.viewerMetadataPath) {
        displayedFiles.getOrNull(pagerState.settledPage)?.let { file ->
            viewModel.setViewerCurrentPath(file.absolutePath)
            if (
                state.viewerMetadataPath != null &&
                (readOnly || state.viewerMetadataPath != file.absolutePath)
            ) {
                viewModel.setViewerMetadataVisible(null, visible = false)
            }
        }
    }



    VideoViewerPlaybackSurface(
        session = session,
        viewModel = viewModel,
        state = state,
        displayedFiles = displayedFiles,
        displayedPaths = displayedPaths,
        pagerState = pagerState,
        showMetadataSheet = showMetadataSheet,
        isDeleteDialogVisible = isDeleteDialogVisible,
        readOnly = readOnly,
        selectionModeEnabled = selectionModeEnabled,
        marqueeEnabled = marqueeEnabled,
        isBackPredicting = isBackPredicting,
        backActionAtStart = backActionAtStart,
        backProgress = backProgress,
        onShareFile = onShareFile,
        onOpenWith = onOpenWith
    )

    if (state.showTrashConfirmation || state.showPermanentDeleteConfirmation || state.showMixedDeleteExplanation) {
        DeleteConfirmationDialog(
            selectedCount = 1,
            isPermanentDeleteChecked = state.isPermanentDeleteChecked,
            isPermanentDeleteToggleEnabled = state.isPermanentDeleteToggleEnabled,
            onConfirm = viewModel::confirmDeleteSelected,
            onDismiss = viewModel::dismissDeleteConfirmation,
            onTogglePermanentDelete = viewModel::togglePermanentDelete,
            decision = state.deleteDecision,
            isShredChecked = state.isShredChecked,
            onToggleShred = viewModel::toggleShred
        )
    }
}
