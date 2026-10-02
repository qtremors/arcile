package dev.qtremors.arcile.feature.audio

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Icon
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
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
    playback: AudioPlaybackState,
    onShowMetadata: () -> Unit,
    onShowQueue: () -> Unit,
    onToggleRepeat: () -> Unit,
    onToggleShuffle: () -> Unit,
    onShowMore: () -> Unit,
    moreMenu: @Composable () -> Unit,
    modifier: Modifier = Modifier
) {
    val buttonColor = MaterialTheme.colorScheme.primaryContainer
    val iconColor = MaterialTheme.colorScheme.onPrimaryContainer
    val compact = LocalConfiguration.current.screenWidthDp < 342
    val buttonSize = 48.dp
    Row(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = if (compact) 12.dp else 30.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            AudioQueueAction(
                icon = Icons.AutoMirrored.Filled.QueueMusic,
                contentDescription = stringResource(R.string.audio_queue),
                shape = RoundedCornerShape(topStart = 50.dp, bottomStart = 50.dp, topEnd = 3.dp, bottomEnd = 3.dp),
                onClick = onShowQueue,
                buttonColor = buttonColor,
                iconColor = iconColor,
                size = buttonSize
            )
            AudioQueueAction(
                icon = Icons.Default.Info,
                contentDescription = stringResource(R.string.audio_metadata),
                shape = RoundedCornerShape(topStart = 3.dp, bottomStart = 3.dp, topEnd = 50.dp, bottomEnd = 50.dp),
                onClick = onShowMetadata,
                buttonColor = buttonColor,
                iconColor = iconColor,
                size = buttonSize
            )
        }
        Spacer(Modifier.size(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            AudioQueueAction(
                icon = Icons.Default.Shuffle,
                contentDescription = stringResource(R.string.audio_shuffle),
                shape = RoundedCornerShape(topStart = 50.dp, bottomStart = 50.dp, topEnd = 3.dp, bottomEnd = 3.dp),
                active = playback.shuffleEnabled,
                onClick = onToggleShuffle,
                buttonColor = buttonColor,
                iconColor = iconColor,
                size = buttonSize
            )
            AudioQueueAction(
                icon = if (playback.repeatMode == AudioRepeatMode.ONE) Icons.Default.RepeatOne else Icons.Default.Repeat,
                contentDescription = stringResource(
                    when (playback.repeatMode) {
                        AudioRepeatMode.OFF -> R.string.audio_repeat
                        AudioRepeatMode.ALL -> R.string.audio_repeat_all
                        AudioRepeatMode.ONE -> R.string.audio_repeat_one
                    }
                ),
                shape = RoundedCornerShape(topStart = 3.dp, bottomStart = 3.dp, topEnd = 50.dp, bottomEnd = 50.dp),
                active = playback.repeatMode != AudioRepeatMode.OFF,
                onClick = onToggleRepeat,
                buttonColor = buttonColor,
                iconColor = iconColor,
                size = buttonSize
            )
        }
        Spacer(Modifier.weight(1f))
        Box {
            Surface(
                onClick = onShowMore,
                shape = CircleShape,
                color = buttonColor,
                contentColor = iconColor,
                modifier = Modifier.size(buttonSize)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.audio_more), modifier = Modifier.size(24.dp))
                }
            }
            moreMenu()
        }
    }
}

@Composable
private fun AudioQueueAction(
    icon: ImageVector,
    contentDescription: String,
    shape: Shape,
    onClick: () -> Unit,
    buttonColor: Color,
    iconColor: Color,
    size: Dp,
    active: Boolean = false
) {
    Surface(
        onClick = onClick,
        shape = shape,
        color = if (active) buttonColor else Color.Transparent,
        contentColor = if (active) iconColor else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
        border = if (active) null else BorderStroke(1.dp, buttonColor.copy(alpha = 0.3f)),
        modifier = Modifier.size(size)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = contentDescription, modifier = Modifier.size(24.dp))
        }
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
        sheetState = rememberBottomSheetState(
            initialValue = SheetValue.Hidden,
            enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded)
        ),
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
                formatFileSize(androidx.compose.ui.platform.LocalContext.current, track.file.size)
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
                track.file.reference
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
