package dev.qtremors.arcile.feature.audio

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.qtremors.arcile.core.storage.domain.AudioTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

internal typealias AudioPlaybackState = dev.qtremors.arcile.core.ui.audio.AudioPlaybackState
internal typealias AudioQueueEntry = dev.qtremors.arcile.core.ui.audio.AudioQueueEntry
internal typealias AudioRepeatMode = dev.qtremors.arcile.core.ui.audio.AudioRepeatMode

@Singleton
internal class AudioPlaybackController @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _state = MutableStateFlow(AudioPlaybackState())
    val state: StateFlow<AudioPlaybackState> = _state.asStateFlow()
    private var controller: MediaController? = null
    private var pendingQueue: Triple<List<AudioTrack>, String, Boolean>? = null
    private val playbackPreferences = AudioPlayerPreferences(context)
    private val sleepTimer = dev.qtremors.arcile.core.ui.audio.AudioSleepTimer(scope) {
        controller?.pause()
        _state.value = _state.value.copy(sleepTimerEndElapsedMs = null)
    }
    private var progressJob: Job? = null

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            publish(player)
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            updateProgressPolling(isPlaying)
        }

        override fun onPlayerError(error: PlaybackException) {
            _state.value = _state.value.copy(error = true)
        }
    }

    init {
        scope.launch {
            playbackPreferences.playbackParametersFlow.collect { controller?.setPlaybackParameters(it) }
        }
        val token = SessionToken(context, ComponentName(context, AudioPlaybackService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        future.addListener(
            {
                runCatching { future.get() }.onSuccess { connected ->
                    controller = connected
                    connected.addListener(listener)
                    connected.setPlaybackParameters(PlaybackParameters(
                        playbackPreferences.playbackSpeed,
                        playbackPreferences.playbackPitch
                    ))
                    publish(connected)
                    updateProgressPolling(connected.isPlaying)
                    pendingQueue?.let { (tracks, initialPath, startPlayback) ->
                        pendingQueue = null
                        playQueue(tracks, initialPath, startPlayback)
                    }
                }
            },
            ContextCompat.getMainExecutor(context)
        )
    }

    private fun updateProgressPolling(isPlaying: Boolean) {
        progressJob?.cancel()
        progressJob = if (isPlaying) scope.launch {
            while (isActive) {
                delay(POSITION_UPDATE_MS)
                controller?.takeIf { it.isPlaying }?.let { publish(it, refreshQueue = false) }
            }
        } else null
    }

    fun playQueue(tracks: List<AudioTrack>, initialPath: String, startPlayback: Boolean = true) {
        val player = controller
        if (player == null) {
            pendingQueue = Triple(tracks, initialPath, startPlayback)
            return
        }
        val items = tracks.map { it.toMediaItem() }
        val initialIndex = tracks.indexOfFirst { it.file.reference == initialPath }
            .takeIf { it >= 0 } ?: 0
        if (!startPlayback) player.pause()
        player.setMediaItems(items, initialIndex, C.TIME_UNSET)
        player.prepare()
        if (startPlayback) player.play()
        publish(player)
    }

    fun expandQueue(tracks: List<AudioTrack>, currentPath: String) {
        if (tracks.none { it.file.reference == currentPath }) return
        val player = controller
        if (player == null) {
            if (pendingQueue?.second == currentPath) {
                pendingQueue = Triple(tracks, currentPath, pendingQueue?.third ?: true)
            }
            return
        }
        if (player.currentMediaItem?.mediaId != currentPath || player.mediaItemCount != 1) return

        val currentIndex = tracks.indexOfFirst { it.file.reference == currentPath }
        val before = tracks.take(currentIndex).map { it.toMediaItem() }
        val after = tracks.drop(currentIndex + 1).map { it.toMediaItem() }
        if (before.isNotEmpty()) player.addMediaItems(0, before)
        if (after.isNotEmpty()) player.addMediaItems(currentIndex + 1, after)
        publish(player)
    }

    fun togglePlayback() {
        controller?.let { player ->
            if (player.isPlaying) player.pause() else player.play()
            publish(player)
        }
    }

    fun closePlayer() {
        setSleepTimerMinutes(null)
        pendingQueue = null
        controller?.let { player ->
            player.stop()
            player.clearMediaItems()
            publish(player)
        }
    }

    fun setPlaybackParameters(speed: Float, pitch: Float) {
        playbackPreferences.setPlaybackParameters(speed, pitch)
        controller?.setPlaybackParameters(PlaybackParameters(playbackPreferences.playbackSpeed, playbackPreferences.playbackPitch))
    }

    fun setSleepTimerMinutes(minutes: Int?) {
        sleepTimer.setMinutes(minutes)
        _state.value = _state.value.copy(sleepTimerEndElapsedMs = sleepTimer.endElapsedMs)
    }

    fun seekTo(positionMs: Long) {
        controller?.seekTo(positionMs.coerceAtLeast(0L))
    }

    fun seekToPrevious() {
        controller?.seekToPreviousMediaItem()
    }

    fun seekToNext() {
        controller?.seekToNextMediaItem()
    }

    fun seekToQueueIndex(index: Int) {
        controller?.let { player ->
            if (index in 0 until player.mediaItemCount) {
                player.seekToDefaultPosition(index)
                player.play()
                publish(player)
            }
        }
    }

    fun seekToQueueMediaId(mediaId: String) {
        controller?.let { player ->
            val index = (0 until player.mediaItemCount).firstOrNull {
                player.getMediaItemAt(it).mediaId == mediaId
            } ?: return@let
            seekToQueueIndex(index)
        }
    }

    fun moveQueueItem(mediaId: String, direction: Int) {
        controller?.let { player ->
            val from = (0 until player.mediaItemCount).firstOrNull {
                player.getMediaItemAt(it).mediaId == mediaId
            } ?: return@let
            val to = from + direction
            if (to in 0 until player.mediaItemCount) {
                player.moveMediaItem(from, to)
                publish(player)
            }
        }
    }

    fun removeQueueItems(paths: Collection<String>) {
        if (paths.isEmpty()) return
        val removed = paths.toSet()
        pendingQueue = pendingQueue?.let { (tracks, initialPath, startPlayback) ->
            val remaining = tracks.filterNot { it.file.reference in removed }
            if (remaining.isEmpty()) {
                null
            } else {
                Triple(remaining, if (initialPath in removed) {
                    audioQueueSuccessor(tracks.map { it.file.reference }, initialPath, removed)
                        ?: remaining.first().file.reference
                } else {
                    initialPath
                }, startPlayback)
            }
        }
        controller?.let { player ->
            removeAudioQueueItems(player, removed)
            publish(player)
        }
    }

    fun replaceQueueItem(oldPath: String, track: AudioTrack) {
        pendingQueue = pendingQueue?.let { (tracks, initialPath, startPlayback) ->
            Triple(tracks.map {
                if (it.file.reference == oldPath) track else it
            }, if (initialPath == oldPath) track.file.reference else initialPath, startPlayback)
        }
        controller?.let { player ->
            val index = (0 until player.mediaItemCount).firstOrNull {
                player.getMediaItemAt(it).mediaId == oldPath
            } ?: return@let
            player.replaceMediaItem(index, track.toMediaItem())
            publish(player)
        }
    }

    fun toggleRepeatMode() {
        controller?.let { player ->
            player.repeatMode = when (player.repeatMode) {
                Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                else -> Player.REPEAT_MODE_OFF
            }
            publish(player)
        }
    }

    fun toggleShuffle() {
        controller?.let { player ->
            player.shuffleModeEnabled = !player.shuffleModeEnabled
            publish(player)
        }
    }

    fun clearError() {
        _state.value = _state.value.copy(error = false)
    }

    private fun publish(player: Player, refreshQueue: Boolean = true) {
        _state.value = dev.qtremors.arcile.core.ui.audio.audioPlaybackSnapshot(player, _state.value, refreshQueue)
    }

    private fun AudioTrack.toMediaItem(): MediaItem {
        val contentUri = file.nodeRef.contentUri?.takeIf(String::isNotBlank)?.let(Uri::parse)
            ?: Uri.fromFile(File(file.reference))
        return MediaItem.Builder()
            .setMediaId(file.reference)
            .setUri(contentUri)
            .setMimeType(file.mimeType)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(displayTitle)
                    .setArtist(artist)
                    .setAlbumTitle(album)
                    .setIsPlayable(true)
                    .build()
            )
            .build()
    }

    private companion object {
        const val POSITION_UPDATE_MS = 500L
    }
}

internal fun audioQueueSuccessor(order: List<String>, currentId: String?, removed: Set<String>) =
    dev.qtremors.arcile.core.ui.audio.audioQueueSuccessor(order, currentId, removed)
internal fun removeAudioQueueItems(player: Player, removed: Set<String>) =
    dev.qtremors.arcile.core.ui.audio.removeAudioQueueItems(player, removed)
