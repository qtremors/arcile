package dev.qtremors.arcile.core.ui.audio

import androidx.media3.common.C
import androidx.media3.common.Player

fun audioQueueSuccessor(order: List<String>, currentId: String?, removed: Set<String>): String? {
    val currentIndex = order.indexOf(currentId)
    return order.drop(currentIndex + 1).firstOrNull { it !in removed }
        ?: order.take(currentIndex.coerceAtLeast(0)).lastOrNull { it !in removed }
}

fun removeAudioQueueItems(player: Player, removed: Set<String>) {
    val currentId = player.currentMediaItem?.mediaId
    val resumePlayback = player.playWhenReady
    val timeline = player.currentTimeline
    val order = buildList {
        var index = timeline.getFirstWindowIndex(player.shuffleModeEnabled)
        while (index != C.INDEX_UNSET && size < player.mediaItemCount) {
            add(player.getMediaItemAt(index).mediaId)
            index = timeline.getNextWindowIndex(index, Player.REPEAT_MODE_OFF, player.shuffleModeEnabled)
        }
    }.ifEmpty { (0 until player.mediaItemCount).map { player.getMediaItemAt(it).mediaId } }
    val successor = if (currentId in removed) audioQueueSuccessor(order, currentId, removed) else null
    (player.mediaItemCount - 1 downTo 0).forEach { index ->
        if (player.getMediaItemAt(index).mediaId in removed) player.removeMediaItem(index)
    }
    if (successor != null) {
        val index = (0 until player.mediaItemCount).firstOrNull {
            player.getMediaItemAt(it).mediaId == successor
        }
        if (index != null) {
            player.seekToDefaultPosition(index)
            player.playWhenReady = resumePlayback
        }
    }
}
