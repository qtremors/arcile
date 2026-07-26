package dev.qtremors.arcile.feature.audio

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.qtremors.arcile.core.presentation.formatFileSize
import dev.qtremors.arcile.core.storage.domain.AudioTrack
import dev.qtremors.arcile.core.ui.SplitButtonGroup
import dev.qtremors.arcile.core.ui.ToolbarAction
import java.text.DateFormat
import java.util.Date

@Composable
internal fun AudioPlayerBottomActions(
    onShowMetadata: () -> Unit,
    onShare: () -> Unit,
    onShowContainingFolder: () -> Unit,
    onShowQueue: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        SplitButtonGroup(
            actions = listOf(
                ToolbarAction(
                    icon = Icons.Default.Info,
                    contentDescription = stringResource(R.string.audio_metadata),
                    onClick = onShowMetadata
                ),
                ToolbarAction(
                    icon = Icons.Default.Share,
                    contentDescription = stringResource(R.string.audio_share),
                    onClick = onShare
                ),
                ToolbarAction(
                    icon = Icons.Default.FolderOpen,
                    contentDescription = stringResource(R.string.audio_show_folder),
                    onClick = onShowContainingFolder
                ),
                ToolbarAction(
                    icon = Icons.AutoMirrored.Filled.QueueMusic,
                    contentDescription = stringResource(R.string.audio_queue),
                    onClick = onShowQueue
                )
            ),
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurface,
            height = 48.dp,
            minWidth = 48.dp,
            iconSize = 24.dp
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AudioMetadataSheet(
    track: AudioTrack,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                stringResource(R.string.audio_metadata),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )
            AudioMetadataRow(stringResource(R.string.audio_metadata_title), track.displayTitle)
            AudioMetadataRow(
                stringResource(R.string.audio_metadata_artist),
                track.artist ?: stringResource(R.string.audio_unknown_artist)
            )
            track.album?.let {
                AudioMetadataRow(stringResource(R.string.audio_metadata_album), it)
            }
            AudioMetadataRow(
                stringResource(R.string.audio_metadata_duration),
                formatAudioDuration(track.durationMs)
            )
            AudioMetadataRow(
                stringResource(R.string.audio_metadata_size),
                formatFileSize(track.file.size)
            )
            AudioMetadataRow(
                stringResource(R.string.audio_metadata_modified),
                DateFormat.getDateTimeInstance().format(Date(track.file.lastModified))
            )
            AudioMetadataRow(
                stringResource(R.string.audio_metadata_format),
                track.file.mimeType.orEmpty().ifBlank { track.file.extension.uppercase() }
            )
            AudioMetadataRow(
                stringResource(R.string.audio_metadata_path),
                track.file.absolutePath
            )
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun AudioMetadataRow(label: String, value: String) {
    Column {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}
