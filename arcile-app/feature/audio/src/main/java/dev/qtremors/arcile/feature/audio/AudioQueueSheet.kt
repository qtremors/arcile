package dev.qtremors.arcile.feature.audio

import dev.qtremors.arcile.core.ui.audio.AudioTextField
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.res.stringResource
import dev.qtremors.arcile.core.storage.domain.AudioTrack

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AudioQueueSheet(
    queue: List<AudioTrack>,
    currentMediaId: String?,
    shuffleEnabled: Boolean,
    onTrackClick: (String) -> Unit,
    onMoveTrack: (String, Int) -> Unit,
    onRemoveTrack: (String) -> Unit,
    onClearQueue: () -> Unit,
    onSaveQueue: (String, List<String>) -> Unit,
    onDismiss: () -> Unit
) {
    var showSaveDialog by remember { mutableStateOf(false) }
    dev.qtremors.arcile.core.ui.audio.AudioQueueSheet(
        queue, currentMediaId, shuffleEnabled, onTrackClick, onMoveTrack, onRemoveTrack, onClearQueue, onDismiss,
        artwork = { track, modifier -> AudioArtwork(track, modifier) },
        onSaveQueue = { showSaveDialog = true }
    )
    if (showSaveDialog) {
        var name by rememberSaveable { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showSaveDialog = false },
            title = { Text(stringResource(R.string.audio_save_queue)) },
            text = {
                AudioTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.audio_playlist_name)) },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onSaveQueue(name.trim(), queue.map { it.file.reference })
                        showSaveDialog = false
                    },
                    enabled = name.isNotBlank()
                ) { Text(stringResource(R.string.audio_save)) }
            },
            dismissButton = {
                TextButton(onClick = { showSaveDialog = false }) {
                    Text(stringResource(dev.qtremors.arcile.core.ui.R.string.cancel))
                }
            }
        )
    }
}
