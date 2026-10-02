package dev.qtremors.arcile.core.ui.metadata

import android.content.Context

/** Shared visual-media fields with optional camera/EXIF details populated by image readers. */
data class VisualMediaMetadata(
    val path: String,
    val size: Long,
    val mimeType: String?,
    val width: Int,
    val height: Int,
    val megapixel: Double,
    val cameraMaker: String?,
    val cameraModel: String?,
    val lensModel: String?,
    val iso: Int?,
    val exposureTime: String?,
    val fNumber: Double?,
    val focalLength: Double?,
    val whiteBalance: String?,
    val flash: String?,
    val dateTaken: String?,
    val latitude: Double?,
    val longitude: Double?,
    val altitude: Double?,
    val description: String? = null,
    val userComment: String? = null,
    val artist: String? = null,
    val copyright: String? = null,
    val isEditable: Boolean = false
)

data class MediaMetadataDetailLabels(
    val title: String,
    val date: String,
    val dateTaken: String,
    val resolution: String,
    val size: String,
    val uri: String,
    val path: String,
    val mimeType: String,
    val extension: String,
    val aspectRatio: String = "Aspect ratio"
)

data class MediaMetadataDetailRow(
    val label: String,
    val value: String
)

fun buildMediaMetadataDetailRows(
    title: String,
    reference: String,
    size: Long,
    lastModifiedText: String?,
    mimeType: String?,
    extension: String?,
    metadata: VisualMediaMetadata?,
    labels: MediaMetadataDetailLabels,
    context: Context,
    isUriReference: Boolean = reference.startsWith("content://"),
    fileSizeFormatter: ((Long) -> String)? = null
): List<MediaMetadataDetailRow> {
    val rows = mutableListOf<MediaMetadataDetailRow>()
    title.takeIf { it.isNotBlank() }?.let { rows += MediaMetadataDetailRow(labels.title, it) }
    lastModifiedText?.takeIf { it.isNotBlank() }?.let { rows += MediaMetadataDetailRow(labels.date, it) }
    metadata?.dateTaken?.takeIf { it.isNotBlank() }?.let { rows += MediaMetadataDetailRow(labels.dateTaken, it) }
    metadata?.let { formatMediaResolution(it.width, it.height) }?.let { rows += MediaMetadataDetailRow(labels.resolution, it) }
    metadata?.let { formatMediaAspectRatio(it.width, it.height) }?.let {
        rows += MediaMetadataDetailRow(labels.aspectRatio, it)
    }
    val formattedSize = fileSizeFormatter?.invoke(size.takeIf { it > 0L } ?: metadata?.size ?: 0L)
        ?: dev.qtremors.arcile.core.presentation.formatFileSize(
            context,
            size.takeIf { it > 0L } ?: metadata?.size ?: 0L
        )
    rows += MediaMetadataDetailRow(labels.size, formattedSize)
    reference.takeIf { it.isNotBlank() }?.let {
        rows += MediaMetadataDetailRow(if (isUriReference) labels.uri else labels.path, it)
    }
    (metadata?.mimeType ?: mimeType)?.takeIf { it.isNotBlank() }?.let {
        rows += MediaMetadataDetailRow(labels.mimeType, it)
    }
    extension?.takeIf { it.isNotBlank() }?.let {
        rows += MediaMetadataDetailRow(labels.extension, it.uppercase())
    }
    return rows
}

fun formatMediaResolution(width: Int, height: Int): String? {
    if (width <= 0 || height <= 0) return null
    return "$width x $height"
}

fun formatMediaAspectRatio(width: Int, height: Int): String? {
    if (width <= 0 || height <= 0) return null
    val divisor = greatestCommonDivisor(width, height)
    return "${width / divisor}:${height / divisor}"
}

private tailrec fun greatestCommonDivisor(a: Int, b: Int): Int =
    if (b == 0) kotlin.math.abs(a).coerceAtLeast(1) else greatestCommonDivisor(b, a % b)
