package dev.qtremors.arcile.feature.audio

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderOpen
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
internal fun AudioNowPlayingScreen(
    track: AudioTrack,
    queue: List<AudioTrack>,
    playback: AudioPlaybackState,
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
    onOpenWith: () -> Unit,
    onShowContainingFolder: () -> Unit
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
    val haptics = rememberArcileHaptics()
    val effectiveDuration = playback.durationMs.takeIf { it > 0L } ?: track.durationMs

    LaunchedEffect(playback.positionMs, isSeeking) {
        if (!isSeeking) sliderPosition = playback.positionMs.toFloat()
    }

    PredictiveBackHandler(enabled = predictiveBackEnabled) { progressFlow ->
        try {
            progressFlow.collect { event -> backProgress = event.progress }
            onCollapse()
        } catch (_: CancellationException) {
            // The player remains expanded when the predictive gesture is cancelled.
        } finally {
            backProgress = 0f
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        val dismissThreshold = with(density) { maxHeight.toPx() * 0.14f }
        val queueThreshold = with(density) { maxHeight.toPx() * 0.08f }
        val horizontalThreshold = with(density) { maxWidth.toPx() * 0.16f }
        val gestureProgress = (dragOffset.value / dismissThreshold).coerceIn(0f, 1f)
        val transitionProgress = max(gestureProgress, backProgress)

        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationX = backProgress * 72.dp.toPx()
                    translationY = dragOffset.value.coerceAtLeast(0f) +
                        backProgress * 48.dp.toPx()
                    val scale = 1f - transitionProgress * 0.08f
                    scaleX = scale
                    scaleY = scale
                    alpha = 1f - transitionProgress * 0.35f
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
                    .padding(start = 28.dp, top = 76.dp, end = 28.dp, bottom = 124.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                AudioArtwork(
                    track = track,
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 420.dp)
                        .aspectRatio(1f)
                        .graphicsLayer {
                            translationX = artworkOffset.value
                            val progress =
                                (abs(artworkOffset.value) / horizontalThreshold).coerceIn(0f, 1f)
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
                                            abs(totalX) > horizontalThreshold -> coroutineScope.launch {
                                            val direction = totalX.sign
                                            artworkOffset.animateTo(
                                                direction * horizontalThreshold * 2f,
                                                spring(stiffness = Spring.StiffnessMedium)
                                            )
                                            if (totalX < 0f) onNext() else onPrevious()
                                            artworkOffset.snapTo(-direction * horizontalThreshold)
                                            artworkOffset.springBack()
                                        }
                                        totalY > dismissThreshold -> coroutineScope.launch {
                                            dragOffset.animateTo(
                                                dismissThreshold * 2f,
                                                spring(stiffness = Spring.StiffnessMedium)
                                            )
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
                )
                Spacer(Modifier.height(24.dp))
                Column(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        track.displayTitle,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        listOfNotNull(
                            track.artist ?: stringResource(R.string.audio_unknown_artist),
                            track.album
                        ).joinToString(" • "),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
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
                Spacer(Modifier.height(20.dp))
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
                showMenu = showMenu,
                onShowMenu = {
                    haptics.selectionStart()
                    showMenu = true
                },
                onDismissMenu = { showMenu = false },
                onShowMetadata = {
                    haptics.selectionStart()
                    showMetadata = true
                },
                onShare = onShare,
                onOpenWith = onOpenWith,
                onShowContainingFolder = onShowContainingFolder,
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }

        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.92f),
            tonalElevation = 4.dp,
            shadowElevation = 4.dp,
            modifier = Modifier
                .statusBarsPadding()
                .padding(start = 16.dp, top = 8.dp)
                .size(48.dp)
                .align(Alignment.TopStart)
                .bounceClickable(onClick = onCollapse)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Default.KeyboardArrowDown,
                    contentDescription = stringResource(R.string.audio_collapse_player)
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
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 400.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
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
            onClick = onToggleRepeat
        )
        SplitButtonGroup(
            actions = listOf(
                ToolbarAction(
                    icon = Icons.Default.SkipPrevious,
                    contentDescription = stringResource(R.string.audio_previous),
                    onClick = onPrevious
                ),
                ToolbarAction(
                    icon = if (playback.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = stringResource(
                        if (playback.isPlaying) R.string.audio_pause else R.string.audio_play
                    ),
                    containerColor = MaterialTheme.colorScheme.primary,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    onClick = onTogglePlayback
                ),
                ToolbarAction(
                    icon = Icons.Default.SkipNext,
                    contentDescription = stringResource(R.string.audio_next),
                    onClick = onNext
                )
            ),
            modifier = Modifier.weight(1f),
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            height = 64.dp,
            minWidth = 52.dp,
            iconSize = 28.dp
        )
        AudioPlaybackToggleButton(
            icon = Icons.Default.Shuffle,
            contentDescription = stringResource(R.string.audio_shuffle),
            selected = shuffleActive,
            onClick = onToggleShuffle
        )
    }
}

@Composable
private fun AudioPlaybackToggleButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        shape = CircleShape,
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.secondaryContainer
        },
        contentColor = if (selected) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSecondaryContainer
        },
        modifier = Modifier
            .size(52.dp)
            .bounceClickable(onClick = onClick)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = contentDescription, modifier = Modifier.size(24.dp))
        }
    }
}

