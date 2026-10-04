package dev.qtremors.arcile.feature.audio

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.qtremors.arcile.core.storage.domain.AudioTrack
import dev.qtremors.arcile.core.ui.ArcileDropdownMenu
import dev.qtremors.arcile.core.ui.ArcileDropdownMenuItem
import dev.qtremors.arcile.core.ui.ArcileGestureAxis
import dev.qtremors.arcile.core.ui.ArcileSwipeDirection
import dev.qtremors.arcile.core.ui.SplitButtonGroup
import dev.qtremors.arcile.core.ui.ToolbarAction
import dev.qtremors.arcile.core.ui.arcileSwipeDirection
import dev.qtremors.arcile.core.ui.theme.bounceClickable
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@OptIn(ExperimentalSharedTransitionApi::class, ExperimentalFoundationApi::class)
@Composable
internal fun AudioMiniPlayer(
    track: AudioTrack,
    playback: AudioPlaybackState,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    onExpand: () -> Unit,
    onDismiss: () -> Unit,
    onTogglePlayback: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit
) {
    val dragOffset = remember { Animatable(0f) }
    val swipeOffset = remember { Animatable(0f) }
    val gestureThresholdPx = with(LocalDensity.current) { 48.dp.toPx() }
    val horizontalThresholdPx = with(LocalDensity.current) { 72.dp.toPx() }
    val systemEdgePx = with(LocalDensity.current) { 24.dp.toPx() }
    val flingThresholdPxPerSecond = with(LocalDensity.current) { 800.dp.toPx() }
    val gestureScope = rememberCoroutineScope()
    var dismissCommitted by remember(track.file.reference) { mutableStateOf(false) }
    val containerModifier = with(sharedTransitionScope) {
        Modifier
            .fillMaxWidth()
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
                translationX = swipeOffset.value
                translationY = dragOffset.value
                alpha = 1f -
                    (dragOffset.value / (gestureThresholdPx * 2f)).coerceIn(0f, 0.5f)
            }
            .pointerInput(gestureThresholdPx, flingThresholdPxPerSecond) {
                var totalDrag = 0f
                var gestureJob: Job? = null
                var velocityTracker = VelocityTracker()
                detectVerticalDragGestures(
                    onDragStart = {
                        totalDrag = 0f
                        velocityTracker = VelocityTracker()
                    },
                    onVerticalDrag = { change, dragAmount ->
                        change.consume()
                        velocityTracker.addPosition(change.uptimeMillis, change.position)
                        totalDrag += dragAmount
                        val dragTarget = totalDrag.coerceIn(
                            -gestureThresholdPx * 1.5f,
                            gestureThresholdPx * 2.5f
                        )
                        gestureJob?.cancel()
                        gestureJob = gestureScope.launch { dragOffset.snapTo(dragTarget) }
                    },
                    onDragCancel = {
                        gestureJob?.cancel()
                        gestureJob = gestureScope.launch { dragOffset.settleMiniDrag() }
                        totalDrag = 0f
                    },
                    onDragEnd = {
                        val gesture = resolveAudioMiniPlayerGesture(
                            dragOffsetPx = totalDrag,
                            thresholdPx = gestureThresholdPx,
                            velocityPxPerSecond = velocityTracker.calculateVelocity().y,
                            velocityThresholdPxPerSecond = flingThresholdPxPerSecond
                        )
                        gestureJob?.cancel()
                        gestureJob = gestureScope.launch {
                            when (gesture) {
                                AudioMiniPlayerGesture.EXPAND -> {
                                    onExpand()
                                    dragOffset.settleMiniDrag()
                                }
                                AudioMiniPlayerGesture.DISMISS -> {
                                    if (!dismissCommitted) {
                                        dismissCommitted = true
                                        try {
                                            dragOffset.animateTo(
                                                targetValue = gestureThresholdPx * 2.5f,
                                                animationSpec = spring(
                                                    stiffness = Spring.StiffnessMedium,
                                                    dampingRatio = Spring.DampingRatioNoBouncy
                                                )
                                            )
                                        } finally {
                                            onDismiss()
                                        }
                                    }
                                }
                                AudioMiniPlayerGesture.NONE -> {
                                    dragOffset.settleMiniDrag()
                                }
                            }
                        }
                        totalDrag = 0f
                    }
                )
            }
            .pointerInput(track.file.reference, horizontalThresholdPx) {
                var totalDrag = 0f
                var startedAtSystemEdge = false
                var swipeJob: Job? = null
                detectHorizontalDragGestures(
                    onDragStart = { change ->
                        totalDrag = 0f
                        startedAtSystemEdge = change.x < systemEdgePx ||
                            change.x > size.width - systemEdgePx
                    },
                    onHorizontalDrag = { change, amount ->
                        if (startedAtSystemEdge) return@detectHorizontalDragGestures
                        change.consume()
                        totalDrag += amount
                        swipeJob?.cancel()
                        swipeJob = gestureScope.launch {
                            swipeOffset.snapTo(
                                totalDrag.coerceIn(-horizontalThresholdPx * 2f, horizontalThresholdPx * 2f)
                            )
                        }
                    },
                    onDragCancel = {
                        swipeJob?.cancel()
                        swipeJob = gestureScope.launch { swipeOffset.settleMiniDrag() }
                    },
                    onDragEnd = {
                        if (startedAtSystemEdge) return@detectHorizontalDragGestures
                        val direction = when {
                            totalDrag > horizontalThresholdPx -> 1
                            totalDrag < -horizontalThresholdPx -> -1
                            else -> 0
                        }
                        swipeJob?.cancel()
                        swipeJob = gestureScope.launch {
                            if (direction != 0) {
                                swipeOffset.animateTo(
                                    direction * horizontalThresholdPx * 2f,
                                    spring(stiffness = Spring.StiffnessMediumLow)
                                )
                                if (direction > 0) onPrevious() else onNext()
                                swipeOffset.snapTo(-direction * horizontalThresholdPx)
                            }
                            swipeOffset.settleMiniDrag()
                        }
                        totalDrag = 0f
                    }
                )
            }
    }
    Surface(
        shape = RoundedCornerShape(32.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
        modifier = containerModifier.clickable(onClick = onExpand)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val progress = audioProgressFraction(
                playback.positionMs.toFloat(),
                playback.durationMs.takeIf { it > 0L } ?: track.durationMs
            )
            val progressColor = MaterialTheme.colorScheme.primary
            val progressTrackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.28f)
            Box(
                modifier = Modifier.size(48.dp),
                contentAlignment = Alignment.Center
            ) {
                AudioArtwork(
                    track = track,
                    shape = CircleShape,
                    modifier = with(sharedTransitionScope) {
                        Modifier
                            .size(40.dp)
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
                            .clickable(
                                onClickLabel = stringResource(if (playback.isPlaying) R.string.audio_pause else R.string.audio_play),
                                onClick = onTogglePlayback
                            )
                    }
                )
                if (!playback.isPlaying) {
                    Box(Modifier.size(40.dp).clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.36f))
                        .clickable(onClick = onTogglePlayback), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.PlayArrow, stringResource(R.string.audio_play),
                            tint = Color.White, modifier = Modifier.size(22.dp))
                    }
                }
                Canvas(Modifier.size(48.dp)) {
                    val stroke = Stroke(3.dp.toPx(), cap = StrokeCap.Round)
                    val arcSize = Size(size.width - stroke.width, size.height - stroke.width)
                    val topLeft = Offset(stroke.width / 2f, stroke.width / 2f)
                    drawArc(progressTrackColor, 0f, 360f, false, topLeft, arcSize, style = stroke)
                    drawArc(progressColor, -90f, 360f * progress, false, topLeft, arcSize, style = stroke)
                }
            }
            Spacer(Modifier.width(16.dp))
            AnimatedContent(
                targetState = track,
                transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(120)) },
                label = "mini player track",
                modifier = Modifier.weight(1f)
            ) { displayedTrack ->
                Column(Modifier.fillMaxWidth().clickable(onClick = onExpand)) {
                    Text(
                        displayedTrack.displayTitle,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Clip,
                        modifier = Modifier.basicMarquee(iterations = 1, initialDelayMillis = 3000)
                    )
                    Text(
                        displayedTrack.artist ?: stringResource(R.string.audio_unknown_artist),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        maxLines = 1,
                        overflow = TextOverflow.Clip,
                        modifier = Modifier.basicMarquee(iterations = 1, initialDelayMillis = 3000)
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            SplitButtonGroup(
                actions = listOf(
                    ToolbarAction(
                        icon = Icons.Default.SkipPrevious,
                        contentDescription = stringResource(R.string.audio_previous),
                        onClick = onPrevious
                    ),
                    ToolbarAction(
                        icon = Icons.Default.SkipNext,
                        contentDescription = stringResource(R.string.audio_next),
                        onClick = onNext
                    )
                ),
                height = 48.dp,
                minWidth = 48.dp,
                iconSize = 20.dp
            )
        }
    }
}

