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
import dev.qtremors.arcile.shared.ui.rememberArcileHaptics
import kotlinx.coroutines.launch
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)

@Composable
fun MetadataSheet(
    file: FileModel,
    metadata: GalleryFileMetadata?,
    onEraseMetadata: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.image_gallery_metadata_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
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
                    IconButton(onClick = onEraseMetadata) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = stringResource(R.string.image_gallery_metadata_erase),
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            LazyColumn(
                modifier = Modifier.fillMaxWidth()
            ) {
                item {
                    MetadataSectionHeader(title = "File Information")
                    MetadataRow(label = "Path", value = file.absolutePath)
                    MetadataRow(label = "Size", value = formatFileSize(file.size))
                    if (metadata != null && metadata.width > 0) {
                        MetadataRow(
                            label = "Dimensions",
                            value = "${metadata.width} x ${metadata.height} (${metadata.megapixel} MP)"
                        )
                    }
                    if (metadata?.mimeType != null) {
                        MetadataRow(label = "Mime Type", value = metadata.mimeType)
                    }
                    if (metadata?.dateTaken != null) {
                        MetadataRow(label = "Date Taken", value = metadata.dateTaken)
                    }
                }

                if (metadata != null && (
                    metadata.cameraMaker != null ||
                    metadata.cameraModel != null ||
                    metadata.lensModel != null ||
                    metadata.iso != null ||
                    metadata.exposureTime != null ||
                    metadata.fNumber != null ||
                    metadata.focalLength != null
                )) {
                    item {
                        Spacer(modifier = Modifier.height(16.dp))
                        MetadataSectionHeader(title = "Camera & EXIF")
                        if (metadata.cameraMaker != null || metadata.cameraModel != null) {
                            MetadataRow(
                                label = "Device",
                                value = listOfNotNull(metadata.cameraMaker, metadata.cameraModel).joinToString(" ")
                            )
                        }
                        if (metadata.lensModel != null) {
                            MetadataRow(label = "Lens", value = metadata.lensModel)
                        }
                        if (metadata.exposureTime != null) {
                            MetadataRow(label = "Exposure Time", value = metadata.exposureTime)
                        }
                        if (metadata.fNumber != null) {
                            MetadataRow(label = "Aperture", value = "f/${metadata.fNumber}")
                        }
                        if (metadata.iso != null) {
                            MetadataRow(label = "ISO", value = metadata.iso.toString())
                        }
                        if (metadata.focalLength != null) {
                            MetadataRow(label = "Focal Length", value = "${metadata.focalLength} mm")
                        }
                        if (metadata.whiteBalance != null) {
                            MetadataRow(label = "White Balance", value = metadata.whiteBalance)
                        }
                        if (metadata.flash != null) {
                            MetadataRow(label = "Flash", value = metadata.flash)
                        }
                    }
                }

                if (metadata?.latitude != null && metadata.longitude != null) {
                    item {
                        Spacer(modifier = Modifier.height(16.dp))
                        MetadataSectionHeader(title = "Location")
                        MetadataRow(label = "Coordinates", value = "${metadata.latitude}, ${metadata.longitude}")
                        if (metadata.altitude != null) {
                            MetadataRow(label = "Altitude", value = "${metadata.altitude} m")
                        }
                    }
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
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(vertical = 8.dp)
    )
}

@Composable
fun MetadataRow(label: String, value: String) {
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
            modifier = Modifier.width(120.dp)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
    }
}

