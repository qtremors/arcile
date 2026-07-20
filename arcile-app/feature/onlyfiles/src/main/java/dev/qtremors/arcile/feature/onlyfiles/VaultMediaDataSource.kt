package dev.qtremors.arcile.feature.onlyfiles

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSourceException
import androidx.media3.datasource.DataSpec
import dev.qtremors.arcile.core.vault.domain.VaultNodeRef
import dev.qtremors.arcile.core.vault.domain.VaultSeekableReader

@androidx.annotation.OptIn(UnstableApi::class)
internal class VaultMediaDataSource(
    private val refsByOpaqueId: Map<String, VaultNodeRef>,
    private val openReader: (VaultNodeRef) -> Result<VaultSeekableReader>
) : BaseDataSource(true) {
    private var reader: VaultSeekableReader? = null
    private var position = 0L
    private var remaining = 0L
    private var openedUri: Uri? = null

    override fun open(dataSpec: DataSpec): Long {
        close()
        transferInitializing(dataSpec)
        if (dataSpec.uri.scheme != "onlyfiles" || dataSpec.uri.authority != "playback") {
            throw DataSourceException(IllegalArgumentException("Unsupported playback source"), 2000)
        }
        val opaqueId = dataSpec.uri.lastPathSegment
        val ref = opaqueId?.let(refsByOpaqueId::get)
            ?: throw DataSourceException(IllegalArgumentException("Unknown playback item"), 2000)
        val opened = openReader(ref).getOrElse { throw DataSourceException(it, 2000) }
        if (dataSpec.position > opened.sizeBytes) {
            opened.close()
            throw DataSourceException(PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE)
        }
        reader = opened
        position = dataSpec.position
        remaining = if (dataSpec.length == C.LENGTH_UNSET.toLong()) {
            opened.sizeBytes - position
        } else {
            minOf(dataSpec.length, opened.sizeBytes - position)
        }
        openedUri = dataSpec.uri
        transferStarted(dataSpec)
        return remaining
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        if (remaining == 0L) return C.RESULT_END_OF_INPUT
        val count = minOf(length.toLong(), remaining).toInt()
        val read = requireNotNull(reader).readAt(position, buffer, offset, count)
        if (read <= 0) {
            throw DataSourceException(IllegalStateException("Encrypted object ended before its declared size"), 2000)
        }
        position += read
        remaining -= read
        bytesTransferred(read)
        return read
    }

    override fun getUri(): Uri? = openedUri

    override fun close() {
        val wasOpen = reader != null
        reader?.close()
        reader = null
        openedUri = null
        if (wasOpen) transferEnded()
    }
}
