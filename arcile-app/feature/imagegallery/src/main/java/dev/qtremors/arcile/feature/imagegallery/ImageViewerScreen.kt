package dev.qtremors.arcile.feature.imagegallery

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import dev.qtremors.arcile.shared.ui.SplitButtonGroup
import dev.qtremors.arcile.shared.ui.ToolbarAction
import dev.qtremors.arcile.ui.theme.menuGroupFirst
import dev.qtremors.arcile.ui.theme.menuGroupLast
import dev.qtremors.arcile.ui.theme.menuGroupMiddle
import dev.qtremors.arcile.ui.theme.menuGroupSingle
import kotlinx.coroutines.launch
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)

@Composable
fun ImageViewerScreen(
    initialPath: String,
    viewModel: ImageGalleryViewModel,
    contextPaths: List<String> = emptyList(),
    onNavigateBack: () -> Unit,
    onShareFile: (String) -> Unit,
    onOpenWith: (String) -> Unit
) {
    val state by viewModel.state.collectAsState()
    val externalFiles = remember(contextPaths) {
        contextPaths.distinct().map(::fileModelFromPath)
    }
    val viewerContext = remember(initialPath, externalFiles, state.displayedFiles, state.files) {
        if (externalFiles.any { it.absolutePath == initialPath }) {
            viewerFileContextForInitialPath(initialPath, externalFiles, externalFiles)
        } else {
            viewerFileContextForInitialPath(initialPath, state.displayedFiles, state.files)
        }
    }
    val displayedFiles = viewerContext.files
    val haptics = rememberArcileHaptics()
    val coroutineScope = rememberCoroutineScope()
    val isDeleteDialogVisible = state.showTrashConfirmation || state.showPermanentDeleteConfirmation || state.showMixedDeleteExplanation

    BackHandler {
        if (isDeleteDialogVisible) {
            viewModel.dismissDeleteConfirmation()
        } else {
            onNavigateBack()
        }
    }

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
        initialPage = viewerContext.initialPage,
        pageCount = { displayedFiles.size }
    )

    LaunchedEffect(displayedFiles.size, viewerContext.initialPage) {
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
            val currentFile = currentFileForSheet

            val dateFormatter = remember { java.text.SimpleDateFormat("MMMM d", java.util.Locale.getDefault()) }
            val timeFormatter = remember { java.text.SimpleDateFormat("h:mm a", java.util.Locale.getDefault()) }
            val dateText = remember(currentFile) {
                if (currentFile != null) dateFormatter.format(java.util.Date(currentFile.lastModified)) else ""
            }
            val timeText = remember(currentFile) {
                if (currentFile != null) timeFormatter.format(java.util.Date(currentFile.lastModified)) else ""
            }

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
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    // Left: Back button in circular translucent background
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .size(56.dp)
                            .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    // Center: Date & Time pill
                    if (dateText.isNotEmpty()) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .align(Alignment.Center)
                                .clip(RoundedCornerShape(20.dp))
                                .background(Color.Black.copy(alpha = 0.5f))
                                .padding(horizontal = 16.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = dateText,
                                color = Color.White,
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = timeText,
                                color = Color.White.copy(alpha = 0.7f),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }

                    // Right: Delete button in circular translucent background
                    IconButton(
                        onClick = {
                            if (currentFile != null) {
                                haptics.selectionStart()
                                viewModel.clearSelection()
                                viewModel.toggleSelection(currentFile.absolutePath)
                                viewModel.requestDeleteSelected()
                            }
                        },
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .size(56.dp)
                            .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = stringResource(R.string.action_delete_selected),
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            }

            // Bottom overlay bar containing thumbnail strip and action bar
            AnimatedVisibility(
                visible = isUiVisible,
                enter = fadeIn(animationSpec = spring(stiffness = Spring.StiffnessLow)),
                exit = fadeOut(animationSpec = spring(stiffness = Spring.StiffnessLow)),
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.5f))
                        .navigationBarsPadding()
                ) {
                    // 1. Thumbnail Strip
                    val lazyListState = rememberLazyListState()
                    LaunchedEffect(pagerState.currentPage) {
                        if (displayedFiles.isNotEmpty() && pagerState.currentPage in displayedFiles.indices) {
                            lazyListState.animateScrollToItem(pagerState.currentPage)
                        }
                    }

                    LazyRow(
                        state = lazyListState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
                        contentPadding = PaddingValues(horizontal = 16.dp)
                    ) {
                        items(displayedFiles.size) { index ->
                            val file = displayedFiles[index]
                            val isSelected = pagerState.currentPage == index
                            Box(
                                modifier = Modifier
                                    .width(28.dp)
                                    .height(42.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .border(
                                        width = if (isSelected) 2.dp else 0.dp,
                                        color = if (isSelected) Color.White else Color.Transparent,
                                        shape = RoundedCornerShape(4.dp)
                                    )
                                    .clickable {
                                        coroutineScope.launch {
                                            pagerState.animateScrollToPage(index)
                                        }
                                    }
                            ) {
                                AsyncImage(
                                    model = ImageRequest.Builder(LocalContext.current)
                                        .data(file.absolutePath)
                                        .crossfade(true)
                                        .build(),
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                    }

                    // 2. Action Bar
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        // Center actions (Favorite, Info, Rotate) in SplitButtonGroup
                        val isFavorite = currentFile != null && currentFile.absolutePath in state.favoriteFiles
                        val actions = remember(currentFile, isFavorite) {
                            listOf(
                                ToolbarAction(
                                    icon = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                    contentDescription = "Favorite",
                                    tint = if (isFavorite) Color.Red else Color.White,
                                    onClick = {
                                        if (currentFile != null) {
                                            haptics.selectionChanged()
                                            viewModel.toggleFavorite(currentFile.absolutePath)
                                        }
                                    }
                                ),
                                ToolbarAction(
                                    icon = Icons.Default.Info,
                                    contentDescription = "Info",
                                    tint = Color.White,
                                    onClick = {
                                        showMetadataSheet = true
                                    }
                                ),
                                ToolbarAction(
                                    icon = Icons.AutoMirrored.Filled.RotateRight,
                                    contentDescription = "Rotate",
                                    tint = Color.White,
                                    onClick = {
                                        if (currentFile != null) {
                                            haptics.selectionChanged()
                                            val currentRot = rotationStates[currentFile.absolutePath] ?: 0f
                                            rotationStates[currentFile.absolutePath] = (currentRot + 90f) % 360f
                                        }
                                    }
                                )
                            )
                        }

                        Box(
                            modifier = Modifier.align(Alignment.Center)
                        ) {
                            SplitButtonGroup(
                                actions = actions,
                                containerColor = Color.Black.copy(alpha = 0.5f),
                                contentColor = Color.White,
                                height = 56.dp,
                                minWidth = 64.dp,
                                iconSize = 28.dp
                            )
                        }

                        // Right actions (Overflow Menu only)
                        Box(
                            modifier = Modifier.align(Alignment.CenterEnd)
                        ) {
                            var showOverflowMenu by remember { mutableStateOf(false) }
                            Surface(
                                onClick = { showOverflowMenu = true },
                                shape = CircleShape,
                                color = Color.Black.copy(alpha = 0.5f),
                                modifier = Modifier.size(56.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.MoreVert,
                                        contentDescription = stringResource(R.string.action_more_options),
                                        tint = Color.White,
                                        modifier = Modifier.size(28.dp)
                                    )
                                }
                            }

                            DropdownMenu(
                                expanded = showOverflowMenu,
                                onDismissRequest = { showOverflowMenu = false },
                                shape = MaterialTheme.shapes.extraLarge,
                                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                                modifier = Modifier.width(200.dp)
                            ) {
                                val menuActions = remember(currentFile) {
                                    mutableListOf<@Composable () -> Unit>().apply {
                                        add {
                                            DropdownMenuItem(
                                                text = {
                                                    Text(
                                                        text = stringResource(R.string.image_gallery_open_with),
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                },
                                                leadingIcon = { Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null) },
                                                onClick = {
                                                    showOverflowMenu = false
                                                    if (currentFile != null) {
                                                        onOpenWith(currentFile.openableReference())
                                                    }
                                                },
                                                modifier = Modifier.fillMaxWidth(),
                                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                                            )
                                        }
                                        add {
                                            DropdownMenuItem(
                                                text = {
                                                    Text(
                                                        text = stringResource(R.string.share),
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                },
                                                leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) },
                                                onClick = {
                                                    showOverflowMenu = false
                                                    if (currentFile != null) {
                                                        onShareFile(currentFile.openableReference())
                                                    }
                                                },
                                                modifier = Modifier.fillMaxWidth(),
                                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                                            )
                                        }
                                    }
                                }

                                menuActions.forEachIndexed { index, action ->
                                    val shape = when {
                                        menuActions.size == 1 -> MaterialTheme.shapes.menuGroupSingle
                                        index == 0 -> MaterialTheme.shapes.menuGroupFirst
                                        index == menuActions.size - 1 -> MaterialTheme.shapes.menuGroupLast
                                        else -> MaterialTheme.shapes.menuGroupMiddle
                                     }
                                     Box(
                                         modifier = Modifier
                                             .fillMaxWidth()
                                             .padding(horizontal = 8.dp, vertical = 2.dp)
                                             .clip(shape)
                                             .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                                     ) {
                                         action()
                                     }
                                 }
                             }
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
