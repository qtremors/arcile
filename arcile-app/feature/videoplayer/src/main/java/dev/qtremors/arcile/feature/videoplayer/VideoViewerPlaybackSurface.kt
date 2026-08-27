package dev.qtremors.arcile.feature.videoplayer

import android.net.Uri
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
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
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
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
import androidx.compose.runtime.mutableLongStateOf
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
import dev.qtremors.arcile.core.presentation.formatFileSize
import dev.qtremors.arcile.core.ui.rememberArcileHaptics
import dev.qtremors.arcile.core.ui.theme.LocalMarqueeFilenames
import dev.qtremors.arcile.core.ui.video.VideoPlaybackSession
import dev.qtremors.arcile.core.ui.viewer.ViewerActionHost
import dev.qtremors.arcile.core.ui.viewer.ViewerFileAction
import dev.qtremors.arcile.core.ui.viewer.ViewerSourceScope
import dev.qtremors.arcile.core.ui.viewer.rememberViewerActionController
import dev.qtremors.arcile.core.ui.image.ArchiveEntryThumbnailData
import dev.qtremors.arcile.core.ui.video.VideoPlaybackItem
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import coil.request.ImageRequest
import java.io.File
import kotlin.math.max
import kotlin.math.min
import androidx.compose.foundation.pager.PagerState

