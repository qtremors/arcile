package dev.qtremors.arcile.feature.audio

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.qtremors.arcile.core.storage.domain.AudioTrack
import dev.qtremors.arcile.core.ui.ArcileDropdownMenu
import dev.qtremors.arcile.core.ui.ArcileDropdownMenuItem
import dev.qtremors.arcile.core.ui.theme.bounceClickable

import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.FolderZip

@Composable
internal fun AudioPlayerTopBar(
    track: AudioTrack,
    showMenu: Boolean,
    onCollapse: () -> Unit,
    onShowMenu: () -> Unit,
    onDismissMenu: () -> Unit,
    onEdit: () -> Unit,
    onOpenWith: () -> Unit,
    onShare: (() -> Unit)? = null,
    onCopy: (() -> Unit)? = null,
    onCut: (() -> Unit)? = null,
    onRename: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
    onProperties: (() -> Unit)? = null,
    onArchive: (() -> Unit)? = null,
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
            val menuItems = buildList<@Composable () -> Unit> {
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
    }
}

@Composable
internal fun AudioPlaybackControls(
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
    icon: ImageVector,
    contentDescription: String,
    width: Dp,
    height: Dp,
    containerColor: Color,
    contentColor: Color,
    onClick: () -> Unit,
    iconSize: Dp = 28.dp,
    shape: Shape = RoundedCornerShape(22.dp)
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
    icon: ImageVector,
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