internal enum class AudioMiniPlayerGesture {
    NONE,
    EXPAND,
    DISMISS
}

internal fun resolveAudioMiniPlayerGesture(
    dragOffsetPx: Float,
    thresholdPx: Float,
    velocityPxPerSecond: Float = 0f,
    velocityThresholdPxPerSecond: Float = Float.POSITIVE_INFINITY
): AudioMiniPlayerGesture {
    val direction = arcileSwipeDirection(
        axis = ArcileGestureAxis.Vertical,
        deltaX = 0f,
        deltaY = dragOffsetPx,
        velocityX = 0f,
        velocityY = velocityPxPerSecond,
        minimumDistance = thresholdPx,
        minimumVelocity = velocityThresholdPxPerSecond
    )
    return when (direction) {
        ArcileSwipeDirection.Up -> AudioMiniPlayerGesture.EXPAND
        ArcileSwipeDirection.Down -> AudioMiniPlayerGesture.DISMISS
        ArcileSwipeDirection.Left,
        ArcileSwipeDirection.Right,
        null -> AudioMiniPlayerGesture.NONE
    }
}

private suspend fun Animatable<Float, *>.settleMiniDrag() {
    animateTo(
        targetValue = 0f,
        animationSpec = spring(
            stiffness = Spring.StiffnessMediumLow,
            dampingRatio = Spring.DampingRatioNoBouncy
        )
    )
}

