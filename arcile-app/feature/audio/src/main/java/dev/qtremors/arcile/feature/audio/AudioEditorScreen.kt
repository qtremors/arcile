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
                                    context.resources.getString(R.string.audio_editor_unsupported_unknown)
                                } else {
                                    context.resources.getString(
                                        R.string.audio_editor_unsupported_format,
                                        result.extension.lowercase()
                                    )
                                }
                            AudioEditPlanResult.MixedFormats -> planningError =
                                context.resources.getString(R.string.audio_editor_same_container)
                            AudioEditPlanResult.InvalidSelection -> planningError =
                                context.resources.getString(R.string.audio_editor_invalid_range)
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
