package dev.qtremors.arcile.feature.audio

import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.LibraryAdd
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Loop
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.io.File
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToLong


@Composable
internal fun SegmentAndWaveformControls(
    selections: List<AudioSelectionRange>,
    activeSelectionId: Long,
    waveformEnabled: Boolean,
    canAdd: Boolean,
    enabled: Boolean,
    onSelect: (Long) -> Unit,
    onAdd: () -> Unit,
    onRemove: (Long) -> Unit,
    onWaveformChanged: (Boolean) -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    stringResource(R.string.audio_editor_segments, selections.size),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                TextButton(onClick = onAdd, enabled = enabled && canAdd) {
                    Icon(Icons.Default.Add, contentDescription = null, Modifier.size(18.dp))
                    Text(stringResource(R.string.audio_editor_add_segment))
                }
                Spacer(Modifier.weight(1f))
                Text(
                    stringResource(R.string.audio_editor_waveform_mode),
                    style = MaterialTheme.typography.labelLarge
                )
                Switch(
                    checked = waveformEnabled,
                    onCheckedChange = onWaveformChanged,
                    enabled = enabled,
                    modifier = Modifier.padding(start = 6.dp)
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                selections.forEachIndexed { index, selection ->
                    FilterChip(
                        selected = selection.id == activeSelectionId,
                        onClick = { onSelect(selection.id) },
                        enabled = enabled,
                        label = { Text("${index + 1}") }
                    )
                }
                if (selections.size > 1) {
                    IconButton(
                        onClick = { onRemove(activeSelectionId) },
                        enabled = enabled,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            Icons.Default.DeleteOutline,
                            contentDescription = stringResource(R.string.audio_editor_remove_segment)
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun AudioMetadataSummary(source: AudioEditorSource) {
    var showDetails by remember { mutableStateOf(false) }
    val artwork = remember(source.metadata.artwork) {
        source.metadata.artwork?.let { bytes ->
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
        }
    }
    if (showDetails) {
        AlertDialog(
            onDismissRequest = { showDetails = false },
            title = { Text(stringResource(R.string.audio_editor_audio_info)) },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    metadataRows(source).forEach { (label, value) ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                label,
                                modifier = Modifier.weight(0.38f),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                value,
                                modifier = Modifier.weight(0.62f),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showDetails = false }) {
                    Text(stringResource(android.R.string.ok))
                }
            }
        )
    }
    Card(
        modifier = Modifier.clickable { showDetails = true },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(58.dp)
                    .background(
                        MaterialTheme.colorScheme.primaryContainer,
                        RoundedCornerShape(12.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (artwork != null) {
                    Image(
                        bitmap = artwork,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp)),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(Icons.Default.AudioFile, contentDescription = null)
                }
            }
            Column(modifier = Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(
                    source.metadata.title ?: source.name,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    listOfNotNull(source.metadata.artist, source.metadata.album)
                        .joinToString(" • ")
                        .ifBlank { formatEditorTime(source.durationMs) },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(Icons.Default.Info, contentDescription = stringResource(R.string.audio_editor_audio_info))
        }
    }
}

@Composable
internal fun ClassicRangeCard(
    source: AudioEditorSource,
    selectionStartMs: Long,
    selectionEndMs: Long,
    enabled: Boolean,
    onSelectionChange: (Long, Long) -> Unit
) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        source.name,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        stringResource(R.string.audio_editor_classic_description),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    formatEditorTime(source.durationMs),
                    style = MaterialTheme.typography.labelLarge
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    formatEditorTime(selectionStartMs),
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    formatEditorTime(selectionEndMs),
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            RangeSlider(
                value = selectionStartMs.toFloat()..selectionEndMs.toFloat(),
                onValueChange = { range ->
                    onSelectionChange(
                        range.start.roundToLong(),
                        range.endInclusive.roundToLong()
                    )
                },
                valueRange = 0f..source.durationMs.toFloat(),
                enabled = enabled
            )
            Text(
                stringResource(
                    R.string.audio_editor_selected_duration,
                    formatEditorTime(selectionEndMs - selectionStartMs)
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
internal fun PrecisionWaveformCard(
    source: AudioEditorSource,
    waveform: AudioWaveform?,
    waveformLoading: Boolean,
    selectionStartMs: Long,
    selectionEndMs: Long,
    allSelections: List<AudioSelectionRange>,
    playheadMs: Long,
    zoom: Float,
    viewportStartMs: Long,
    enabled: Boolean,
    onSelectionChange: (Long, Long) -> Unit,
    onPlayheadChange: (Long) -> Unit,
    onZoomChange: (Float) -> Unit,
    onViewportChange: (Long) -> Unit
) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        source.name,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        formatEditorTime(source.durationMs),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    stringResource(R.string.audio_editor_zoom_value, zoom),
                    style = MaterialTheme.typography.labelLarge
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(176.dp)
                    .background(
                        MaterialTheme.colorScheme.surfaceContainerHighest,
                        RoundedCornerShape(16.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (waveformLoading) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 3.dp)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            stringResource(R.string.audio_editor_building_waveform),
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                } else {
                    WaveformSelectionCanvas(
                        waveform = waveform,
                        durationMs = source.durationMs,
                        selectionStartMs = selectionStartMs,
                        selectionEndMs = selectionEndMs,
                        allSelections = allSelections,
                        playheadMs = playheadMs,
                        viewportStartMs = viewportStartMs,
                        viewportDurationMs = viewportDuration(source.durationMs, zoom),
                        enabled = enabled,
                        onSelectionChange = onSelectionChange,
                        onPlayheadChange = onPlayheadChange
                    )
                }
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(formatEditorTime(viewportStartMs), style = MaterialTheme.typography.labelSmall)
                Text(
                    formatEditorTime(
                        (viewportStartMs + viewportDuration(source.durationMs, zoom))
                            .coerceAtMost(source.durationMs)
                    ),
                    style = MaterialTheme.typography.labelSmall
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.audio_editor_zoom), style = MaterialTheme.typography.labelMedium)
                Slider(
                    value = zoom,
                    onValueChange = onZoomChange,
                    valueRange = 1f..32f,
                    steps = 30,
                    enabled = enabled,
                    modifier = Modifier.weight(1f).padding(start = 10.dp)
                )
            }
            if (zoom > 1.01f) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.audio_editor_position), style = MaterialTheme.typography.labelMedium)
                    Slider(
                        value = viewportStartMs.toFloat(),
                        onValueChange = { onViewportChange(it.roundToLong()) },
                        valueRange = 0f..maxViewportStart(source.durationMs, zoom).toFloat()
                            .coerceAtLeast(1f),
                        enabled = enabled,
                        modifier = Modifier.weight(1f).padding(start = 10.dp)
                    )
                }
            }
        }
    }
}


