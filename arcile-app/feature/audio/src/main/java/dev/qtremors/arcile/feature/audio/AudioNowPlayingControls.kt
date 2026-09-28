package dev.qtremors.arcile.feature.audio

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.qtremors.arcile.core.storage.domain.AudioTrack
import dev.qtremors.arcile.core.ui.ArcileDropdownMenu
import dev.qtremors.arcile.core.ui.ArcileDropdownMenuItem
import dev.qtremors.arcile.core.ui.theme.LocalReducedMotionEnabled

import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder

@Composable
internal fun AudioPlayerSongActions(
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit
) {
    val colors = IconButtonDefaults.filledIconButtonColors(
        containerColor = if (isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
        contentColor = if (isFavorite) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
    )
    FilledIconButton(
        onClick = onToggleFavorite,
        shape = CircleShape,
        colors = colors,
        modifier = Modifier.size(42.dp)
    ) {
        Icon(
            if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
            contentDescription = stringResource(
                if (isFavorite) R.string.audio_remove_from_favorites else R.string.audio_add_to_favorites
            ),
            modifier = Modifier.size(24.dp)
        )
    }
}

@Composable
internal fun AudioPlayerTopBar(
    track: AudioTrack,
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
        Spacer(Modifier.size(42.dp))
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
        Spacer(Modifier.size(42.dp))
    }
}

@Composable
internal fun AudioPlayerMoreMenu(
    showMenu: Boolean,
    onDismissMenu: () -> Unit,
    visualizerEnabled: Boolean,
    onToggleVisualizer: () -> Unit,
    onEdit: () -> Unit,
    onEditTags: () -> Unit,
    onShowLyrics: () -> Unit,
    onOpenWith: () -> Unit,
    onShare: (() -> Unit)? = null,
    onCopy: (() -> Unit)? = null,
    onCut: (() -> Unit)? = null,
    onRename: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
    onProperties: (() -> Unit)? = null,
    onArchive: (() -> Unit)? = null
) {
            val menuItems = buildList<@Composable () -> Unit> {
                add {
                    ArcileDropdownMenuItem(
                        text = stringResource(
                            if (visualizerEnabled) R.string.audio_visualizer_off
                            else R.string.audio_visualizer_on
                        ),
                        leadingIcon = { Icon(Icons.Default.Equalizer, contentDescription = null) },
                        onClick = {
                            onDismissMenu()
                            onToggleVisualizer()
                        }
                    )
                }
                add {
                    ArcileDropdownMenuItem(
                        text = stringResource(R.string.audio_edit),
                        leadingIcon = {
                            Icon(Icons.Default.Edit, contentDescription = null)
                        },
                        onClick = {
                            onDismissMenu()
                            onEdit()
                        }
                    )
                }
                add {
                    ArcileDropdownMenuItem(
                        text = stringResource(R.string.audio_edit_tags),
                        leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                        onClick = { onDismissMenu(); onEditTags() }
                    )
                }
                add {
                    ArcileDropdownMenuItem(
                        text = stringResource(R.string.audio_lyrics),
                        leadingIcon = { Icon(Icons.Default.MusicNote, contentDescription = null) },
                        onClick = { onDismissMenu(); onShowLyrics() }
                    )
                }
                add {
                    ArcileDropdownMenuItem(
                        text = stringResource(R.string.audio_open_with),
                        leadingIcon = {
                            Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null)
                        },
                        onClick = {
                            onDismissMenu()
                            onOpenWith()
                        }
                    )
                }
                if (onShare != null) {
                    add {
                        ArcileDropdownMenuItem(
                            text = stringResource(R.string.audio_share),
                            leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) },
                            onClick = {
                                onDismissMenu()
                                onShare()
                            }
                        )
                    }
                }
                if (onRename != null) {
                    add {
                        ArcileDropdownMenuItem(
                            text = stringResource(dev.qtremors.arcile.core.ui.R.string.action_rename),
                            leadingIcon = { Icon(Icons.Default.DriveFileRenameOutline, contentDescription = null) },
                            onClick = {
                                onDismissMenu()
                                onRename()
                            }
                        )
                    }
                }
                if (onCopy != null) {
                    add {
                        ArcileDropdownMenuItem(
                            text = stringResource(dev.qtremors.arcile.core.ui.R.string.action_copy),
                            leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null) },
                            onClick = {
                                onDismissMenu()
                                onCopy()
                            }
                        )
                    }
                }
                if (onCut != null) {
                    add {
                        ArcileDropdownMenuItem(
                            text = stringResource(dev.qtremors.arcile.core.ui.R.string.action_cut),
                            leadingIcon = { Icon(Icons.Default.ContentCut, contentDescription = null) },
                            onClick = {
                                onDismissMenu()
                                onCut()
                            }
                        )
                    }
                }
                if (onProperties != null) {
                    add {
                        ArcileDropdownMenuItem(
                            text = stringResource(dev.qtremors.arcile.core.ui.R.string.action_properties),
                            leadingIcon = { Icon(Icons.Default.Info, contentDescription = null) },
                            onClick = {
                                onDismissMenu()
                                onProperties()
                            }
                        )
                    }
                }
                if (onArchive != null) {
                    add {
                        ArcileDropdownMenuItem(
                            text = stringResource(dev.qtremors.arcile.core.ui.R.string.action_create_archive),
                            leadingIcon = { Icon(Icons.Default.FolderZip, contentDescription = null) },
                            onClick = {
                                onDismissMenu()
                                onArchive()
                            }
                        )
                    }
                }
                if (onDelete != null) {
                    add {
                        ArcileDropdownMenuItem(
                            text = stringResource(dev.qtremors.arcile.core.ui.R.string.action_delete),
                            leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                            onClick = {
                                onDismissMenu()
                                onDelete()
                            }
                        )
                    }
                }
            }

            ArcileDropdownMenu(
                expanded = showMenu,
                onDismissRequest = onDismissMenu,
                items = menuItems
            )
}

