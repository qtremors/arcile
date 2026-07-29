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
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.qtremors.arcile.core.presentation.formatFileSize
import dev.qtremors.arcile.core.storage.domain.AudioTrack
import dev.qtremors.arcile.core.ui.ArcileDropdownMenu
import dev.qtremors.arcile.core.ui.ArcileDropdownMenuItem
import dev.qtremors.arcile.core.ui.SplitButtonGroup
import dev.qtremors.arcile.core.ui.ToolbarAction
import dev.qtremors.arcile.core.ui.rememberArcileHaptics
import dev.qtremors.arcile.core.ui.theme.bounceClickable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sign

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
    onOpenWith: () -> Unit
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
    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp
    val haptics = rememberArcileHaptics()
    val effectiveDuration = playback.durationMs.takeIf { it > 0L } ?: track.durationMs
    val collapseThreshold = with(density) { screenHeight.toPx() * 0.14f }
    val collapseProgress = max(
        (dragOffset.value / collapseThreshold).coerceIn(0f, 1f),
        backProgress
    )
    val containerModifier = with(sharedTransitionScope) {
        Modifier
            .fillMaxSize()
            .sharedBounds(
                sharedContentState = rememberSharedContentState(
                    "audio-player-container-${track.file.absolutePath}"
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
            modifier = Modifier.fillMaxSize()
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
                                    "audio-player-artwork-${track.file.absolutePath}"
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
                                detectDragGestures(
                                    onDrag = { change, amount ->
                                        change.consume()
                                        totalX += amount.x
                                        totalY += amount.y
                                        coroutineScope.launch {
                                            if (abs(totalX) > abs(totalY)) {
                                                artworkOffset.snapTo(totalX)
                                            } else {
                                                dragOffset.snapTo(totalY.coerceAtLeast(0f))
                                            }
                                        }
                                    },
                                    onDragCancel = {
                                        coroutineScope.launch {
                                            dragOffset.springBack()
                                            artworkOffset.springBack()
                                        }
                                    },
                                    onDragEnd = {
                                        when {
                                            abs(totalX) > abs(totalY) &&
                                                abs(totalX) > horizontalThreshold ->
                                                coroutineScope.launch {
                                                    val direction = totalX.sign
                                                    artworkOffset.animateTo(
                                                        direction * horizontalThreshold * 2f,
                                                        spring(stiffness = Spring.StiffnessMedium)
                                                    )
                                                    if (totalX < 0f) onNext() else onPrevious()
                                                    artworkOffset.snapTo(
                                                        -direction * horizontalThreshold
                                                    )
                                                    artworkOffset.springBack()
                                                }
                                            totalY > dismissThreshold -> coroutineScope.launch {
                                                onCollapse()
                                            }
                                            totalY < -queueThreshold -> {
                                                haptics.selectionStart()
                                                showQueue = true
                                                coroutineScope.launch { dragOffset.snapTo(0f) }
                                            }
                                            else -> coroutineScope.launch {
                                                dragOffset.springBack()
                                                artworkOffset.springBack()
                                            }
                                        }
                                        totalX = 0f
                                        totalY = 0f
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
                onOpenWith = onOpenWith,
                modifier = Modifier.align(Alignment.TopCenter)
            )
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

@Composable
private fun AudioPlayerTopBar(
    track: AudioTrack,
    showMenu: Boolean,
    onCollapse: () -> Unit,
    onShowMenu: () -> Unit,
    onDismissMenu: () -> Unit,
    onOpenWith: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .height(52.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.94f),
            tonalElevation = 3.dp,
            modifier = Modifier
                .size(48.dp)
                .bounceClickable(onClick = onCollapse)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Default.KeyboardArrowDown,
                    contentDescription = stringResource(R.string.audio_collapse_player)
                )
            }
        }
        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                stringResource(R.string.audio_now_playing),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )
            Text(
                track.album ?: track.artist ?: stringResource(R.string.audio_title),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Box {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.94f),
                tonalElevation = 3.dp,
                modifier = Modifier
                    .size(48.dp)
                    .bounceClickable(onClick = onShowMenu)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.MoreVert,
                        contentDescription = stringResource(R.string.audio_more)
                    )
                }
            }
            ArcileDropdownMenu(
                expanded = showMenu,
                onDismissRequest = onDismissMenu,
                items = listOf {
                    ArcileDropdownMenuItem(
                        text = stringResource(R.string.audio_open_with),
                        leadingIcon = {
                            Icon(Icons.Default.Headphones, contentDescription = null)
                        },
                        onClick = {
                            onDismissMenu()
                            onOpenWith()
                        }
                    )
                }
            )
        }
    }
}