@Composable
internal fun PreviewControls(
    playing: Boolean,
    loopEnabled: Boolean,
    positionMs: Long,
    selectionDurationMs: Long,
    enabled: Boolean,
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onStop: () -> Unit,
    onLoopChanged: (Boolean) -> Unit
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = if (playing) onPause else onPlay, enabled = enabled) {
                Icon(
                    if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = stringResource(
                        if (playing) R.string.audio_editor_pause
                        else R.string.audio_editor_play_selection
                    )
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.audio_editor_selection_preview),
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    stringResource(
                        R.string.audio_editor_preview_position,
                        formatEditorTime(positionMs),
                        formatEditorTime(selectionDurationMs)
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
            IconButton(onClick = onStop, enabled = enabled) {
                Icon(Icons.Default.Stop, stringResource(R.string.audio_editor_stop))
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Loop, contentDescription = null, Modifier.size(16.dp))
                    Text(
                        stringResource(R.string.audio_editor_loop),
                        style = MaterialTheme.typography.labelSmall
                    )
                }
                Switch(
                    checked = loopEnabled,
                    onCheckedChange = onLoopChanged,
                    enabled = enabled
                )
            }
        }
    }
}

@Composable
internal fun PrecisionControls(
    startMs: Long,
    endMs: Long,
    playheadMs: Long,
    durationMs: Long,
    stepMs: Long,
    enabled: Boolean,
    onStepChange: (Long) -> Unit,
    onSelectionChange: (Long, Long) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(stringResource(R.string.audio_editor_precision), fontWeight = FontWeight.SemiBold)
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            PRECISION_STEPS.forEach { step ->
                FilterChip(
                    selected = stepMs == step,
                    onClick = { onStepChange(step) },
                    enabled = enabled,
                    label = { Text(formatPrecisionStep(step)) }
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TimeControl(
                label = stringResource(R.string.audio_editor_start),
                valueMs = startMs,
                enabled = enabled,
                onDecrease = { onSelectionChange((startMs - stepMs).coerceAtLeast(0), endMs) },
                onIncrease = {
                    onSelectionChange((startMs + stepMs).coerceAtMost(endMs - 1), endMs)
                },
                onSetToPlayhead = {
                    onSelectionChange(playheadMs.coerceAtMost(endMs - 1), endMs)
                },
                modifier = Modifier.weight(1f)
            )
            TimeControl(
                label = stringResource(R.string.audio_editor_end),
                valueMs = endMs,
                enabled = enabled,
                onDecrease = {
                    onSelectionChange(startMs, (endMs - stepMs).coerceAtLeast(startMs + 1))
                },
                onIncrease = {
                    onSelectionChange(startMs, (endMs + stepMs).coerceAtMost(durationMs))
                },
                onSetToPlayhead = {
                    onSelectionChange(startMs, playheadMs.coerceAtLeast(startMs + 1))
                },
                modifier = Modifier.weight(1f)
            )
        }
        Text(
            stringResource(
                R.string.audio_editor_selected_duration,
                formatEditorTime(endMs - startMs)
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun TimeControl(
    label: String,
    valueMs: Long,
    enabled: Boolean,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
    onSetToPlayhead: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(label, style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.weight(1f))
                IconButton(
                    onClick = onSetToPlayhead,
                    enabled = enabled,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.Default.MyLocation,
                        contentDescription = stringResource(R.string.audio_editor_set_playhead),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            Text(
                formatEditorTime(valueMs),
                style = MaterialTheme.typography.bodyLarge,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                IconButton(onClick = onDecrease, enabled = enabled, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.Remove, stringResource(R.string.audio_editor_decrease_time))
                }
                IconButton(onClick = onIncrease, enabled = enabled, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.Add, stringResource(R.string.audio_editor_increase_time))
                }
            }
        }
    }
}
