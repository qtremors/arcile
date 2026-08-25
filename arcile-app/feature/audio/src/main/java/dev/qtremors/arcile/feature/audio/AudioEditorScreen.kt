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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AudioEditorScreen(
    state: AudioEditorState,
    onBack: () -> Unit,
    onExport: (AudioEditPlan, List<AudioEditorSource>) -> Unit,
    onCancelExport: () -> Unit,
    onPreview: (String, Long, Long) -> Unit,
    onTogglePreview: () -> Unit,
    onStopPreview: () -> Unit,
    onSeekPreview: (Long) -> Unit,
    onLoopChanged: (Boolean) -> Unit,
    onAddAudio: (List<android.net.Uri>) -> Unit,
    onRemoveAudio: (String) -> Unit,
    onSourceOrderChanged: (List<String>) -> Unit
) {
    BackHandler(onBack = onBack)
    var showExportInfo by remember { mutableStateOf(false) }
    val addAudioLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris -> onAddAudio(uris) }
    if (showExportInfo) {
        AlertDialog(
            onDismissRequest = { showExportInfo = false },
            icon = { Icon(Icons.Default.Info, contentDescription = null) },
            title = { Text(stringResource(R.string.audio_editor_export_details)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(stringResource(R.string.audio_editor_lossless_note))
                    Text(
                        stringResource(
                            if (state.sources.size > 1) {
                                R.string.audio_editor_combine_metadata_note
                            } else {
                                R.string.audio_editor_metadata_note
                            }
                        )
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showExportInfo = false }) {
                    Text(stringResource(android.R.string.ok))
                }
            }
        )
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.audio_editor_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.audio_editor_back)
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { addAudioLauncher.launch(arrayOf("audio/*")) },
                        enabled = !state.isExporting && !state.isAddingSources
                    ) {
                        if (state.isAddingSources) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(22.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                Icons.Default.LibraryAdd,
                                contentDescription = stringResource(R.string.audio_editor_add_audio)
                            )
                        }
                    }
                    IconButton(onClick = { showExportInfo = true }) {
                        Icon(
                            Icons.Default.Info,
                            contentDescription = stringResource(R.string.audio_editor_export_details)
                        )
                    }
                }
            )
        }
    ) { contentPadding ->
        when {
            state.isLoading -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(contentPadding),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator() }
            state.sources.isEmpty() -> AudioEditorMessage(
                title = stringResource(R.string.audio_editor_unavailable),
                message = state.error ?: stringResource(R.string.audio_editor_no_files),
                modifier = Modifier.padding(contentPadding)
            )
            else -> AudioEditorContent(
                state = state,
                onExport = onExport,
                onCancelExport = onCancelExport,
                onPreview = onPreview,
                onTogglePreview = onTogglePreview,
                onStopPreview = onStopPreview,
                onSeekPreview = onSeekPreview,
                onLoopChanged = onLoopChanged,
                onRemoveAudio = onRemoveAudio,
                onSourceOrderChanged = onSourceOrderChanged,
                modifier = Modifier.padding(contentPadding)
            )
        }
    }
}

