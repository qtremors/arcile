package dev.qtremors.arcile.feature.audio

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.qtremors.arcile.core.storage.domain.AudioTrack
import dev.qtremors.arcile.core.ui.ArcileGestureAxis
import dev.qtremors.arcile.core.ui.ArcileGestureDefaults
import dev.qtremors.arcile.core.ui.arcileGestureAxis
import dev.qtremors.arcile.core.ui.rememberArcileHaptics
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.max
import dev.qtremors.arcile.core.storage.domain.FileModel
import dev.qtremors.arcile.core.ui.viewer.ViewerActionHost
import dev.qtremors.arcile.core.ui.viewer.ViewerFileAction
import dev.qtremors.arcile.core.ui.viewer.ViewerSourceScope
import dev.qtremors.arcile.core.ui.viewer.rememberViewerActionController

@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalFoundationApi::class,
    ExperimentalSharedTransitionApi::class
)
@Composable
internal fun AudioNowPlayingScreen(
    track: AudioTrack,
    queue: List<AudioTrack>,
    playback: AudioPlaybackState,
    visualizerEnabled: Boolean,
    onToggleVisualizer: () -> Unit,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    predictiveBackEnabled: Boolean = true,
    onCollapse: () -> Unit,
    onTogglePlayback: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onQueueTrack: (String) -> Unit,
    onMoveQueueTrack: (String, Int) -> Unit,
    onRemoveQueueTrack: (String) -> Unit,
    onClearQueue: () -> Unit,
    onSaveQueue: (String, List<String>) -> Unit,
    onToggleRepeat: () -> Unit,
    onToggleShuffle: () -> Unit,
    onPlaybackParametersChange: (Float, Float) -> Unit,
    onSleepTimerChange: (Int?) -> Unit,
    onSeek: (Long) -> Unit,
    onShare: () -> Unit,
    onEdit: () -> Unit,
    tagEditor: AudioTagEditor,
    onTagSaved: (AudioTrack) -> Unit,
    onOpenWith: () -> Unit,
    onFileRenamed: (String, FileModel) -> Unit = { _, _ -> },
    onFileDeleted: (String) -> Unit = {}
) {
    var showMenu by remember { mutableStateOf(false) }
    var showMetadata by remember { mutableStateOf(false) }
    var showQueue by remember { mutableStateOf(false) }
    var showTags by remember { mutableStateOf(false) }
    var showLyrics by remember { mutableStateOf(false) }
    var showPlaybackSettings by remember { mutableStateOf(false) }
    var showSleepTimer by remember { mutableStateOf(false) }
    var sliderPosition by remember(playback.currentMediaId) {
        mutableFloatStateOf(playback.positionMs.toFloat())
    }
    var isSeeking by remember { mutableStateOf(false) }
    var backProgress by remember { mutableFloatStateOf(0f) }
    val dragOffset = remember { Animatable(0f) }
    val artworkOffset = remember { Animatable(0f) }
    val gestureScope = rememberCoroutineScope()
    val density = LocalDensity.current
    val screenHeightPx = LocalWindowInfo.current.containerSize.height.toFloat()
    val haptics = rememberArcileHaptics()
    val viewerActionController = rememberViewerActionController(
        currentFile = track.file,
        sourceScope = if (track.file.nodeRef.contentUri != null) {
            ViewerSourceScope.External
        } else {
            ViewerSourceScope.Normal
        },
        onFileRenamed = onFileRenamed,
        onFileDeleted = onFileDeleted
    )
    val effectiveDuration = playback.durationMs.takeIf { it > 0L } ?: track.durationMs
    DisposableEffect(visualizerEnabled) {
        AudioPlaybackSpectrum.setSpectrumVisible(visualizerEnabled)
        onDispose { AudioPlaybackSpectrum.setSpectrumVisible(false) }
    }

    val collapseThreshold = screenHeightPx * 0.14f
    val collapseProgress = max(
        (dragOffset.value / collapseThreshold).coerceIn(0f, 1f),
        backProgress
    )
    val containerModifier = with(sharedTransitionScope) {
        Modifier
            .fillMaxSize()
            .sharedBounds(
                sharedContentState = rememberSharedContentState(
                    AUDIO_PLAYER_CONTAINER_TRANSITION_KEY
                ),
                animatedVisibilityScope = animatedVisibilityScope,
                boundsTransform = { _, _ ->
                    spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessMediumLow
                    )
                }
            )
            .graphicsLayer {
                translationX = backProgress * 72.dp.toPx()
                translationY = dragOffset.value.coerceAtLeast(0f) +
                    backProgress * 48.dp.toPx()
                val scale = 1f - collapseProgress * 0.08f
                scaleX = scale
                scaleY = scale
            }
    }

    LaunchedEffect(playback.positionMs, isSeeking) {
        if (!isSeeking) sliderPosition = playback.positionMs.toFloat()
    }

    PredictiveBackHandler(enabled = predictiveBackEnabled) { progressFlow ->
        try {
            progressFlow.collect { event -> backProgress = event.progress }
            onCollapse()
        } catch (_: CancellationException) {
            // The player remains expanded when the predictive gesture is cancelled.
            backProgress = 0f
        }
    }

    Surface(
        modifier = containerModifier,
        color = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val dismissThreshold = with(density) { maxHeight.toPx() * 0.14f }
        val queueThreshold = with(density) { maxHeight.toPx() * 0.08f }
        val horizontalThreshold = with(density) { maxWidth.toPx() * 0.16f }
        val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        val playerContentHeight = maxHeight - topInset - bottomInset - 68.dp - 66.dp
        val artworkSize = minOf(
            (minOf(maxWidth, 600.dp) - 64.dp).coerceAtLeast(1.dp),
            440.dp,
            (playerContentHeight - 246.dp).coerceAtLeast(160.dp)
        )
        val needsScroll = playerContentHeight < artworkSize + 246.dp

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(queueThreshold) {
                    awaitEachGesture {
                        val down = awaitFirstDown(
                            requireUnconsumed = false,
                            pass = PointerEventPass.Initial
                        )
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) {
                                val delta = change.position - down.position
                                if (delta.y < -queueThreshold && abs(delta.y) > abs(delta.x) * 1.2f) {
                                    haptics.selectionStart()
                                    showQueue = true
                                }
                                break
                            }
                        }
                    }
                }
                .pointerInput(dismissThreshold) {
                    var totalY = 0f
                    var gestureJob: Job? = null
                    var velocityTracker = VelocityTracker()
                    detectVerticalDragGestures(
                        onDragStart = {
                            totalY = 0f
                            velocityTracker = VelocityTracker()
                        },
                        onVerticalDrag = { change, dragAmount ->
                            if (dragAmount > 0f || totalY > 0f) {
                                change.consume()
                                velocityTracker.addPosition(change.uptimeMillis, change.position)
                                totalY += dragAmount
                                val targetY = totalY.coerceAtLeast(0f)
                                gestureJob?.cancel()
                                gestureJob = gestureScope.launch {
                                    dragOffset.snapTo(targetY)
                                }
                            }
                        },
                        onDragCancel = {
                            gestureJob?.cancel()
                            gestureJob = gestureScope.launch {
                                dragOffset.springBack()
                            }
                            totalY = 0f
                        },
                        onDragEnd = {
                            val velocity = velocityTracker.calculateVelocity()
                            val resolution = resolveAudioNowPlayingGesture(
                                lockedAxis = ArcileGestureAxis.Vertical,
                                deltaX = 0f,
                                deltaY = totalY,
                                velocityX = velocity.x,
                                velocityY = velocity.y,
                                horizontalThreshold = Float.MAX_VALUE,
                                collapseThreshold = dismissThreshold,
                                queueThreshold = dismissThreshold,
                                minimumVelocity = ArcileGestureDefaults.MinimumSwipeVelocity.toPx()
                            )
                            gestureJob?.cancel()
                            if (resolution == AudioNowPlayingGesture.Collapse) {
                                onCollapse()
                            } else {
                                gestureJob = gestureScope.launch {
                                    dragOffset.springBack()
                                }
                            }
                            totalY = 0f
                        }
                    )
                }
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 600.dp)
                    .fillMaxWidth()
                    .fillMaxHeight()
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(
                        start = 32.dp,
                        top = 68.dp,
                        end = 32.dp,
                        bottom = 66.dp
                    )
                    .then(if (needsScroll) Modifier.verticalScroll(rememberScrollState()) else Modifier),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (!needsScroll) Spacer(Modifier.weight(1f))
                AudioArtwork(
                    track = track,
                    shape = RoundedCornerShape(24.dp),
                    modifier = with(sharedTransitionScope) {
                        Modifier
                            .width(artworkSize)
                            .aspectRatio(1f)
                            .sharedBounds(
                                sharedContentState = rememberSharedContentState(
                                    AUDIO_PLAYER_ARTWORK_TRANSITION_KEY
                                ),
                                animatedVisibilityScope = animatedVisibilityScope,
                                boundsTransform = { _, _ ->
                                    spring(
                                        dampingRatio = Spring.DampingRatioNoBouncy,
                                        stiffness = Spring.StiffnessMediumLow
                                    )
                                }
                            )
                            .graphicsLayer {
                                translationX = artworkOffset.value
                                val progress =
                                    (abs(artworkOffset.value) / horizontalThreshold)
                                        .coerceIn(0f, 1f)
                                scaleX = 1f - progress * 0.04f
                                scaleY = 1f - progress * 0.04f
                                alpha = 1f - progress * 0.22f
                            }
                            .combinedClickable(
                                onClick = {},
                                onLongClick = {
                                    haptics.selectionStart()
                                    showMetadata = true
                                }
                            )
                            .pointerInput(
                                track.file.reference,
                                dismissThreshold,
                                queueThreshold,
                                horizontalThreshold
                            ) {
                                var totalX = 0f
                                var totalY = 0f
                                var lockedAxis: ArcileGestureAxis? = null
                                var gestureJob: Job? = null
                                var velocityTracker = VelocityTracker()
                                detectDragGestures(
                                    onDragStart = {
                                        totalX = 0f
                                        totalY = 0f
                                        lockedAxis = null
                                        velocityTracker = VelocityTracker()
                                    },
                                    onDrag = { change, amount ->
                                        velocityTracker.addPosition(change.uptimeMillis, change.position)
                                        totalX += amount.x
                                        totalY += amount.y
                                        if (lockedAxis == null) {
                                            lockedAxis = arcileGestureAxis(
                                                deltaX = totalX,
                                                deltaY = totalY,
                                                touchSlop = viewConfiguration.touchSlop
                                            )
                                        }
                                        val axis = lockedAxis ?: return@detectDragGestures
                                        change.consume()
                                        val dragX = totalX
                                        val dragY = totalY
                                        gestureJob?.cancel()
                                        gestureJob = gestureScope.launch {
                                            if (axis == ArcileGestureAxis.Horizontal) {
                                                artworkOffset.snapTo(dragX)
                                            } else {
                                                dragOffset.snapTo(dragY.coerceAtLeast(0f))
                                            }
                                        }
                                    },
                                    onDragCancel = {
                                        gestureJob?.cancel()
                                        gestureJob = gestureScope.launch {
                                            dragOffset.springBack()
                                            artworkOffset.springBack()
                                        }
                                        totalX = 0f
                                        totalY = 0f
                                        lockedAxis = null
                                    },
                                    onDragEnd = {
                                        val finalX = totalX
                                        val finalY = totalY
                                        val velocity = velocityTracker.calculateVelocity()
                                        gestureJob?.cancel()
                                        val resolution = resolveAudioNowPlayingGesture(
                                            lockedAxis = lockedAxis,
                                            deltaX = finalX,
                                            deltaY = finalY,
                                            velocityX = velocity.x,
                                            velocityY = velocity.y,
                                            horizontalThreshold = horizontalThreshold,
                                            collapseThreshold = dismissThreshold,
                                            queueThreshold = queueThreshold,
                                            minimumVelocity = ArcileGestureDefaults.MinimumSwipeVelocity.toPx()
                                        )
                                        when (resolution) {
                                            AudioNowPlayingGesture.Next,
                                            AudioNowPlayingGesture.Previous -> {
                                                gestureJob = gestureScope.launch {
                                                    val directionSign = if (resolution == AudioNowPlayingGesture.Next) -1f else 1f
                                                    artworkOffset.animateTo(
                                                        directionSign * horizontalThreshold * 2f,
                                                        spring(stiffness = Spring.StiffnessMedium)
                                                    )
                                                    if (resolution == AudioNowPlayingGesture.Next) onNext() else onPrevious()
                                                    artworkOffset.snapTo(
                                                        -directionSign * horizontalThreshold
                                                    )
                                                    artworkOffset.springBack()
                                                }
                                            }
                                            AudioNowPlayingGesture.Collapse -> onCollapse()
                                            AudioNowPlayingGesture.Queue -> {
                                                gestureJob = gestureScope.launch { dragOffset.snapTo(0f) }
                                            }
                                            AudioNowPlayingGesture.None -> {
                                                gestureJob = gestureScope.launch {
                                                    dragOffset.springBack()
                                                    artworkOffset.springBack()
                                                }
                                            }
                                        }
                                        totalX = 0f
                                        totalY = 0f
                                        lockedAxis = null
                                    }
                                )
                            }
                    }
                )
                Spacer(Modifier.height(12.dp))
                if (!needsScroll) Spacer(Modifier.weight(1f))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AnimatedContent(
                        targetState = track,
                        transitionSpec = {
                            fadeIn(tween(220)) togetherWith fadeOut(tween(140))
                        },
                        label = "now playing track",
                        modifier = Modifier.weight(1f)
                    ) { displayedTrack ->
                        Column {
                            Text(
                                displayedTrack.displayTitle,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.basicMarquee(iterations = 1, initialDelayMillis = 3000)
                            )
                            Text(
                                displayedTrack.artist ?: stringResource(R.string.audio_unknown_artist),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.basicMarquee(iterations = 1, initialDelayMillis = 3000)
                            )
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    AudioPlayerSongActions(
                        isFavorite = isFavorite,
                        onToggleFavorite = onToggleFavorite
                    )
                }
                Spacer(Modifier.height(12.dp))
                if (!needsScroll) Spacer(Modifier.weight(1f))
                AudioPlaybackProgress(
                    positionMs = sliderPosition,
                    durationMs = effectiveDuration,
                    onPositionChange = {
                        isSeeking = true
                        sliderPosition = it
                    },
                    onPositionChangeFinished = {
                        onSeek(sliderPosition.toLong())
                        isSeeking = false
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))
                if (!needsScroll) Spacer(Modifier.weight(1f))
                AudioPlaybackControls(
                    playback = playback,
                    visualizerEnabled = visualizerEnabled,
                    onTogglePlayback = onTogglePlayback,
                    onPrevious = onPrevious,
                    onNext = onNext
                )
                if (!needsScroll) Spacer(Modifier.weight(1f))
            }

            AudioPlayerBottomActions(
                playback = playback,
                onShowMetadata = {
                    haptics.selectionStart()
                    showMetadata = true
                },
                onToggleRepeat = onToggleRepeat,
                onToggleShuffle = onToggleShuffle,
                onShowMore = {
                    haptics.selectionStart()
                    showMenu = true
                },
                onShowQueue = {
                    haptics.selectionStart()
                    showQueue = true
                },
                moreMenu = {
                    AudioPlayerMoreMenu(
                        showMenu = showMenu,
                        onDismissMenu = { showMenu = false },
                        visualizerEnabled = visualizerEnabled,
                        onToggleVisualizer = onToggleVisualizer,
                        onShowPlaybackSettings = { showPlaybackSettings = true },
                        onShowSleepTimer = { showSleepTimer = true },
                        onEdit = onEdit,
                        onEditTags = { showTags = true },
                        onShowLyrics = { showLyrics = true },
                        onOpenWith = onOpenWith,
                        onShare = onShare.takeIf { ViewerFileAction.Share in viewerActionController.state.allowedActions },
                        onCopy = {
                            viewerActionController.onAction(ViewerFileAction.Copy)
                        }.takeIf { ViewerFileAction.Copy in viewerActionController.state.allowedActions },
                        onCut = {
                            viewerActionController.onAction(ViewerFileAction.Cut)
                        }.takeIf { ViewerFileAction.Cut in viewerActionController.state.allowedActions },
                        onRename = {
                            viewerActionController.onAction(ViewerFileAction.Rename)
                        }.takeIf { ViewerFileAction.Rename in viewerActionController.state.allowedActions },
                        onDelete = {
                            viewerActionController.onAction(ViewerFileAction.Delete)
                        }.takeIf { ViewerFileAction.Delete in viewerActionController.state.allowedActions },
                        onProperties = {
                            viewerActionController.onAction(ViewerFileAction.Properties)
                        }.takeIf { ViewerFileAction.Properties in viewerActionController.state.allowedActions },
                        onArchive = {
                            viewerActionController.onAction(ViewerFileAction.CreateArchive)
                        }.takeIf { ViewerFileAction.CreateArchive in viewerActionController.state.allowedActions }
                    )
                },
                modifier = Modifier.align(Alignment.BottomCenter)
            )
            AudioPlayerTopBar(
                track = track,
                modifier = Modifier.align(Alignment.TopCenter)
            )
            ViewerActionHost(viewerActionController)
        }
    }
    }

    if (showMetadata) {
        AudioMetadataSheet(track = track, onDismiss = { showMetadata = false })
    }
    if (showQueue) {
        val queueById = queue.associateBy { it.file.reference }
        val orderedIds = playback.shuffleMediaIds.takeIf { playback.shuffleEnabled && it.isNotEmpty() }
            ?: playback.queueMediaIds
        AudioQueueSheet(
            queue = orderedIds.mapNotNull(queueById::get),
            currentMediaId = playback.currentMediaId,
            shuffleEnabled = playback.shuffleEnabled,
            onTrackClick = onQueueTrack,
            onMoveTrack = onMoveQueueTrack,
            onRemoveTrack = onRemoveQueueTrack,
            onClearQueue = onClearQueue,
            onSaveQueue = onSaveQueue,
            onDismiss = { showQueue = false }
        )
    }
    if (showTags) {
        AudioMetadataEditorSheet(
            track = track,
            editor = tagEditor,
            onSaved = { updated ->
                onTagSaved(updated)
                showTags = false
            },
            onDismiss = { showTags = false }
        )
    }
    if (showLyrics) {
        AudioLyricsDialog(
            track = track,
            positionMs = playback.positionMs,
            isPlaying = playback.isPlaying,
            editor = tagEditor,
            onSeek = onSeek,
            onTogglePlayback = onTogglePlayback,
            onEdit = {
                showLyrics = false
                showTags = true
            },
            onDismiss = { showLyrics = false }
        )
    }
    if (showPlaybackSettings) {
        AudioPlaybackSettingsDialog(
            speed = playback.playbackSpeed,
            pitch = playback.playbackPitch,
            onSave = onPlaybackParametersChange,
            onDismiss = { showPlaybackSettings = false }
        )
    }
    if (showSleepTimer) {
        AudioSleepTimerDialog(
            active = playback.sleepTimerEndElapsedMs != null,
            onSet = onSleepTimerChange,
            onDismiss = { showSleepTimer = false }
        )
    }
}

private suspend fun Animatable<Float, *>.springBack() {
    animateTo(
        0f,
        spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        )
    )
}
