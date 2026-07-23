package dev.qtremors.arcile.feature.imagegallery

import androidx.activity.compose.BackHandler
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import dev.qtremors.arcile.core.storage.domain.FileModel
import dev.qtremors.arcile.core.ui.R
import dev.qtremors.arcile.core.ui.dialogs.DeleteConfirmationDialog
import dev.qtremors.arcile.core.ui.rememberArcileHaptics
import dev.qtremors.arcile.core.ui.theme.LocalMarqueeFilenames
import dev.qtremors.arcile.core.ui.theme.ExpressiveShapes
import kotlin.math.abs

internal enum class ViewerBackAction {
    DismissDelete,
    DismissMetadata,
    ExitViewer
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun ImageViewerScreen(
    initialPath: String,
    viewModel: ImageViewerViewModel,
    contextFiles: List<FileModel> = emptyList(),
    selectionModeEnabled: Boolean = false,
    readOnly: Boolean = false,
    onNavigateBack: () -> Unit,
    onShareFile: (FileModel) -> Unit,
    onOpenWith: (FileModel) -> Unit
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val marqueeEnabled = LocalMarqueeFilenames.current
    val externalFiles = remember(contextFiles) { contextFiles.distinctBy(FileModel::absolutePath) }
    val viewerContext = remember(initialPath, externalFiles, state.displayedFiles, state.files) {
        if (externalFiles.any { it.absolutePath == initialPath }) {
            viewerFileContextForInitialPath(initialPath, externalFiles, externalFiles)
        } else {
            viewerFileContextForInitialPath(initialPath, state.displayedFiles, state.files)
        }
    }
    val displayedFiles = viewerContext.files
    val haptics = rememberArcileHaptics()
    val isDeleteDialogVisible = state.showTrashConfirmation || state.showPermanentDeleteConfirmation || state.showMixedDeleteExplanation
    val showMetadataSheet = !readOnly && state.viewerMetadataPath != null

    var backProgress by remember { mutableStateOf(0f) }
    var isBackPredicting by remember { mutableStateOf(false) }
    var backActionAtStart by remember { mutableStateOf<ViewerBackAction?>(null) }

    PredictiveBackHandler(enabled = true) { progressFlow ->
        backActionAtStart = when {
            isDeleteDialogVisible -> ViewerBackAction.DismissDelete
            state.viewerMetadataPath != null -> ViewerBackAction.DismissMetadata
            else -> ViewerBackAction.ExitViewer
        }
        isBackPredicting = true
        try {
            progressFlow.collect { backEvent ->
                backProgress = backEvent.progress
            }
            when (backActionAtStart) {
                ViewerBackAction.DismissDelete -> viewModel.dismissDeleteConfirmation()
                ViewerBackAction.DismissMetadata -> viewModel.setViewerMetadataVisible(null, visible = false)
                ViewerBackAction.ExitViewer -> onNavigateBack()
                null -> Unit
            }
        } catch (e: Exception) {
            // Cancelled
        } finally {
            isBackPredicting = false
            backProgress = 0f
            backActionAtStart = null
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
                LoadingIndicator(color = Color.White)
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

    val restoredPage = remember(
        initialPath,
        state.viewerSessionInitialPath,
        state.viewerCurrentPath,
        viewerContext
    ) {
        viewerInitialPageForSession(
            initialPath = initialPath,
            viewerSessionInitialPath = state.viewerSessionInitialPath,
            viewerCurrentPath = state.viewerCurrentPath,
            viewerContext = viewerContext
        )
    }
    val pagerState = rememberPagerState(
        initialPage = restoredPage,
        pageCount = { displayedFiles.size }
    )

    LaunchedEffect(initialPath, viewerContext.initialPage, displayedFiles.size) {
        if (displayedFiles.isNotEmpty() && state.viewerSessionInitialPath != initialPath) {
            viewModel.startViewerSession(initialPath)
            pagerState.scrollToPage(viewerContext.initialPage.coerceIn(0, displayedFiles.lastIndex))
        }
    }

    val displayedPaths = remember(displayedFiles) {
        displayedFiles.map(FileModel::absolutePath)
    }
    LaunchedEffect(displayedPaths) {
        if (displayedFiles.isEmpty()) return@LaunchedEffect
        val anchoredPath = state.viewerCurrentPath ?: initialPath
        val anchoredPage = viewerPageAfterDatasetChange(
            anchoredPath,
            pagerState.currentPage,
            displayedFiles
        )
        if (pagerState.currentPage != anchoredPage) pagerState.scrollToPage(anchoredPage)
        if (displayedFiles[anchoredPage].absolutePath != anchoredPath) {
            viewModel.setViewerCurrentPath(displayedFiles[anchoredPage].absolutePath)
        }
    }

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                if (isBackPredicting && backActionAtStart == ViewerBackAction.ExitViewer) {
                    val scale = 1f - (backProgress * 0.08f)
                    scaleX = scale
                    scaleY = scale
                    translationX = backProgress * 100.dp.toPx()
                    alpha = 1f - (backProgress * 0.4f)
                }
            },
        color = Color.Black
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            var currentScale by remember { androidx.compose.runtime.mutableFloatStateOf(1f) }
            var previousPagePath by remember { mutableStateOf<String?>(null) }
            LaunchedEffect(pagerState.currentPage) {
                val pagePath = displayedFiles.getOrNull(pagerState.currentPage)?.absolutePath
                if (previousPagePath != null && previousPagePath != pagePath) {
                    currentScale = 1f
                    viewModel.setViewerMetadataVisible(null, visible = false)
                }
                previousPagePath = pagePath
                viewModel.setViewerCurrentPath(pagePath)
            }

            val currentFileForSheet = displayedFiles.getOrNull(pagerState.currentPage)
            val currentFile = currentFileForSheet
            val metadataCache = remember { mutableStateMapOf<String, GalleryFileMetadata>() }
            LaunchedEffect(state.isRefreshing, state.viewerMetadataRevision) {
                if (state.isRefreshing || state.viewerMetadataRevision > 0L) {
                    metadataCache.clear()
                }
            }
            val currentPath = currentFile?.absolutePath
            LaunchedEffect(currentPath, state.isRefreshing, state.viewerMetadataRevision) {
                val file = currentFile ?: return@LaunchedEffect
                if (!state.isRefreshing && metadataCache[file.absolutePath] == null) {
                    metadataCache.putBoundedViewerEntry(
                        file.absolutePath,
                        viewModel.readImageMetadata(file.absolutePath, file.mimeType)
                    )
                }
            }

            val dateText = remember(currentFile) { currentFile?.let { formatViewerDateTime(it.lastModified) }.orEmpty() }
            val currentResolution = currentPath
                ?.let(metadataCache::get)
                ?.let { formatResolution(it.width, it.height) }
                .orEmpty()
            val currentSizeText = remember(currentFile) {
                currentFile?.size?.takeIf { it > 0L }?.let(::formatFileSize).orEmpty()
            }
            val positionText = viewerPositionLabel(pagerState.currentPage, displayedFiles.size)
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                beyondViewportPageCount = 1,
                userScrollEnabled = currentScale <= 1.05f
            ) { page ->
                val file = displayedFiles.getOrNull(page)
                if (file != null) {
                    val rotation = state.viewerRotationDegrees[file.absolutePath] ?: 0f

                    val showEraseDialog = state.viewerEraseDialogPath == file.absolutePath

                    val verticalPagerState = rememberPagerState(
                        initialPage = if (!readOnly && state.viewerMetadataPath == file.absolutePath) 1 else 0,
                        pageCount = { if (readOnly) 1 else 2 }
                    )

                    val isCurrentPage = pagerState.currentPage == page
                    var metadata by remember(file.absolutePath) {
                        mutableStateOf<GalleryFileMetadata?>(metadataCache[file.absolutePath])
                    }
                    LaunchedEffect(
                        file.absolutePath,
                        state.isRefreshing,
                        state.viewerMetadataRevision,
                        showMetadataSheet,
                        isCurrentPage
                    ) {
                        if (
                            !state.isRefreshing &&
                            state.viewerMetadataSavingPath != file.absolutePath &&
                            showMetadataSheet &&
                            isCurrentPage
                        ) {
                            val data = metadataCache[file.absolutePath]
                                ?: viewModel.readImageMetadata(file.absolutePath, file.mimeType).also {
                                    metadataCache.putBoundedViewerEntry(file.absolutePath, it)
                                }
                            metadata = data
                        }
                    }

                    LaunchedEffect(showMetadataSheet, isCurrentPage) {
                        if (isCurrentPage) {
                            val shouldShowMetadata = state.viewerMetadataPath == file.absolutePath
                            if (shouldShowMetadata && verticalPagerState.currentPage == 0) {
                                verticalPagerState.animateScrollToPage(1, animationSpec = spring(stiffness = Spring.StiffnessLow))
                            } else if (!shouldShowMetadata && verticalPagerState.currentPage == 1) {
                                verticalPagerState.animateScrollToPage(0, animationSpec = spring(stiffness = Spring.StiffnessLow))
                            }
                        }
                    }

                    LaunchedEffect(verticalPagerState.currentPage, isCurrentPage) {
                        if (isCurrentPage) {
                            viewModel.setViewerMetadataVisible(
                                path = file.absolutePath,
                                visible = verticalPagerState.currentPage == 1
                            )
                        }
                    }

                    VerticalPager(
                        state = verticalPagerState,
                        modifier = Modifier.fillMaxSize(),
                        userScrollEnabled = (verticalPagerState.currentPage == 1) || (currentScale <= 1.05f),
                        beyondViewportPageCount = 1,
                        flingBehavior = PagerDefaults.flingBehavior(
                            state = verticalPagerState,
                            snapPositionalThreshold = 0.15f
                        )
                    ) { vertPage ->
                        if (vertPage == 0) {
                            ZoomableImageViewer(
                                file = file,
                                rotation = rotation,
                                onDismiss = onNavigateBack,
                                onTap = { viewModel.toggleViewerUi() },
                                onScaleChanged = { scaleVal ->
                                    if (pagerState.currentPage == page) {
                                        currentScale = scaleVal
                                    }
                                },
                                onSwipeUp = {
                                    if (!readOnly) {
                                        viewModel.setViewerMetadataVisible(file.absolutePath, visible = true)
                                    }
                                },
                                onOpenWith = { onOpenWith(file) }
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .graphicsLayer {
                                        if (isBackPredicting && backActionAtStart == ViewerBackAction.DismissMetadata) {
                                            translationY = backProgress * size.height.toFloat()
                                        }
                                    }
                            ) {
                                MetadataSheet(
                                    file = file,
                                    metadata = metadata,
                                    isSaving = state.viewerMetadataSavingPath == file.absolutePath,
                                    metadataRevision = state.viewerMetadataRevision,
                                    onSaveMetadata = { update ->
                                        viewModel.updateMetadata(file.absolutePath, update)
                                    },
                                    onEraseMetadata = { viewModel.setViewerEraseDialogPath(file.absolutePath) },
                                    onDismiss = { viewModel.setViewerMetadataVisible(null, visible = false) }
                                )
                            }
                        }
                    }

                    if (!readOnly && showEraseDialog) {
                        val confirmClick = {
                            haptics.destructiveConfirm()
                            viewModel.eraseMetadata(file.absolutePath)
                        }
                        val cancelClick = {
                            haptics.selectionChanged()
                            viewModel.setViewerEraseDialogPath(null)
                        }
                        androidx.compose.material3.AlertDialog(
                            onDismissRequest = cancelClick,
                            title = { Text(stringResource(R.string.image_gallery_metadata_erase_dialog_title)) },
                            text = { Text(stringResource(R.string.image_gallery_metadata_erase_dialog_message)) },
                            confirmButton = {
                                Button(
                                    onClick = confirmClick,
                                    shape = ExpressiveShapes.medium,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.error,
                                        contentColor = MaterialTheme.colorScheme.onError
                                    )
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(stringResource(R.string.settings_clear_thumbnail_cache))
                                }
                            },
                            dismissButton = {
                                androidx.compose.material3.TextButton(
                                    onClick = cancelClick,
                                    shape = ExpressiveShapes.medium
                                ) {
                                    Text(stringResource(R.string.cancel))
                                }
                            }
                        )
                    }
                }
            }