@Composable
private fun AudioPlaybackControls(
    playback: AudioPlaybackState,
    onTogglePlayback: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onToggleRepeat: () -> Unit,
    onToggleShuffle: () -> Unit
) {
    val repeatActive = playback.repeatMode != AudioRepeatMode.OFF
    val shuffleActive = playback.shuffleEnabled
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 420.dp)
    ) {
        val compact = maxWidth < 360.dp
        val toggleSize = if (compact) 46.dp else 52.dp
        val skipWidth = if (compact) 56.dp else 64.dp
        val skipHeight = if (compact) 64.dp else 72.dp
        val playSize = if (compact) 72.dp else 82.dp
        val spacing = if (compact) 5.dp else 8.dp

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(
                space = spacing,
                alignment = Alignment.CenterHorizontally
            ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AudioPlaybackToggleButton(
                icon = if (playback.repeatMode == AudioRepeatMode.ONE) {
                    Icons.Default.RepeatOne
                } else {
                    Icons.Default.Repeat
                },
                contentDescription = stringResource(
                    when (playback.repeatMode) {
                        AudioRepeatMode.OFF -> R.string.audio_repeat
                        AudioRepeatMode.ALL -> R.string.audio_repeat_all
                        AudioRepeatMode.ONE -> R.string.audio_repeat_one
                    }
                ),
                selected = repeatActive,
                size = toggleSize,
                onClick = onToggleRepeat
            )
            AudioTransportButton(
                icon = Icons.Default.SkipPrevious,
                contentDescription = stringResource(R.string.audio_previous),
                width = skipWidth,
                height = skipHeight,
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                onClick = onPrevious
            )
            AudioTransportButton(
                icon = if (playback.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = stringResource(
                    if (playback.isPlaying) R.string.audio_pause else R.string.audio_play
                ),
                width = playSize,
                height = playSize,
                iconSize = 34.dp,
                shape = RoundedCornerShape(28.dp),
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                onClick = onTogglePlayback
            )
            AudioTransportButton(
                icon = Icons.Default.SkipNext,
                contentDescription = stringResource(R.string.audio_next),
                width = skipWidth,
                height = skipHeight,
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                onClick = onNext
            )
            AudioPlaybackToggleButton(
                icon = Icons.Default.Shuffle,
                contentDescription = stringResource(R.string.audio_shuffle),
                selected = shuffleActive,
                size = toggleSize,
                onClick = onToggleShuffle
            )
        }
    }
}

@Composable
private fun AudioTransportButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    width: Dp,
    height: Dp,
    containerColor: androidx.compose.ui.graphics.Color,
    contentColor: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit,
    iconSize: Dp = 28.dp,
    shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(22.dp)
) {
    Surface(
        shape = shape,
        color = containerColor,
        contentColor = contentColor,
        tonalElevation = 2.dp,
        modifier = Modifier
            .width(width)
            .height(height)
            .bounceClickable(onClick = onClick)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                modifier = Modifier.size(iconSize)
            )
        }
    }
}

@Composable
private fun AudioPlaybackToggleButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    selected: Boolean,
    size: Dp,
    onClick: () -> Unit
) {
    Surface(
        shape = CircleShape,
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        },
        contentColor = if (selected) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        border = if (selected) {
            null
        } else {
            BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        },
        modifier = Modifier
            .size(size)
            .bounceClickable(onClick = onClick)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = contentDescription, modifier = Modifier.size(24.dp))
        }
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
