package dev.qtremors.arcile.shared.ui.metadata

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
fun ImageMetadataSections(
    fileRows: List<ImageMetadataDetailRow>,
    metadata: ImageFileMetadata?,
    sectionTitle: String,
    cameraTitle: String,
    locationTitle: String
) {
    if (fileRows.isEmpty() && !imageHasExif(metadata)) return
    ImageMetadataSectionHeader(title = sectionTitle)
    fileRows.forEach { row ->
        ImageMetadataRow(label = row.label, value = row.value)
    }

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
        ImageMetadataSectionHeader(title = cameraTitle)
        if (metadata.cameraMaker != null || metadata.cameraModel != null) {
            ImageMetadataRow("Device", listOfNotNull(metadata.cameraMaker, metadata.cameraModel).joinToString(" "))
        }
        metadata.lensModel?.let { ImageMetadataRow("Lens", it) }
        metadata.exposureTime?.let { ImageMetadataRow("Exposure Time", it) }
        metadata.fNumber?.let { ImageMetadataRow("Aperture", "f/$it") }
        metadata.iso?.let { ImageMetadataRow("ISO", it.toString()) }
        metadata.focalLength?.let { ImageMetadataRow("Focal Length", "$it mm") }
        metadata.whiteBalance?.let { ImageMetadataRow("White Balance", it) }
        metadata.flash?.let { ImageMetadataRow("Flash", it) }
    }

    if (metadata?.latitude != null && metadata.longitude != null) {
        Spacer(modifier = Modifier.height(16.dp))
        ImageMetadataSectionHeader(title = locationTitle)
        ImageMetadataRow("Coordinates", "${metadata.latitude}, ${metadata.longitude}")
        metadata.altitude?.let { ImageMetadataRow("Altitude", "$it m") }
    }
}

@Composable
fun ImageMetadataSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(vertical = 8.dp)
    )
}

@Composable
fun ImageMetadataRow(label: String, value: String) {
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
