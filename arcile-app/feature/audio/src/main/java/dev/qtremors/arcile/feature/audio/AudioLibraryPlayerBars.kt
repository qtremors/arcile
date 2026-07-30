package dev.qtremors.arcile.feature.audio

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
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
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SkipNext
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.qtremors.arcile.core.storage.domain.AudioTrack
import dev.qtremors.arcile.core.ui.ArcileDropdownMenu
import dev.qtremors.arcile.core.ui.ArcileDropdownMenuItem
import dev.qtremors.arcile.core.ui.SplitButtonGroup
import dev.qtremors.arcile.core.ui.ToolbarAction
import dev.qtremors.arcile.core.ui.theme.bounceClickable
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
internal fun AudioMiniPlayer(
    track: AudioTrack,
    playback: AudioPlaybackState,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    onExpand: () -> Unit,
    onDismiss: () -> Unit,
    onTogglePlayback: () -> Unit,
    onNext: () -> Unit
) {
    val dragOffset = remember { Animatable(0f) }
    val gestureThresholdPx = with(LocalDensity.current) { 48.dp.toPx() }
    val gestureScope = rememberCoroutineScope()
    val containerModifier = with(sharedTransitionScope) {
        Modifier
            .fillMaxWidth()
            .sharedBounds(
                sharedContentState = rememberSharedContentState(
                    AUDIO_PLAYER_CONTAINER_TRANSITION_KEY
                ),
                animatedVisibilityScope = animatedVisibilityScope
            )
            .graphicsLayer {
                translationY = dragOffset.value
                alpha = 1f -
                    (dragOffset.value / (gestureThresholdPx * 2f)).coerceIn(0f, 0.5f)
            }
            .pointerInput(gestureThresholdPx) {
                var totalDrag = 0f
                var hasTriggeredExpand = false
                var gestureJob: Job? = null
                detectVerticalDragGestures(
                    onVerticalDrag = { change, dragAmount ->
                        change.consume()
                        totalDrag += dragAmount
                        val dragTarget = totalDrag.coerceIn(
                            -gestureThresholdPx * 10f,
                            gestureThresholdPx * 2.5f
                        )
                        gestureJob?.cancel()
                        gestureJob = gestureScope.launch { dragOffset.snapTo(dragTarget) }
                        if (totalDrag <= -gestureThresholdPx && !hasTriggeredExpand) {
                            hasTriggeredExpand = true
                            onExpand()
                        }
                    },
                    onDragCancel = {
                        gestureJob?.cancel()
                        gestureJob = gestureScope.launch { dragOffset.settleMiniDrag() }
                        totalDrag = 0f
                        hasTriggeredExpand = false
                    },
                    onDragEnd = {
                        if (!hasTriggeredExpand) {
                            val gesture = resolveAudioMiniPlayerGesture(
                                dragOffsetPx = totalDrag,
                                thresholdPx = gestureThresholdPx
                            )
                            gestureJob?.cancel()
                            gestureJob = gestureScope.launch {
                                when (gesture) {
                                    AudioMiniPlayerGesture.EXPAND -> {
                                        onExpand()
                                    }
                                    AudioMiniPlayerGesture.DISMISS -> {
                                        dragOffset.animateTo(
                                            targetValue = gestureThresholdPx * 2.5f,
                                            animationSpec = spring(
                                                stiffness = Spring.StiffnessMedium,
                                                dampingRatio = Spring.DampingRatioNoBouncy
                                            )
                                        )
                                        onDismiss()
                                    }
                                    AudioMiniPlayerGesture.NONE -> {
                                        dragOffset.settleMiniDrag()
                                    }
                                }
                            }
                        }
                        totalDrag = 0f
                        hasTriggeredExpand = false
                    }
                )
            }
    }
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 4.dp,
        shadowElevation = 4.dp,
        modifier = containerModifier
    ) {
        Column {
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(72.dp)
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AudioArtwork(
                    track,
                    with(sharedTransitionScope) {
                        Modifier
                            .size(56.dp)
                            .sharedBounds(
                                sharedContentState = rememberSharedContentState(
                                    AUDIO_PLAYER_ARTWORK_TRANSITION_KEY
                                ),
                                animatedVisibilityScope = animatedVisibilityScope
                            )
                            .bounceClickable(onClick = onExpand)
                    }
                )
                Spacer(Modifier.width(12.dp))
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .bounceClickable(onClick = onExpand)
                ) {
                    Text(
                        track.displayTitle,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        track.artist ?: stringResource(R.string.audio_unknown_artist),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                SplitButtonGroup(
                    actions = listOf(
                        ToolbarAction(
                            icon = if (playback.isPlaying) {
                                Icons.Default.Pause
                            } else {
                                Icons.Default.PlayArrow
                            },
                            contentDescription = stringResource(
                                if (playback.isPlaying) R.string.audio_pause else R.string.audio_play
                            ),
                            onClick = onTogglePlayback
                        ),
                        ToolbarAction(
                            icon = Icons.Default.SkipNext,
                            contentDescription = stringResource(R.string.audio_next),
                            onClick = onNext
                        )
                    ),
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    height = 48.dp,
                    minWidth = 48.dp,
                    iconSize = 24.dp
                )
            }
            AudioMiniPlaybackProgress(
                positionMs = playback.positionMs,
                durationMs = playback.durationMs.takeIf { it > 0L } ?: track.durationMs,
                modifier = Modifier.fillMaxWidth()
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
    thresholdPx: Float
): AudioMiniPlayerGesture = when {
    thresholdPx <= 0f -> AudioMiniPlayerGesture.NONE
    dragOffsetPx <= -thresholdPx -> AudioMiniPlayerGesture.EXPAND
    dragOffsetPx >= thresholdPx -> AudioMiniPlayerGesture.DISMISS
    else -> AudioMiniPlayerGesture.NONE
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
    onToggleFavorite: () -> Unit
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
                                dev.qtremors.arcile.core.ui.R.string.archive_compress_zip
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