@Composable
internal fun AudioPlaybackControls(
    playback: AudioPlaybackState,
    visualizerEnabled: Boolean,
    onTogglePlayback: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 420.dp)
    ) {
        val compact = maxWidth < 300.dp
        val buttonHeight = 68.dp
        val spacing = 8.dp
        val centerColor = MaterialTheme.colorScheme.primary
        val centerContentColor = MaterialTheme.colorScheme.onPrimary
        val previousInteraction = remember { MutableInteractionSource() }
        val playInteraction = remember { MutableInteractionSource() }
        val nextInteraction = remember { MutableInteractionSource() }
        val previousPressed by previousInteraction.collectIsPressedAsState()
        val playPressed by playInteraction.collectIsPressedAsState()
        val nextPressed by nextInteraction.collectIsPressedAsState()
        val previousWeight by animateFloatAsState(
            targetValue = if (previousPressed) 0.65f else if (playPressed) 0.35f else 0.45f,
            animationSpec = spring(dampingRatio = 0.6f, stiffness = 500f),
            label = "previous button width"
        )
        val playWeight by animateFloatAsState(
            targetValue = if (playPressed) 1.9f else if (previousPressed || nextPressed) 1.1f else 1.3f,
            animationSpec = spring(dampingRatio = 0.6f, stiffness = 500f),
            label = "play button width"
        )
        val nextWeight by animateFloatAsState(
            targetValue = if (nextPressed) 0.65f else if (playPressed) 0.35f else 0.45f,
            animationSpec = spring(dampingRatio = 0.6f, stiffness = 500f),
            label = "next button width"
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(
                space = spacing,
                alignment = Alignment.CenterHorizontally
            ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AudioTransportButton(
                icon = Icons.Default.SkipPrevious,
                contentDescription = stringResource(R.string.audio_previous),
                modifier = Modifier.weight(previousWeight).height(buttonHeight),
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                interactionSource = previousInteraction,
                onClick = onPrevious,
                iconSize = if (compact) 28.dp else 32.dp
            )
            AudioTransportButton(
                icon = if (playback.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = stringResource(
                    if (playback.isPlaying) R.string.audio_pause else R.string.audio_play
                ),
                modifier = Modifier.weight(playWeight).height(buttonHeight),
                iconSize = if (compact) 28.dp else 32.dp,
                visualizerPlaying = playback.isPlaying && visualizerEnabled,
                containerColor = centerColor,
                contentColor = centerContentColor,
                interactionSource = playInteraction,
                onClick = onTogglePlayback
            )
            AudioTransportButton(
                icon = Icons.Default.SkipNext,
                contentDescription = stringResource(R.string.audio_next),
                modifier = Modifier.weight(nextWeight).height(buttonHeight),
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                interactionSource = nextInteraction,
                onClick = onNext,
                iconSize = if (compact) 28.dp else 32.dp
            )
        }
    }
}

@Composable
private fun AudioTransportButton(
    icon: ImageVector,
    contentDescription: String,
    modifier: Modifier,
    containerColor: Color,
    contentColor: Color,
    interactionSource: MutableInteractionSource,
    onClick: () -> Unit,
    iconSize: Dp = 28.dp,
    visualizerPlaying: Boolean = false
) {
    val reducedMotion = LocalReducedMotionEnabled.current
    Surface(
        onClick = onClick,
        interactionSource = interactionSource,
        shape = CircleShape,
        color = containerColor,
        contentColor = contentColor,
        modifier = modifier.semantics { this.contentDescription = contentDescription }
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            if (visualizerPlaying && !reducedMotion) {
                PlaybackButtonSpectrum(Modifier.fillMaxWidth(0.84f).height(54.dp))
            } else {
                AnimatedContent(
                    targetState = icon,
                    transitionSpec = { (fadeIn() + scaleIn(initialScale = 0.75f)) togetherWith
                        (fadeOut() + scaleOut(targetScale = 0.75f)) },
                    label = "transport icon"
                ) { displayedIcon ->
                    Icon(
                        imageVector = displayedIcon,
                        contentDescription = null,
                        modifier = Modifier.size(iconSize)
                    )
                }
            }
        }
    }
}

@Composable
private fun PlaybackButtonSpectrum(modifier: Modifier = Modifier) {
    val color = LocalContentColor.current
    val levels by AudioPlaybackSpectrum.levels.collectAsStateWithLifecycle()
    val animatedLevels = levels.mapIndexed { index, level ->
        animateFloatAsState(
            targetValue = level,
            animationSpec = spring(dampingRatio = 0.85f, stiffness = 2400f),
            label = "visualizer band $index"
        ).value
    }

    Canvas(modifier) {
        val slotWidth = size.width / animatedLevels.size
        val barWidth = slotWidth * 0.52f
        val bassPulse = maxOf(animatedLevels[0], animatedLevels[1])
        animatedLevels.forEachIndexed { index, level ->
            val signal = (level * 0.85f + bassPulse * 0.15f).coerceIn(0f, 1f)
            val barHeight = size.height * (0.11f + signal * 0.83f)
            drawRoundRect(
                color = color.copy(alpha = 0.92f),
                topLeft = Offset(index * slotWidth + (slotWidth - barWidth) / 2f, (size.height - barHeight) / 2f),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(barWidth / 2f)
            )
        }
    }
}
