package dev.qtremors.arcile.feature.onlyfiles

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSourceException
import androidx.media3.datasource.DataSpec
import dev.qtremors.arcile.core.vault.domain.VaultId
import dev.qtremors.arcile.core.vault.domain.VaultPath
import dev.qtremors.arcile.core.vault.domain.VaultSeekableReader

@OptIn(UnstableApi::class)
internal class VaultMediaDataSource(
    private val vaultId: VaultId,
    private val path: VaultPath,
    private val openReader: (VaultId, VaultPath) -> Result<VaultSeekableReader>
) : BaseDataSource(true) {
    private var reader: VaultSeekableReader? = null
    private var position = 0L
    private var remaining = 0L
    private var openedUri: Uri? = null

    override fun open(dataSpec: DataSpec): Long {
        transferInitializing(dataSpec)
        val opened = openReader(vaultId, path).getOrElse { throw DataSourceException(it, 2000) }
        if (dataSpec.position > opened.sizeBytes) {
            opened.close()
            throw DataSourceException(DataSourceException.POSITION_OUT_OF_RANGE)
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
        if (read <= 0) return C.RESULT_END_OF_INPUT
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