@Composable
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal fun VideoViewerPlaybackSurface(
    session: VideoPlaybackSession,
    viewModel: VideoViewerViewModel,
    state: VideoViewerState,
    displayedFiles: List<FileModel>,
    displayedPaths: List<String>,
    pagerState: PagerState,
    showMetadataSheet: Boolean,
    isDeleteDialogVisible: Boolean,
    readOnly: Boolean,
    selectionModeEnabled: Boolean,
    marqueeEnabled: Boolean,
    isBackPredicting: Boolean,
    backActionAtStart: VideoViewerBackAction?,
    backProgress: Float,
    onNavigateBack: () -> Unit,
    onShareFile: (FileModel) -> Unit,
    onOpenWith: (FileModel) -> Unit
) {
    val dismissOffsetY = remember { Animatable(0f) }
    val coroutineScope = rememberCoroutineScope()
    var isDismissing by remember { mutableStateOf(false) }
    var controlsInteractionVersion by remember { mutableLongStateOf(0L) }

    val dismissPlayer: () -> Unit = {
        if (!isDismissing) {
            isDismissing = true
            coroutineScope.launch {
                dismissOffsetY.animateTo(
                    targetValue = 2000f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioLowBouncy,
                        stiffness = Spring.StiffnessMediumLow
                    )
                )
                onNavigateBack()
            }
        }
    }

    val onDragDismiss: (Float) -> Unit = { dragAccumulator ->
        if (!isDismissing) {
            coroutineScope.launch {
                dismissOffsetY.snapTo(dragAccumulator)
            }
        }
    }

    val onDragDismissEnd: () -> Unit = {
        if (!isDismissing) {
            val currentOffset = dismissOffsetY.value
            if (currentOffset > 250f) {
                isDismissing = true
                coroutineScope.launch {
                    dismissOffsetY.animateTo(
                        targetValue = 2000f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioLowBouncy,
                            stiffness = Spring.StiffnessMediumLow
                        )
                    )
                    onNavigateBack()
                }
            } else {
                coroutineScope.launch {
                    dismissOffsetY.animateTo(
                        targetValue = 0f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioLowBouncy,
                            stiffness = Spring.StiffnessMedium
                        )
                    )
                }
            }
        }
    }

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                if (dismissOffsetY.value > 0f) {
                    translationY = dismissOffsetY.value
                    val progress = (dismissOffsetY.value / 1500f).coerceIn(0f, 1f)
                    alpha = 1f - (progress * 0.7f)
                } else if (isBackPredicting && backActionAtStart == VideoViewerBackAction.ExitViewer) {
                    val scale = 1f - (backProgress * 0.08f)
                    scaleX = scale
                    scaleY = scale
                    translationX = backProgress * 100.dp.toPx()
                    alpha = 1f - (backProgress * 0.4f)
                }
            },
        color = Color.Black
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        controlsInteractionVersion++
                    }
                }
        ) {
            val context = LocalContext.current
            val lifecycleOwner = LocalLifecycleOwner.current
            val playbackItemResolver = remember(session.items, session.files) {
                VideoPlaybackItemResolver(session)
            }
            var restoredPlaybackPath by rememberSaveable { mutableStateOf<String?>(null) }
            var restoredPlaybackPosition by rememberSaveable { mutableLongStateOf(0L) }
            val initialPlaybackPage = remember(session) {
                pagerState.currentPage.coerceIn(displayedFiles.indices)
            }
            val initialPlaybackItem = remember(
                playbackItemResolver,
                initialPlaybackPage
            ) {
                playbackItemResolver.resolve(
                    displayedFiles[initialPlaybackPage],
                    initialPlaybackPage
                )
            }
            val backgroundPlaybackAllowed = videoBackgroundPlaybackAllowed(session.securityScopeId)
            val backgroundPlayer = rememberVideoBackgroundPlayer(
                enabled = backgroundPlaybackAllowed,
                onCloseRequested = onNavigateBack
            )
            val localPlayer = remember(
                initialPlaybackItem,
                session.dataSourceFactory,
                backgroundPlaybackAllowed
            ) {
                if (backgroundPlaybackAllowed) return@remember null
                val builder = ExoPlayer.Builder(context)
                    .setAudioAttributes(videoPlaybackAudioAttributes(), true)
                    .setHandleAudioBecomingNoisy(true)
                session.dataSourceFactory?.let { dataSourceFactory ->
                    builder.setMediaSourceFactory(
                        DefaultMediaSourceFactory(context).setDataSourceFactory(dataSourceFactory)
                    )
                }
                builder.build().apply {
                    val initialPosition = restoredPlaybackPosition.takeIf {
                        restoredPlaybackPath == displayedPaths.getOrNull(initialPlaybackPage)
                    } ?: 0L
                    setMediaItem(initialPlaybackItem.mediaItem.withVideoTitle(initialPlaybackItem.title), initialPosition)
                    repeatMode = Player.REPEAT_MODE_ONE
                    playWhenReady = true
                    prepare()
                }
            }
            val player = backgroundPlayer ?: localPlayer
            var isPlaying by remember { mutableStateOf(false) }
            var playbackPosition by remember { mutableLongStateOf(0L) }
            var playbackDuration by remember { mutableLongStateOf(0L) }
            var playbackError by remember { mutableStateOf<PlaybackException?>(null) }
            var isBuffering by remember { mutableStateOf(true) }
            var playbackEnded by remember { mutableStateOf(false) }
            var renderedPath by remember(player) { mutableStateOf<String?>(null) }
            var transitionedPath by remember(player) {
                mutableStateOf(player?.currentMediaItem?.mediaId)
            }
            var isScrubbing by remember(player) { mutableStateOf(false) }
            var resumeAfterScrub by remember(player) { mutableStateOf(false) }
            VideoViewerWindowEffects(
                keepScreenOn = isPlaying || isBuffering,
                immersive = true,
                onBackgrounded = onNavigateBack.takeIf {
                    !backgroundPlaybackAllowed
                }
            )
            if (player == null) {
                LoadingIndicator(
                    color = Color.White,
                    modifier = Modifier.align(Alignment.Center)
                )
                return@Box
            }
            var resizeModeIndex by rememberSaveable(session) { mutableIntStateOf(0) }
            val resizeModes = remember {
                intArrayOf(
                    AspectRatioFrameLayout.RESIZE_MODE_FIT,
                    AspectRatioFrameLayout.RESIZE_MODE_ZOOM,
                    AspectRatioFrameLayout.RESIZE_MODE_FILL
                )
            }
            val playbackPositions = remember(session) { LinkedHashMap<String, Long>() }
            var loadedPath by remember(player) {
                mutableStateOf(
                    displayedPaths.getOrNull(initialPlaybackPage)
                        .takeUnless { backgroundPlaybackAllowed }
                )
            }

            LaunchedEffect(player, backgroundPlaybackAllowed, initialPlaybackItem, displayedPaths) {
                if (!backgroundPlaybackAllowed) return@LaunchedEffect
                val initialPath = displayedPaths.getOrNull(initialPlaybackPage)
                if (initialPath != null && player.currentMediaItem?.mediaId == initialPath) {
                    loadedPath = initialPath
                    return@LaunchedEffect
                }
                val initialPosition = restoredPlaybackPosition.takeIf {
                    restoredPlaybackPath == initialPath
                } ?: 0L
                transitionedPath = null
                player.setMediaItem(
                    initialPlaybackItem.mediaItem.withVideoTitle(initialPlaybackItem.title),
                    initialPosition
                )
                player.repeatMode = Player.REPEAT_MODE_ONE
                player.prepare()
                player.play()
                loadedPath = initialPath
            }

            DisposableEffect(player) {
                val playerListener = object : Player.Listener {
                    override fun onIsPlayingChanged(playing: Boolean) {
                        isPlaying = playing
                    }

                    override fun onPlaybackStateChanged(playbackState: Int) {
                        isBuffering = playbackState == Player.STATE_BUFFERING
                        playbackEnded = playbackState == Player.STATE_ENDED
                        playbackDuration = player.duration.takeIf { it != C.TIME_UNSET } ?: 0L
                        if (playbackState == Player.STATE_READY) playbackError = null
                    }

                    override fun onPlayerError(error: PlaybackException) {
                        playbackError = error
                        isBuffering = false
                    }

                    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                        transitionedPath = mediaItem?.mediaId
                        playbackPosition = player.currentPosition.coerceAtLeast(0L)
                        playbackDuration = player.duration.takeIf { it != C.TIME_UNSET } ?: 0L
                        playbackError = null
                    }

                    override fun onRenderedFirstFrame() {
                        renderedPath = videoRenderedPathForFirstFrame(
                            currentMediaId = player.currentMediaItem?.mediaId,
                            loadedPath = loadedPath,
                            transitionedPath = transitionedPath
                        ) ?: renderedPath
                    }
                }
                player.addListener(playerListener)
                isPlaying = player.isPlaying
                isBuffering = player.playbackState == Player.STATE_BUFFERING
                onDispose {
                    player.removeListener(playerListener)
                    if (!backgroundPlaybackAllowed) player.release()
                }
            }

            LaunchedEffect(player) {
                while (true) {
                    if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                        playbackPosition = player.currentPosition.coerceAtLeast(0L)
                        loadedPath?.let { path ->
                            restoredPlaybackPath = path
                            restoredPlaybackPosition = playbackPosition
                        }
                        playbackDuration = player.duration.takeIf { it != C.TIME_UNSET } ?: 0L
                    }
                    kotlinx.coroutines.delay(500L)
                }
            }

            // Auto-hide controls after 3 seconds of playing inactivity
            LaunchedEffect(
                isPlaying,
                state.viewerUiVisible,
                showMetadataSheet,
                isDeleteDialogVisible,
                isScrubbing,
                controlsInteractionVersion
            ) {
                if (
                    isPlaying && state.viewerUiVisible && !showMetadataSheet &&
                    !isDeleteDialogVisible && !isScrubbing
                ) {
                    kotlinx.coroutines.delay(3000L)
                    viewModel.setViewerUiVisible(false)
                }
            }

            val playbackObscured = showMetadataSheet || isDeleteDialogVisible
            var resumeAfterOverlay by remember(player) { mutableStateOf(false) }
            LaunchedEffect(playbackObscured, player) {
                if (playbackObscured) {
                    resumeAfterOverlay = player.playWhenReady
                    player.pause()
                } else if (resumeAfterOverlay) {
                    resumeAfterOverlay = false
                    player.play()
                }
            }

            LaunchedEffect(pagerState.settledPage, displayedPaths, player) {
                val targetPage = pagerState.settledPage.coerceIn(displayedFiles.indices)
                val targetPath = displayedPaths[targetPage]
                val previousPath = loadedPath
                if (previousPath != null && previousPath != targetPath) {
                    playbackPositions.putBounded(previousPath, player.currentPosition.coerceAtLeast(0L), 128)
                }
                if (videoPlaybackNeedsMediaSwitch(previousPath, targetPath)) {
                    val restoredPosition = playbackPositions[targetPath] ?: 0L
                    val targetItem = playbackItemResolver.resolve(
                        displayedFiles[targetPage],
                        targetPage
                    )
                    val shouldPlay = player.playWhenReady
                    playbackPosition = restoredPosition
                    playbackDuration = 0L
                    playbackError = null
                    playbackEnded = false
                    isBuffering = true
                    transitionedPath = null
                    player.setMediaItem(
                        targetItem.mediaItem.withVideoTitle(targetItem.title),
                        restoredPosition
                    )
                    player.prepare()
                    player.playWhenReady = shouldPlay
                    loadedPath = targetPath
                }
            }

            val settledPage = pagerState.settledPage.coerceIn(displayedFiles.indices)
            val currentFile = displayedFiles.getOrNull(settledPage)
            val currentPlaybackItem = remember(playbackItemResolver, currentFile, settledPage) {
                currentFile?.let { playbackItemResolver.resolve(it, settledPage) }
            }
            val isArchiveEntry = currentFile?.absolutePath
                ?.startsWith(ArchiveEntryThumbnailData.VIRTUAL_PREFIX) == true
            val fallbackFileActionsAllowed = session.securityScopeId == null && !isArchiveEntry
            val canOpenWith = currentPlaybackItem?.onOpenWith != null || fallbackFileActionsAllowed
            val canShare = currentPlaybackItem?.onShare != null || fallbackFileActionsAllowed
            val actionFile = currentFile?.let { file ->
                val capabilities = file.nodeRef.capabilities.copy(
                    canWrite = fallbackFileActionsAllowed && !readOnly,
                    canDelete = fallbackFileActionsAllowed && file.nodeRef.capabilities.canDelete,
                    canTrash = fallbackFileActionsAllowed && file.nodeRef.capabilities.canTrash,
                    canArchive = fallbackFileActionsAllowed && file.nodeRef.capabilities.canArchive,
                    canRename = fallbackFileActionsAllowed && !readOnly && file.nodeRef.capabilities.canRename,
                    canCopy = fallbackFileActionsAllowed && file.nodeRef.capabilities.canCopy,
                    canMove = fallbackFileActionsAllowed && !readOnly && file.nodeRef.capabilities.canMove,
                    canShare = canShare,
                    canOpenWith = canOpenWith
                )
                file.copy(nodeRef = file.nodeRef.copy(capabilities = capabilities))
            }
            val viewerActionController = rememberViewerActionController(
                currentFile = actionFile,
                sourceScope = when {
                    readOnly -> ViewerSourceScope.ManagedTrash
                    session.securityScopeId != null -> ViewerSourceScope.Vault
                    isArchiveEntry -> ViewerSourceScope.ArchiveEntry
                    else -> ViewerSourceScope.Normal
                },
                onFileRenamed = viewModel::applyViewerRename,
                onFileDeleted = viewModel::applyViewerDelete
            )
            val metadataCache = remember { mutableStateMapOf<String, VideoFileMetadata>() }
            LaunchedEffect(state.isRefreshing) {
                if (state.isRefreshing) {
                    metadataCache.clear()
                }
            }
            val currentPath = currentFile?.absolutePath
            LaunchedEffect(currentPath, state.isRefreshing, readOnly) {
                val file = currentFile ?: return@LaunchedEffect
                if (!readOnly && !state.isRefreshing && metadataCache[file.absolutePath] == null) {
                    metadataCache.putBounded(
                        file.absolutePath,
                        viewModel.readVideoMetadata(file.absolutePath, file.mimeType),
                        64
                    )
                }
            }

            val dateText = remember(currentFile) {
                currentFile?.let { formatViewerDateTime(it.lastModified) }.orEmpty()
            }
            val currentResolution = currentPath
                ?.let(metadataCache::get)
                ?.let { formatResolution(it.width, it.height) }
                .orEmpty()
            val currentSizeText = remember(currentFile, context) {
                currentFile?.size
                    ?.takeIf { it > 0L }
                    ?.let { formatFileSize(context, it) }
                    .orEmpty()
            }
            val positionText = viewerPositionLabel(settledPage, displayedFiles.size)
            var currentVideoZoomed by remember(currentPath) { mutableStateOf(false) }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                beyondViewportPageCount = 1,
                userScrollEnabled = !currentVideoZoomed
            ) { page ->
                val file = displayedFiles.getOrNull(page)
                if (file != null) {
                    val verticalPagerState = rememberPagerState(
                        initialPage = if (!readOnly && state.viewerMetadataPath == file.absolutePath) 1 else 0,
                        pageCount = { if (readOnly) 1 else 2 }
                    )

                    val isCurrentPage = settledPage == page
                    var metadata by remember(file.absolutePath) {
                        mutableStateOf<VideoFileMetadata?>(metadataCache[file.absolutePath])
                    }
                    LaunchedEffect(
                        file.absolutePath,
                        state.isRefreshing,
                        showMetadataSheet,
                        isCurrentPage,
                        readOnly
                    ) {
                        if (!readOnly && !state.isRefreshing && showMetadataSheet && isCurrentPage) {
                            val data = metadataCache[file.absolutePath]
                                ?: viewModel.readVideoMetadata(file.absolutePath, file.mimeType).also {
                                    metadataCache.putBounded(file.absolutePath, it, 64)
                                }
                            metadata = data
                        }
                    }

                    LaunchedEffect(showMetadataSheet, isCurrentPage) {
                        if (isCurrentPage) {
                            val shouldShowMetadata = state.viewerMetadataPath == file.absolutePath
                            if (shouldShowMetadata && verticalPagerState.currentPage == 0) {
                                verticalPagerState.animateScrollToPage(
                                    1,
                                    animationSpec = spring(stiffness = Spring.StiffnessLow)
                                )
                            } else if (!shouldShowMetadata && verticalPagerState.currentPage == 1) {
                                verticalPagerState.animateScrollToPage(
                                    0,
                                    animationSpec = spring(stiffness = Spring.StiffnessLow)
                                )
                            }
                        }
                    }

                    LaunchedEffect(verticalPagerState.currentPage, isCurrentPage) {
                        if (isCurrentPage) {
                            viewModel.setViewerMetadataVisible(
                                path = file.absolutePath,
                                visible = verticalPagerState.currentPage == 1
                            )
                        }
                    }

                    VerticalPager(
                        state = verticalPagerState,
                        modifier = Modifier.fillMaxSize(),
                        userScrollEnabled = !readOnly && verticalPagerState.currentPage == 1,
                        beyondViewportPageCount = 1,
                        flingBehavior = PagerDefaults.flingBehavior(
                            state = verticalPagerState,
                            snapPositionalThreshold = 0.15f
                        )
                    ) { vertPage ->
                        if (vertPage == 0) {
                            VideoPlayerItemView(
                                player = player,
                                file = file,
                                isPageFocused = isCurrentPage,
                                attachPlayerSurface = videoPlayerSurfaceCanAttach(
                                    isPageFocused = isCurrentPage,
                                    loadedPath = loadedPath,
                                    pagePath = file.absolutePath
                                ),
                                isBuffering = isCurrentPage && isBuffering && !isScrubbing,
                                showPlaceholder = isCurrentPage && renderedPath != file.absolutePath,
                                playbackError = playbackError.takeIf { isCurrentPage },
                                resizeMode = resizeModes[resizeModeIndex],
                                onTap = { viewModel.toggleViewerUi() },
                                onPlayPauseToggle = {
                                    if (player.isPlaying) {
                                        player.pause()
                                    } else {
                                        if (playbackEnded) player.seekTo(0L)
                                        player.play()
                                    }
                                },
                                onToggleMetadata = { visible ->
                                    if (visible) {
                                        viewModel.setViewerMetadataVisible(file.absolutePath, visible = true)
                                    } else {
                                        if (state.viewerMetadataPath == file.absolutePath) {
                                            viewModel.setViewerMetadataVisible(null, visible = false)
                                        } else {
                                            dismissPlayer()
                                        }
                                    }
                                },
                                onDragDismiss = onDragDismiss,
                                onDragDismissEnd = onDragDismissEnd,
                                onZoomStateChanged = { zoomed ->
                                    if (isCurrentPage) currentVideoZoomed = zoomed
                                }
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .graphicsLayer {
                                        if (isBackPredicting && backActionAtStart == VideoViewerBackAction.DismissMetadata) {
                                            translationY = backProgress * size.height
                                        }
                                    }
                            ) {
                                VideoMetadataSheet(
                                    file = file,
                                    metadata = metadata,
                                    durationMs = if (isCurrentPage) playbackDuration else 0L,
                                    onDismiss = { viewModel.setViewerMetadataVisible(null, visible = false) }
                                )
                            }
                        }
                    }
                }
            }

            VideoViewerTopChrome(
                visible = state.viewerUiVisible && !showMetadataSheet,
                currentFile = currentFile,
                positionText = positionText,
                dateText = dateText,
                resolutionText = currentResolution,
                sizeText = currentSizeText,
                marqueeEnabled = marqueeEnabled,
                showThumbnails = state.showThumbnails,
                onNavigateBack = onNavigateBack,
                modifier = Modifier.align(Alignment.TopCenter)
            )

            val chromeActions = remember(
                viewModel,
                pagerState,
                onOpenWith,
                onShareFile,
                player,
                currentPlaybackItem,
                fallbackFileActionsAllowed,
                playbackEnded
            ) {
                VideoViewerChromeActions(
                    onPageSelected = { page -> pagerState.animateScrollToPage(page) },
                    onToggleFavorite = viewModel::toggleFavorite,
                    onDelete = { path -> viewModel.requestDeleteCurrent(path) },
                    onToggleSelection = viewModel::toggleSelection,
                    onShowMetadata = { path -> viewModel.setViewerMetadataVisible(path, visible = true) },
                    onOpenWith = { file ->
                        currentPlaybackItem?.onOpenWith?.invoke()
                            ?: if (fallbackFileActionsAllowed) onOpenWith(file) else Unit
                    },
                    onShare = { file ->
                        currentPlaybackItem?.onShare?.invoke()
                            ?: if (fallbackFileActionsAllowed) onShareFile(file) else Unit
                    },
                    onPlayPauseToggle = {
                        if (player.isPlaying) {
                            player.pause()
                        } else {
                            if (playbackEnded) player.seekTo(0L)
                            player.play()
                        }
                    },
                    onSeek = { position ->
                        player.seekTo(position)
                    },
                    onScrubStateChange = { scrubbing ->
                        val transition = videoScrubPlaybackTransition(
                            wasScrubbing = isScrubbing,
                            resumeWhenFinished = resumeAfterScrub,
                            scrubbing = scrubbing,
                            isPlaying = player.isPlaying
                        )
                        isScrubbing = transition.isScrubbing
                        resumeAfterScrub = transition.resumeWhenFinished
                        if (transition.pausePlayback) player.pause()
                        if (transition.resumePlayback) player.play()
                        if (scrubbing && !state.viewerUiVisible) {
                            viewModel.setViewerUiVisible(true)
                        }
                    },
                    onResizeModeToggle = {
                        resizeModeIndex = nextVideoResizeModeIndex(resizeModeIndex)
                    },
                    onToggleThumbnails = {
                        viewModel.toggleThumbnails()
                    }
                )
            }

            VideoViewerBottomChrome(
                visible = state.viewerUiVisible && !showMetadataSheet,
                files = displayedFiles,
                currentPage = pagerState.currentPage.coerceIn(displayedFiles.indices),
                currentFile = currentFile,
                favoriteFiles = state.favoriteFiles,
                selectedFiles = state.selectedFiles,
                selectionModeEnabled = selectionModeEnabled,
                readOnly = readOnly,
                isPlaying = isPlaying,
                playbackPosition = playbackPosition,
                playbackDuration = playbackDuration,
                canOpenWith = canOpenWith,
                canShare = canShare,
                viewerActions = viewerActionController.state.allowedActions,
                onViewerAction = { action ->
                    when (action) {
                        ViewerFileAction.Share -> {
                            currentPlaybackItem?.onShare?.invoke()
                                ?: viewerActionController.onAction(action)
                        }
                        ViewerFileAction.OpenWith -> {
                            currentPlaybackItem?.onOpenWith?.invoke()
                                ?: viewerActionController.onAction(action)
                        }
                        ViewerFileAction.Properties -> currentFile?.let {
                            viewModel.setViewerMetadataVisible(it.absolutePath, visible = true)
                        }
                        else -> viewerActionController.onAction(action)
                    }
                },
                resizeModeIndex = resizeModeIndex,
                showThumbnails = state.showThumbnails,
                actions = chromeActions,
                modifier = Modifier.align(Alignment.BottomCenter)
            )
            ViewerActionHost(viewerActionController)
        }
    }
}
