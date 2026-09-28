package dev.qtremors.arcile.feature.audio

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.qtremors.arcile.core.storage.domain.AudioTrack
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AudioMetadataEditorSheet(
    track: AudioTrack,
    editor: AudioTagEditor,
    onSaved: (AudioTrack) -> Unit,
    onDismiss: () -> Unit
) {
    val trackKey = track.file.absolutePath
    var title by rememberSaveable(trackKey) { mutableStateOf(track.title) }
    var artist by rememberSaveable(trackKey) { mutableStateOf(track.artist.orEmpty()) }
    var album by rememberSaveable(trackKey) { mutableStateOf(track.album.orEmpty()) }
    var albumArtist by rememberSaveable(trackKey) { mutableStateOf(track.albumArtist.orEmpty()) }
    var genre by rememberSaveable(trackKey) { mutableStateOf(track.genre.orEmpty()) }
    var trackNumber by rememberSaveable(trackKey) { mutableStateOf(track.trackNumber?.toString().orEmpty()) }
    var discNumber by rememberSaveable(trackKey) { mutableStateOf(track.discNumber?.toString().orEmpty()) }
    var year by rememberSaveable(trackKey) { mutableStateOf(track.year?.toString().orEmpty()) }
    var plainLyrics by rememberSaveable(trackKey) { mutableStateOf("") }
    var syncedLyrics by rememberSaveable(trackKey) { mutableStateOf("") }
    var artworkUri by rememberSaveable(trackKey) { mutableStateOf<String?>(null) }
    var removeArtwork by rememberSaveable(trackKey) { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var lyricsLoaded by remember(trackKey) { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val chooseArtwork = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) {
        artworkUri = it?.toString()
        if (it != null) removeArtwork = false
    }

    LaunchedEffect(trackKey) {
        loading = true
        runCatching { editor.lyrics(track) }.onSuccess { lyrics ->
            plainLyrics = lyrics.plain.orEmpty()
            syncedLyrics = lyrics.syncedLrc.orEmpty()
            lyricsLoaded = true
        }.onFailure { error = it.message ?: "Lyrics could not be loaded" }
        loading = false
    }

    fun save() {
        if (saving || loading || !lyricsLoaded) return
        if (listOf(trackNumber, discNumber, year).any {
                it.isNotBlank() && it.toIntOrNull() == null
            }
        ) {
            error = "Track, disc, and year must be valid numbers"
            return
        }
        if (syncedLyrics.isNotBlank() && parseLrc(syncedLyrics).isEmpty()) {
            error = "Timed lyrics need at least one [mm:ss.xx] cue"
            return
        }
        val edit = AudioMetadataEdit(
            title = title.trim(),
            artist = artist.trim(),
            album = album.trim(),
            albumArtist = albumArtist.trim(),
            genre = genre.trim(),
            trackNumber = trackNumber.trim().toIntOrNull(),
            discNumber = discNumber.trim().toIntOrNull(),
            year = year.trim().toIntOrNull(),
            artworkUri = artworkUri,
            removeArtwork = removeArtwork,
            lyrics = AudioLyrics(
                plain = plainLyrics.takeIf(String::isNotBlank),
                syncedLrc = syncedLyrics.takeIf(String::isNotBlank)
            )
        )
        error = null
        saving = true
        scope.launch {
            editor.editMetadata(track, edit).onSuccess(onSaved)
                .onFailure { failure ->
                    error = failure.message ?: "The edit could not be saved"
                }
            saving = false
        }
    }

    ModalBottomSheet(onDismissRequest = { if (!saving) onDismiss() }) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(stringResource(R.string.audio_edit_music_details),
                style = MaterialTheme.typography.titleLarge)
            Text(
                track.file.name,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            OutlinedTextField(title, { title = it }, label = { Text("Title") },
                modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(artist, { artist = it }, label = { Text("Artist") },
                modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(album, { album = it }, label = { Text("Album") },
                modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(albumArtist, { albumArtist = it }, label = { Text("Album artist") },
                modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(genre, { genre = it }, label = { Text("Genre") },
                modifier = Modifier.fillMaxWidth(), singleLine = true)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(trackNumber, { trackNumber = it.filter(Char::isDigit) },
                    label = { Text("Track") }, modifier = Modifier.weight(1f), singleLine = true)
                OutlinedTextField(discNumber, { discNumber = it.filter(Char::isDigit) },
                    label = { Text("Disc") }, modifier = Modifier.weight(1f), singleLine = true)
                OutlinedTextField(year, { year = it.filter(Char::isDigit) },
                    label = { Text("Year") }, modifier = Modifier.weight(1f), singleLine = true)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { chooseArtwork.launch("image/*") }) {
                    Text(stringResource(if (artworkUri == null) R.string.audio_choose_artwork
                        else R.string.audio_artwork_selected))
                }
                OutlinedButton(onClick = {
                    artworkUri = null
                    removeArtwork = true
                }) { Text(stringResource(if (removeArtwork) R.string.audio_artwork_removed
                    else R.string.audio_remove_artwork)) }
            }
            OutlinedTextField(
                value = plainLyrics,
                onValueChange = { plainLyrics = it },
                label = { Text(stringResource(R.string.audio_lyrics)) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 4
            )
            OutlinedTextField(
                value = syncedLyrics,
                onValueChange = { syncedLyrics = it },
                label = { Text(stringResource(R.string.audio_timed_lyrics)) },
                supportingText = { Text(stringResource(R.string.audio_timed_lyrics_hint)) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 4
            )
            error?.let {
                Text(it, color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium)
            }
            Text(
                stringResource(R.string.audio_file_write_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(onClick = ::save, enabled = !saving && !loading && lyricsLoaded) {
                Text(stringResource(R.string.audio_save_to_file))
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}
