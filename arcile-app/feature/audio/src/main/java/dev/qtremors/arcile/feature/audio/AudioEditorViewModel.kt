package dev.qtremors.arcile.feature.audio

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.transformer.ExportResult
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal data class AudioEditorSource(
    val path: String,
    val name: String,
    val durationMs: Long,
    val sizeBytes: Long,
    val metadata: AudioEditorMetadata,
    val temporary: Boolean = false
)

internal data class AudioEditorMetadata(
    val title: String? = null,
    val artist: String? = null,
    val album: String? = null,
    val albumArtist: String? = null,
    val genre: String? = null,
    val year: String? = null,
    val bitrate: String? = null,
    val mimeType: String? = null,
    val artwork: ByteArray? = null
)

internal data class AudioEditorState(
    val isLoading: Boolean = true,
    val sources: List<AudioEditorSource> = emptyList(),
    val waveforms: Map<String, AudioWaveform> = emptyMap(),
    val waveformPathsLoading: Set<String> = emptySet(),
    val isAddingSources: Boolean = false,
    val isExporting: Boolean = false,
    val isPreservingMetadata: Boolean = false,
    val progress: Int? = null,
    val completedPath: String? = null,
    val completedWarnings: List<String> = emptyList(),
    val error: String? = null,
    val previewPlaying: Boolean = false,
    val previewPrepared: Boolean = false,
    val previewPositionMs: Long = 0,
    val loopEnabled: Boolean = true
)

