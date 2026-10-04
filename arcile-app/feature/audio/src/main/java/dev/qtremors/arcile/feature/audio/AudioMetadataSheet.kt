package dev.qtremors.arcile.feature.audio

import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lyrics
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.vector.ImageVector
import dev.qtremors.arcile.core.ui.theme.ExpressiveShapes
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.qtremors.arcile.core.presentation.formatFileSize
import dev.qtremors.arcile.core.storage.domain.AudioTrack
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private data class AudioFileDetails(val bitrate: Int?, val sampleRate: Int?, val channels: Int?)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AudioMetadataSheet(
    track: AudioTrack,
    listeningStore: AudioListeningStore,
    onEditTags: () -> Unit,
    onEditLyrics: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val recordFlow = remember(track.file.reference, listeningStore) {
        listeningStore.trackRecord(track.file.reference)
    }
    val record by recordFlow.collectAsStateWithLifecycle(initialValue = null)
    var fileDetails by remember(track) { mutableStateOf<AudioFileDetails?>(null) }
    LaunchedEffect(track) {
        fileDetails = withContext(Dispatchers.IO) {
            val extractor = MediaExtractor()
            try {
                val uri = track.file.nodeRef.contentUri
                if (uri != null) extractor.setDataSource(context, Uri.parse(uri), null)
                else extractor.setDataSource(track.file.reference)
                (0 until extractor.trackCount).asSequence().map(extractor::getTrackFormat)
                    .firstOrNull { it.getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true }
                    ?.let { format ->
                        fun number(key: String) = if (format.containsKey(key))
                            format.getInteger(key).takeIf { it > 0 } else null
                        AudioFileDetails(number(MediaFormat.KEY_BIT_RATE),
                            number(MediaFormat.KEY_SAMPLE_RATE), number(MediaFormat.KEY_CHANNEL_COUNT))
                    }
            } catch (_: Exception) {
                null
            } finally {
                extractor.release()
            }
        }
    }
    ModalBottomSheet(onDismissRequest = onDismiss,
        sheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden,
            enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded)),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                AudioArtwork(track, Modifier.size(72.dp), MaterialTheme.shapes.large)
                Column(Modifier.weight(1f)) {
                    Text(track.displayTitle, style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold)
                    Text(track.artist ?: stringResource(R.string.audio_unknown_artist),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                if (maxWidth < 300.dp || LocalDensity.current.fontScale > 1.3f) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        AudioMetadataEditButton(Icons.Default.Lyrics, stringResource(R.string.audio_lyrics_editor),
                            onEditLyrics, Modifier.fillMaxWidth())
                        AudioMetadataEditButton(Icons.Default.Edit, stringResource(R.string.audio_tag_editor),
                            onEditTags, Modifier.fillMaxWidth())
                    }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AudioMetadataEditButton(Icons.Default.Lyrics, stringResource(R.string.audio_lyrics_editor),
                            onEditLyrics, Modifier.weight(1f))
                        AudioMetadataEditButton(Icons.Default.Edit, stringResource(R.string.audio_tag_editor),
                            onEditTags, Modifier.weight(1f))
                    }
                }
            }
            AudioMetadataCard(stringResource(R.string.audio_metadata)) {
                AudioMetadataRow(stringResource(R.string.audio_metadata_album), track.album)
                AudioMetadataRow(stringResource(R.string.audio_metadata_artist), track.artist)
                AudioMetadataRow(stringResource(R.string.audio_metadata_genre), track.genre)
                AudioMetadataRow(stringResource(R.string.audio_metadata_year), track.year?.toString())
                AudioMetadataRow(stringResource(R.string.audio_metadata_track), track.trackNumber?.toString())
                AudioMetadataRow(stringResource(R.string.audio_metadata_disc), track.discNumber?.toString())
            }
            AudioMetadataCard(stringResource(R.string.audio_play_info)) {
                AudioMetadataRow(stringResource(R.string.audio_play_count), (record?.playCount ?: 0).toString())
                AudioMetadataRow(stringResource(R.string.audio_last_played),
                    record?.lastPlayedAt?.takeIf { it > 0L }?.let { DateFormat.getDateTimeInstance().format(Date(it)) }
                        ?: stringResource(R.string.audio_never_played))
            }
            AudioMetadataCard(stringResource(R.string.audio_file_info)) {
                AudioMetadataRow(stringResource(R.string.audio_metadata_format),
                    track.file.mimeType.orEmpty().ifBlank { track.file.extension.uppercase(Locale.ROOT) })
                fileDetails?.bitrate?.let { AudioMetadataRow(stringResource(R.string.audio_bitrate),
                    stringResource(R.string.audio_bitrate_value, it / 1000)) }
                fileDetails?.sampleRate?.let { AudioMetadataRow(stringResource(R.string.audio_sample_rate),
                    stringResource(R.string.audio_sample_rate_value, String.format(Locale.getDefault(), "%.1f", it / 1000f))) }
                fileDetails?.channels?.let { AudioMetadataRow(stringResource(R.string.audio_channels), it.toString()) }
                AudioMetadataRow(stringResource(R.string.audio_metadata_duration), formatAudioDuration(track.durationMs))
                AudioMetadataRow(stringResource(R.string.audio_metadata_size), formatFileSize(context, track.file.size))
                AudioMetadataRow(stringResource(R.string.audio_metadata_path), track.file.reference)
                AudioMetadataRow(stringResource(R.string.audio_metadata_modified),
                    DateFormat.getDateTimeInstance().format(Date(track.file.lastModified)))
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun AudioMetadataEditButton(icon: ImageVector, label: String, onClick: () -> Unit, modifier: Modifier) {
    FilledTonalButton(onClick = onClick, modifier = modifier.height(56.dp),
        shape = ExpressiveShapes.medium, contentPadding = PaddingValues(horizontal = 12.dp)) {
        Icon(icon, null, Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun AudioMetadataCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            content()
        }
    }
}

@Composable
private fun AudioMetadataRow(label: String, value: String?) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(label, Modifier.weight(0.4f), style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        SelectionContainer(Modifier.weight(0.6f)) {
            Text(value?.takeIf(String::isNotBlank) ?: stringResource(R.string.audio_metadata_unknown),
                Modifier.fillMaxWidth(), style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.End)
        }
    }
}
