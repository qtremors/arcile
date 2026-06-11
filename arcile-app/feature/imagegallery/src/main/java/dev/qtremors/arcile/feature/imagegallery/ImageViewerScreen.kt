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
fun ImageViewerScreen(
    initialPath: String,
    viewModel: ImageGalleryViewModel,
    onNavigateBack: () -> Unit,
    onShareFile: (String) -> Unit,
    onOpenWith: (String) -> Unit
) {
    val state by viewModel.state.collectAsState()
    val displayedFiles = remember(initialPath, state.displayedFiles, state.files) {
        viewerFilesForInitialPath(initialPath, state.displayedFiles, state.files)
    }
    val haptics = rememberArcileHaptics()
    val coroutineScope = rememberCoroutineScope()

    // Auto navigate back if dataset becomes empty (e.g. after deleting all files)
    LaunchedEffect(displayedFiles.size, state.isLoading) {
        if (displayedFiles.isEmpty() && !state.isLoading) {
            onNavigateBack()
        }
    }

    if (displayedFiles.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            if (state.isLoading) {
                CircularProgressIndicator(color = Color.White)
            } else {
                Text(
                    text = stringResource(R.string.no_results_found),
                    color = Color.White,
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }
        return
    }

    val pagerState = rememberPagerState(
        initialPage = 0,
        pageCount = { displayedFiles.size }
    )

    LaunchedEffect(displayedFiles.size) {
        if (displayedFiles.isNotEmpty() && pagerState.currentPage > displayedFiles.lastIndex) {
            pagerState.scrollToPage(displayedFiles.lastIndex)
        }
    }

    // Store custom visual rotations (multiples of 90 degrees) per image path
    val rotationStates = remember { mutableStateMapOf<String, Float>() }

    var isUiVisible by remember { mutableStateOf(true) }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color.Black
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            var currentScale by remember { androidx.compose.runtime.mutableFloatStateOf(1f) }
            LaunchedEffect(pagerState.currentPage) {
                currentScale = 1f
            }

            var showMetadataSheet by remember { mutableStateOf(false) }
            val currentFileForSheet = displayedFiles.getOrNull(pagerState.currentPage)

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                beyondViewportPageCount = 1,
                userScrollEnabled = currentScale <= 1.05f
            ) { page ->
                val file = displayedFiles.getOrNull(page)
                if (file != null) {
                    val rotation = rotationStates[file.absolutePath] ?: 0f
                    ZoomableImageViewer(
                        file = file,
                        rotation = rotation,
                        onDismiss = onNavigateBack,
                        onTap = { isUiVisible = !isUiVisible },
                        onScaleChanged = { scaleVal ->
                            if (pagerState.currentPage == page) {
                                currentScale = scaleVal
                            }
                        },
                        onSwipeUp = {
                            showMetadataSheet = true
                        }
                    )
                }
            }

            if (showMetadataSheet && currentFileForSheet != null) {
                var metadata by remember(currentFileForSheet.absolutePath, state.isRefreshing) {
                    mutableStateOf<GalleryFileMetadata?>(null)
                }
                LaunchedEffect(currentFileForSheet.absolutePath, state.isRefreshing) {
                    if (!state.isRefreshing) {
                        val data = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                            ExifMetadataReader.readMetadata(currentFileForSheet.absolutePath, currentFileForSheet.mimeType)
                        }
                        metadata = data
                    }
                }

                var showEraseDialog by remember { mutableStateOf(false) }

                MetadataSheet(
                    file = currentFileForSheet,
                    metadata = metadata,
                    onEraseMetadata = { showEraseDialog = true },
                    onDismiss = { showMetadataSheet = false }
                )

                if (showEraseDialog) {
                    androidx.compose.material3.AlertDialog(
                        onDismissRequest = { showEraseDialog = false },
                        title = { Text(stringResource(R.string.image_gallery_metadata_erase_dialog_title)) },
                        text = { Text(stringResource(R.string.image_gallery_metadata_erase_dialog_message)) },
                        confirmButton = {
                            androidx.compose.material3.TextButton(
                                onClick = {
                                    showEraseDialog = false
                                    viewModel.eraseMetadata(currentFileForSheet.absolutePath)
                                }
                            ) {
                                Text(stringResource(R.string.settings_clear_thumbnail_cache), color = MaterialTheme.colorScheme.error)
                            }
                        },
                        dismissButton = {
                            androidx.compose.material3.TextButton(onClick = { showEraseDialog = false }) {
                                Text(stringResource(R.string.cancel))
                            }
                        }
                    )
                }
            }

            // Top overlay bar
            AnimatedVisibility(
                visible = isUiVisible,
                enter = fadeIn(animationSpec = spring(stiffness = Spring.StiffnessLow)),
                exit = fadeOut(animationSpec = spring(stiffness = Spring.StiffnessLow)),
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
            ) {
                val currentFile = displayedFiles.getOrNull(pagerState.currentPage)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.5f))
                        .statusBarsPadding()
                        .padding(horizontal = 8.dp, vertical = 12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onNavigateBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.back),
                                tint = Color.White
                            )
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = currentFile?.name ?: "",
                                color = Color.White,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        // Rotation editing action (Clockwise 90 degrees)
                        IconButton(
                            onClick = {
                                if (currentFile != null) {
                                    haptics.selectionChanged()
                                    val currentRot = rotationStates[currentFile.absolutePath] ?: 0f
                                    rotationStates[currentFile.absolutePath] = (currentRot + 90f) % 360f
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.RotateRight,
                                contentDescription = stringResource(R.string.image_gallery_view_mode_aspect),
                                tint = Color.White
                            )
                        }

                        val isFavorite = currentFile != null && currentFile.absolutePath in state.favoriteFiles
                        IconButton(
                            onClick = {
                                if (currentFile != null) {
                                    haptics.selectionChanged()
                                    viewModel.toggleFavorite(currentFile.absolutePath)
                                }
                            }
                        ) {
                            Icon(
                                imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                contentDescription = stringResource(R.string.image_gallery_favorite),
                                tint = if (isFavorite) Color.Red else Color.White
                            )
                        }

                        IconButton(
                            onClick = {
                                if (currentFile != null) {
                                    onOpenWith(currentFile.openableReference())
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                contentDescription = stringResource(R.string.image_gallery_open_with),
                                tint = Color.White
                            )
                        }

                        IconButton(
                            onClick = {
                                if (currentFile != null) {
                                    onShareFile(currentFile.openableReference())
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = stringResource(R.string.share),
                                tint = Color.White
                            )
                        }

                        IconButton(
                            onClick = {
                                if (currentFile != null) {
                                    haptics.selectionStart()
                                    viewModel.clearSelection()
                                    viewModel.toggleSelection(currentFile.absolutePath)
                                    viewModel.requestDeleteSelected()
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = stringResource(R.string.action_delete_selected),
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }

            // Bottom overlay stats bar
            AnimatedVisibility(
                visible = isUiVisible,
                enter = fadeIn(animationSpec = spring(stiffness = Spring.StiffnessLow)),
                exit = fadeOut(animationSpec = spring(stiffness = Spring.StiffnessLow)),
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
            ) {
                val currentFile = displayedFiles.getOrNull(pagerState.currentPage)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.5f))
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (currentFile != null) {
                            Text(
                                text = formatFileSize(currentFile.size),
                                color = Color.White.copy(alpha = 0.8f),
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = currentFile.absolutePath,
                                color = Color.White.copy(alpha = 0.6f),
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }

    // Reuse the exact same confirmation dialog structure as the gallery view
    if (state.showTrashConfirmation || state.showPermanentDeleteConfirmation || state.showMixedDeleteExplanation) {
        DeleteConfirmationDialog(
            selectedCount = 1,
            isPermanentDeleteChecked = state.isPermanentDeleteChecked,
            isPermanentDeleteToggleEnabled = state.isPermanentDeleteToggleEnabled,
            onConfirm = viewModel::confirmDeleteSelected,
            onDismiss = viewModel::dismissDeleteConfirmation,
            onTogglePermanentDelete = viewModel::togglePermanentDelete,
            decision = state.deleteDecision,
            isShredChecked = state.isShredChecked,
            onToggleShred = viewModel::toggleShred
        )
    }
}

@Composable
fun ZoomableImageViewer(
    file: FileModel,
    rotation: Float,
    onDismiss: () -> Unit,
    onTap: () -> Unit,
    onScaleChanged: (Float) -> Unit,
    onSwipeUp: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current

    val screenHeightPx = remember(configuration, density) {
        with(density) { configuration.screenHeightDp.dp.toPx() }
    }

    // Animation states for scale & offsets
    val scale = remember { Animatable(1f) }
    val offsetX = remember { Animatable(0f) }
    val offsetY = remember { Animatable(0f) }

    // Visual rotation degree transition
    val animatedRotation by animateFloatAsState(
        targetValue = rotation,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "rotation"
    )

    // Reset offsets and scales when the file changes
    LaunchedEffect(file) {
        scale.snapTo(1f)
        offsetX.snapTo(0f)
        offsetY.snapTo(0f)
    }

    val requestData = remember(context, file) { imageRequestDataFor(context, file) }
    val request = remember(context, requestData) {
        ImageRequest.Builder(context)
            .data(requestData)
            .build()
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(file) {
                val touchSlop = viewConfiguration.touchSlop
                val doubleTapTimeout = 300L
                var lastTapTime = 0L
                var lastTapPosition = Offset.Zero
                
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val downTime = System.currentTimeMillis()
                    val downPos = down.position
                    
                    var isMultiTouch = false
                    var dragStarted = false
                    var dragDirection: DragDirection? = null
                    
                    while (true) {
                        val event = awaitPointerEvent()
                        val pointers = event.changes
                        if (pointers.isEmpty() || pointers.all { !it.pressed }) {
                            break
                        }
                        
                        if (pointers.size >= 2) {
                            isMultiTouch = true
                            pointers.forEach { it.consume() }
                            
                            val zoomChange = event.calculateZoom()
                            val panChange = event.calculatePan()
                            
                            coroutineScope.launch {
                                val rawScale = scale.value * zoomChange
                                val constrainedScale = if (rawScale < 0.8f) {
                                    0.8f
                                } else if (rawScale > 5f) {
                                    5f
                                } else {
                                    rawScale
                                }
                                scale.snapTo(constrainedScale)
                                onScaleChanged(constrainedScale)
                                
                                val newX = offsetX.value + panChange.x
                                val newY = offsetY.value + panChange.y
                                offsetX.snapTo(newX)
                                offsetY.snapTo(newY)
                            }
                        } else if (pointers.size == 1 && !isMultiTouch) {
                            val change = pointers[0]
                            if (change.pressed) {
                                val currentPos = change.position
                                val delta = currentPos - change.previousPosition
                                val totalDelta = currentPos - downPos
                                
                                if (!dragStarted) {
                                    if (totalDelta.getDistance() > touchSlop) {
                                        dragStarted = true
                                        dragDirection = if (abs(totalDelta.y) > abs(totalDelta.x)) {
                                            DragDirection.VERTICAL
                                        } else {
                                            DragDirection.HORIZONTAL
                                        }
                                    }
                                }
                                
                                if (dragStarted) {
                                    if (scale.value > 1.05f) {
                                        change.consume()
                                        coroutineScope.launch {
                                            val maxX = (scale.value - 1f) * size.width / 2f
                                            val maxY = (scale.value - 1f) * size.height / 2f
                                            
                                            val targetX = offsetX.value + delta.x
                                            val targetY = offsetY.value + delta.y
                                            
                                            val newX = if (targetX < -maxX) {
                                                -maxX - (-maxX - targetX) * 0.4f
                                            } else if (targetX > maxX) {
                                                maxX + (targetX - maxX) * 0.4f
                                            } else {
                                                targetX
                                            }
                                            
                                            val newY = if (targetY < -maxY) {
                                                -maxY - (-maxY - targetY) * 0.4f
                                            } else if (targetY > maxY) {
                                                maxY + (targetY - maxY) * 0.4f
                                            } else {
                                                targetY
                                            }
                                            
                                            offsetX.snapTo(newX)
                                            offsetY.snapTo(newY)
                                        }
                                    } else {
                                        if (dragDirection == DragDirection.VERTICAL) {
                                            change.consume()
                                            coroutineScope.launch {
                                                offsetY.snapTo(offsetY.value + delta.y)
                                                offsetX.snapTo(offsetX.value + delta.x * 0.3f)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    
                    val releaseTime = System.currentTimeMillis()
                    val dragY = offsetY.value
                    
                    if (isMultiTouch || scale.value > 1.05f) {
                        coroutineScope.launch {
                            val targetScale = scale.value.coerceIn(1f, 4f)
                            if (scale.value != targetScale) {
                                launch { scale.animateTo(targetScale, spring(stiffness = Spring.StiffnessMedium)) }
                                onScaleChanged(targetScale)
                            }
                            
                            val maxX = (targetScale - 1f) * size.width / 2f
                            val maxY = (targetScale - 1f) * size.height / 2f
                            val targetX = offsetX.value.coerceIn(-maxX, maxX)
                            val targetY = offsetY.value.coerceIn(-maxY, maxY)
                            
                            if (offsetX.value != targetX) {
                                launch { offsetX.animateTo(targetX, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium)) }
                            }
                            if (offsetY.value != targetY) {
                                launch { offsetY.animateTo(targetY, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium)) }
                            }
                        }
                    } else if (dragStarted && dragDirection == DragDirection.VERTICAL) {
                        if (dragY > screenHeightPx * 0.15f) {
                            coroutineScope.launch {
                                offsetY.animateTo(screenHeightPx, spring(stiffness = Spring.StiffnessMedium))
                                onDismiss()
                            }
                        } else if (dragY < -screenHeightPx * 0.08f) {
                            coroutineScope.launch {
                                launch { offsetY.animateTo(0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium)) }
                                launch { offsetX.animateTo(0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium)) }
                            }
                            onSwipeUp()
                        } else {
                            coroutineScope.launch {
                                launch { offsetY.animateTo(0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium)) }
                                launch { offsetX.animateTo(0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium)) }
                            }
                        }
                    } else if (!dragStarted && (releaseTime - downTime) < 300L) {
                        val timeDiff = releaseTime - lastTapTime
                        val distDiff = (downPos - lastTapPosition).getDistance()
                        if (timeDiff < doubleTapTimeout && distDiff < touchSlop * 2) {
                            coroutineScope.launch {
                                if (scale.value > 1.05f) {
                                    launch { scale.animateTo(1f, spring(stiffness = Spring.StiffnessMedium)) }
                                    launch { offsetX.animateTo(0f, spring(stiffness = Spring.StiffnessMedium)) }
                                    launch { offsetY.animateTo(0f, spring(stiffness = Spring.StiffnessMedium)) }
                                    onScaleChanged(1f)
                                } else {
                                    val targetScale = 2.5f
                                    launch { scale.animateTo(targetScale, spring(stiffness = Spring.StiffnessMedium)) }
                                    onScaleChanged(targetScale)
                                    
                                    val maxX = (targetScale - 1f) * size.width / 2f
                                    val maxY = (targetScale - 1f) * size.height / 2f
                                    val targetX = ((size.width / 2f - downPos.x) * (targetScale - 1f)).coerceIn(-maxX, maxX)
                                    val targetY = ((size.height / 2f - downPos.y) * (targetScale - 1f)).coerceIn(-maxY, maxY)
                                    launch { offsetX.animateTo(targetX, spring(stiffness = Spring.StiffnessMedium)) }
                                    launch { offsetY.animateTo(targetY, spring(stiffness = Spring.StiffnessMedium)) }
                                }
                            }
                            lastTapTime = 0L
                            lastTapPosition = Offset.Zero
                        } else {
                            onTap()
                            lastTapTime = releaseTime
                            lastTapPosition = downPos
                        }
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        val dragFraction = (abs(offsetY.value) / screenHeightPx).coerceIn(0f, 1f)
        val backdropAlpha = (1f - dragFraction * 0.8f).coerceIn(0.1f, 1f)
        val viewScale = (scale.value * (1f - dragFraction * 0.15f)).coerceIn(0.5f, 4f)

        // Backdrop fade overlay on vertical drag
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = backdropAlpha))
        )

        AsyncImage(
            model = request,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = viewScale
                    scaleY = viewScale
                    translationX = offsetX.value
                    translationY = offsetY.value
                    rotationZ = animatedRotation
                }
        )
    }
}

// Reusable utility method for format size
private fun formatFileSize(size: Long): String {
    if (size <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (Math.log10(size.toDouble()) / Math.log10(1024.0)).toInt()
    return String.format("%.2f %s", size / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
}

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

private enum class DragDirection {
    VERTICAL, HORIZONTAL
}

private fun viewerFilesForInitialPath(
    initialPath: String,
    displayedFiles: List<FileModel>,
    allFiles: List<FileModel>
): List<FileModel> {
    val clickedFile = displayedFiles.firstOrNull { it.absolutePath == initialPath }
        ?: allFiles.firstOrNull { it.absolutePath == initialPath }
        ?: fileModelFromPath(initialPath)
    return listOf(clickedFile) + displayedFiles.filterNot { it.absolutePath == initialPath }
}

private fun fileModelFromPath(path: String): FileModel {
    val file = java.io.File(path)
    return FileModel(
        name = file.name.ifBlank { path.substringAfterLast('/').ifBlank { path } },
        absolutePath = path,
        size = file.takeIf { it.exists() }?.length() ?: 0L,
        lastModified = file.takeIf { it.exists() }?.lastModified() ?: 0L,
        isDirectory = false,
        extension = path.substringAfterLast('/', path).substringAfterLast('.', "").lowercase(),
        isHidden = file.name.startsWith("."),
        mimeType = android.webkit.MimeTypeMap.getSingleton()
            .getMimeTypeFromExtension(path.substringAfterLast('.', "").lowercase())
    )
}

private fun FileModel.openableReference(): String =
    nodeRef.contentUri?.takeIf { it.isNotBlank() } ?: absolutePath
