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
internal fun AudioEditorSourceCard(
    source: AudioEditorSource,
    waveform: AudioWaveform?,
    waveformLoading: Boolean,
    index: Int,
    count: Int,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRemove: () -> Unit
) {
    val artwork = remember(source.metadata.artwork) {
        source.metadata.artwork?.let { bytes ->
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
        }
    }
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    if (artwork != null) {
                        Image(
                            bitmap = artwork,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(10.dp)),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Text("${index + 1}", fontWeight = FontWeight.Bold)
                    }
                }
                Column(modifier = Modifier.weight(1f).padding(horizontal = 10.dp)) {
                    Text(
                        source.metadata.title ?: source.name,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        listOfNotNull(source.metadata.artist, formatEditorTime(source.durationMs))
                            .joinToString(" • "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onMoveUp, enabled = index > 0, modifier = Modifier.size(38.dp)) {
                    Icon(
                        Icons.Default.ArrowUpward,
                        stringResource(R.string.audio_editor_move_up),
                        Modifier.size(20.dp)
                    )
                }
                IconButton(
                    onClick = onMoveDown,
                    enabled = index < count - 1,
                    modifier = Modifier.size(38.dp)
                ) {
                    Icon(
                        Icons.Default.ArrowDownward,
                        stringResource(R.string.audio_editor_move_down),
                        Modifier.size(20.dp)
                    )
                }
                IconButton(onClick = onRemove, modifier = Modifier.size(38.dp)) {
                    Icon(
                        Icons.Default.DeleteOutline,
                        stringResource(R.string.audio_editor_remove_audio),
                        Modifier.size(20.dp)
                    )
                }
            }
            Box(
                modifier = Modifier.fillMaxWidth().height(54.dp),
                contentAlignment = Alignment.Center
            ) {
                if (waveformLoading) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                } else {
                    MiniWaveform(waveform)
                }
            }
        }
    }
}

@Composable
private fun MiniWaveform(waveform: AudioWaveform?) {
    val color = MaterialTheme.colorScheme.primary
    Canvas(Modifier.fillMaxSize()) {
        val peaks = waveform?.peaks ?: return@Canvas
        val bars = (size.width / 4.dp.toPx()).toInt().coerceAtLeast(1)
        repeat(bars) { bar ->
            val start = bar * peaks.size / bars
            val end = ((bar + 1) * peaks.size / bars).coerceAtLeast(start + 1)
                .coerceAtMost(peaks.size)
            var peak = 0f
            for (index in start until end) peak = max(peak, peaks[index])
            val height = max(2.dp.toPx(), peak * size.height * 0.46f)
            val x = (bar + 0.5f) * size.width / bars
            drawLine(
                color,
                Offset(x, size.height / 2 - height),
                Offset(x, size.height / 2 + height),
                2.dp.toPx(),
                StrokeCap.Round
            )
        }
    }
}

@Composable
internal fun ExportProgress(state: AudioEditorState, onCancelExport: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (state.progress != null) {
            LinearProgressIndicator(
                progress = { state.progress / 100f },
                modifier = Modifier.fillMaxWidth()
            )
        } else {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
        Text(
            if (state.isPreservingMetadata) {
                stringResource(R.string.audio_editor_copying_metadata)
            } else if (state.progress != null) {
                stringResource(R.string.audio_editor_export_percent, state.progress)
            } else {
                stringResource(R.string.audio_editor_exporting)
            }
        )
        OutlinedButton(onClick = onCancelExport, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.audio_editor_cancel_export))
        }
    }
}

@Composable
internal fun CompletionCard(outputPath: String, warnings: List<String>) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (warnings.isEmpty()) {
                MaterialTheme.colorScheme.secondaryContainer
            } else {
                MaterialTheme.colorScheme.tertiaryContainer
            }
        )
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (warnings.isEmpty()) Icons.Default.CheckCircle else Icons.Default.WarningAmber,
                    contentDescription = null
                )
                Text(stringResource(R.string.audio_editor_complete), fontWeight = FontWeight.SemiBold)
            }
            Text(outputPath, style = MaterialTheme.typography.bodySmall)
            warnings.forEach { warning ->
                Text("• $warning", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
internal fun AudioEditorMessage(title: String, message: String, modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier.padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(Icons.Default.AudioFile, null, Modifier.size(48.dp))
            Text(title, style = MaterialTheme.typography.titleLarge)
            Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}


@Composable
internal fun metadataRows(source: AudioEditorSource): List<Pair<String, String>> = buildList {
    add(stringResource(R.string.audio_editor_info_name) to source.name)
    source.metadata.title?.let { add(stringResource(R.string.audio_editor_info_title) to it) }
    source.metadata.artist?.let { add(stringResource(R.string.audio_editor_info_artist) to it) }
    source.metadata.album?.let { add(stringResource(R.string.audio_editor_info_album) to it) }
    source.metadata.albumArtist?.let {
        add(stringResource(R.string.audio_editor_info_album_artist) to it)
    }
    source.metadata.genre?.let { add(stringResource(R.string.audio_editor_info_genre) to it) }
    source.metadata.year?.let { add(stringResource(R.string.audio_editor_info_year) to it) }
    add(
        stringResource(R.string.audio_editor_info_duration) to
            formatEditorTime(source.durationMs)
    )
    source.metadata.bitrate?.toLongOrNull()?.let { bitrate ->
        add(
            stringResource(R.string.audio_editor_info_bitrate) to
                "${bitrate / 1_000} kbps"
        )
    }
    source.metadata.mimeType?.let {
        add(stringResource(R.string.audio_editor_info_format) to it)
    }
    add(stringResource(R.string.audio_editor_info_size) to formatFileSize(source.sizeBytes))
    add(stringResource(R.string.audio_editor_info_location) to source.path)
}

private fun formatFileSize(bytes: Long): String {
    if (bytes < 1_024) return "$bytes B"
    val units = listOf("KB", "MB", "GB", "TB")
    var value = bytes.toDouble() / 1_024
    var unit = 0
    while (value >= 1_024 && unit < units.lastIndex) {
        value /= 1_024
        unit += 1
    }
    return "%.1f %s".format(value, units[unit])
}
