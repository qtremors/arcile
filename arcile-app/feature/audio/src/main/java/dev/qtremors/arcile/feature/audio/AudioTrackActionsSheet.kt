package dev.qtremors.arcile.feature.audio

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.qtremors.arcile.core.storage.domain.AudioTrack

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AudioTrackActionsSheet(
    track: AudioTrack,
    isFavorite: Boolean,
    onDismiss: () -> Unit,
    onToggleFavorite: () -> Unit,
    onEditTags: () -> Unit,
    onEditAudio: () -> Unit,
    onAddToPlaylist: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.navigationBarsPadding().padding(bottom = 12.dp)) {
            Text(
                track.displayTitle,
                style = MaterialTheme.typography.titleLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
            )
            HorizontalDivider(Modifier.padding(horizontal = 24.dp, vertical = 6.dp))
            ListItem(
                headlineContent = {
                    Text(stringResource(if (isFavorite) R.string.audio_remove_favorite
                        else R.string.audio_add_favorite))
                },
                leadingContent = {
                    Icon(if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = null)
                },
                modifier = Modifier.fillMaxWidth().clickable(onClick = onToggleFavorite)
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.audio_add_to_playlist)) },
                leadingContent = { Icon(Icons.Default.LibraryMusic, contentDescription = null) },
                modifier = Modifier.fillMaxWidth().clickable(onClick = onAddToPlaylist)
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.audio_edit_music_details)) },
                leadingContent = { Icon(Icons.Default.Edit, contentDescription = null) },
                modifier = Modifier.fillMaxWidth().clickable(onClick = onEditTags)
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.audio_edit_audio)) },
                leadingContent = { Icon(Icons.Default.Tune, contentDescription = null) },
                modifier = Modifier.fillMaxWidth().clickable(onClick = onEditAudio)
            )
        }
    }
}
