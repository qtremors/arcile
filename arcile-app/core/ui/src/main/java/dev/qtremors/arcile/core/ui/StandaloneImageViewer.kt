package dev.qtremors.arcile.core.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.PredictiveBackHandler
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
import dev.qtremors.arcile.core.ui.theme.bounceClickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
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
import dev.qtremors.arcile.core.ui.metadata.VisualMediaMetadata
import dev.qtremors.arcile.core.ui.metadata.MediaMetadataDetailLabels
import dev.qtremors.arcile.core.ui.metadata.MediaMetadataSections
import dev.qtremors.arcile.core.ui.metadata.SharedImageMetadataReader
import dev.qtremors.arcile.core.presentation.formatFileSize
import dev.qtremors.arcile.core.ui.metadata.buildMediaMetadataDetailRows
import dev.qtremors.arcile.core.ui.metadata.formatMediaResolution
import dev.qtremors.arcile.core.ui.theme.LocalMarqueeFilenames
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import dev.qtremors.arcile.core.storage.domain.FileModel
import dev.qtremors.arcile.core.storage.domain.StorageNodeCapabilities
import dev.qtremors.arcile.core.storage.domain.StorageNodeRef
import dev.qtremors.arcile.core.ui.viewer.ViewerActionHost
import dev.qtremors.arcile.core.ui.viewer.ViewerFileAction
import dev.qtremors.arcile.core.ui.viewer.ViewerOverflowMenu
import dev.qtremors.arcile.core.ui.viewer.ViewerSourceScope
import dev.qtremors.arcile.core.ui.viewer.rememberViewerActionController

@Composable
fun StandaloneImageViewer(
    reference: String,
    title: String,
    sizeBytes: Long,
    mimeType: String?,
    onNavigateBack: () -> Unit,
    onShare: () -> Unit,
    onOpenWith: () -> Unit,
    onFileRenamed: (String, FileModel) -> Unit = { _, _ -> },
    onFileDeleted: (String) -> Unit = {}
) {
    val context = LocalContext.current
    var uiVisible by remember { mutableStateOf(true) }
    var metadataVisible by remember { mutableStateOf(false) }
    var rotation by remember(reference) { mutableFloatStateOf(0f) }
    var metadata by remember(reference) { mutableStateOf<VisualMediaMetadata?>(null) }
    var renderFailed by remember(reference) { mutableStateOf(false) }
    val marqueeEnabled = LocalMarqueeFilenames.current
    val isExternalReference = remember(reference) {
        runCatching { android.net.Uri.parse(reference).scheme == "content" }.getOrDefault(false)
    }
    val currentFile = remember(reference, title, sizeBytes, mimeType, isExternalReference) {
        val capabilities = if (isExternalReference) {
            StorageNodeCapabilities(
                canRead = true,
                canWrite = false,
                canDelete = false,
                canTrash = false,
                canArchive = false,
                canRename = false,
                canCopy = false,
                canMove = false,
                canShare = true,
                canOpenWith = true
            )
        } else {
            StorageNodeCapabilities(canTrash = true)
        }
        FileModel(
            name = title,
            reference = reference,
            size = sizeBytes,
            extension = title.substringAfterLast('.', ""),
            mimeType = mimeType,
            nodeRef = StorageNodeRef.local(reference, capabilities = capabilities).copy(
                contentUri = reference.takeIf { isExternalReference }
            )
        )
    }
    val viewerActionController = rememberViewerActionController(
        currentFile = currentFile,
        sourceScope = if (isExternalReference) ViewerSourceScope.External else ViewerSourceScope.Normal,
        onFileRenamed = onFileRenamed,
        onFileDeleted = onFileDeleted
    )

    LaunchedEffect(reference) {
        metadata = withContext(Dispatchers.IO) {
            runCatching { SharedImageMetadataReader.readMetadata(context, reference, mimeType) }.getOrNull()
        }
    }

    var metadataBackProgress by remember { mutableFloatStateOf(0f) }
    var isMetadataBackPredicting by remember { mutableStateOf(false) }

    PredictiveBackHandler(enabled = metadataVisible) { progressFlow ->
        isMetadataBackPredicting = true
        try {
            progressFlow.collect { backEvent ->
                metadataBackProgress = backEvent.progress
            }
            metadataVisible = false
        } finally {
            isMetadataBackPredicting = false
            metadataBackProgress = 0f
        }
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
                onSuccess = { renderFailed = false },
                onError = { renderFailed = true },
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(reference) {
                        detectTapGestures(onTap = { uiVisible = !uiVisible })
                    }
                    .graphicsLayer { rotationZ = rotation }
            )

            if (renderFailed && !metadataVisible) {
                Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(24.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(Color.Black.copy(alpha = 0.7f))
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = stringResource(R.string.cannot_render_image),
                        color = Color.White,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = onOpenWith) {
                        Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.viewer_open_with))
                    }
                }
            }

            AnimatedVisibility(
                visible = uiVisible && !metadataVisible,
                enter = fadeIn(animationSpec = spring(stiffness = Spring.StiffnessLow)),
                exit = fadeOut(animationSpec = spring(stiffness = Spring.StiffnessLow)),
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.5f))
                            .size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                            tint = Color.White
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(
                        modifier = Modifier
                            .weight(1f)
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
                            formatMediaResolution(metadata?.width ?: 0, metadata?.height ?: 0),
                            sizeBytes.takeIf { it > 0L }?.let { formatFileSize(context, it) }
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
                        height = 48.dp,
                        minWidth = 48.dp,
                        iconSize = 24.dp
                    )
                    Spacer(Modifier.weight(1f))
                    ViewerOverflowMenu(
                        currentFile = currentFile,
                        allowedActions = viewerActionController.state.allowedActions,
                        onAction = { action ->
                            when (action) {
                                ViewerFileAction.Share -> onShare()
                                ViewerFileAction.OpenWith -> onOpenWith()
                                ViewerFileAction.Properties -> metadataVisible = true
                                else -> viewerActionController.onAction(action)
                            }
                        },
                        onShowMetadata = { metadataVisible = true }
                    )
                }
            }

            if (metadataVisible) {
                StandaloneImageMetadata(
                    title = title,
                    reference = reference,
                    sizeBytes = sizeBytes,
                    mimeType = mimeType,
                    metadata = metadata,
                    onDismiss = { metadataVisible = false },
                    modifier = Modifier.graphicsLayer {
                        if (isMetadataBackPredicting) {
                            translationY = metadataBackProgress * size.height
                        }
                    }
                )
            }
            ViewerActionHost(viewerActionController)
        }
    }
}

