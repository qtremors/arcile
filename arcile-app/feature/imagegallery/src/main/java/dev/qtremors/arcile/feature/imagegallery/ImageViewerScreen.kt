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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
    onShareFile: (String) -> Unit
) {
    val state by viewModel.state.collectAsState()
    val displayedFiles = state.displayedFiles
    val haptics = rememberArcileHaptics()
    val coroutineScope = rememberCoroutineScope()

    // Auto navigate back if dataset becomes empty (e.g. after deleting all files)
    LaunchedEffect(displayedFiles.size) {
        if (displayedFiles.isEmpty()) {
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
            Text(
                text = stringResource(R.string.no_results_found),
                color = Color.White,
                style = MaterialTheme.typography.bodyLarge
            )
        }
        return
    }

    val initialIndex = remember(displayedFiles) {
        val idx = displayedFiles.indexOfFirst { it.absolutePath == initialPath }
        if (idx != -1) idx else 0
    }

    val pagerState = rememberPagerState(
        initialPage = initialIndex,
        pageCount = { displayedFiles.size }
    )

    // Store custom visual rotations (multiples of 90 degrees) per image path
    val rotationStates = remember { mutableStateMapOf<String, Float>() }

    var isUiVisible by remember { mutableStateOf(true) }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color.Black
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                beyondViewportPageCount = 1,
                userScrollEnabled = true
            ) { page ->
                val file = displayedFiles.getOrNull(page)
                if (file != null) {
                    val rotation = rotationStates[file.absolutePath] ?: 0f
                    ZoomableImageViewer(
                        file = file,
                        rotation = rotation,
                        onDismiss = onNavigateBack,
                        onTap = { isUiVisible = !isUiVisible }
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

                        IconButton(
                            onClick = {
                                if (currentFile != null) {
                                    onShareFile(currentFile.absolutePath)
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

    val request = remember(context, file.absolutePath) {
        ImageRequest.Builder(context)
            .data(file.absolutePath)
            .build()
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(file) {
                // Handle pinch-to-zoom and pan interactions
                detectTransformGestures { _, pan, zoom, _ ->
                    coroutineScope.launch {
                        val newScale = (scale.value * zoom).coerceIn(1f, 4f)
                        scale.snapTo(newScale)

                        if (newScale > 1f) {
                            // Pan relative to active zoom bounds
                            val maxX = (newScale - 1f) * size.width / 2f
                            val maxY = (newScale - 1f) * size.height / 2f
                            offsetX.snapTo((offsetX.value + pan.x).coerceIn(-maxX, maxX))
                            offsetY.snapTo((offsetY.value + pan.y).coerceIn(-maxY, maxY))
                        }
                    }
                }
            }
            .pointerInput(file) {
                // Double-tap zoom and Single-tap HUD toggle and Vertical Swipe-to-Dismiss mechanics
                detectTapGestures(
                    onDoubleTap = { tapOffset ->
                        coroutineScope.launch {
                            if (scale.value > 1f) {
                                // Zoom out to normal
                                launch { scale.animateTo(1f, spring(stiffness = Spring.StiffnessMedium)) }
                                launch { offsetX.animateTo(0f, spring(stiffness = Spring.StiffnessMedium)) }
                                launch { offsetY.animateTo(0f, spring(stiffness = Spring.StiffnessMedium)) }
                            } else {
                                // Zoom in to 2.5x
                                launch { scale.animateTo(2.5f, spring(stiffness = Spring.StiffnessMedium)) }
                                // Center zoom target to double tap location
                                val targetX = (size.width / 2f - tapOffset.x) * 1.5f
                                val targetY = (size.height / 2f - tapOffset.y) * 1.5f
                                launch { offsetX.animateTo(targetX, spring(stiffness = Spring.StiffnessMedium)) }
                                launch { offsetY.animateTo(targetY, spring(stiffness = Spring.StiffnessMedium)) }
                            }
                        }
                    },
                    onTap = { onTap() }
                )
            }
            .pointerInput(file) {
                // Elastic vertical swipe-to-dismiss gesture
                detectTransformGestures { _, pan, _, _ ->
                    // Only dismiss if scale is roughly 1
                    if (scale.value <= 1.05f) {
                        coroutineScope.launch {
                            val newY = offsetY.value + pan.y
                            offsetY.snapTo(newY)
                            // Add slight visual drift on X-axis during swipe
                            offsetX.snapTo(offsetX.value + pan.x * 0.3f)
                        }
                    }
                }
            }
            .pointerInput(file) {
                // Reset gesture tracking upon release
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        val allReleased = event.changes.all { !it.pressed }
                        if (allReleased && scale.value <= 1.05f) {
                            val dragY = offsetY.value
                            if (abs(dragY) > screenHeightPx * 0.15f) {
                                // Exit swipe gesture threshold crossed: Dismiss screen
                                coroutineScope.launch {
                                    val targetY = if (dragY > 0) screenHeightPx else -screenHeightPx
                                    offsetY.animateTo(targetY, spring(stiffness = Spring.StiffnessMedium))
                                    onDismiss()
                                }
                            } else {
                                // Snap back to center
                                coroutineScope.launch {
                                    launch { offsetY.animateTo(0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium)) }
                                    launch { offsetX.animateTo(0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium)) }
                                }
                            }
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
