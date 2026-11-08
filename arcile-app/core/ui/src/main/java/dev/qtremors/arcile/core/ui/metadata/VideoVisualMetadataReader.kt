package dev.qtremors.arcile.core.ui.metadata

import android.media.MediaMetadataRetriever
import kotlinx.coroutines.CancellationException

/** Reads the same display fields for file, provider and protected media sources. */
fun readVideoVisualMetadata(
    reference: String,
    size: Long,
    mimeType: String?,
    setDataSource: (MediaMetadataRetriever) -> Unit
): VisualMediaMetadata {
    val retriever = MediaMetadataRetriever()
    var width = 0
    var height = 0
    var dateTaken: String? = null
    var latitude: Double? = null
    var longitude: Double? = null
    try {
        setDataSource(retriever)
        width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
        height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
        dateTaken = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DATE)
        val location = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_LOCATION)
        val coordinates = location?.let(videoLocationPattern::find)
        latitude = coordinates?.groupValues?.get(1)?.toDoubleOrNull()
        longitude = coordinates?.groupValues?.get(2)?.toDoubleOrNull()
    } catch (error: CancellationException) {
        throw error
    } catch (_: Exception) {
        // File properties remain available when the platform cannot decode metadata.
    } finally {
        runCatching { retriever.release() }
    }
    return VisualMediaMetadata(
        path = reference, size = size, mimeType = mimeType, width = width, height = height,
        megapixel = width.toDouble() * height / 1_000_000.0,
        cameraMaker = null, cameraModel = null, lensModel = null, iso = null, exposureTime = null,
        fNumber = null, focalLength = null, whiteBalance = null, flash = null,
        dateTaken = dateTaken, latitude = latitude, longitude = longitude, altitude = null
    )
}

private val videoLocationPattern = Regex("([+-]\\d+\\.\\d+)([+-]\\d+\\.\\d+)")
