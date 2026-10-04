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
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.TextButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
    currentMediaId: String?,
    shuffleEnabled: Boolean,
    onTrackClick: (String) -> Unit,
    onMoveTrack: (String, Int) -> Unit,
    onRemoveTrack: (String) -> Unit,
    onClearQueue: () -> Unit,
    onSaveQueue: (String, List<String>) -> Unit,
    onDismiss: () -> Unit
) {
    var showSaveDialog by remember { mutableStateOf(false) }
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
                    androidx.compose.ui.res.pluralStringResource(R.plurals.audio_queue_count, queue.size, queue.size),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = { showSaveDialog = true }, enabled = queue.isNotEmpty()) {
                    Text(stringResource(R.string.audio_save_queue))
                }
                TextButton(onClick = onClearQueue, enabled = queue.isNotEmpty()) {
                    Text(stringResource(R.string.audio_clear_queue))
                }
            }
            if (queue.isEmpty()) {
                Text(
                    stringResource(R.string.audio_queue_empty),
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 24.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                val currentIndex = queue.indexOfFirst {
                    it.file.reference == currentMediaId
                }
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
                        key = { _, track -> track.file.reference }
                    ) { index, track ->
                        var menuExpanded by remember(track.file.reference) { mutableStateOf(false) }
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = if (index == currentIndex) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerLow
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .bounceClickable { onTrackClick(track.file.reference) }
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
                                Box {
                                    IconButton(onClick = { menuExpanded = true }) {
                                        Icon(
                                            Icons.Default.MoreVert,
                                            contentDescription = stringResource(R.string.audio_queue_item_options)
                                        )
                                    }
                                    DropdownMenu(
                                        expanded = menuExpanded,
                                        onDismissRequest = { menuExpanded = false }
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.audio_move_up)) },
                                            enabled = !shuffleEnabled && index > 0,
                                            onClick = {
                                                menuExpanded = false
                                                onMoveTrack(track.file.reference, -1)
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.audio_move_down)) },
                                            enabled = !shuffleEnabled && index < queue.lastIndex,
                                            onClick = {
                                                menuExpanded = false
                                                onMoveTrack(track.file.reference, 1)
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.audio_remove_from_queue)) },
                                            onClick = {
                                                menuExpanded = false
                                                onRemoveTrack(track.file.reference)
                                            }
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
    if (showSaveDialog) {
        var name by rememberSaveable { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showSaveDialog = false },
            title = { Text(stringResource(R.string.audio_save_queue)) },
            text = {
                AudioTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.audio_playlist_name)) },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onSaveQueue(name.trim(), queue.map { it.file.reference })
                        showSaveDialog = false
                    },
                    enabled = name.isNotBlank()
                ) { Text(stringResource(R.string.audio_save)) }
            },
            dismissButton = {
                TextButton(onClick = { showSaveDialog = false }) {
                    Text(stringResource(dev.qtremors.arcile.core.ui.R.string.cancel))
                }
            }
        )
    }
}
