package dev.qtremors.arcile.feature.videoplayer

import android.content.Context
import androidx.core.net.toUri
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.qtremors.arcile.core.storage.domain.FileModel
import dev.qtremors.arcile.core.ui.metadata.VisualMediaMetadata
import dev.qtremors.arcile.core.ui.metadata.MediaMetadataDetailLabels
import dev.qtremors.arcile.core.ui.metadata.MediaMetadataDetailRow
import dev.qtremors.arcile.core.ui.metadata.buildMediaMetadataDetailRows
import dev.qtremors.arcile.core.ui.metadata.readVideoVisualMetadata
import dev.qtremors.arcile.core.runtime.di.ArcileDispatchers
import java.io.File
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.withContext

internal typealias VideoFileMetadata = VisualMediaMetadata

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
        val isContent = filePath.startsWith("content://")
        val size = if (isContent) 0L else File(filePath).length()
        readVideoVisualMetadata(filePath, size, mimeType) { retriever ->
            if (isContent) {
                retriever.setDataSource(context, filePath.toUri())
            } else {
                retriever.setDataSource(filePath)
            }
        }
    }

}

internal fun buildVideoMetadataRows(
    context: Context,
    file: FileModel,
    metadata: VideoFileMetadata?,
    labels: MediaMetadataDetailLabels,
    durationMs: Long?,
    dateText: String?,
    durationLabel: String,
    showReference: Boolean = true
): List<MediaMetadataDetailRow> {
    val baseRows = buildMediaMetadataDetailRows(
        title = file.name,
        reference = if (showReference) file.reference else "",
        size = file.size,
        lastModifiedText = dateText ?: formatViewerDateTime(file.lastModified),
        mimeType = file.mimeType,
        extension = file.extension,
        metadata = metadata,
        labels = labels,
        context = context,
        isUriReference = file.reference.startsWith("content://")
    )

    val finalRows = baseRows.toMutableList()

    // Insert duration row if available
    if (durationMs != null && durationMs > 0L) {
        val formattedDuration = formatDuration(durationMs)
        // Insert it right after resolution if resolution row exists, otherwise at end of general section
        val resIndex = finalRows.indexOfFirst { it.label == labels.resolution }
        if (resIndex >= 0) {
            finalRows.add(resIndex + 1, MediaMetadataDetailRow(durationLabel, formattedDuration))
        } else {
            finalRows.add(MediaMetadataDetailRow(durationLabel, formattedDuration))
        }
    }

    // Insert Content URI if available
    val uri = file.nodeRef.contentUri?.takeIf { showReference && it.isNotBlank() }
    if (uri != null) {
        val pathIndex = finalRows.indexOfFirst { it.label == labels.path }.takeIf { it >= 0 } ?: finalRows.size
        finalRows.add(pathIndex, MediaMetadataDetailRow(labels.uri, uri))
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
