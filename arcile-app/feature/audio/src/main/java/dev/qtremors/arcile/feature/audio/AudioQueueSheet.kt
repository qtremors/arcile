package dev.qtremors.arcile.feature.audio

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.qtremors.arcile.core.storage.domain.AudioTrack
import dev.qtremors.arcile.core.ui.theme.bounceClickable

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AudioQueueSheet(
    queue: List<AudioTrack>,
    currentIndex: Int,
    onTrackClick: (Int) -> Unit,
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
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    stringResource(R.string.audio_queue),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    stringResource(R.string.audio_queue_count, queue.size),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (queue.isEmpty()) {
                Text(
                    stringResource(R.string.audio_queue_empty),
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 24.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                val listState = androidx.compose.foundation.lazy.rememberLazyListState(
                    initialFirstVisibleItemIndex = (currentIndex - 1).coerceAtLeast(0)
                )
                androidx.compose.runtime.LaunchedEffect(currentIndex) {
                    if (currentIndex in queue.indices) {
                        listState.animateScrollToItem((currentIndex - 1).coerceAtLeast(0))
                    }
                }
                LazyColumn(state = listState) {
                    itemsIndexed(
                        items = queue,
                        key = { _, track -> track.file.absolutePath }
                    ) { index, track ->
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = if (index == currentIndex) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerLow
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .bounceClickable { onTrackClick(index) }
                        ) {
                            Row(
                                modifier = Modifier.padding(8.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                AudioArtwork(
                                    track = track,
                                    modifier = Modifier.size(52.dp)
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        track.displayTitle,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = if (index == currentIndex) {
                                            FontWeight.SemiBold
                                        } else {
                                            FontWeight.Normal
                                        },
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        track.artist
                                            ?: stringResource(R.string.audio_unknown_artist),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                if (index == currentIndex) {
                                    Box(
                                        modifier = Modifier.size(40.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            Icons.Default.Equalizer,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