@HiltViewModel
internal class AudioEditorViewModel @Inject constructor(
    application: Application,
    private val workspace: AudioEditorWorkspace
) : AndroidViewModel(application) {
    private val exporter = AudioLosslessExporter(application)
    private val previewPlayer = ExoPlayer.Builder(application)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                .setUsage(C.USAGE_MEDIA)
                .build(),
            true
        )
        .build()
    private val _state = MutableStateFlow(AudioEditorState())
    val state: StateFlow<AudioEditorState> = _state.asStateFlow()
    private var progressJob: Job? = null
    private var previewPositionJob: Job? = null
    private var metadataJob: Job? = null
    private var activeOutputPath: String? = null
    private var previewStartMs = 0L
    private var previewEndMs = 0L
    private var loaded = false
    private val addedUris = mutableSetOf<String>()

    init {
        previewPlayer.addListener(
            object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    _state.update { it.copy(previewPlaying = isPlaying) }
                    if (isPlaying) startPreviewPositionUpdates()
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_ENDED) {
                        _state.update {
                            it.copy(
                                previewPlaying = false,
                                previewPrepared = false,
                                previewPositionMs = previewEndMs
                            )
                        }
                    }
                }

                override fun onPlayerError(error: PlaybackException) {
                    _state.update {
                        it.copy(
                            previewPlaying = false,
                            previewPrepared = false,
                            error = getApplication<Application>().getString(
                                R.string.audio_editor_preview_failed
                            )
                        )
                    }
                }
            }
        )
    }

    fun load(paths: List<String>) {
        if (loaded) return
        loaded = true
        viewModelScope.launch {
            val sources = workspace.readSources(paths)
            if (sources.isEmpty()) {
                _state.value = AudioEditorState(
                    isLoading = false,
                    error = getApplication<Application>().getString(R.string.audio_editor_no_files)
                )
                return@launch
            }
            _state.value = AudioEditorState(
                isLoading = false,
                sources = sources,
                waveformPathsLoading = sources.mapTo(mutableSetOf(), AudioEditorSource::path)
            )
            sources.forEach { source ->
                extractWaveform(source)
            }
        }
    }

    fun addAudioUris(uris: List<Uri>) {
        if (uris.isEmpty() || _state.value.isAddingSources) return
        val freshUris = uris.distinctBy(Uri::toString).filter { addedUris.add(it.toString()) }
        if (freshUris.isEmpty()) return
        _state.update { it.copy(isAddingSources = true, error = null) }
        viewModelScope.launch {
            val importResults = workspace.importSources(freshUris)
            importResults.filter { it.second == null }.forEach { (uri, _) ->
                addedUris -= uri.toString()
            }
            val addedSources = importResults.mapNotNull(Pair<Uri, AudioEditorSource?>::second)
            if (addedSources.isEmpty()) {
                _state.update {
                    it.copy(
                        isAddingSources = false,
                        error = getApplication<Application>().getString(
                            R.string.audio_editor_add_failed
                        )
                    )
                }
                return@launch
            }
            _state.update { current ->
                current.copy(
                    sources = (current.sources + addedSources).distinctBy(AudioEditorSource::path),
                    waveformPathsLoading = current.waveformPathsLoading +
                        addedSources.map(AudioEditorSource::path),
                    isAddingSources = false
                )
            }
            addedSources.forEach { source -> extractWaveform(source) }
        }
    }

    fun removeSource(path: String) {
        val current = _state.value
        if (current.sources.size <= 1 || current.isExporting) return
        val source = current.sources.firstOrNull { it.path == path } ?: return
        _state.update {
            it.copy(
                sources = it.sources.filterNot { item -> item.path == path },
                waveforms = it.waveforms - path,
                waveformPathsLoading = it.waveformPathsLoading - path
            )
        }
        if (source.temporary) {
            workspace.removeTemporary(path)
        }
    }

    fun reorderSources(paths: List<String>) {
        val current = _state.value
        if (current.isExporting || paths.size != current.sources.size) return
        val sourcesByPath = current.sources.associateBy(AudioEditorSource::path)
        if (paths.toSet() != sourcesByPath.keys) return
        _state.update { it.copy(sources = paths.mapNotNull(sourcesByPath::get)) }
    }

    fun previewSelection(path: String, startMs: Long, endMs: Long) {
        if (endMs <= startMs) return
        previewStartMs = startMs
        previewEndMs = endMs
        val mediaItem = MediaItem.Builder()
            .setUri(workspace.previewUri(path))
            .setClippingConfiguration(
                MediaItem.ClippingConfiguration.Builder()
                    .setStartPositionMs(startMs)
                    .setEndPositionMs(endMs)
                    .build()
            )
            .build()
        previewPlayer.repeatMode = if (_state.value.loopEnabled) {
            Player.REPEAT_MODE_ONE
        } else {
            Player.REPEAT_MODE_OFF
        }
        previewPlayer.setMediaItem(mediaItem)
        previewPlayer.prepare()
        previewPlayer.play()
        _state.update {
            it.copy(previewPrepared = true, previewPositionMs = startMs, error = null)
        }
    }

    fun togglePreview() {
        if (previewPlayer.isPlaying) previewPlayer.pause() else previewPlayer.play()
    }

    fun stopPreview() {
        previewPositionJob?.cancel()
        previewPlayer.stop()
        _state.update { it.copy(previewPlaying = false, previewPrepared = false) }
    }

    fun seekPreview(positionMs: Long) {
        val absolute = positionMs.coerceIn(previewStartMs, previewEndMs)
        previewPlayer.seekTo((absolute - previewStartMs).coerceAtLeast(0))
        _state.update { it.copy(previewPositionMs = absolute) }
    }

    fun setLoopEnabled(enabled: Boolean) {
        previewPlayer.repeatMode = if (enabled) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
        _state.update { it.copy(loopEnabled = enabled) }
    }

    fun export(plan: AudioEditPlan, orderedSources: List<AudioEditorSource>) {
        if (_state.value.isExporting) return
        stopPreview()
        activeOutputPath = plan.outputPath
        _state.update {
            it.copy(
                sources = orderedSources,
                isExporting = true,
                isPreservingMetadata = false,
                progress = null,
                completedPath = null,
                completedWarnings = emptyList(),
                error = null
            )
        }
        exporter.start(
            plan = plan,
            onCompleted = { exportResult -> finishExport(plan, exportResult) },
            onError = ::failExport
        )
        progressJob = viewModelScope.launch {
            while (_state.value.isExporting && !_state.value.isPreservingMetadata) {
                _state.update { it.copy(progress = exporter.progressPercent()) }
                delay(300)
            }
        }
    }

    private fun finishExport(plan: AudioEditPlan, exportResult: ExportResult) {
        progressJob?.cancel()
        _state.update { it.copy(progress = 100, isPreservingMetadata = true) }
        metadataJob = viewModelScope.launch {
            val metadataResult = workspace.preserveMetadata(
                plan.segments.first().path,
                plan.outputPath
            )
            val warnings = buildList {
                if (exportResult.audioConversionProcess != ExportResult.CONVERSION_PROCESS_TRANSMUXED) {
                    add(getApplication<Application>().getString(R.string.audio_editor_reencoded_warning))
                }
                metadataResult.warning?.let(::add)
                if (plan.mode == AudioEditMode.COMBINE) {
                    add(
                        getApplication<Application>().getString(
                            R.string.audio_editor_first_metadata_warning
                        )
                    )
                }
            }
            activeOutputPath = null
            workspace.scan(plan.outputPath)
            _state.update {
                it.copy(
                    isExporting = false,
                    isPreservingMetadata = false,
                    progress = 100,
                    completedPath = plan.outputPath,
                    completedWarnings = warnings
                )
            }
        }
    }

    private fun failExport(error: Throwable) {
        progressJob?.cancel()
        activeOutputPath = null
        _state.update {
            it.copy(
                isExporting = false,
                isPreservingMetadata = false,
                progress = null,
                error = error.localizedMessage?.takeIf(String::isNotBlank)
                    ?: getApplication<Application>().getString(R.string.audio_editor_export_failed)
            )
        }
    }

    fun cancelExport() {
        progressJob?.cancel()
        metadataJob?.cancel()
        exporter.cancel()
        activeOutputPath?.let(workspace::delete)
        activeOutputPath = null
        _state.update {
            it.copy(
                isExporting = false,
                isPreservingMetadata = false,
                progress = null,
                error = null
            )
        }
    }

    override fun onCleared() {
        previewPositionJob?.cancel()
        metadataJob?.cancel()
        previewPlayer.release()
        if (_state.value.isExporting) cancelExport()
        workspace.clearTemporaryInputs()
        super.onCleared()
    }

    private fun startPreviewPositionUpdates() {
        previewPositionJob?.cancel()
        previewPositionJob = viewModelScope.launch {
            while (previewPlayer.isPlaying) {
                _state.update {
                    it.copy(
                        previewPositionMs = (previewStartMs + previewPlayer.currentPosition)
                            .coerceIn(previewStartMs, previewEndMs)
                    )
                }
                delay(33)
            }
        }
    }

    private suspend fun extractWaveform(source: AudioEditorSource) {
        val waveform = workspace.extractWaveform(source)
        _state.update { current ->
            current.copy(
                waveforms = waveform?.let {
                    current.waveforms + (source.path to it)
                } ?: current.waveforms,
                waveformPathsLoading = current.waveformPathsLoading - source.path
            )
        }
    }

}
