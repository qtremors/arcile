package dev.qtremors.arcile.core.ui.audio

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Lyrics
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.qtremors.arcile.core.ui.R
import dev.qtremors.arcile.core.storage.domain.AudioTrack
import dev.qtremors.arcile.core.ui.ArcileDropdownMenu
import dev.qtremors.arcile.core.ui.ArcileDropdownMenuItem
import dev.qtremors.arcile.core.ui.theme.LocalReducedMotionEnabled
import dev.qtremors.arcile.core.ui.audio.AudioTransportControls

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
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Bedtime

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
    onCollapse: () -> Unit,
    lyricsVisible: Boolean,
    onShowLyrics: (() -> Unit)?,
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
        IconButton(onClick = onCollapse) {
            Icon(Icons.Default.KeyboardArrowDown,
                contentDescription = stringResource(R.string.audio_collapse_player))
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
        if (onShowLyrics != null) IconButton(onClick = onShowLyrics) {
            Icon(if (lyricsVisible) Icons.Default.Album else Icons.Default.Lyrics,
                contentDescription = stringResource(
                    if (lyricsVisible) R.string.audio_show_artwork else R.string.audio_lyrics
                ))
        }
    }
}

@Composable
internal fun AudioPlayerMoreMenu(
    showMenu: Boolean,
    onDismissMenu: () -> Unit,
    visualizerEnabled: Boolean,
    onToggleVisualizer: (() -> Unit)?,
    onShowPlaybackSettings: () -> Unit,
    onShowSleepTimer: () -> Unit,
    onEdit: (() -> Unit)?,
    onEditTags: (() -> Unit)?,
    onShowLyrics: (() -> Unit)?,
    onOpenWith: (() -> Unit)?,
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
                        text = stringResource(R.string.audio_playback_settings),
                        leadingIcon = { Icon(Icons.Default.Speed, contentDescription = null) },
                        onClick = { onDismissMenu(); onShowPlaybackSettings() }
                    )
                }
                add {
                    ArcileDropdownMenuItem(
                        text = stringResource(R.string.audio_sleep_timer),
                        leadingIcon = { Icon(Icons.Default.Bedtime, contentDescription = null) },
                        onClick = { onDismissMenu(); onShowSleepTimer() }
                    )
                }
                if (onToggleVisualizer != null) add {
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
                if (onEdit != null) add {
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
                if (onEditTags != null) add {
                    ArcileDropdownMenuItem(
                        text = stringResource(R.string.audio_edit_tags),
                        leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                        onClick = { onDismissMenu(); onEditTags() }
                    )
                }
                if (onShowLyrics != null) add {
                    ArcileDropdownMenuItem(
                        text = stringResource(R.string.audio_lyrics),
                        leadingIcon = { Icon(Icons.Default.MusicNote, contentDescription = null) },
                        onClick = { onDismissMenu(); onShowLyrics() }
                    )
                }
                if (onOpenWith != null) add {
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
    spectrumLevels: List<Float>?,
    onTogglePlayback: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit
) {
    val showSpectrum = spectrumLevels != null && !LocalReducedMotionEnabled.current
    AudioTransportControls(
        isPlaying = playback.isPlaying,
        previousLabel = stringResource(R.string.audio_previous),
        playPauseLabel = stringResource(if (playback.isPlaying) R.string.audio_pause else R.string.audio_play),
        nextLabel = stringResource(R.string.audio_next),
        onPrevious = onPrevious,
        onTogglePlayback = onTogglePlayback,
        onNext = onNext,
        playingContent = if (showSpectrum) {
            { PlaybackButtonSpectrum(spectrumLevels.orEmpty(), Modifier.fillMaxWidth(0.84f).height(54.dp)) }
        } else null
    )
}

@Composable
private fun PlaybackButtonSpectrum(levels: List<Float>, modifier: Modifier = Modifier) {
    val color = LocalContentColor.current
    val animatedLevels = levels.mapIndexed { index, level ->
        animateFloatAsState(
            targetValue = level,
            animationSpec = spring(dampingRatio = 0.85f, stiffness = 2400f),
            label = "visualizer band $index"
        ).value
    }

    Canvas(modifier) {
        if (animatedLevels.size < 2) return@Canvas
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