@Composable
private fun AudioPlayerBottomActions(
    showMenu: Boolean,
    onShowMenu: () -> Unit,
    onDismissMenu: () -> Unit,
    onShowMetadata: () -> Unit,
    onShare: () -> Unit,
    onOpenWith: () -> Unit,
    onShowContainingFolder: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        SplitButtonGroup(
            actions = listOf(
                ToolbarAction(
                    icon = Icons.Default.Info,
                    contentDescription = stringResource(R.string.audio_metadata),
                    onClick = onShowMetadata
                ),
                ToolbarAction(
                    icon = Icons.Default.Share,
                    contentDescription = stringResource(R.string.audio_share),
                    onClick = onShare
                ),
                ToolbarAction(
                    icon = Icons.Default.FolderOpen,
                    contentDescription = stringResource(R.string.audio_show_folder),
                    onClick = onShowContainingFolder
                )
            ),
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurface,
            height = 48.dp,
            minWidth = 48.dp,
            iconSize = 24.dp
        )
        Box {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                contentColor = MaterialTheme.colorScheme.onSurface,
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

private suspend fun Animatable<Float, *>.springBack() {
    animateTo(
        0f,
        spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        )
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AudioMetadataSheet(
    track: AudioTrack,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                stringResource(R.string.audio_metadata),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )
            AudioMetadataRow(stringResource(R.string.audio_metadata_title), track.displayTitle)
            AudioMetadataRow(
                stringResource(R.string.audio_metadata_artist),
                track.artist ?: stringResource(R.string.audio_unknown_artist)
            )
            track.album?.let {
                AudioMetadataRow(stringResource(R.string.audio_metadata_album), it)
            }
            AudioMetadataRow(
                stringResource(R.string.audio_metadata_duration),
                formatAudioDuration(track.durationMs)
            )
            AudioMetadataRow(
                stringResource(R.string.audio_metadata_size),
                formatFileSize(track.file.size)
            )
            AudioMetadataRow(
                stringResource(R.string.audio_metadata_modified),
                DateFormat.getDateTimeInstance().format(Date(track.file.lastModified))
            )
            AudioMetadataRow(
                stringResource(R.string.audio_metadata_format),
                track.file.mimeType.orEmpty().ifBlank { track.file.extension.uppercase() }
            )
            AudioMetadataRow(
                stringResource(R.string.audio_metadata_path),
                track.file.absolutePath
            )
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun AudioMetadataRow(label: String, value: String) {
    Column {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}
