package dev.qtremors.arcile.feature.videoplayer

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.qtremors.arcile.core.storage.domain.FileModel
import dev.qtremors.arcile.core.ui.metadata.ImageFileMetadata
import dev.qtremors.arcile.core.ui.metadata.ImageMetadataDetailLabels
import dev.qtremors.arcile.core.ui.metadata.ImageMetadataDetailRow
import dev.qtremors.arcile.core.ui.metadata.buildImageMetadataDetailRows
import dev.qtremors.arcile.core.ui.metadata.formatImageFileSize
import dev.qtremors.arcile.core.runtime.di.ArcileDispatchers
import java.io.File
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.withContext

internal typealias VideoFileMetadata = ImageFileMetadata

internal interface VideoMetadataRepository {
    suspend fun read(filePath: String, mimeType: String? = null): VideoFileMetadata
}

internal class DefaultVideoMetadataRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val dispatchers: ArcileDispatchers
) : VideoMetadataRepository {

    override suspend fun read(
        filePath: String,
        mimeType: String?
    ): VideoFileMetadata = withContext(dispatchers.io) {
        val file = File(filePath)
        val retriever = MediaMetadataRetriever()
        var width = 0
        var height = 0
        var dateTaken: String? = null
        val isEditable = false
        var latitude: Double? = null
        var longitude: Double? = null

        try {
            if (filePath.startsWith("content://")) {
                retriever.setDataSource(context, Uri.parse(filePath))
            } else {
                retriever.setDataSource(filePath)
            }
            width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
            height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
            dateTaken = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DATE)

            // Extract location if present
            val location = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_LOCATION)
            if (location != null) {
                // Location usually in format "+XX.XXXX+YY.YYYY/" or similar ISO 6709
                val match = Regex("([+-]\\d+\\.\\d+)([+-]\\d+\\.\\d+)").find(location)
                if (match != null) {
                    latitude = match.groupValues[1].toDoubleOrNull()
                    longitude = match.groupValues[2].toDoubleOrNull()
                }
            }
        } catch (e: Exception) {
            // Fallback or ignore retriever error
        } finally {
            try {
                retriever.release()
            } catch (e: Exception) {
                // Ignore
            }
        }

        ImageFileMetadata(
            path = filePath,
            size = if (filePath.startsWith("content://")) 0L else file.takeIf { it.exists() }?.length() ?: 0L,
            mimeType = mimeType,
            width = width,
            height = height,
            megapixel = (width * height).toDouble() / 1_000_000.0,
            cameraMaker = null,
            cameraModel = null,
            lensModel = null,
            iso = null,
            exposureTime = null,
            fNumber = null,
            focalLength = null,
            whiteBalance = null,
            flash = null,
            dateTaken = dateTaken,
            latitude = latitude,
            longitude = longitude,
            altitude = null,
            isEditable = isEditable
        )
    }

}

internal fun buildVideoMetadataRows(
    file: FileModel,
    metadata: VideoFileMetadata?,
    labels: ImageMetadataDetailLabels,
    durationMs: Long?,
    dateText: String?,
    durationLabel: String
): List<ImageMetadataDetailRow> {
    val baseRows = buildImageMetadataDetailRows(
        title = file.name,
        reference = file.absolutePath,
        size = file.size,
        lastModifiedText = dateText ?: formatViewerDateTime(file.lastModified),
        mimeType = file.mimeType,
        extension = file.extension,
        metadata = metadata,
        labels = labels,
        isUriReference = file.absolutePath.startsWith("content://")
    )

    val finalRows = baseRows.toMutableList()

    // Insert duration row if available
    if (durationMs != null && durationMs > 0L) {
        val formattedDuration = formatDuration(durationMs)
        // Insert it right after resolution if resolution row exists, otherwise at end of general section
        val resIndex = finalRows.indexOfFirst { it.label == labels.resolution }
        if (resIndex >= 0) {
            finalRows.add(resIndex + 1, ImageMetadataDetailRow(durationLabel, formattedDuration))
        } else {
            finalRows.add(ImageMetadataDetailRow(durationLabel, formattedDuration))
        }
    }

    // Insert Content URI if available
    val uri = file.nodeRef.contentUri?.takeIf { it.isNotBlank() }
    if (uri != null) {
        val pathIndex = finalRows.indexOfFirst { it.label == labels.path }.takeIf { it >= 0 } ?: finalRows.size
        finalRows.add(pathIndex, ImageMetadataDetailRow(labels.uri, uri))
    }

    return finalRows
}

private fun formatDuration(durationMs: Long): String {
    val totalSeconds = durationMs / 1000
    val seconds = totalSeconds % 60
    val totalMinutes = totalSeconds / 60
    val minutes = totalMinutes % 60
    val hours = totalMinutes / 60
    return if (hours > 0) {
        String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.ROOT, "%d:%02d", minutes, seconds)
    }
}
