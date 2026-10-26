package dev.qtremors.arcile.core.ui.audio

import androidx.media3.common.C
import androidx.media3.common.Player

fun audioPlaybackSnapshot(player: Player, previous: AudioPlaybackState, refreshQueue: Boolean = true): AudioPlaybackState {
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

        return AudioPlaybackState(
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
