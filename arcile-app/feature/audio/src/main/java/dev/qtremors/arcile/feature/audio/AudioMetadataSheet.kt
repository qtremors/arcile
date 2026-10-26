package dev.qtremors.arcile.feature.audio

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.qtremors.arcile.core.storage.domain.AudioTrack

import dev.qtremors.arcile.core.ui.audio.AudioListeningInfo

@Composable
internal fun AudioMetadataSheet(
    track: AudioTrack, listeningStore: AudioListeningStore, tagEditor: AudioTagEditor,
    onEditTags: () -> Unit, onEditLyrics: () -> Unit, onDismiss: () -> Unit
) {
    val recordFlow = remember(track.file.reference, listeningStore) { listeningStore.trackRecord(track.file.reference) }
    val record by recordFlow.collectAsStateWithLifecycle(initialValue = null)
    var fileDetails by remember(track) { mutableStateOf<AudioFileDetails?>(null) }
    LaunchedEffect(track) { fileDetails = tagEditor.fileDetails(track) }
    dev.qtremors.arcile.core.ui.audio.AudioMetadataSheet(
        track, fileDetails, onDismiss,
        artwork = { item, modifier -> AudioArtwork(item, modifier) },
        onEditTags = onEditTags, onEditLyrics = onEditLyrics,
        listening = AudioListeningInfo(record?.playCount?.toLong() ?: 0L, record?.lastPlayedAt)
    )
}
