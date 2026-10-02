package dev.qtremors.arcile.core.ui.metadata

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun MediaMetadataSections(
    fileRows: List<MediaMetadataDetailRow>,
    metadata: VisualMediaMetadata?,
    sectionTitle: String,
    cameraTitle: String,
    locationTitle: String
) {
    if (fileRows.isEmpty() && !imageHasExif(metadata)) return
    MediaMetadataSectionHeader(title = sectionTitle)
    fileRows.forEach { row ->
        MediaMetadataRow(label = row.label, value = row.value)
    }
    metadata?.description?.let { MediaMetadataRow("Description", it) }
    metadata?.userComment?.let { MediaMetadataRow("Comment", it) }
    metadata?.artist?.let { MediaMetadataRow("Artist", it) }
    metadata?.copyright?.let { MediaMetadataRow("Copyright", it) }

    if (metadata != null && (
            metadata.cameraMaker != null ||
                metadata.cameraModel != null ||
                metadata.lensModel != null ||
                metadata.iso != null ||
                metadata.exposureTime != null ||
                metadata.fNumber != null ||
                metadata.focalLength != null ||
                metadata.whiteBalance != null ||
                metadata.flash != null
            )
    ) {
        Spacer(modifier = Modifier.height(16.dp))
        MediaMetadataSectionHeader(title = cameraTitle)
        if (metadata.cameraMaker != null || metadata.cameraModel != null) {
            MediaMetadataRow("Device", listOfNotNull(metadata.cameraMaker, metadata.cameraModel).joinToString(" "))
        }
        metadata.lensModel?.let { MediaMetadataRow("Lens", it) }
        metadata.exposureTime?.let { MediaMetadataRow("Exposure Time", it) }
        metadata.fNumber?.let { MediaMetadataRow("Aperture", "f/$it") }
        metadata.iso?.let { MediaMetadataRow("ISO", it.toString()) }
        metadata.focalLength?.let { MediaMetadataRow("Focal Length", "$it mm") }
        metadata.whiteBalance?.let { MediaMetadataRow("White Balance", it) }
        metadata.flash?.let { MediaMetadataRow("Flash", it) }
    }

    if (metadata?.latitude != null && metadata.longitude != null) {
        Spacer(modifier = Modifier.height(16.dp))
        MediaMetadataSectionHeader(title = locationTitle)
        MediaMetadataRow("Coordinates", "${metadata.latitude}, ${metadata.longitude}")
        metadata.altitude?.let { MediaMetadataRow("Altitude", "$it m") }
    }
}

@Composable
fun MediaMetadataSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(vertical = 8.dp)
    )
}

@Composable
fun MediaMetadataRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.4f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(0.6f)
        )
    }
}
