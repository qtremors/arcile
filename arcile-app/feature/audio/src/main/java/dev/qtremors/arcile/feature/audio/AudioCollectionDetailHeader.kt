package dev.qtremors.arcile.feature.audio

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.unit.dp
import dev.qtremors.arcile.core.storage.domain.AudioTrack

@Composable
internal fun AudioCollectionDetailHeader(
    folder: AudioFolder,
    onPlayAll: () -> Unit,
    onSelectAll: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tracks = folder.tracks
    val type = when (folder.kind) {
        AudioFolderKind.Directory -> stringResource(R.string.audio_folder_type)
        AudioFolderKind.Favorites -> stringResource(R.string.audio_favorites_type)
        AudioFolderKind.Artist -> stringResource(R.string.audio_artist_type)
        AudioFolderKind.Album -> stringResource(R.string.audio_album_type)
        AudioFolderKind.Genre -> stringResource(R.string.audio_genre_type)
        AudioFolderKind.Playlist -> stringResource(R.string.audio_playlist_type)
    }
    val details = buildList {
        add(pluralStringResource(R.plurals.audio_song_count, tracks.size, tracks.size))
        tracks.sumOf(AudioTrack::durationMs).takeIf { it > 0L }?.let {
            add(formatAudioDuration(it))
        }
        if (folder.kind == AudioFolderKind.Album) {
            tracks.mapNotNull(AudioTrack::year).maxOrNull()?.let { add(it.toString()) }
        }
        if (folder.kind == AudioFolderKind.Artist) {
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
                folder.coverTrack?.let { AudioArtwork(it, Modifier.size(104.dp)) }
                    ?: Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = MaterialTheme.shapes.large,
                        modifier = Modifier.size(104.dp)
                    ) {
                        androidx.compose.foundation.layout.Box(contentAlignment = Alignment.Center) {
                            Icon(
                                when (folder.kind) {
                                    AudioFolderKind.Directory -> Icons.Default.Folder
                                    AudioFolderKind.Artist -> Icons.Default.Person
                                    AudioFolderKind.Album -> Icons.Default.Album
                                    AudioFolderKind.Playlist -> Icons.Default.LibraryMusic
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
                    Text(folder.title.ifBlank { stringResource(R.string.audio_favorites_type) },
                        style = MaterialTheme.typography.headlineSmall,
                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                    folder.subtitle?.takeIf(String::isNotBlank)?.let {
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
