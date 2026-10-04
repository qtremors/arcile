package dev.qtremors.arcile.feature.audio

import android.content.ClipData
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.qtremors.arcile.core.storage.domain.AudioTrack
import dev.qtremors.arcile.core.storage.domain.AudioLibraryRepository
import dev.qtremors.arcile.core.storage.domain.FileModel
import dev.qtremors.arcile.core.storage.domain.StorageNodeRef
import dev.qtremors.arcile.core.ui.externalfile.ExternalFileAccessHelper
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

internal data class AudioPanelState(
    val queue: List<AudioTrack> = emptyList(),
    val initialPath: String? = null,
    val visible: Boolean = false,
    val expanded: Boolean = false
)

@Singleton
internal class AudioPlayerCoordinator @Inject constructor(
    @ApplicationContext private val context: Context,
    private val playback: AudioPlaybackController,
    private val musicStore: AudioCollectionStore,
    private val listeningStore: AudioListeningStore,
    private val repository: AudioLibraryRepository
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mutableState = MutableStateFlow(AudioPanelState())
    val state = mutableState.asStateFlow()

    init {
        ContextCompat.registerReceiver(
            context,
            object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    if (intent?.action == ACTION_CLOSE_AUDIO_PLAYER) mutableState.value = AudioPanelState()
                }
            },
            IntentFilter(ACTION_CLOSE_AUDIO_PLAYER),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        scope.launch {
            playback.state.map { it.queueEntries to it.currentMediaId }
                .distinctUntilChanged()
                .collectLatest { (entries, currentId) ->
                    if (currentId == null || entries.isEmpty()) {
                        if (playback.state.value.isConnected) mutableState.value = AudioPanelState()
                        return@collectLatest
                    }
                    val panel = mutableState.value
                    if (panel.visible && panel.queue.map { it.file.reference } ==
                        entries.map { it.id }
                    ) return@collectLatest
                    val existing = panel.queue.associateBy { it.file.reference }
                    val indexed = if (entries.any { it.id !in existing }) {
                        repository.getTracks().getOrNull().orEmpty()
                            .associateBy { it.file.reference }
                    } else emptyMap()
                    val queue = entries.map { entry ->
                        existing[entry.id] ?: indexed[entry.id] ?: entry.toFallbackTrack(
                            playback.state.value.durationMs.takeIf { entry.id == currentId } ?: 0L
                        )
                    }
                    if (queue.none { it.file.reference == currentId }) return@collectLatest
                    mutableState.value = panel.copy(
                        queue = queue,
                        initialPath = currentId,
                        visible = true
                    )
                }
        }
    }

    private fun AudioQueueEntry.toFallbackTrack(durationMs: Long): AudioTrack {
        val uri = uri.ifBlank { id }
        val name = title?.takeIf(String::isNotBlank)
            ?: id.substringAfterLast('/').ifBlank { "Audio" }
        val nodeRef = if (uri.startsWith("content://")) {
            StorageNodeRef.mediaStore(
                id = uri.hashCode().toLong(),
                volumeName = null,
                contentUri = uri,
                displayPath = "/external/$name"
            )
        } else StorageNodeRef.local(id)
        return AudioTrack(
            file = FileModel(
                name = name,
                reference = id,
                extension = name.substringAfterLast('.', "").lowercase(),
                nodeRef = nodeRef
            ),
            title = name.substringBeforeLast('.', name),
            artist = artist,
            album = album,
            durationMs = durationMs
        )
    }

    fun open(track: AudioTrack, queue: List<AudioTrack>, startPlayback: Boolean) {
        val ordered = queue.distinctBy { it.file.reference }
            .takeIf { tracks -> tracks.any { it.file.reference == track.file.reference } }
            ?: listOf(track)
        mutableState.value = AudioPanelState(ordered, track.file.reference, visible = true)
        playback.playQueue(ordered, track.file.reference, startPlayback)
    }

    fun expandQueue(queue: List<AudioTrack>, currentPath: String) {
        val ordered = queue.distinctBy { it.file.reference }
        if (ordered.none { it.file.reference == currentPath }) return
        mutableState.value = mutableState.value.copy(queue = ordered)
        playback.expandQueue(ordered, currentPath)
    }

    fun expand() { mutableState.value = mutableState.value.copy(expanded = true) }
    fun collapse() { mutableState.value = mutableState.value.copy(expanded = false) }

    fun close() {
        mutableState.value = AudioPanelState()
        playback.closePlayer()
    }

    fun replace(oldPath: String, track: AudioTrack) {
        mutableState.value = mutableState.value.let { current ->
            current.copy(
                queue = current.queue.map { if (it.file.reference == oldPath) track else it },
                initialPath = if (current.initialPath == oldPath) track.file.reference
                    else current.initialPath
            )
        }
        playback.replaceQueueItem(oldPath, track)
        musicStore.notifyMediaChanged()
        if (oldPath != track.file.reference) {
            scope.launch {
                runCatching {
                    musicStore.replaceTrackPath(oldPath, track.file.reference)
                    listeningStore.replacePath(oldPath, track.file.reference)
                }
            }
        }
    }

    fun remove(path: String) {
        val queue = mutableState.value.queue.filterNot { it.file.reference == path }
        playback.removeQueueItems(listOf(path))
        if (queue.isEmpty()) close()
        else mutableState.value = mutableState.value.copy(queue = queue)
        musicStore.notifyMediaChanged()
    }

    fun removeFromQueue(path: String) {
        val queue = mutableState.value.queue.filterNot { it.file.reference == path }
        playback.removeQueueItems(listOf(path))
        if (queue.isEmpty()) close()
        else mutableState.value = mutableState.value.copy(queue = queue)
    }

    fun saveQueue(name: String, paths: List<String>) {
        scope.launch { runCatching { musicStore.createPlaylist(name, paths) } }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
internal fun AudioPlayerDock(
    sharedTransitionScope: SharedTransitionScope,
    coordinator: AudioPlayerCoordinator,
    playbackController: AudioPlaybackController,
    aboveNavigation: Boolean = false
) {
    val panel by coordinator.state.collectAsStateWithLifecycle()
    val playback by playbackController.state.collectAsStateWithLifecycle()
    val imeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    val track = panel.queue.firstOrNull { it.file.reference == playback.currentMediaId }
        ?: panel.queue.firstOrNull { it.file.reference == panel.initialPath }
    AnimatedVisibility(visible = panel.visible && !panel.expanded && track != null && !imeVisible) {
        if (track != null) {
            val visibilityScope = this
            Box(
                modifier = Modifier.fillMaxWidth()
                    .then(if (aboveNavigation) Modifier else Modifier.navigationBarsPadding())
                    .padding(horizontal = if (aboveNavigation) 16.dp else 12.dp, vertical = 8.dp)
            ) { AudioMiniPlayer(
                track = track,
                playback = playback,
                sharedTransitionScope = sharedTransitionScope,
                animatedVisibilityScope = visibilityScope,
                onExpand = coordinator::expand,
                onDismiss = coordinator::close,
                onTogglePlayback = playbackController::togglePlayback,
                onPrevious = playbackController::seekToPrevious,
                onNext = playbackController::seekToNext
            ) }
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
internal fun AudioPlayerExpanded(
    sharedTransitionScope: SharedTransitionScope,
    coordinator: AudioPlayerCoordinator,
    playbackController: AudioPlaybackController,
    listeningStore: AudioListeningStore,
    tagEditor: AudioTagEditor
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val panel by coordinator.state.collectAsStateWithLifecycle()
    val playback by playbackController.state.collectAsStateWithLifecycle()
    val favorites by listeningStore.favoritePaths.collectAsStateWithLifecycle(initialValue = emptySet())
    val playerPreferences = rememberAudioPlayerPreferences()
    val visualizerEnabled by playerPreferences.visualizerEnabledState()
    val track = panel.queue.firstOrNull { it.file.reference == playback.currentMediaId }
        ?: panel.queue.firstOrNull { it.file.reference == panel.initialPath }
    AnimatedVisibility(
        visible = panel.visible && panel.expanded && track != null,
        modifier = Modifier.fillMaxSize(),
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        if (track != null) {
            AudioNowPlayingScreen(
                track = track,
                queue = panel.queue,
                playback = playback,
                visualizerEnabled = visualizerEnabled,
                onToggleVisualizer = {
                    playerPreferences.visualizerEnabled = !playerPreferences.visualizerEnabled
                },
                isFavorite = track.file.reference in favorites,
                onToggleFavorite = {
                    val path = track.file.reference
                    scope.launch {
                        runCatching { listeningStore.setFavorite(path, path !in favorites) }
                    }
                },
                sharedTransitionScope = sharedTransitionScope,
                animatedVisibilityScope = this,
                onCollapse = coordinator::collapse,
                onTogglePlayback = playbackController::togglePlayback,
                onPrevious = playbackController::seekToPrevious,
                onNext = playbackController::seekToNext,
                onQueueTrack = playbackController::seekToQueueMediaId,
                onMoveQueueTrack = playbackController::moveQueueItem,
                onRemoveQueueTrack = coordinator::removeFromQueue,
                onClearQueue = coordinator::close,
                onSaveQueue = coordinator::saveQueue,
                onToggleRepeat = playbackController::toggleRepeatMode,
                onToggleShuffle = playbackController::toggleShuffle,
                onPlaybackParametersChange = playbackController::setPlaybackParameters,
                onSleepTimerChange = playbackController::setSleepTimerMinutes,
                onSeek = playbackController::seekTo,
                onShare = { scope.launch { shareAudioFile(context, track.file) } },
                onEdit = {
                    if (File(track.file.reference).isFile) {
                        context.startActivity(createAudioEditorIntent(context, listOf(track.file.reference)))
                    }
                },
                tagEditor = tagEditor,
                listeningStore = listeningStore,
                onTagSaved = { coordinator.replace(it.file.reference, it) },
                onOpenWith = { scope.launch { openAudioWith(context, track.file) } },
                onFileRenamed = { oldPath, file ->
                    coordinator.state.value.queue.firstOrNull { it.file.reference == oldPath }
                        ?.let { coordinator.replace(oldPath, it.copy(file = file)) }
                },
                onFileDeleted = coordinator::remove
            )
        }
    }
}

private suspend fun shareAudioFile(context: Context, file: FileModel) {
    val target = ExternalFileAccessHelper.createShareTargets(
        context,
        listOf(file.audioHandoffReference())
    ).singleOrNull() ?: return
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = target.mimeType
        putExtra(Intent.EXTRA_STREAM, target.uri)
        clipData = ClipData.newUri(context.contentResolver, target.displayName, target.uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, target.displayName))
}

private suspend fun openAudioWith(context: Context, file: FileModel) {
    val intent = ExternalFileAccessHelper.createOpenIntent(context, file.audioHandoffReference())
    context.startActivity(Intent.createChooser(intent, file.name))
}

private fun FileModel.audioHandoffReference() = ExternalFileAccessHelper.ExternalFileReference(
    path = reference,
    displayName = name,
    sizeBytes = size,
    mimeType = mimeType,
    nodeRef = nodeRef
)
