package dev.qtremors.arcile.feature.audio

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import org.json.JSONArray
import org.json.JSONObject

internal data class SavedAudioPlayback(
    val items: List<MediaItem>,
    val index: Int,
    val positionMs: Long,
    val repeatMode: Int,
    val shuffleEnabled: Boolean
)

internal class AudioPlaybackQueueStore(context: Context) {
    private val preferences = context.getSharedPreferences("audio_playback_queue", Context.MODE_PRIVATE)

    fun hasSavedQueue(): Boolean = preferences.contains(KEY_QUEUE)

    fun read(): SavedAudioPlayback? = runCatching {
        val raw = preferences.getString(KEY_QUEUE, null) ?: return null
        val array = JSONArray(raw)
        val items = buildList {
            for (index in 0 until array.length()) {
                val row = array.getJSONObject(index)
                val id = row.optString("id")
                val uri = row.optString("uri")
                if (id.isBlank() || uri.isBlank()) continue
                val metadata = MediaMetadata.Builder()
                    .setTitle(row.optString("title"))
                    .setArtist(row.optString("artist"))
                    .setAlbumTitle(row.optString("album"))
                    .setIsPlayable(true)
                    .build()
                add(MediaItem.Builder()
                    .setMediaId(id)
                    .setUri(Uri.parse(uri))
                    .setMediaMetadata(metadata)
                    .build())
            }
        }
        if (items.isEmpty()) return null
        SavedAudioPlayback(
            items = items,
            index = preferences.getInt(KEY_INDEX, 0).coerceIn(items.indices),
            positionMs = preferences.getLong(KEY_POSITION, 0L).coerceAtLeast(0L),
            repeatMode = preferences.getInt(KEY_REPEAT, Player.REPEAT_MODE_OFF),
            shuffleEnabled = preferences.getBoolean(KEY_SHUFFLE, false)
        )
    }.getOrNull()

    fun saveQueue(player: Player) {
        if (player.mediaItemCount == 0) {
            preferences.edit().remove(KEY_QUEUE).remove(KEY_INDEX).remove(KEY_POSITION)
                .apply()
            return
        }
        val array = JSONArray()
        for (index in 0 until player.mediaItemCount) {
            val item = player.getMediaItemAt(index)
            val uri = item.localConfiguration?.uri?.toString().orEmpty()
            if (uri.isBlank()) continue
            array.put(JSONObject()
                .put("id", item.mediaId)
                .put("uri", uri)
                .put("title", item.mediaMetadata.title?.toString().orEmpty())
                .put("artist", item.mediaMetadata.artist?.toString().orEmpty())
                .put("album", item.mediaMetadata.albumTitle?.toString().orEmpty()))
        }
        preferences.edit().putString(KEY_QUEUE, array.toString())
            .putInt(KEY_INDEX, player.currentMediaItemIndex.coerceAtLeast(0))
            .putLong(KEY_POSITION, player.currentPosition.coerceAtLeast(0L))
            .putInt(KEY_REPEAT, player.repeatMode)
            .putBoolean(KEY_SHUFFLE, player.shuffleModeEnabled)
            .apply()
    }

    fun savePosition(player: Player) {
        if (player.mediaItemCount == 0) return
        preferences.edit()
            .putInt(KEY_INDEX, player.currentMediaItemIndex.coerceAtLeast(0))
            .putLong(KEY_POSITION, player.currentPosition.coerceAtLeast(0L))
            .putInt(KEY_REPEAT, player.repeatMode)
            .putBoolean(KEY_SHUFFLE, player.shuffleModeEnabled)
            .apply()
    }

    private companion object {
        const val KEY_QUEUE = "queue_v1"
        const val KEY_INDEX = "index_v1"
        const val KEY_POSITION = "position_v1"
        const val KEY_REPEAT = "repeat_v1"
        const val KEY_SHUFFLE = "shuffle_v1"
    }
}
