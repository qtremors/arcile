package dev.qtremors.arcile.feature.onlyfiles

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.media.ExifInterface
import dev.qtremors.arcile.core.vault.domain.VaultNodeMetadata
import dev.qtremors.arcile.core.vault.domain.VaultNodeRef
import dev.qtremors.arcile.core.vault.domain.VaultSeekableReader
import java.io.InputStream
import java.nio.ByteBuffer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal suspend fun decodeVaultViewerImage(
    node: VaultNodeMetadata,
    targetWidth: Int,
    targetHeight: Int,
    openReader: (VaultNodeRef) -> Result<VaultSeekableReader>
): Result<Any> = withContext(Dispatchers.IO) {
    runCatching {
        val animated = node.extension.equals("gif", ignoreCase = true) || node.mimeType == "image/gif"
        if (animated && node.sizeBytes in 1..MAX_ANIMATED_IMAGE_BYTES.toLong()) {
            val bytes = openReader(node.ref).getOrThrow().use { reader ->
                require(reader.sizeBytes in 1..MAX_ANIMATED_IMAGE_BYTES.toLong())
                ByteArray(reader.sizeBytes.toInt()).also { target ->
                    var offset = 0
                    while (offset < target.size) {
                        val count = reader.readAt(offset.toLong(), target, offset, minOf(64 * 1024, target.size - offset))
                        check(count > 0) { "Encrypted image ended before its declared size" }
                        offset += count
                    }
                }
            }
            return@runCatching ImageDecoder.decodeDrawable(ImageDecoder.createSource(ByteBuffer.wrap(bytes))) {
                    decoder, info, _ ->
                val scale = minOf(
                    1f, targetWidth.toFloat() / info.size.width, targetHeight.toFloat() / info.size.height
                )
                decoder.setTargetSize(
                    (info.size.width * scale).toInt().coerceAtLeast(1),
                    (info.size.height * scale).toInt().coerceAtLeast(1)
                )
            }
        }

        val bitmap = requireNotNull(decodeSampledVaultImage(node.ref, targetWidth, targetHeight, openReader).getOrThrow())
        val orientation = openReader(node.ref).getOrNull()?.use { reader ->
            runCatching {
                ExifInterface(VaultReaderInputStream(reader)).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL
                )
            }.getOrNull()
        }
        val transform = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> transform.setScale(-1f, 1f)
            ExifInterface.ORIENTATION_ROTATE_180 -> transform.setRotate(180f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> transform.setScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> { transform.setRotate(90f); transform.postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_ROTATE_90 -> transform.setRotate(90f)
            ExifInterface.ORIENTATION_TRANSVERSE -> { transform.setRotate(-90f); transform.postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_ROTATE_270 -> transform.setRotate(-90f)
        }
        if (transform.isIdentity) bitmap
        else Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, transform, true)
    }
}

private const val MAX_ANIMATED_IMAGE_BYTES = 8 * 1024 * 1024

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
