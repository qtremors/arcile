package dev.qtremors.arcile.feature.onlyfiles

import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import dev.qtremors.arcile.core.vault.domain.VaultNodeMetadata

@androidx.annotation.OptIn(UnstableApi::class)
internal fun syncVaultAudioPlaylist(player: Player, tracks: List<VaultNodeMetadata>, initialReference: String? = null) {
    val oldReference = player.currentMediaItem?.mediaId ?: initialReference
    val oldIndex = player.currentMediaItemIndex
    for ((index, node) in tracks.withIndex()) {
        val reference = node.toSharedFileModel().reference
        val existing = (index until player.mediaItemCount).firstOrNull {
            player.getMediaItemAt(it).mediaId == reference
        }
        if (existing == null) {
            player.addMediaItem(index, MediaItem.Builder()
                .setMediaId(reference).setUri("onlyfiles://playback/${node.ref.nodeId.value}")
                .setMimeType(node.mimeType).build())
        } else if (existing != index) {
            player.moveMediaItem(existing, index)
        }
    }
    while (player.mediaItemCount > tracks.size) player.removeMediaItem(player.mediaItemCount - 1)
    if (tracks.isNotEmpty()) {
        val retained = tracks.indexOfFirst { it.toSharedFileModel().reference == oldReference }
        if (retained < 0) player.seekTo(oldIndex.coerceIn(tracks.indices), 0L)
        else if (player.currentMediaItem?.mediaId != oldReference) player.seekTo(retained, 0L)
    }
}
