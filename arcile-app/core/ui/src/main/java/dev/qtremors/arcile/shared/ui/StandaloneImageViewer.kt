package dev.qtremors.arcile.shared.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import dev.qtremors.arcile.core.ui.R
import dev.qtremors.arcile.shared.ui.metadata.ImageFileMetadata
import dev.qtremors.arcile.shared.ui.metadata.ImageMetadataDetailLabels
import dev.qtremors.arcile.shared.ui.metadata.ImageMetadataSections
import dev.qtremors.arcile.shared.ui.metadata.SharedImageMetadataReader
import dev.qtremors.arcile.shared.ui.metadata.buildImageMetadataDetailRows
import dev.qtremors.arcile.shared.ui.metadata.formatImageFileSize
import dev.qtremors.arcile.shared.ui.metadata.formatImageResolution
import dev.qtremors.arcile.ui.theme.LocalMarqueeFilenames
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun StandaloneImageViewer(
    reference: String,
    title: String,
    sizeBytes: Long,
    mimeType: String?,
    onNavigateBack: () -> Unit,
    onShare: () -> Unit,
    onOpenWith: () -> Unit
) {
    val context = LocalContext.current
    var uiVisible by remember { mutableStateOf(true) }
    var metadataVisible by remember { mutableStateOf(false) }
    var rotation by remember(reference) { mutableFloatStateOf(0f) }
    var metadata by remember(reference) { mutableStateOf<ImageFileMetadata?>(null) }
    val marqueeEnabled = LocalMarqueeFilenames.current

    LaunchedEffect(reference) {
        metadata = withContext(Dispatchers.IO) {
            runCatching { SharedImageMetadataReader.readMetadata(context, reference, mimeType) }.getOrNull()
        }
    }

    BackHandler(enabled = metadataVisible) {
        metadataVisible = false
    }

    Surface(modifier = Modifier.fillMaxSize(), color = Color.Black) {
        Box(modifier = Modifier.fillMaxSize()) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(reference)
                    .crossfade(true)
                    .build(),
                contentDescription = title,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(reference) {
                        detectTapGestures(onTap = { uiVisible = !uiVisible })
                    }
                    .graphicsLayer { rotationZ = rotation }
            )

            AnimatedVisibility(
                visible = uiVisible && !metadataVisible,
                enter = fadeIn(animationSpec = spring(stiffness = Spring.StiffnessLow)),
                exit = fadeOut(animationSpec = spring(stiffness = Spring.StiffnessLow)),
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(horizontal = 4.dp)
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color.Black.copy(alpha = 0.5f))
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = title.ifBlank { reference.substringAfterLast('/') },
                            color = Color.White,
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                            maxLines = 1,
                            overflow = if (marqueeEnabled) TextOverflow.Clip else TextOverflow.Ellipsis,
                            modifier = if (marqueeEnabled) Modifier.basicMarquee() else Modifier
                        )
                        val topDetails = listOfNotNull(
                            formatImageResolution(metadata?.width ?: 0, metadata?.height ?: 0),
                            sizeBytes.takeIf { it > 0L }?.let(::formatImageFileSize)
                        )
                        if (topDetails.isNotEmpty()) {
                            Text(
                                text = topDetails.joinToString(" • "),
                                color = Color.White.copy(alpha = 0.7f),
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = if (marqueeEnabled) TextOverflow.Clip else TextOverflow.Ellipsis,
                                modifier = if (marqueeEnabled) Modifier.basicMarquee() else Modifier
                            )
                        }
                    }
                }
            }

            AnimatedVisibility(
                visible = uiVisible && !metadataVisible,
                enter = fadeIn(animationSpec = spring(stiffness = Spring.StiffnessLow)),
                exit = fadeOut(animationSpec = spring(stiffness = Spring.StiffnessLow)),
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.5f))
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SplitButtonGroup(
                        actions = listOf(
                            ToolbarAction(
                                icon = Icons.AutoMirrored.Filled.RotateRight,
                                contentDescription = stringResource(R.string.action_rotate),
                                tint = Color.White,
                                onClick = { rotation = (rotation + 90f) % 360f }
                            )
                        ),
                        containerColor = Color.Black.copy(alpha = 0.5f),
                        contentColor = Color.White,
                        height = 56.dp,
                        minWidth = 64.dp,
                        iconSize = 28.dp
                    )
                    Spacer(Modifier.weight(1f))
                    Box {
                        var menuVisible by remember { mutableStateOf(false) }
                        Surface(
                            onClick = { menuVisible = true },
                            shape = CircleShape,
                            color = Color.Black.copy(alpha = 0.5f),
                            modifier = Modifier.size(56.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.action_more_options), tint = Color.White)
                            }
                        }
                        DropdownMenu(expanded = menuVisible, onDismissRequest = { menuVisible = false }) {
                            ArcileDropdownMenuItem(
                                text = { Text(stringResource(R.string.action_info)) },
                                leadingIcon = { Icon(Icons.Default.Info, contentDescription = null) },
                                onClick = {
                                    menuVisible = false
                                    metadataVisible = true
                                }
                            )
                            ArcileDropdownMenuItem(
                                text = { Text(stringResource(R.string.image_gallery_open_with)) },
                                leadingIcon = { Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null) },
                                onClick = {
                                    menuVisible = false
                                    onOpenWith()
                                }
                            )
                            ArcileDropdownMenuItem(
                                text = { Text(stringResource(R.string.share)) },
                                leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) },
                                onClick = {
                                    menuVisible = false
                                    onShare()
                                }
                            )
                        }
                    }
                }
            }

            if (metadataVisible) {
                StandaloneImageMetadata(
                    title = title,
                    reference = reference,
                    sizeBytes = sizeBytes,
                    mimeType = mimeType,
                    metadata = metadata,
                    onDismiss = { metadataVisible = false }
                )
            }
        }
    }
}

@Composable
private fun StandaloneImageMetadata(
    title: String,
    reference: String,
    sizeBytes: Long,
    mimeType: String?,
    metadata: ImageFileMetadata?,
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
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.image_gallery_metadata_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 24.dp),
                contentPadding = PaddingValues(bottom = 32.dp)
            ) {
                item {
                    val labels = ImageMetadataDetailLabels(
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
                        fileRows = buildImageMetadataDetailRows(
                            title = title,
                            reference = reference,
                            size = sizeBytes,
                            lastModifiedText = null,
                            mimeType = mimeType,
                            extension = title.substringAfterLast('.', ""),
                            metadata = metadata,
                            labels = labels,
                            isUriReference = reference.startsWith("content://")
                        ),
                        metadata = metadata,
                        sectionTitle = stringResource(R.string.image_gallery_metadata_file_information),
                        cameraTitle = stringResource(R.string.image_gallery_metadata_camera_exif),
                        locationTitle = stringResource(R.string.image_gallery_metadata_location)
                    )
                }
                item { Spacer(modifier = Modifier.height(32.dp)) }
            }
        }
    }
}
