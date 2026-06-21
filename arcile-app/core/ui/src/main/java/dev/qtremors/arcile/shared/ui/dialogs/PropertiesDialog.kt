package dev.qtremors.arcile.shared.ui.dialogs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.qtremors.arcile.core.ui.R
import dev.qtremors.arcile.core.storage.domain.PropertiesAccessStatus
import dev.qtremors.arcile.shared.presentation.PropertiesUiModel
import dev.qtremors.arcile.shared.ui.metadata.ImageFileMetadata
import dev.qtremors.arcile.shared.ui.metadata.ImageMetadataSectionHeader
import dev.qtremors.arcile.shared.ui.metadata.SharedImageMetadataReader
import dev.qtremors.arcile.shared.ui.metadata.formatImageResolution
import dev.qtremors.arcile.utils.formatFileSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date

@Composable
fun PropertiesDialog(
    properties: PropertiesUiModel?,
    isLoading: Boolean,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var imageMetadata by remember(properties?.pathSummary, properties?.isSingleItem) {
        mutableStateOf<ImageFileMetadata?>(null)
    }
    val shouldLoadImageMetadata = remember(properties) { properties?.isSingleImageProperty() == true }

    LaunchedEffect(properties?.pathSummary, shouldLoadImageMetadata) {
        imageMetadata = null
        val model = properties ?: return@LaunchedEffect
        if (!shouldLoadImageMetadata) return@LaunchedEffect
        imageMetadata = withContext(Dispatchers.IO) {
            runCatching {
                SharedImageMetadataReader.readMetadata(
                    context = context,
                    reference = model.pathSummary,
                    mimeType = model.mimeTypeSummary
                )
            }.getOrNull()
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .widthIn(max = 560.dp),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier.padding(start = 18.dp, top = 18.dp, end = 18.dp, bottom = 10.dp)
            ) {
                Text(
                    text = stringResource(R.string.properties_title),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )

                if (isLoading && properties == null) {
                    Text(
                        text = stringResource(R.string.properties_loading),
                        modifier = Modifier.padding(vertical = 20.dp),
                        style = MaterialTheme.typography.bodyMedium
                    )
                } else {
                    val model = properties ?: return@Column
                    val isPartialTotal = model.accessStatus != PropertiesAccessStatus.Full
                    val formattedSize = if (isPartialTotal) {
                        stringResource(R.string.properties_size_partial, formatFileSize(model.totalBytes))
                    } else {
                        formatFileSize(model.totalBytes)
                    }
                    val modifiedText = model.newestModifiedAt?.let { DateFormat.getDateTimeInstance().format(Date(it)) }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 460.dp)
                            .verticalScroll(rememberScrollState())
                            .padding(top = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        ImageMetadataSectionHeader(title = stringResource(R.string.image_gallery_metadata_file_information))
                        PropertiesRow(stringResource(R.string.properties_name), model.title)
                        PropertiesRow(stringResource(R.string.properties_location), model.pathSummary)
                        modifiedText?.let { PropertiesRow(stringResource(R.string.properties_modified), it) }
                        imageMetadata?.dateTaken?.let { PropertiesRow(stringResource(R.string.image_gallery_metadata_label_date_taken), it) }
                        imageMetadata?.let { metadata ->
                            formatImageResolution(metadata.width, metadata.height)?.let {
                                PropertiesRow(stringResource(R.string.image_gallery_metadata_label_resolution), it)
                            }
                        }
                        PropertiesRow(stringResource(R.string.properties_size), formattedSize)
                        model.mimeTypeSummary?.let { PropertiesRow(stringResource(R.string.properties_type), it) }
                        model.extensionSummary?.let { PropertiesRow(stringResource(R.string.properties_extension), it.uppercase()) }

                        val folderFileCount = model.folderFileCount
                        val folderTotalBytes = model.folderTotalBytes
                        if (model.isSingleItem && model.isDirectory == true && folderFileCount != null && folderTotalBytes != null) {
                            PropertiesRow(
                                stringResource(R.string.properties_contains),
                                stringResource(
                                    R.string.properties_contains_value,
                                    folderFileCount.toInt(),
                                    if (isPartialTotal) {
                                        stringResource(R.string.properties_size_partial, formatFileSize(folderTotalBytes))
                                    } else {
                                        formatFileSize(folderTotalBytes)
                                    }
                                )
                            )
                        }
                        if (!model.isSingleItem || model.isDirectory == true) {
                            PropertiesRow(stringResource(R.string.properties_items), model.itemCount.toString())
                            PropertiesRow(stringResource(R.string.properties_files), model.fileCount.toString())
                            PropertiesRow(stringResource(R.string.properties_folders), model.folderCount.toString())
                        }
                        model.archiveSummary?.let { archive ->
                            PropertiesRow("Archive format", archive.format.displayName)
                            PropertiesRow("Archive entries", archive.entryCount.toString())
                            PropertiesRow("Archive files", archive.fileCount.toString())
                            PropertiesRow("Archive folders", archive.folderCount.toString())
                            PropertiesRow("Uncompressed size", formatFileSize(archive.totalUncompressedSize))
                            archive.compressionRatio?.let {
                                PropertiesRow("Compression ratio", "${(it * 100).toInt()}%")
                            }
                        }
                        if (!model.isSingleItem) {
                            model.oldestModifiedAt?.let {
                                PropertiesRow(stringResource(R.string.properties_oldest_modified), DateFormat.getDateTimeInstance().format(Date(it)))
                            }
                        }
                        PropertiesRow(stringResource(R.string.properties_hidden_items), model.hiddenCount.toString())
                        PropertiesRow(
                            stringResource(R.string.properties_access),
                            when (model.accessStatus) {
                                PropertiesAccessStatus.Full -> stringResource(R.string.properties_access_full)
                                PropertiesAccessStatus.Partial -> stringResource(R.string.properties_access_partial)
                                PropertiesAccessStatus.Limited -> stringResource(R.string.properties_access_limited)
                            }
                        )

                        ImageExifSections(
                            metadata = imageMetadata,
                            cameraTitle = stringResource(R.string.image_gallery_metadata_camera_exif),
                            locationTitle = stringResource(R.string.image_gallery_metadata_location)
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.ok))
                    }
                }
            }
        }
    }
}

