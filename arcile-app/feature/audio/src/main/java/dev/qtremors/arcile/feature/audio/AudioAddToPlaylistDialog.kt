package dev.qtremors.arcile.feature.audio

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

@Composable
internal fun AudioAddToPlaylistDialog(
    playlists: List<AudioPlaylist>,
    onAdd: (String) -> Unit,
    onCreate: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var newName by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.audio_add_to_playlist)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (playlists.isEmpty()) {
                    Text(stringResource(R.string.audio_create_playlist_to_save),
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 240.dp)) {
                        items(playlists, key = AudioPlaylist::id) { playlist ->
                            Row(
                                modifier = Modifier.fillMaxWidth().clickable { onAdd(playlist.id) }
                                    .padding(vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(Icons.Default.PlaylistAdd, contentDescription = null)
                                Text(playlist.name, style = MaterialTheme.typography.bodyLarge)
                            }
                        }
                    }
                }
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text(stringResource(R.string.audio_playlist_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onCreate(newName.trim()) }, enabled = newName.isNotBlank()) {
                Text(stringResource(R.string.audio_create_and_add))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) {
            Text(stringResource(dev.qtremors.arcile.core.ui.R.string.cancel))
        } }
    )
}
