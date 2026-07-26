package dev.qtremors.arcile.feature.audio

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.qtremors.arcile.core.presentation.formatFileSize

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun AudioFolderListItem(
    folder: AudioFolder,
    zoom: Float,
    isSelected: Boolean,
    showDetails: Boolean,
    canPaste: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onPaste: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 1.dp)
            .clip(MaterialTheme.shapes.extraLarge)
            .background(
                if (isSelected) {
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                } else {
                    androidx.compose.ui.graphics.Color.Transparent
                }
            )
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AudioArtwork(folder.coverTrack, Modifier.size((48f * zoom).dp))
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                folder.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                stringResource(R.string.audio_track_count, folder.tracks.size),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (showDetails) {
                Text(
                    "${folder.subtitle.orEmpty()} • ${formatFileSize(folder.totalSize)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (canPaste && !isSelected) {
            IconButton(onClick = onPaste) {
                Icon(
                    Icons.Default.ContentPaste,
                    contentDescription = stringResource(R.string.audio_paste_here),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        } else if (isSelected) {
            Icon(
                Icons.Default.CheckCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .padding(start = 8.dp)
                    .size(22.dp)
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun AudioFolderGridItem(
    folder: AudioFolder,
    isSelected: Boolean,
    showDetails: Boolean,
    canPaste: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onPaste: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = MaterialTheme.shapes.large
    val itemModifier = Modifier
        .fillMaxWidth()
        .clip(shape)
        .combinedClickable(onClick = onClick, onLongClick = onLongClick)
    Box(modifier = modifier) {
        if (showDetails) {
            Surface(
                shape = shape,
                color = if (isSelected) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceContainerLow
                },
                modifier = itemModifier
            ) {
                Column {
                    AudioArtwork(
                        folder.coverTrack,
                        Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                    )
                    Column(modifier = Modifier.padding(8.dp)) {
                        Text(
                            folder.title,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            stringResource(R.string.audio_track_count, folder.tracks.size),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            formatFileSize(folder.totalSize),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            Box(modifier = itemModifier.aspectRatio(1f)) {
                AudioArtwork(folder.coverTrack, Modifier.fillMaxSize())
                if (isSelected) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
                    )
                }
            }
        }
        if (canPaste && !isSelected) {
            Surface(
                onClick = onPaste,
                shape = androidx.compose.foundation.shape.CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                shadowElevation = 4.dp,
                modifier = Modifier
                    .padding(8.dp)
                    .size(40.dp)
                    .align(Alignment.TopEnd)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.ContentPaste,
                        contentDescription = stringResource(R.string.audio_paste_here)
                    )
                }
            }
        } else if (isSelected) {
            Icon(
                Icons.Default.CheckCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .padding(8.dp)
                    .size(24.dp)
                    .align(Alignment.TopEnd)
            )
        }
    }
}