@Composable
private fun ImageExifSections(
    metadata: ImageFileMetadata?,
    cameraTitle: String,
    locationTitle: String
) {
    if (metadata == null) return
    val hasCamera = metadata.cameraMaker != null ||
        metadata.cameraModel != null ||
        metadata.lensModel != null ||
        metadata.iso != null ||
        metadata.exposureTime != null ||
        metadata.fNumber != null ||
        metadata.focalLength != null ||
        metadata.whiteBalance != null ||
        metadata.flash != null
    if (hasCamera) {
        Spacer(modifier = Modifier.height(10.dp))
        ImageMetadataSectionHeader(title = cameraTitle)
        if (metadata.cameraMaker != null || metadata.cameraModel != null) {
            PropertiesRow("Device", listOfNotNull(metadata.cameraMaker, metadata.cameraModel).joinToString(" "))
        }
        metadata.lensModel?.let { PropertiesRow("Lens", it) }
        metadata.exposureTime?.let { PropertiesRow("Exposure Time", it) }
        metadata.fNumber?.let { PropertiesRow("Aperture", "f/$it") }
        metadata.iso?.let { PropertiesRow("ISO", it.toString()) }
        metadata.focalLength?.let { PropertiesRow("Focal Length", "$it mm") }
        metadata.whiteBalance?.let { PropertiesRow("White Balance", it) }
        metadata.flash?.let { PropertiesRow("Flash", it) }
    }

    if (metadata.latitude != null && metadata.longitude != null) {
        Spacer(modifier = Modifier.height(10.dp))
        ImageMetadataSectionHeader(title = locationTitle)
        PropertiesRow("Coordinates", "${metadata.latitude}, ${metadata.longitude}")
        metadata.altitude?.let { PropertiesRow("Altitude", "$it m") }
    }
}

private fun PropertiesUiModel.isSingleImageProperty(): Boolean {
    if (!isSingleItem || isDirectory == true) return false
    val mime = mimeTypeSummary?.lowercase()
    if (mime?.startsWith("image/") == true) return true
    return extensionSummary?.lowercase() in setOf("jpg", "jpeg", "png", "webp", "heic", "heif", "gif", "bmp", "avif")
}

@Composable
private fun PropertiesRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.34f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(0.66f)
        )
    }
}