internal const val AUDIO_PLAYER_CONTAINER_TRANSITION_KEY = "audio-player-container"
internal const val AUDIO_PLAYER_ARTWORK_TRANSITION_KEY = "audio-player-artwork"

@Composable
internal fun AudioSelectionActionsBar(
    canUseSingleTrackActions: Boolean,
    allSelectedFavorite: Boolean,
    onPlay: () -> Unit,
    onCopy: () -> Unit,
    onCut: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onShare: () -> Unit,
    onProperties: () -> Unit,
    onCreateZip: () -> Unit,
    onOpenWith: () -> Unit,
    onToggleFavorite: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onEditTags: () -> Unit,
    onEditAudio: () -> Unit
) {
    var showMenu by rememberSaveable { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        SplitButtonGroup(
            actions = listOf(
                ToolbarAction(
                    icon = Icons.Default.PlayArrow,
                    contentDescription = stringResource(R.string.audio_play_selected),
                    onClick = onPlay
                ),
                ToolbarAction(
                    icon = Icons.Default.ContentCopy,
                    contentDescription = stringResource(R.string.audio_copy),
                    onClick = onCopy
                ),
                ToolbarAction(
                    icon = Icons.Default.ContentCut,
                    contentDescription = stringResource(R.string.audio_cut),
                    onClick = onCut
                ),
                ToolbarAction(
                    icon = Icons.Default.Delete,
                    contentDescription = stringResource(
                        dev.qtremors.arcile.core.ui.R.string.action_delete_selected
                    ),
                    tint = MaterialTheme.colorScheme.error,
                    onClick = onDelete
                ),
                *if (canUseSingleTrackActions) {
                    arrayOf(
                        ToolbarAction(
                            icon = Icons.Default.Edit,
                            contentDescription = stringResource(R.string.audio_rename),
                            onClick = onRename
                        )
                    )
                } else {
                    emptyArray()
                }
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
                    .bounceClickable { showMenu = true }
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
                onDismissRequest = { showMenu = false },
                items = buildList {
                    add {
                        ArcileDropdownMenuItem(
                            text = stringResource(R.string.audio_add_to_playlist),
                            leadingIcon = { Icon(Icons.AutoMirrored.Filled.QueueMusic, contentDescription = null) },
                            onClick = {
                                showMenu = false
                                onAddToPlaylist()
                            }
                        )
                    }
                    if (canUseSingleTrackActions) add {
                        ArcileDropdownMenuItem(
                            text = stringResource(R.string.audio_edit_tags),
                            leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                            onClick = {
                                showMenu = false
                                onEditTags()
                            }
                        )
                    }
                    add {
                        ArcileDropdownMenuItem(
                            text = stringResource(R.string.audio_edit),
                            leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                            onClick = {
                                showMenu = false
                                onEditAudio()
                            }
                        )
                    }
                    add {
                        ArcileDropdownMenuItem(
                            text = stringResource(
                                if (allSelectedFavorite) {
                                    R.string.audio_remove_from_favorites
                                } else {
                                    R.string.audio_add_to_favorites
                                }
                            ),
                            leadingIcon = {
                                Icon(
                                    if (allSelectedFavorite) {
                                        Icons.Default.Favorite
                                    } else {
                                        Icons.Default.FavoriteBorder
                                    },
                                    contentDescription = null
                                )
                            },
                            onClick = {
                                showMenu = false
                                onToggleFavorite()
                            }
                        )
                    }
                    add {
                        ArcileDropdownMenuItem(
                            text = stringResource(
                                dev.qtremors.arcile.core.ui.R.string.archive_create_menu_action
                            ),
                            leadingIcon = {
                                Icon(Icons.Default.FolderZip, contentDescription = null)
                            },
                            onClick = {
                                showMenu = false
                                onCreateZip()
                            }
                        )
                    }
                    add {
                        ArcileDropdownMenuItem(
                            text = stringResource(R.string.audio_share),
                            leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) },
                            onClick = {
                                showMenu = false
                                onShare()
                            }
                        )
                    }
                    add {
                        ArcileDropdownMenuItem(
                            text = stringResource(
                                dev.qtremors.arcile.core.ui.R.string.properties_title
                            ),
                            leadingIcon = {
                                Icon(Icons.Default.Info, contentDescription = null)
                            },
                            onClick = {
                                showMenu = false
                                onProperties()
                            }
                        )
                    }
                    if (canUseSingleTrackActions) {
                        add {
                            ArcileDropdownMenuItem(
                                text = stringResource(R.string.audio_open_with),
                                leadingIcon = {
                                    Icon(Icons.Default.Headphones, contentDescription = null)
                                },
                                onClick = {
                                    showMenu = false
                                    onOpenWith()
                                }
                            )
                        }
                    }
                }
            )
        }
    }
}
