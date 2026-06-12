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

