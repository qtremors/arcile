package dev.qtremors.arcile.feature.audio

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
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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

@Composable
internal fun AudioMiniPlayer(
    track: AudioTrack,
    playback: AudioPlaybackState,
    onExpand: () -> Unit,
    onTogglePlayback: () -> Unit,
    onNext: () -> Unit
) {
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 4.dp,
        shadowElevation = 4.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(68.dp)
                .padding(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AudioArtwork(
                track,
                Modifier
                    .size(56.dp)
                    .bounceClickable(onClick = onExpand)
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
                        icon = if (playback.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
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
    }
}

@Composable
internal fun AudioSelectionActionsBar(
    canUseSingleTrackActions: Boolean,
    onPlay: () -> Unit,
    onCopy: () -> Unit,
    onCut: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onShare: () -> Unit,
    onProperties: () -> Unit,
    onCreateZip: () -> Unit,
    onOpenWith: () -> Unit,
    onShowFolder: () -> Unit
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
                        add {
                            ArcileDropdownMenuItem(
                                text = stringResource(R.string.audio_show_folder),
                                leadingIcon = {
                                    Icon(Icons.Default.FolderOpen, contentDescription = null)
                                },
                                onClick = {
                                    showMenu = false
                                    onShowFolder()
                                }
                            )
                        }
                    }
                }
            )
        }
    }
}
