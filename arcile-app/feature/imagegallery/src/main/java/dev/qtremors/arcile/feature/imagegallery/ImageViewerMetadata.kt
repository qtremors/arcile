package dev.qtremors.arcile.feature.imagegallery

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import dev.qtremors.arcile.core.storage.domain.FileModel
import dev.qtremors.arcile.core.ui.R
import dev.qtremors.arcile.shared.ui.dialogs.DeleteConfirmationDialog
import dev.qtremors.arcile.shared.ui.metadata.ImageMetadataDetailLabels
import dev.qtremors.arcile.shared.ui.metadata.ImageMetadataDetailRow
import dev.qtremors.arcile.shared.ui.metadata.ImageMetadataRow
import dev.qtremors.arcile.shared.ui.metadata.ImageMetadataSectionHeader
import dev.qtremors.arcile.shared.ui.metadata.ImageMetadataSections
import dev.qtremors.arcile.shared.ui.metadata.buildImageMetadataDetailRows
import dev.qtremors.arcile.shared.ui.rememberArcileHaptics
import kotlinx.coroutines.launch
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)

internal typealias MetadataDetailLabels = ImageMetadataDetailLabels
internal typealias MetadataDetailRow = ImageMetadataDetailRow

internal fun buildMetadataDetailRows(
    file: FileModel,
    metadata: GalleryFileMetadata?,
    labels: MetadataDetailLabels,
    dateText: String? = formatViewerDateTime(file.lastModified)
): List<MetadataDetailRow> = buildImageMetadataDetailRows(
    title = file.name,
    reference = file.absolutePath,
    size = file.size,
    lastModifiedText = dateText,
    mimeType = file.mimeType,
    extension = file.extension,
    metadata = metadata,
    labels = labels,
    isUriReference = false
).let { rows ->
    val uri = file.nodeRef.contentUri?.takeIf { it.isNotBlank() } ?: return@let rows
    val pathIndex = rows.indexOfFirst { it.label == labels.path }.takeIf { it >= 0 } ?: rows.size
    rows.toMutableList().apply {
        add(pathIndex, MetadataDetailRow(labels.uri, uri))
    }
}

@Composable
fun MetadataSheet(
    file: FileModel,
    metadata: GalleryFileMetadata?,
    onEraseMetadata: () -> Unit,
    onDismiss: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            // Header Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.clip(CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(R.string.action_close),
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Text(
                    text = stringResource(R.string.image_gallery_metadata_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )

                val hasExif = remember(metadata) {
                    metadata != null && (
                        metadata.cameraMaker != null ||
                        metadata.cameraModel != null ||
                        metadata.latitude != null ||
                        metadata.longitude != null ||
                        metadata.dateTaken != null
                    )
                }

                if (hasExif) {
                    IconButton(
                        onClick = onEraseMetadata,
                        modifier = Modifier.clip(CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = stringResource(R.string.image_gallery_metadata_erase),
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 24.dp)
            ) {
                item {
                    val labels = MetadataDetailLabels(
                        title = stringResource(R.string.image_gallery_metadata_label_title),
                        date = stringResource(R.string.image_gallery_metadata_label_date),
                        dateTaken = stringResource(R.string.image_gallery_metadata_label_date_taken),
                        resolution = stringResource(R.string.image_gallery_metadata_label_resolution),
                        size = stringResource(R.string.image_gallery_metadata_label_size),
                        uri = stringResource(R.string.image_gallery_metadata_label_uri),
                        path = stringResource(R.string.image_gallery_metadata_label_path),
                        mimeType = stringResource(R.string.image_gallery_metadata_label_mime_type),
                        extension = stringResource(R.string.image_gallery_metadata_label_extension)
                    )
                    ImageMetadataSections(
                        fileRows = buildMetadataDetailRows(file, metadata, labels),
                        metadata = metadata,
                        sectionTitle = stringResource(R.string.image_gallery_metadata_file_information),
                        cameraTitle = stringResource(R.string.image_gallery_metadata_camera_exif),
                        locationTitle = stringResource(R.string.image_gallery_metadata_location)
                    )
                }

                item {
                    Spacer(modifier = Modifier.height(32.dp))
                }
            }
        }
    }
}

@Composable
fun MetadataSectionHeader(title: String) {
    ImageMetadataSectionHeader(title)
}

@Composable
fun MetadataRow(label: String, value: String) {
    ImageMetadataRow(label, value)
}

