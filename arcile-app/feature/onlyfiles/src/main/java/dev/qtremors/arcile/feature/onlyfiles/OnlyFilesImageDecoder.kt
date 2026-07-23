package dev.qtremors.arcile.feature.onlyfiles

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import dev.qtremors.arcile.core.vault.domain.VaultNodeRef
import dev.qtremors.arcile.core.vault.domain.VaultSeekableReader
import java.io.InputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal suspend fun decodeSampledVaultImageOnWorker(
    ref: VaultNodeRef,
    targetWidth: Int,
    targetHeight: Int,
    openReader: (VaultNodeRef) -> Result<VaultSeekableReader>
): Result<Bitmap?> = withContext(Dispatchers.IO) {
    decodeSampledVaultImage(ref, targetWidth, targetHeight, openReader)
}

internal fun decodeSampledVaultImage(
    ref: VaultNodeRef,
    targetWidth: Int,
    targetHeight: Int,
    openReader: (VaultNodeRef) -> Result<VaultSeekableReader>
): Result<Bitmap?> = runCatching {
    require(targetWidth > 0 && targetHeight > 0)
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    openReader(ref).getOrThrow().use { reader ->
        BitmapFactory.decodeStream(VaultReaderInputStream(reader), null, bounds)
    }
    require(bounds.outWidth > 0 && bounds.outHeight > 0) { "Unsupported or damaged image" }
    var sample = 1
    while (bounds.outWidth / sample > targetWidth * 2 || bounds.outHeight / sample > targetHeight * 2) {
        sample = sample shl 1
        if (sample <= 0) throw IllegalArgumentException("Image dimensions are too large")
    }
    val options = BitmapFactory.Options().apply {
        inSampleSize = sample
        inPreferredConfig = Bitmap.Config.ARGB_8888
    }
    openReader(ref).getOrThrow().use { reader ->
        BitmapFactory.decodeStream(VaultReaderInputStream(reader), null, options)
    }
}

private class VaultReaderInputStream(
    private val reader: VaultSeekableReader
) : InputStream() {
    private var position = 0L
    private val single = ByteArray(1)

    override fun read(): Int {
        val count = read(single, 0, 1)
        return if (count < 0) -1 else single[0].toInt() and 0xff
    }

    override fun read(target: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        if (position >= reader.sizeBytes) return -1
        val requested = minOf(length.toLong(), reader.sizeBytes - position).toInt()
        val count = reader.readAt(position, target, offset, requested)
        if (count <= 0) throw IllegalStateException("Encrypted image ended before its declared size")
        position += count
        return count
    }

    override fun skip(count: Long): Long {
        val skipped = count.coerceAtLeast(0L).coerceAtMost(reader.sizeBytes - position)
        position += skipped
        return skipped
    }

    override fun available(): Int = minOf(Int.MAX_VALUE.toLong(), reader.sizeBytes - position).toInt()
}
