package dev.qtremors.arcile.feature.audio

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import android.os.SystemClock
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
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

internal data class AudioPlaybackState(
    val isConnected: Boolean = false,
    val currentMediaId: String? = null,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val hasPrevious: Boolean = false,
    val hasNext: Boolean = false,
    val queueMediaIds: List<String> = emptyList(),
    val queueEntries: List<AudioQueueEntry> = emptyList(),
    val currentMediaIndex: Int = 0,
    val shuffleMediaIds: List<String> = emptyList(),
    val repeatMode: AudioRepeatMode = AudioRepeatMode.OFF,
    val shuffleEnabled: Boolean = false,
    val playbackSpeed: Float = 1f,
    val playbackPitch: Float = 1f,
    val sleepTimerEndElapsedMs: Long? = null,
    val error: Boolean = false
)

internal data class AudioQueueEntry(
    val id: String,
    val uri: String,
    val title: String?,
    val artist: String?,
    val album: String?
)

internal enum class AudioRepeatMode {
    OFF,
    ALL,
    ONE
}

@Singleton
internal class AudioPlaybackController @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _state = MutableStateFlow(AudioPlaybackState())
    val state: StateFlow<AudioPlaybackState> = _state.asStateFlow()
    private var controller: MediaController? = null
    private var pendingQueue: Triple<List<AudioTrack>, String, Boolean>? = null
    private val playbackPreferences = context.getSharedPreferences("audio_playback_settings", Context.MODE_PRIVATE)
    private var sleepTimerJob: Job? = null
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
        val token = SessionToken(context, ComponentName(context, AudioPlaybackService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        future.addListener(
            {
                runCatching { future.get() }.onSuccess { connected ->
                    controller = connected
                    connected.addListener(listener)
                    connected.setPlaybackParameters(PlaybackParameters(
                        playbackPreferences.getFloat("speed", 1f),
                        playbackPreferences.getFloat("pitch", 1f)
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
        val safeSpeed = speed.coerceIn(0.5f, 2f)
        val safePitch = pitch.coerceIn(0.5f, 2f)
        playbackPreferences.edit().putFloat("speed", safeSpeed)
            .putFloat("pitch", safePitch).apply()
        controller?.setPlaybackParameters(PlaybackParameters(safeSpeed, safePitch))
    }

    fun setSleepTimerMinutes(minutes: Int?) {
        sleepTimerJob?.cancel()
        sleepTimerJob = null
        val durationMs = minutes?.takeIf { it > 0 }?.toLong()?.times(60_000L)
        _state.value = _state.value.copy(
            sleepTimerEndElapsedMs = durationMs?.let { SystemClock.elapsedRealtime() + it }
        )
        if (durationMs != null) {
            sleepTimerJob = scope.launch {
                delay(durationMs)
                controller?.pause()
                _state.value = _state.value.copy(sleepTimerEndElapsedMs = null)
            }
        }
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
                    remaining.first().file.reference
                } else {
                    initialPath
                }, startPlayback)
            }
        }
        controller?.let { player ->
            (player.mediaItemCount - 1 downTo 0).forEach { index ->
                if (player.getMediaItemAt(index).mediaId in removed) {
                    player.removeMediaItem(index)
                }
            }
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
        val previous = _state.value
        val mediaCount = player.mediaItemCount
        val queueIds = if (refreshQueue || previous.queueMediaIds.size != mediaCount) {
            (0 until mediaCount).map { index -> player.getMediaItemAt(index).mediaId }
        } else previous.queueMediaIds
        val queueEntries = if (refreshQueue || previous.queueEntries.size != mediaCount) {
            (0 until mediaCount).map { index ->
                val item = player.getMediaItemAt(index)
                AudioQueueEntry(
                    id = item.mediaId,
                    uri = item.localConfiguration?.uri?.toString().orEmpty(),
                    title = item.mediaMetadata.title?.toString(),
                    artist = item.mediaMetadata.artist?.toString(),
                    album = item.mediaMetadata.albumTitle?.toString()
                )
            }
        } else previous.queueEntries
        val timeline = player.currentTimeline
        val shuffleIds = if (!refreshQueue && previous.shuffleEnabled == player.shuffleModeEnabled &&
            previous.shuffleMediaIds.size == mediaCount
        ) previous.shuffleMediaIds else if (!timeline.isEmpty && player.shuffleModeEnabled) {
            val ids = mutableListOf<String>()
            var idx = timeline.getFirstWindowIndex(true)
            while (idx != C.INDEX_UNSET && ids.size < mediaCount) {
                ids.add(player.getMediaItemAt(idx).mediaId)
                idx = timeline.getNextWindowIndex(idx, Player.REPEAT_MODE_OFF, true)
            }
            ids.ifEmpty { (0 until mediaCount).map { player.getMediaItemAt(it).mediaId } }
        } else emptyList()
        val currentId = player.currentMediaItem?.mediaId?.takeIf(String::isNotBlank)
        val currentIndex = player.currentMediaItemIndex.coerceAtLeast(0)

        _state.value = AudioPlaybackState(
            isConnected = true,
            currentMediaId = currentId,
            isPlaying = player.isPlaying,
            positionMs = player.currentPosition.coerceAtLeast(0L),
            durationMs = player.duration.takeIf { it != C.TIME_UNSET }?.coerceAtLeast(0L) ?: 0L,
            hasPrevious = player.hasPreviousMediaItem(),
            hasNext = player.hasNextMediaItem(),
            queueMediaIds = queueIds,
            queueEntries = queueEntries,
            currentMediaIndex = currentIndex,
            shuffleMediaIds = shuffleIds,
            repeatMode = when (player.repeatMode) {
                Player.REPEAT_MODE_ALL -> AudioRepeatMode.ALL
                Player.REPEAT_MODE_ONE -> AudioRepeatMode.ONE
                else -> AudioRepeatMode.OFF
            },
            shuffleEnabled = player.shuffleModeEnabled,
            playbackSpeed = player.playbackParameters.speed,
            playbackPitch = player.playbackParameters.pitch,
            sleepTimerEndElapsedMs = previous.sleepTimerEndElapsedMs,
            error = previous.error
        )
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
