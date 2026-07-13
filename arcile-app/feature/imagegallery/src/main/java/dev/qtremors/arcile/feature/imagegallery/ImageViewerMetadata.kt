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
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.runtime.saveable.rememberSaveable
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import dev.qtremors.arcile.core.storage.domain.FileModel
import dev.qtremors.arcile.core.ui.R
import dev.qtremors.arcile.core.ui.dialogs.DeleteConfirmationDialog
import dev.qtremors.arcile.core.ui.metadata.ImageMetadataDetailLabels
import dev.qtremors.arcile.core.ui.metadata.ImageMetadataDetailRow
import dev.qtremors.arcile.core.ui.metadata.ImageMetadataRow
import dev.qtremors.arcile.core.ui.metadata.ImageMetadataSectionHeader
import dev.qtremors.arcile.core.ui.metadata.ImageMetadataSections
import dev.qtremors.arcile.core.ui.metadata.ImageMetadataUpdate
import dev.qtremors.arcile.core.ui.metadata.buildImageMetadataDetailRows
import dev.qtremors.arcile.core.ui.metadata.imageHasExif
import dev.qtremors.arcile.core.ui.rememberArcileHaptics
import dev.qtremors.arcile.core.ui.theme.ExpressiveShapes
import dev.qtremors.arcile.core.ui.theme.bounceClickable
import kotlinx.coroutines.launch
import kotlin.math.abs
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

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
internal fun MetadataSheet(
    file: FileModel,
    metadata: GalleryFileMetadata?,
    isSaving: Boolean,
    metadataRevision: Long,
    onSaveMetadata: (ImageMetadataUpdate) -> Unit,
    onEraseMetadata: () -> Unit,
    onDismiss: () -> Unit
) {
    var isEditing by rememberSaveable(file.absolutePath) { mutableStateOf(false) }
    var revisionAtEditStart by rememberSaveable(file.absolutePath) { mutableStateOf(metadataRevision) }
    val canEdit = metadata?.isEditable == true

    LaunchedEffect(metadataRevision) {
        if (metadataRevision > revisionAtEditStart) {
            isEditing = false
        }
    }

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

                val hasExif = remember(metadata) { imageHasExif(metadata) }

                if (!isEditing && canEdit) {
                    IconButton(
                        onClick = {
                            revisionAtEditStart = metadataRevision
                            isEditing = true
                        },
                        modifier = Modifier.clip(CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = stringResource(R.string.image_gallery_metadata_edit),
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                if (!isEditing && hasExif && canEdit) {
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

            if (isEditing) {
                MetadataEditor(
                    metadata = metadata,
                    isSaving = isSaving,
                    onCancel = { isEditing = false },
                    onSave = onSaveMetadata,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                )
            } else {
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
                            extension = stringResource(R.string.image_gallery_metadata_label_extension),
                            aspectRatio = stringResource(R.string.image_gallery_metadata_label_aspect_ratio)
                        )
                        val formattedDateTaken = metadata?.dateTaken?.let { dt ->
                            val parts = dt.trim().split(' ')
                            val datePart = parts.firstOrNull() ?: ""
                            val timePart = parts.getOrNull(1) ?: ""
                            if (datePart.isBlank()) {
                                dt
                            } else {
                                val formattedDate = datePart.replace(':', '-')
                                if (timePart.isNotBlank()) "$formattedDate $timePart" else formattedDate
                            }
                        }
                        val displayMetadata = metadata?.copy(dateTaken = formattedDateTaken)
                        ImageMetadataSections(
                            fileRows = buildMetadataDetailRows(file, displayMetadata, labels),
                            metadata = displayMetadata,
                            sectionTitle = stringResource(R.string.image_gallery_metadata_file_information),
                            cameraTitle = stringResource(R.string.image_gallery_metadata_camera_exif),
                            locationTitle = stringResource(R.string.image_gallery_metadata_location)
                        )
                    }

                    if (!canEdit) {
                        item {
                            Text(
                                text = stringResource(R.string.image_gallery_metadata_read_only),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 16.dp)
                            )
                        }
                    }

                    item {
                        Spacer(modifier = Modifier.height(32.dp))
                    }
                }
            }
        }
    }
}

@Composable
internal fun MetadataSectionHeader(title: String) {
    ImageMetadataSectionHeader(title)
}

@Composable
internal fun MetadataRow(label: String, value: String) {
    ImageMetadataRow(label, value)
}

