package dev.qtremors.arcile.feature.audio

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.TextButton
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import dev.qtremors.arcile.core.ui.theme.ExpressiveShapes
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.qtremors.arcile.core.storage.domain.AudioTrack
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AudioMetadataEditorSheet(
    track: AudioTrack,
    editor: AudioTagEditor,
    onSaved: (AudioTrack) -> Unit,
    onDismiss: () -> Unit,
    lyricsOnly: Boolean = false
) {
    val trackKey = track.file.reference
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

    val content: @Composable () -> Unit = {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (lyricsOnly) {
                    IconButton(onClick = { if (!saving) onDismiss() }, enabled = !saving) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack,
                            stringResource(R.string.audio_back_to_player))
                    }
                }
                Text(stringResource(if (lyricsOnly) R.string.audio_lyrics_editor
                    else R.string.audio_edit_music_details),
                    style = MaterialTheme.typography.titleLarge)
            }
            Text(
                track.file.name,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (!lyricsOnly) {
                AudioEditorSection(stringResource(R.string.audio_metadata)) {
                AudioTextField(title, { title = it }, label = { Text(stringResource(R.string.audio_metadata_title)) },
                    modifier = Modifier.fillMaxWidth(), singleLine = true)
                AudioTextField(artist, { artist = it }, label = { Text(stringResource(R.string.audio_metadata_artist)) },
                    modifier = Modifier.fillMaxWidth(), singleLine = true)
                AudioTextField(album, { album = it }, label = { Text(stringResource(R.string.audio_metadata_album)) },
                    modifier = Modifier.fillMaxWidth(), singleLine = true)
                AudioTextField(albumArtist, { albumArtist = it }, label = { Text(stringResource(R.string.audio_metadata_album_artist)) },
                    modifier = Modifier.fillMaxWidth(), singleLine = true)
                AudioTextField(genre, { genre = it }, label = { Text(stringResource(R.string.audio_metadata_genre)) },
                    modifier = Modifier.fillMaxWidth(), singleLine = true)
                AudioTextField(trackNumber, { trackNumber = it.filter(Char::isDigit) },
                    label = { Text(stringResource(R.string.audio_metadata_track)) }, modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
                AudioTextField(discNumber, { discNumber = it.filter(Char::isDigit) },
                    label = { Text(stringResource(R.string.audio_metadata_disc)) }, modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
                AudioTextField(year, { year = it.filter(Char::isDigit) },
                    label = { Text(stringResource(R.string.audio_metadata_year)) }, modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
                }
                AudioEditorSection(stringResource(R.string.audio_artwork_section)) {
                    FilledTonalButton(onClick = { chooseArtwork.launch("image/*") },
                        modifier = Modifier.fillMaxWidth().height(56.dp), shape = ExpressiveShapes.medium) {
                        Text(stringResource(if (artworkUri == null) R.string.audio_choose_artwork
                            else R.string.audio_artwork_selected))
                    }
                    TextButton(onClick = {
                        artworkUri = null
                        removeArtwork = true
                    }, modifier = Modifier.fillMaxWidth(), shape = ExpressiveShapes.medium) {
                        Text(stringResource(if (removeArtwork) R.string.audio_artwork_removed
                        else R.string.audio_remove_artwork)) }
                }
            }
            AudioEditorSection(stringResource(R.string.audio_lyrics)) {
            AudioTextField(
                value = plainLyrics,
                onValueChange = { plainLyrics = it },
                label = { Text(stringResource(R.string.audio_lyrics)) },
                enabled = !loading && !saving && lyricsLoaded,
                modifier = Modifier.fillMaxWidth(),
                minLines = 6
            )
            AudioTextField(
                value = syncedLyrics,
                onValueChange = { syncedLyrics = it },
                label = { Text(stringResource(R.string.audio_timed_lyrics)) },
                enabled = !loading && !saving && lyricsLoaded,
                supportingText = { Text(stringResource(R.string.audio_timed_lyrics_hint)) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 6
            )
            }
            error?.let {
                Text(it, color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium)
            }
            Text(
                stringResource(R.string.audio_file_write_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(onClick = ::save, enabled = !saving && !loading && lyricsLoaded,
                modifier = Modifier.fillMaxWidth().height(56.dp), shape = ExpressiveShapes.medium) {
                Text(stringResource(R.string.audio_save_to_file))
            }
            Spacer(Modifier.height(12.dp))
        }
    }
    if (lyricsOnly) {
        Dialog(onDismissRequest = { if (!saving) onDismiss() },
            properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                Column(Modifier.fillMaxSize().statusBarsPadding()) { content() }
            }
        }
    } else {
        ModalBottomSheet(onDismissRequest = { if (!saving) onDismiss() }) { content() }
    }
}

@Composable
private fun AudioEditorSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(title, modifier = Modifier.padding(top = 8.dp), style = MaterialTheme.typography.titleMedium)
        content()
    }
}
