package dev.qtremors.arcile.feature.onlyfiles

import dev.qtremors.arcile.core.runtime.runCatchingCancellable
import android.media.MediaDataSource
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import dev.qtremors.arcile.core.storage.domain.AudioTrack
import dev.qtremors.arcile.core.ui.audio.AudioFileDetails
import dev.qtremors.arcile.core.vault.domain.VaultNodeMetadata
import dev.qtremors.arcile.core.vault.domain.VaultNodeRef
import dev.qtremors.arcile.core.vault.domain.VaultSeekableReader
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

internal data class VaultAudioMetadata(val track: AudioTrack, val details: AudioFileDetails?)

internal fun vaultAudioTrack(node: VaultNodeMetadata, tag: (Int) -> String? = { null }): AudioTrack {
    fun text(key: Int) = tag(key)?.trim()?.takeIf { it.isNotEmpty() }?.take(4_096)
    fun number(key: Int) = text(key)?.substringBefore('/')?.toIntOrNull()?.takeIf { it >= 0 }
    return AudioTrack(
        file = node.toSharedFileModel(), title = text(MediaMetadataRetriever.METADATA_KEY_TITLE) ?: node.name.substringBeforeLast('.'),
        artist = text(MediaMetadataRetriever.METADATA_KEY_ARTIST), album = text(MediaMetadataRetriever.METADATA_KEY_ALBUM),
        durationMs = text(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()?.coerceAtLeast(0L) ?: 0L,
        albumArtist = text(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST), genre = text(MediaMetadataRetriever.METADATA_KEY_GENRE),
        trackNumber = number(MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER),
        year = number(MediaMetadataRetriever.METADATA_KEY_YEAR), discNumber = number(MediaMetadataRetriever.METADATA_KEY_DISC_NUMBER)
    )
}

/** Metadata is read directly from a leased vault reader, without a local-file fallback. */
internal suspend fun readVaultAudioMetadata(
    node: VaultNodeMetadata, openReader: (VaultNodeRef) -> Result<VaultSeekableReader>
): VaultAudioMetadata = withContext(Dispatchers.IO) {
    var track = vaultAudioTrack(node)
    var details: AudioFileDetails? = null
    val coroutine = currentCoroutineContext()
    openReader(node.ref).getOrNull()?.use { reader ->
        val source = object : MediaDataSource() {
            private var remaining = 32L * 1024L * 1024L
            override fun getSize() = reader.sizeBytes
            override fun readAt(position: Long, buffer: ByteArray, offset: Int, size: Int): Int {
                coroutine.ensureActive()
                if (size == 0) return 0
                if (position >= reader.sizeBytes) return -1
                if (size > remaining) throw IOException("Audio metadata read limit exceeded")
                val read = reader.readAt(position, buffer, offset, size)
                if (read > 0) remaining -= read
                return read
            }
            override fun close() = Unit
        }
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(source)
            track = vaultAudioTrack(node, retriever::extractMetadata)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // Playback remains available when a platform decoder cannot read tags.
        } finally {
            runCatchingCancellable { retriever.release() }
        }
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(source)
            val format = (0 until extractor.trackCount).map(extractor::getTrackFormat)
                .firstOrNull { it.getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true }
            if (format != null) {
                fun number(key: String) = if (format.containsKey(key)) format.getInteger(key).takeIf { it > 0 } else null
                details = AudioFileDetails(number(MediaFormat.KEY_BIT_RATE), number(MediaFormat.KEY_SAMPLE_RATE), number(MediaFormat.KEY_CHANNEL_COUNT))
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // Container details are optional; names and file properties remain usable.
        } finally {
            runCatchingCancellable { extractor.release() }
        }
    }
    VaultAudioMetadata(track, details)
}