@Composable
private fun AudioEditorContent(
    state: AudioEditorState,
    onExport: (AudioEditPlan, List<AudioEditorSource>) -> Unit,
    onCancelExport: () -> Unit,
    onPreview: (String, Long, Long) -> Unit,
    onTogglePreview: () -> Unit,
    onStopPreview: () -> Unit,
    onSeekPreview: (Long) -> Unit,
    onLoopChanged: (Boolean) -> Unit,
    onRemoveAudio: (String) -> Unit,
    onSourceOrderChanged: (List<String>) -> Unit,
    modifier: Modifier = Modifier
) {
    var orderedSources by remember(state.sources) { mutableStateOf(state.sources) }
    val isCombine = orderedSources.size > 1
    var modeName by rememberSaveable(isCombine) {
        mutableStateOf(if (isCombine) AudioEditMode.COMBINE.name else AudioEditMode.EXTRACT.name)
    }
    val mode = if (isCombine) AudioEditMode.COMBINE else AudioEditMode.valueOf(modeName)
    val source = orderedSources.first()
    val durationMs = source.durationMs
    val initialStartMs = (durationMs * 0.25).roundToLong()
    val initialEndMs = (durationMs * 0.75).roundToLong()
    var selections by remember(source.path, durationMs) {
        mutableStateOf(listOf(AudioSelectionRange(1, initialStartMs, initialEndMs)))
    }
    var activeSelectionId by rememberSaveable(source.path, durationMs) { mutableLongStateOf(1) }
    val activeSelection = selections.firstOrNull { it.id == activeSelectionId }
        ?: selections.first()
    val selectionStartMs = activeSelection.startMs
    val selectionEndMs = activeSelection.endMs
    var playheadMs by rememberSaveable(durationMs) { mutableLongStateOf(initialStartMs) }
    var precisionStepMs by rememberSaveable { mutableLongStateOf(10) }
    var rangeStyleName by rememberSaveable { mutableStateOf(AudioRangeStyle.WAVEFORM.name) }
    val rangeStyle = AudioRangeStyle.valueOf(rangeStyleName)
    var zoom by rememberSaveable { mutableFloatStateOf(1f) }
    var viewportStartMs by rememberSaveable(durationMs) { mutableLongStateOf(0) }
    var planningError by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current

    LaunchedEffect(state.previewPositionMs) {
        if (state.previewPositionMs in selectionStartMs..selectionEndMs) {
            playheadMs = state.previewPositionMs
        }
    }

    fun updateSelection(startMs: Long, endMs: Long) {
        onStopPreview()
        val safeStart = startMs.coerceIn(0, durationMs - 1)
        val safeEnd = endMs.coerceIn(safeStart + 1, durationMs)
        selections = selections.map { range ->
            if (range.id == activeSelectionId) {
                range.copy(startMs = safeStart, endMs = safeEnd)
            } else {
                range
            }
        }
        playheadMs = playheadMs.coerceIn(safeStart, safeEnd)
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = if (isCombine) {
                        stringResource(R.string.audio_editor_combine_heading)
                    } else {
                        stringResource(R.string.audio_editor_precision_heading)
                    },
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = if (isCombine) {
                        stringResource(R.string.audio_editor_combine_subheading)
                    } else {
                        stringResource(R.string.audio_editor_precision_subheading)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (!isCombine) {
            item {
                AudioMetadataSummary(source = source)
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FilterChip(
                        selected = mode == AudioEditMode.EXTRACT,
                        onClick = { modeName = AudioEditMode.EXTRACT.name },
                        label = { Text(stringResource(R.string.audio_editor_extract)) },
                        leadingIcon = { Icon(Icons.Default.Save, null, Modifier.size(18.dp)) }
                    )
                    FilterChip(
                        selected = mode == AudioEditMode.REMOVE,
                        onClick = { modeName = AudioEditMode.REMOVE.name },
                        label = { Text(stringResource(R.string.audio_editor_remove)) },
                        leadingIcon = { Icon(Icons.Default.ContentCut, null, Modifier.size(18.dp)) }
                    )
                }
            }
            item {
                SegmentAndWaveformControls(
                    selections = selections,
                    activeSelectionId = activeSelectionId,
                    waveformEnabled = rangeStyle == AudioRangeStyle.WAVEFORM,
                    canAdd = nextSelectionRange(selections, durationMs) != null,
                    enabled = !state.isExporting,
                    onSelect = { id ->
                        onStopPreview()
                        activeSelectionId = id
                        selections.firstOrNull { it.id == id }?.let { selected ->
                            playheadMs = selected.startMs
                        }
                    },
                    onAdd = {
                        nextSelectionRange(selections, durationMs)?.let { range ->
                            selections = selections + range
                            activeSelectionId = range.id
                            playheadMs = range.startMs
                            onStopPreview()
                        }
                    },
                    onRemove = { id ->
                        if (selections.size > 1) {
                            selections = selections.filterNot { it.id == id }
                            if (activeSelectionId == id) {
                                activeSelectionId = selections.first().id
                                playheadMs = selections.first().startMs
                            }
                            onStopPreview()
                        }
                    },
                    onWaveformChanged = { enabled ->
                        rangeStyleName = if (enabled) {
                            AudioRangeStyle.WAVEFORM.name
                        } else {
                            AudioRangeStyle.CLASSIC.name
                        }
                    }
                )
            }
            item {
                if (rangeStyle == AudioRangeStyle.WAVEFORM) {
                    PrecisionWaveformCard(
                        source = source,
                        waveform = state.waveforms[source.path],
                        waveformLoading = source.path in state.waveformPathsLoading,
                        selectionStartMs = selectionStartMs,
                        selectionEndMs = selectionEndMs,
                        allSelections = selections,
                        playheadMs = playheadMs,
                        zoom = zoom,
                        viewportStartMs = viewportStartMs,
                        enabled = !state.isExporting,
                        onSelectionChange = ::updateSelection,
                        onPlayheadChange = { position ->
                            playheadMs = position
                            if (state.previewPlaying) onSeekPreview(position)
                        },
                        onZoomChange = { newZoom ->
                            zoom = newZoom
                            val visibleDuration = viewportDuration(durationMs, newZoom)
                            viewportStartMs = (playheadMs - visibleDuration / 2)
                                .coerceIn(0, maxViewportStart(durationMs, newZoom))
                        },
                        onViewportChange = { viewportStartMs = it }
                    )
                } else {
                    ClassicRangeCard(
                        source = source,
                        selectionStartMs = selectionStartMs,
                        selectionEndMs = selectionEndMs,
                        enabled = !state.isExporting,
                        onSelectionChange = ::updateSelection
                    )
                }
            }
            item {
                PreviewControls(
                    playing = state.previewPlaying,
                    loopEnabled = state.loopEnabled,
                    positionMs = playheadMs,
                    selectionDurationMs = selectionEndMs - selectionStartMs,
                    enabled = !state.isExporting,
                    onPlay = {
                        if (state.previewPlaying) onTogglePreview()
                        else if (state.previewPrepared) onTogglePreview()
                        else onPreview(source.path, selectionStartMs, selectionEndMs)
                    },
                    onPause = onTogglePreview,
                    onStop = onStopPreview,
                    onLoopChanged = onLoopChanged
                )
            }
            item {
                PrecisionControls(
                    startMs = selectionStartMs,
                    endMs = selectionEndMs,
                    playheadMs = playheadMs,
                    durationMs = durationMs,
                    stepMs = precisionStepMs,
                    enabled = !state.isExporting,
                    onStepChange = { precisionStepMs = it },
                    onSelectionChange = ::updateSelection
                )
            }
        } else {
            itemsIndexed(orderedSources, key = { _, item -> item.path }) { index, item ->
                AudioEditorSourceCard(
                    source = item,
                    waveform = state.waveforms[item.path],
                    waveformLoading = item.path in state.waveformPathsLoading,
                    index = index,
                    count = orderedSources.size,
                    onMoveUp = {
                        orderedSources = orderedSources.moved(index, index - 1)
                        onSourceOrderChanged(orderedSources.map(AudioEditorSource::path))
                    },
                    onMoveDown = {
                        orderedSources = orderedSources.moved(index, index + 1)
                        onSourceOrderChanged(orderedSources.map(AudioEditorSource::path))
                    },
                    onRemove = { onRemoveAudio(item.path) }
                )
            }
        }
        if (state.isExporting) {
            item {
                ExportProgress(state = state, onCancelExport = onCancelExport)
            }
        } else {
            item {
                Button(
                    onClick = {
                        val sources = orderedSources.map { AudioEditSource(it.path, it.durationMs) }
                        when (
                            val result = AudioEditPlanner.create(
                                sources = sources,
                                mode = mode,
                                selectionStartMs = selectionStartMs,
                                selectionEndMs = selectionEndMs,
                                selectionRanges = selections.map { range ->
                                    AudioEditRange(range.startMs, range.endMs)
                                },
                                outputDirectoryPath = state.sources
                                    .firstOrNull { !it.temporary }
                                    ?.path
                                    ?.let { File(it).parent }
                            )
                        ) {
                            is AudioEditPlanResult.Ready -> {
                                planningError = null
                                onExport(result.plan, orderedSources)
                            }
                            is AudioEditPlanResult.UnsupportedFormat -> planningError =
                                if (result.extension.isBlank()) {
                                    context.getString(R.string.audio_editor_unsupported_unknown)
                                } else {
                                    context.getString(
                                        R.string.audio_editor_unsupported_format,
                                        result.extension.lowercase()
                                    )
                                }
                            AudioEditPlanResult.MixedFormats -> planningError =
                                context.getString(R.string.audio_editor_same_container)
                            AudioEditPlanResult.InvalidSelection -> planningError =
                                context.getString(R.string.audio_editor_invalid_range)
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        if (isCombine) Icons.Default.LibraryAdd else Icons.Default.Save,
                        contentDescription = null
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        stringResource(
                            when (mode) {
                                AudioEditMode.EXTRACT -> R.string.audio_editor_export_extract
                                AudioEditMode.REMOVE -> R.string.audio_editor_export_remove
                                AudioEditMode.COMBINE -> R.string.audio_editor_export_combine
                            }
                        )
                    )
                }
            }
        }
        (planningError ?: state.error)?.let { message ->
            item { Text(message, color = MaterialTheme.colorScheme.error) }
        }
        state.completedPath?.let { outputPath ->
            item {
                CompletionCard(outputPath, state.completedWarnings)
            }
        }
        item { Spacer(Modifier.navigationBarsPadding()) }
    }
}

@Composable
private fun SegmentAndWaveformControls(
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
private fun AudioMetadataSummary(source: AudioEditorSource) {
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
private fun ClassicRangeCard(
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
private fun PrecisionWaveformCard(
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
private fun WaveformSelectionCanvas(
    waveform: AudioWaveform?,
    durationMs: Long,
    selectionStartMs: Long,
    selectionEndMs: Long,
    allSelections: List<AudioSelectionRange>,
    playheadMs: Long,
    viewportStartMs: Long,
    viewportDurationMs: Long,
    enabled: Boolean,
    onSelectionChange: (Long, Long) -> Unit,
    onPlayheadChange: (Long) -> Unit
) {
    val selectedColor = MaterialTheme.colorScheme.primary
    val unselectedColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.42f)
    val selectionFill = MaterialTheme.colorScheme.primary.copy(alpha = 0.13f)
    val inactiveSelectionFill = MaterialTheme.colorScheme.secondary.copy(alpha = 0.08f)
    val handleColor = MaterialTheme.colorScheme.primary
    val playheadColor = MaterialTheme.colorScheme.tertiary
    val density = LocalDensity.current
    val handleHitPx = with(density) { 30.dp.toPx() }
    val currentSelectionStartMs by rememberUpdatedState(selectionStartMs)
    val currentSelectionEndMs by rememberUpdatedState(selectionEndMs)
    val currentOnSelectionChange by rememberUpdatedState(onSelectionChange)
    val currentOnPlayheadChange by rememberUpdatedState(onPlayheadChange)

    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(
                enabled,
                durationMs,
                viewportStartMs,
                viewportDurationMs
            ) {
                if (!enabled) return@pointerInput
                var target = WaveformDragTarget.PLAYHEAD
                detectDragGestures(
                    onDragStart = { offset ->
                        val width = size.width.toFloat().coerceAtLeast(1f)
                        val startX = timeToX(
                            currentSelectionStartMs,
                            viewportStartMs,
                            viewportDurationMs,
                            width
                        )
                        val endX = timeToX(
                            currentSelectionEndMs,
                            viewportStartMs,
                            viewportDurationMs,
                            width
                        )
                        target = when {
                            kotlin.math.abs(offset.x - startX) <= handleHitPx ->
                                WaveformDragTarget.START
                            kotlin.math.abs(offset.x - endX) <= handleHitPx ->
                                WaveformDragTarget.END
                            else -> WaveformDragTarget.PLAYHEAD
                        }
                        val position = xToTime(
                            offset.x,
                            width,
                            viewportStartMs,
                            viewportDurationMs,
                            durationMs
                        )
                        when (target) {
                            WaveformDragTarget.START -> currentOnSelectionChange(
                                position.coerceAtMost(currentSelectionEndMs - 1),
                                currentSelectionEndMs
                            )
                            WaveformDragTarget.END -> currentOnSelectionChange(
                                currentSelectionStartMs,
                                position.coerceAtLeast(currentSelectionStartMs + 1)
                            )
                            WaveformDragTarget.PLAYHEAD -> currentOnPlayheadChange(position)
                        }
                    }
                ) { change, _ ->
                    change.consume()
                    val position = xToTime(
                        change.position.x,
                        size.width.toFloat().coerceAtLeast(1f),
                        viewportStartMs,
                        viewportDurationMs,
                        durationMs
                    )
                    when (target) {
                        WaveformDragTarget.START -> currentOnSelectionChange(
                            position.coerceAtMost(currentSelectionEndMs - 1),
                            currentSelectionEndMs
                        )
                        WaveformDragTarget.END -> currentOnSelectionChange(
                            currentSelectionStartMs,
                            position.coerceAtLeast(currentSelectionStartMs + 1)
                        )
                        WaveformDragTarget.PLAYHEAD -> currentOnPlayheadChange(position)
                    }
                }
            }
    ) {
        val visibleEnd = (viewportStartMs + viewportDurationMs).coerceAtMost(durationMs)
        val selectionLeft = timeToX(selectionStartMs, viewportStartMs, viewportDurationMs, size.width)
        val selectionRight = timeToX(selectionEndMs, viewportStartMs, viewportDurationMs, size.width)
        allSelections.forEach { range ->
            if (range.startMs == selectionStartMs && range.endMs == selectionEndMs) return@forEach
            val left = timeToX(range.startMs, viewportStartMs, viewportDurationMs, size.width)
            val right = timeToX(range.endMs, viewportStartMs, viewportDurationMs, size.width)
            if (right > 0 && left < size.width) {
                drawRect(
                    color = inactiveSelectionFill,
                    topLeft = Offset(left.coerceAtLeast(0f), 0f),
                    size = Size(
                        (right.coerceAtMost(size.width) - left.coerceAtLeast(0f))
                            .coerceAtLeast(0f),
                        size.height
                    )
                )
            }
        }
        if (selectionRight > 0 && selectionLeft < size.width) {
            drawRect(
                color = selectionFill,
                topLeft = Offset(selectionLeft.coerceAtLeast(0f), 0f),
                size = Size(
                    (selectionRight.coerceAtMost(size.width) - selectionLeft.coerceAtLeast(0f))
                        .coerceAtLeast(0f),
                    size.height
                )
            )
        }
        drawWaveform(
            waveform = waveform,
            durationMs = durationMs,
            visibleStartMs = viewportStartMs,
            visibleEndMs = visibleEnd,
            selectionStartMs = selectionStartMs,
            selectionEndMs = selectionEndMs,
            selectedColor = selectedColor,
            unselectedColor = unselectedColor
        )
        drawHandle(selectionLeft, handleColor)
        drawHandle(selectionRight, handleColor)
        if (playheadMs in viewportStartMs..visibleEnd) {
            val playheadX = timeToX(playheadMs, viewportStartMs, viewportDurationMs, size.width)
            drawLine(
                color = playheadColor,
                start = Offset(playheadX, 0f),
                end = Offset(playheadX, size.height),
                strokeWidth = 2.dp.toPx()
            )
            drawCircle(playheadColor, radius = 4.dp.toPx(), center = Offset(playheadX, 8.dp.toPx()))
        }
    }
}

private fun DrawScope.drawWaveform(
    waveform: AudioWaveform?,
    durationMs: Long,
    visibleStartMs: Long,
    visibleEndMs: Long,
    selectionStartMs: Long,
    selectionEndMs: Long,
    selectedColor: Color,
    unselectedColor: Color
) {
    val peaks = waveform?.peaks ?: return
    val visibleDuration = (visibleEndMs - visibleStartMs).coerceAtLeast(1)
    val bars = (size.width / 3.dp.toPx()).toInt().coerceAtLeast(1)
    val firstPeak = (visibleStartMs * peaks.size / durationMs).toInt().coerceIn(peaks.indices)
    val lastPeak = ceil(visibleEndMs.toDouble() * peaks.size / durationMs)
        .toInt()
        .coerceIn(firstPeak + 1, peaks.size)
    val peakSpan = lastPeak - firstPeak
    val centerY = size.height / 2f
    repeat(bars) { bar ->
        val rangeStart = firstPeak + bar * peakSpan / bars
        val rangeEnd = (firstPeak + (bar + 1) * peakSpan / bars).coerceAtLeast(rangeStart + 1)
            .coerceAtMost(peaks.size)
        var peak = 0f
        for (index in rangeStart until rangeEnd) peak = max(peak, peaks[index])
        val timeMs = visibleStartMs + bar.toLong() * visibleDuration / bars
        val color = if (timeMs in selectionStartMs..selectionEndMs) selectedColor
        else unselectedColor
        val halfHeight = max(2.dp.toPx(), peak * size.height * 0.43f)
        val x = (bar + 0.5f) * size.width / bars
        drawLine(
            color = color,
            start = Offset(x, centerY - halfHeight),
            end = Offset(x, centerY + halfHeight),
            strokeWidth = 2.dp.toPx(),
            cap = StrokeCap.Round
        )
    }
}

private fun DrawScope.drawHandle(x: Float, color: Color) {
    if (x !in -1f..size.width + 1f) return
    drawLine(
        color = color,
        start = Offset(x, 0f),
        end = Offset(x, size.height),
        strokeWidth = 4.dp.toPx()
    )
    drawRoundRect(
        color = color,
        topLeft = Offset(x - 7.dp.toPx(), 4.dp.toPx()),
        size = Size(14.dp.toPx(), 28.dp.toPx()),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(7.dp.toPx())
    )
}

@Composable
private fun PreviewControls(
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
private fun PrecisionControls(
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

@Composable
private fun AudioEditorSourceCard(
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
private fun ExportProgress(state: AudioEditorState, onCancelExport: () -> Unit) {
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
private fun CompletionCard(outputPath: String, warnings: List<String>) {
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
private fun AudioEditorMessage(title: String, message: String, modifier: Modifier = Modifier) {
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

private enum class WaveformDragTarget { START, END, PLAYHEAD }

private enum class AudioRangeStyle { WAVEFORM, CLASSIC }

private data class AudioSelectionRange(
    val id: Long,
    val startMs: Long,
    val endMs: Long
)

private fun nextSelectionRange(
    selections: List<AudioSelectionRange>,
    durationMs: Long
): AudioSelectionRange? {
    if (selections.size >= MAX_SELECTION_SEGMENTS || durationMs <= 1) return null
    val gaps = buildList {
        var cursorMs = 0L
        selections.sortedBy(AudioSelectionRange::startMs).forEach { range ->
            if (range.startMs > cursorMs) add(cursorMs..range.startMs)
            cursorMs = maxOf(cursorMs, range.endMs)
        }
        if (cursorMs < durationMs) add(cursorMs..durationMs)
    }
    val gap = gaps.maxByOrNull { it.last - it.first }
        ?.takeIf { it.last - it.first > 1 }
        ?: return null
    val inset = (gap.last - gap.first) / 4
    val startMs = gap.first + inset
    val endMs = (gap.last - inset).coerceAtLeast(startMs + 1)
    return AudioSelectionRange(
        id = (selections.maxOfOrNull(AudioSelectionRange::id) ?: 0) + 1,
        startMs = startMs,
        endMs = endMs
    )
}

@Composable
private fun metadataRows(source: AudioEditorSource): List<Pair<String, String>> = buildList {
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

private fun timeToX(timeMs: Long, viewportStartMs: Long, viewportDurationMs: Long, width: Float): Float =
    (timeMs - viewportStartMs).toFloat() / viewportDurationMs.coerceAtLeast(1) * width

private fun xToTime(
    x: Float,
    width: Float,
    viewportStartMs: Long,
    viewportDurationMs: Long,
    durationMs: Long
): Long = (viewportStartMs + x.coerceIn(0f, width) / width * viewportDurationMs)
    .roundToLong()
    .coerceIn(0, durationMs)

private fun viewportDuration(durationMs: Long, zoom: Float): Long =
    (durationMs / zoom.coerceAtLeast(1f)).roundToLong().coerceAtLeast(1)

private fun maxViewportStart(durationMs: Long, zoom: Float): Long =
    (durationMs - viewportDuration(durationMs, zoom)).coerceAtLeast(0)

internal fun formatEditorTime(durationMs: Long): String {
    val safe = durationMs.coerceAtLeast(0)
    val hours = safe / 3_600_000
    val minutes = safe % 3_600_000 / 60_000
    val seconds = safe % 60_000 / 1_000
    val millis = safe % 1_000
    return if (hours > 0) {
        "%d:%02d:%02d.%03d".format(hours, minutes, seconds, millis)
    } else {
        "%02d:%02d.%03d".format(minutes, seconds, millis)
    }
}

private fun formatPrecisionStep(stepMs: Long): String = when {
    stepMs >= 1_000 -> "${stepMs / 1_000} s"
    else -> "$stepMs ms"
}

private fun List<AudioEditorSource>.moved(from: Int, to: Int): List<AudioEditorSource> {
    if (from !in indices || to !in indices || from == to) return this
    return toMutableList().apply { add(to, removeAt(from)) }
}

private val PRECISION_STEPS = listOf(1L, 10L, 100L, 1_000L)
private const val MAX_SELECTION_SEGMENTS = 12
