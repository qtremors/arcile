package dev.qtremors.arcile.core.ui.audio

import dev.qtremors.arcile.core.ui.audio.AudioPlaybackProgress
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
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
import androidx.compose.runtime.saveable.rememberSaveable
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
import dev.qtremors.arcile.core.ui.viewer.ViewerFileAction

import dev.qtremors.arcile.core.ui.R
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun AudioNowPlayingScreen(
    track: AudioTrack,
    playback: AudioPlaybackState,
    onCollapse: () -> Unit,
    onTogglePlayback: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onToggleRepeat: () -> Unit,
    onToggleShuffle: () -> Unit,
    onPlaybackParametersChange: (Float, Float) -> Unit,
    onSleepTimerChange: (Int?) -> Unit,
    onSeek: (Long) -> Unit,
    onShowMetadata: () -> Unit,
    artwork: @Composable (AudioTrack, Modifier) -> Unit,
    queueContent: @Composable (() -> Unit) -> Unit,
    modifier: Modifier = Modifier,
    artworkModifier: @Composable (Modifier) -> Modifier = { it },
    coverLyrics: (@Composable (Modifier, () -> Unit, () -> Unit) -> Unit)? = null,
    onShowLyrics: (() -> Unit)? = null,
    onEdit: (() -> Unit)? = null,
    onEditTags: (() -> Unit)? = null,
    onOpenWith: (() -> Unit)? = null,
    onShare: (() -> Unit)? = null,
    viewerActions: Set<ViewerFileAction> = emptySet(),
    onViewerAction: (ViewerFileAction) -> Unit = {},
    visualizerEnabled: Boolean = false,
    onToggleVisualizer: (() -> Unit)? = null,
    spectrumLevels: List<Float>? = null,
    onSpectrumVisibility: (Boolean) -> Unit = {},
    isFavorite: Boolean = false,
    onToggleFavorite: (() -> Unit)? = null,
    privateSession: Boolean = false,
    predictiveBackEnabled: Boolean = true
) {
    var showMenu by remember { mutableStateOf(false) }
    var showQueue by remember { mutableStateOf(false) }
    var showCoverLyrics by if (privateSession) remember { mutableStateOf(false) }
        else rememberSaveable { mutableStateOf(false) }
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
    val effectiveDuration = playback.durationMs.takeIf { it > 0L } ?: track.durationMs
    val spectrumVisible = visualizerEnabled && !showCoverLyrics
    DisposableEffect(spectrumVisible) {
        onSpectrumVisibility(spectrumVisible)
        onDispose { onSpectrumVisibility(false) }
    }

    val collapseThreshold = screenHeightPx * 0.14f
    val collapseProgress = max(
        (dragOffset.value / collapseThreshold).coerceIn(0f, 1f),
        backProgress
    )
    val containerModifier = modifier
            .fillMaxSize()
            .graphicsLayer {
                translationX = backProgress * 72.dp.toPx()
                translationY = dragOffset.value.coerceAtLeast(0f) +
                    backProgress * 48.dp.toPx()
                val scale = 1f - collapseProgress * 0.08f
                scaleX = scale
                scaleY = scale
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
                .pointerInput(queueThreshold, showCoverLyrics) {
                    if (showCoverLyrics) return@pointerInput
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
                .pointerInput(dismissThreshold, showCoverLyrics) {
                    if (showCoverLyrics) return@pointerInput
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
                if (showCoverLyrics) {
                    coverLyrics?.invoke(
                        Modifier.width(artworkSize).aspectRatio(1f),
                        { showCoverLyrics = false }, onShowLyrics ?: {}
                    )
                } else {
                    artwork(
                        track,
                        artworkModifier(Modifier.width(artworkSize).aspectRatio(1f))
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
                                    onClickLabel = stringResource(if (coverLyrics == null) R.string.audio_metadata else R.string.audio_lyrics),
                                    onLongClickLabel = stringResource(R.string.audio_metadata),
                                    onClick = { if (coverLyrics != null) showCoverLyrics = true else onShowMetadata() },
                                    onLongClick = {
                                        haptics.selectionStart()
                                        onShowMetadata()
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
                    )
                }
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
                    if (onToggleFavorite != null) AudioPlayerSongActions(
                        isFavorite = isFavorite,
                        onToggleFavorite = onToggleFavorite
                    )
                }
                Spacer(Modifier.height(12.dp))
                if (!needsScroll) Spacer(Modifier.weight(1f))
                if (playback.error) Text(stringResource(R.string.audio_playback_failed), color = MaterialTheme.colorScheme.error)
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
                    spectrumLevels = spectrumLevels.takeIf { visualizerEnabled },
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
                    onShowMetadata()
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
                        onEditTags = onEditTags,
                        onShowLyrics = onShowLyrics,
                        onOpenWith = onOpenWith,
                        onShare = onShare.takeIf { ViewerFileAction.Share in viewerActions },
                        onCopy = {
                            onViewerAction(ViewerFileAction.Copy)
                        }.takeIf { ViewerFileAction.Copy in viewerActions },
                        onCut = {
                            onViewerAction(ViewerFileAction.Cut)
                        }.takeIf { ViewerFileAction.Cut in viewerActions },
                        onRename = {
                            onViewerAction(ViewerFileAction.Rename)
                        }.takeIf { ViewerFileAction.Rename in viewerActions },
                        onDelete = {
                            onViewerAction(ViewerFileAction.Delete)
                        }.takeIf { ViewerFileAction.Delete in viewerActions },
                        onProperties = {
                            onViewerAction(ViewerFileAction.Properties)
                        }.takeIf { ViewerFileAction.Properties in viewerActions },
                        onArchive = {
                            onViewerAction(ViewerFileAction.CreateArchive)
                        }.takeIf { ViewerFileAction.CreateArchive in viewerActions }
                    )
                },
                modifier = Modifier.align(Alignment.BottomCenter)
            )
            AudioPlayerTopBar(
                track = track,
                onCollapse = onCollapse,
                lyricsVisible = showCoverLyrics,
                onShowLyrics = if (coverLyrics == null) null else { { showCoverLyrics = !showCoverLyrics } },
                modifier = Modifier.align(Alignment.TopCenter)
            )
        }
    }
    }

    if (showQueue) queueContent { showQueue = false }
    if (showPlaybackSettings) AudioPlaybackSettingsDialog(
        playback.playbackSpeed, playback.playbackPitch, onPlaybackParametersChange, { showPlaybackSettings = false }
    )
    if (showSleepTimer) AudioSleepTimerDialog(
        playback.sleepTimerEndElapsedMs != null, onSleepTimerChange, { showSleepTimer = false }
    )
}
