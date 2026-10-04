package dev.qtremors.arcile.feature.audio

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.qtremors.arcile.core.storage.domain.AudioTrack

@Composable
internal fun AudioCollectionDetailHeader(
    collection: AudioCollection,
    onPlayAll: () -> Unit,
    onSelectAll: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (collection.kind == AudioCollectionType.Album) {
        AudioAlbumDetailHeader(collection, onPlayAll, onSelectAll, modifier)
        return
    }
    val tracks = collection.tracks
    val type = when (collection.kind) {
        AudioCollectionType.Directory -> stringResource(R.string.audio_folder_type)
        AudioCollectionType.Favorites -> stringResource(R.string.audio_favorites_type)
        AudioCollectionType.Artist -> stringResource(R.string.audio_artist_type)
        AudioCollectionType.Album -> stringResource(R.string.audio_album_type)
        AudioCollectionType.Genre -> stringResource(R.string.audio_genre_type)
        AudioCollectionType.Playlist -> stringResource(R.string.audio_playlist_type)
    }
    val details = buildList {
        add(pluralStringResource(R.plurals.audio_song_count, tracks.size, tracks.size))
        tracks.sumOf(AudioTrack::durationMs).takeIf { it > 0L }?.let {
            add(formatAudioDuration(it))
        }
        if (collection.kind == AudioCollectionType.Album) {
            tracks.mapNotNull(AudioTrack::year).maxOrNull()?.let { add(it.toString()) }
        }
        if (collection.kind == AudioCollectionType.Artist) {
            tracks.mapNotNull(AudioTrack::album).distinct().size.takeIf { it > 0 }?.let {
                add(pluralStringResource(R.plurals.audio_album_count, it, it))
            }
        }
    }.joinToString(" • ")
    Surface(
        modifier = modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 12.dp),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                collection.coverTrack?.let { AudioArtwork(it, Modifier.size(104.dp)) }
                    ?: Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = MaterialTheme.shapes.large,
                        modifier = Modifier.size(104.dp)
                    ) {
                        androidx.compose.foundation.layout.Box(contentAlignment = Alignment.Center) {
                            Icon(
                                when (collection.kind) {
                                    AudioCollectionType.Directory -> Icons.Default.Folder
                                    AudioCollectionType.Artist -> Icons.Default.Person
                                    AudioCollectionType.Album -> Icons.Default.Album
                                    AudioCollectionType.Playlist -> Icons.Default.LibraryMusic
                                    else -> Icons.Default.MusicNote
                                },
                                contentDescription = null,
                                modifier = Modifier.size(48.dp)
                            )
                        }
                    }
                Column(modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(type.uppercase(), style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold)
                    Text(collection.title.ifBlank { stringResource(R.string.audio_favorites_type) },
                        style = MaterialTheme.typography.headlineSmall,
                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                    collection.subtitle?.takeIf(String::isNotBlank)?.let {
                        Text(it, style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Text(details, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (tracks.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onPlayAll) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.audio_play_collection))
                    }
                    OutlinedButton(onClick = onSelectAll) {
                        Text(stringResource(R.string.audio_select_collection_songs))
                    }
                }
            } else {
                Text(stringResource(R.string.audio_empty_collection_type, type.lowercase()),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun AudioAlbumDetailHeader(
    album: AudioCollection,
    onPlayAll: () -> Unit,
    onSelectAll: () -> Unit,
    modifier: Modifier
) {
    BoxWithConstraints(modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 20.dp)) {
        val artworkSize = minOf(maxWidth, 280.dp)
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            album.coverTrack?.let {
                AudioArtwork(it, Modifier.size(artworkSize).aspectRatio(1f),
                    MaterialTheme.shapes.extraLarge)
            }
            Text(album.title, style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Text(buildList {
                album.subtitle?.takeIf(String::isNotBlank)?.let(::add)
                album.tracks.mapNotNull(AudioTrack::year).maxOrNull()?.let { add(it.toString()) }
                add(formatAudioDuration(album.tracks.sumOf(AudioTrack::durationMs)))
            }.joinToString(" • "), style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onPlayAll, enabled = album.tracks.isNotEmpty()) {
                    Icon(Icons.Default.PlayArrow, null)
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.audio_play_collection))
                }
                OutlinedButton(onClick = onSelectAll, enabled = album.tracks.isNotEmpty()) {
                    Text(stringResource(R.string.audio_select_collection_songs))
                }
            }
            Text(pluralStringResource(R.plurals.audio_song_count, album.tracks.size, album.tracks.size),
                Modifier.fillMaxWidth(), style = MaterialTheme.typography.titleMedium)
        }
    }
}