@Composable
private fun StandaloneImageMetadata(
    title: String,
    reference: String,
    sizeBytes: Long,
    mimeType: String?,
    metadata: VisualMediaMetadata?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxSize(),
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
                    text = stringResource(R.string.viewer_metadata_title),
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
                    val labels = MediaMetadataDetailLabels(
                        title = stringResource(R.string.viewer_metadata_label_title),
                        date = stringResource(R.string.viewer_metadata_label_date),
                        dateTaken = stringResource(R.string.viewer_metadata_label_date_taken),
                        resolution = stringResource(R.string.viewer_metadata_label_resolution),
                        size = stringResource(R.string.viewer_metadata_label_size),
                        uri = stringResource(R.string.viewer_metadata_label_uri),
                        path = stringResource(R.string.viewer_metadata_label_path),
                        mimeType = stringResource(R.string.viewer_metadata_label_mime_type),
                        extension = stringResource(R.string.viewer_metadata_label_extension)
                    )
                    MediaMetadataSections(
                        fileRows = buildMediaMetadataDetailRows(
                            title = title,
                            reference = reference,
                            size = sizeBytes,
                            lastModifiedText = null,
                            mimeType = mimeType,
                            extension = title.substringAfterLast('.', ""),
                            metadata = metadata,
                            labels = labels,
                            isUriReference = reference.startsWith("content://"),
                            context = androidx.compose.ui.platform.LocalContext.current
                        ),
                        metadata = metadata,
                        sectionTitle = stringResource(R.string.viewer_metadata_file_information),
                        cameraTitle = stringResource(R.string.image_gallery_metadata_camera_exif),
                        locationTitle = stringResource(R.string.viewer_metadata_location)
                    )
                }
                item { Spacer(modifier = Modifier.height(32.dp)) }
            }
        }
    }
}
