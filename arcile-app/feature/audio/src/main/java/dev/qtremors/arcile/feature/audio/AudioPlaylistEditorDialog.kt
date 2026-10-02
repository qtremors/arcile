package dev.qtremors.arcile.feature.audio

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.qtremors.arcile.core.storage.domain.AudioTrack

@Composable
internal fun AudioPlaylistEditorDialog(
    playlist: AudioPlaylist,
    tracks: List<AudioTrack>,
    onSave: (List<String>) -> Unit,
    onDismiss: () -> Unit
) {
    val ordered = remember(playlist.id) {
        mutableStateListOf<String>().apply { addAll(playlist.trackPaths) }
    }
    val byId = remember(tracks) { tracks.associateBy { it.file.reference } }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.audio_edit_playlist, playlist.name)) },
        text = {
            if (ordered.isEmpty()) {
                Text(stringResource(R.string.audio_empty_playlist_edit))
            } else {
                LazyColumn(modifier = Modifier.heightIn(max = 420.dp)) {
                    itemsIndexed(ordered, key = { _, id -> id }) { index, id ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    byId[id]?.displayTitle ?: stringResource(R.string.audio_unavailable_song),
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                byId[id]?.artist?.let {
                                    Text(
                                        it,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            IconButton(
                                onClick = {
                                    if (index > 0) {
                                        val previous = ordered[index - 1]
                                        ordered[index - 1] = id
                                        ordered[index] = previous
                                    }
                                },
                                enabled = index > 0
                            ) {
                                Icon(Icons.Default.ArrowUpward,
                                    contentDescription = stringResource(R.string.audio_move_up))
                            }
                            IconButton(
                                onClick = {
                                    if (index < ordered.lastIndex) {
                                        val next = ordered[index + 1]
                                        ordered[index + 1] = id
                                        ordered[index] = next
                                    }
                                },
                                enabled = index < ordered.lastIndex
                            ) {
                                Icon(Icons.Default.ArrowDownward,
                                    contentDescription = stringResource(R.string.audio_move_down))
                            }
                            IconButton(onClick = { ordered.removeAt(index) }) {
                                Icon(Icons.Default.Close,
                                    contentDescription = stringResource(R.string.audio_remove_from_playlist))
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(ordered.toList()) }) {
                Text(stringResource(R.string.audio_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(dev.qtremors.arcile.core.ui.R.string.cancel))
            }
        }
    )
}
