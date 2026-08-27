package dev.qtremors.arcile.feature.audio

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.qtremors.arcile.core.presentation.formatFileSize
import dev.qtremors.arcile.core.storage.domain.AudioTrack
import dev.qtremors.arcile.core.ui.ArcileDropdownMenu
import dev.qtremors.arcile.core.ui.ArcileDropdownMenuItem
import dev.qtremors.arcile.core.ui.ArcileGestureAxis
import dev.qtremors.arcile.core.ui.ArcileGestureDefaults
import dev.qtremors.arcile.core.ui.ArcileSwipeDirection
import dev.qtremors.arcile.core.ui.SplitButtonGroup
import dev.qtremors.arcile.core.ui.ToolbarAction
import dev.qtremors.arcile.core.ui.arcileGestureAxis
import dev.qtremors.arcile.core.ui.arcileSwipeDirection
import dev.qtremors.arcile.core.ui.rememberArcileHaptics
import dev.qtremors.arcile.core.ui.theme.bounceClickable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date
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
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    predictiveBackEnabled: Boolean = true,
    onCollapse: () -> Unit,
    onTogglePlayback: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onQueueTrack: (Int) -> Unit,
    onToggleRepeat: () -> Unit,
    onToggleShuffle: () -> Unit,
    onSeek: (Long) -> Unit,
    onShare: () -> Unit,
    onEdit: () -> Unit,
    onOpenWith: () -> Unit,
    onFileRenamed: (String, FileModel) -> Unit = { _, _ -> },
    onFileDeleted: (String) -> Unit = {}
) {
    var showMenu by remember { mutableStateOf(false) }
    var showMetadata by remember { mutableStateOf(false) }
    var showQueue by remember { mutableStateOf(false) }
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
                animatedVisibilityScope = animatedVisibilityScope
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
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val dismissThreshold = with(density) { maxHeight.toPx() * 0.14f }
        val queueThreshold = with(density) { maxHeight.toPx() * 0.08f }
        val horizontalThreshold = with(density) { maxWidth.toPx() * 0.16f }
        val compactHeight = maxHeight < 720.dp
        val artworkMaxSize = if (compactHeight) 320.dp else 440.dp

        Box(
            modifier = Modifier
                .fillMaxSize()
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
                    .fillMaxWidth()
                    .widthIn(max = 600.dp)
                    .align(Alignment.Center)
                    .verticalScroll(rememberScrollState())
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(
                        start = 24.dp,
                        top = if (compactHeight) 72.dp else 84.dp,
                        end = 24.dp,
                        bottom = if (compactHeight) 88.dp else 104.dp
                    ),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                AudioArtwork(
                    track = track,
                    modifier = with(sharedTransitionScope) {
                        Modifier
                            .fillMaxWidth()
                            .widthIn(max = artworkMaxSize)
                            .aspectRatio(1f)
                            .sharedBounds(
                                sharedContentState = rememberSharedContentState(
                                    AUDIO_PLAYER_ARTWORK_TRANSITION_KEY
                                ),
                                animatedVisibilityScope = animatedVisibilityScope
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
                                track.file.absolutePath,
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
                                                haptics.selectionStart()
                                                showQueue = true
                                                gestureJob = gestureScope.launch {
                                                    dragOffset.snapTo(0f)
                                                }
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
                Spacer(Modifier.height(if (compactHeight) 18.dp else 24.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(if (compactHeight) 84.dp else 96.dp),
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        track.displayTitle,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        track.artist ?: stringResource(R.string.audio_unknown_artist),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        track.album?.takeIf(String::isNotBlank) ?: " ",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.height(if (compactHeight) 16.dp else 24.dp))
                AudioPlaybackProgress(
                    positionMs = sliderPosition,
                    durationMs = effectiveDuration,
                    isPlaying = playback.isPlaying,
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
                Spacer(Modifier.height(if (compactHeight) 12.dp else 20.dp))
                AudioPlaybackControls(
                    playback = playback,
                    onTogglePlayback = onTogglePlayback,
                    onPrevious = onPrevious,
                    onNext = onNext,
                    onToggleRepeat = onToggleRepeat,
                    onToggleShuffle = onToggleShuffle
                )
            }

            AudioPlayerBottomActions(
                onShowMetadata = {
                    haptics.selectionStart()
                    showMetadata = true
                },
                onShare = onShare,
                onShowQueue = {
                    haptics.selectionStart()
                    showQueue = true
                },
                modifier = Modifier.align(Alignment.BottomCenter)
            )
            AudioPlayerTopBar(
                track = track,
                showMenu = showMenu,
                onCollapse = onCollapse,
                onShowMenu = {
                    haptics.selectionStart()
                    showMenu = true
                },
                onDismissMenu = { showMenu = false },
                onEdit = onEdit,
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
                }.takeIf { ViewerFileAction.CreateArchive in viewerActionController.state.allowedActions },
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
        AudioQueueSheet(
            queue = queue,
            currentIndex = playback.currentMediaIndex,
            onTrackClick = onQueueTrack,
            onDismiss = { showQueue = false }
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

internal enum class AudioNowPlayingGesture {
    Previous,
    Next,
    Collapse,
    Queue,
    None
}

internal fun resolveAudioNowPlayingGesture(
    lockedAxis: ArcileGestureAxis?,
    deltaX: Float,
    deltaY: Float,
    velocityX: Float,
    velocityY: Float,
    horizontalThreshold: Float,
    collapseThreshold: Float,
    queueThreshold: Float,
    minimumVelocity: Float
): AudioNowPlayingGesture {
    val axis = lockedAxis ?: return AudioNowPlayingGesture.None
    val minimumDistance = when (axis) {
        ArcileGestureAxis.Horizontal -> horizontalThreshold
        ArcileGestureAxis.Vertical -> if (deltaY >= 0f) collapseThreshold else queueThreshold
    }
    return when (
        arcileSwipeDirection(
            axis = axis,
            deltaX = deltaX,
            deltaY = deltaY,
            velocityX = velocityX,
            velocityY = velocityY,
            minimumDistance = minimumDistance,
            minimumVelocity = minimumVelocity
        )
    ) {
        ArcileSwipeDirection.Left -> AudioNowPlayingGesture.Next
        ArcileSwipeDirection.Right -> AudioNowPlayingGesture.Previous
        ArcileSwipeDirection.Down -> AudioNowPlayingGesture.Collapse
        ArcileSwipeDirection.Up -> AudioNowPlayingGesture.Queue
        null -> AudioNowPlayingGesture.None
    }
}