            ImageViewerTopChrome(
                visible = state.viewerUiVisible && !showMetadataSheet,
                currentFile = currentFile,
                positionText = positionText,
                dateText = dateText,
                resolutionText = currentResolution,
                sizeText = currentSizeText,
                marqueeEnabled = marqueeEnabled,
                modifier = Modifier.align(Alignment.TopCenter)
            )

            val chromeActions = remember(viewModel, pagerState, onOpenWith, onShareFile) {
                ImageViewerChromeActions(
                    onPageSelected = { page -> pagerState.animateScrollToPage(page) },
                    onToggleFavorite = viewModel::toggleFavorite,
                    onRotate = viewModel::rotateViewerImage,
                    onDelete = { path ->
                        viewModel.requestDeleteCurrent(path)
                    },
                    onToggleSelection = viewModel::toggleSelection,
                    onShowMetadata = { path ->
                        viewModel.setViewerMetadataVisible(path, visible = true)
                    },
                    onOpenWith = onOpenWith,
                    onShare = onShareFile
                )
            }
            ImageViewerBottomChrome(
                visible = state.viewerUiVisible && !showMetadataSheet,
                files = displayedFiles,
                currentPage = pagerState.currentPage,
                currentFile = currentFile,
                favoriteFiles = state.favoriteFiles,
                selectedFiles = state.selectedFiles,
                selectionModeEnabled = selectionModeEnabled,
                readOnly = readOnly,
                actions = chromeActions,
                modifier = Modifier.align(Alignment.BottomCenter)
            )
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
