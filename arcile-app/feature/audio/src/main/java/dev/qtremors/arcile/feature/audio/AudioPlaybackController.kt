package dev.qtremors.arcile.feature.audio

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.qtremors.arcile.core.storage.domain.AudioTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
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
    val currentMediaIndex: Int = 0,
    val repeatMode: AudioRepeatMode = AudioRepeatMode.OFF,
    val shuffleEnabled: Boolean = false,
    val error: Boolean = false
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
    private var pendingQueue: Pair<List<AudioTrack>, String>? = null

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            publish(player)
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
                    publish(connected)
                    pendingQueue?.let { (tracks, initialPath) ->
                        pendingQueue = null
                        playQueue(tracks, initialPath)
                    }
                }
            },
            ContextCompat.getMainExecutor(context)
        )
        scope.launch {
            while (isActive) {
                controller?.let(::publish)
                delay(POSITION_UPDATE_MS)
            }
        }
    }

    fun playQueue(tracks: List<AudioTrack>, initialPath: String) {
        val player = controller
        if (player == null) {
            pendingQueue = tracks to initialPath
            return
        }
        val items = tracks.map { it.toMediaItem() }
        val initialIndex = tracks.indexOfFirst { it.file.absolutePath == initialPath }
            .takeIf { it >= 0 } ?: 0
        player.setMediaItems(items, initialIndex, C.TIME_UNSET)
        player.prepare()
        player.play()
        publish(player)
    }

    fun togglePlayback() {
        controller?.let { player ->
            if (player.isPlaying) player.pause() else player.play()
            publish(player)
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

    fun removeQueueItems(paths: Collection<String>) {
        if (paths.isEmpty()) return
        val removed = paths.toSet()
        pendingQueue = pendingQueue?.let { (tracks, initialPath) ->
            val remaining = tracks.filterNot { it.file.absolutePath in removed }
            if (remaining.isEmpty()) {
                null
            } else {
                remaining to if (initialPath in removed) {
                    remaining.first().file.absolutePath
                } else {
                    initialPath
                }
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
        pendingQueue = pendingQueue?.let { (tracks, initialPath) ->
            tracks.map {
                if (it.file.absolutePath == oldPath) track else it
            } to if (initialPath == oldPath) track.file.absolutePath else initialPath
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

    private fun publish(player: Player) {
        val timeline = player.currentTimeline
        val mediaCount = player.mediaItemCount
        val queueIds = if (!timeline.isEmpty && player.shuffleModeEnabled) {
            val ids = mutableListOf<String>()
            var idx = timeline.getFirstWindowIndex(true)
            while (idx != C.INDEX_UNSET && ids.size < mediaCount) {
                ids.add(player.getMediaItemAt(idx).mediaId)
                idx = timeline.getNextWindowIndex(idx, Player.REPEAT_MODE_OFF, true)
            }
            ids.ifEmpty { (0 until mediaCount).map { player.getMediaItemAt(it).mediaId } }
        } else {
            (0 until mediaCount).map { index ->
                player.getMediaItemAt(index).mediaId
            }
        }
        val currentId = player.currentMediaItem?.mediaId?.takeIf(String::isNotBlank)
        val currentIndex = if (player.shuffleModeEnabled && currentId != null && queueIds.isNotEmpty()) {
            queueIds.indexOf(currentId).takeIf { it >= 0 } ?: player.currentMediaItemIndex.coerceAtLeast(0)
        } else {
            player.currentMediaItemIndex.coerceAtLeast(0)
        }

        _state.value = AudioPlaybackState(
            isConnected = true,
            currentMediaId = currentId,
            isPlaying = player.isPlaying,
            positionMs = player.currentPosition.coerceAtLeast(0L),
            durationMs = player.duration.takeIf { it != C.TIME_UNSET }?.coerceAtLeast(0L) ?: 0L,
            hasPrevious = player.hasPreviousMediaItem(),
            hasNext = player.hasNextMediaItem(),
            queueMediaIds = queueIds,
            currentMediaIndex = currentIndex,
            repeatMode = when (player.repeatMode) {
                Player.REPEAT_MODE_ALL -> AudioRepeatMode.ALL
                Player.REPEAT_MODE_ONE -> AudioRepeatMode.ONE
                else -> AudioRepeatMode.OFF
            },
            shuffleEnabled = player.shuffleModeEnabled,
            error = _state.value.error
        )
    }

    private fun AudioTrack.toMediaItem(): MediaItem {
        val contentUri = file.nodeRef.contentUri?.takeIf(String::isNotBlank)?.let(Uri::parse)
            ?: Uri.fromFile(File(file.absolutePath))
        return MediaItem.Builder()
            .setMediaId(file.absolutePath)
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
