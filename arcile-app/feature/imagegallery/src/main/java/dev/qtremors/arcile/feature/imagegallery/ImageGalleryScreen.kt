@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package dev.qtremors.arcile.feature.imagegallery

import androidx.activity.compose.PredictiveBackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.drawBehind
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.qtremors.arcile.core.operation.BulkFileOperationType
import dev.qtremors.arcile.core.operation.OperationCompletionStatus
import dev.qtremors.arcile.core.storage.domain.BrowserPresentationPreferences
import dev.qtremors.arcile.core.storage.domain.ClipboardOperation
import dev.qtremors.arcile.core.storage.domain.ConflictResolution
import dev.qtremors.arcile.core.storage.domain.ImageGalleryDefaultTab
import dev.qtremors.arcile.core.storage.domain.ImageGalleryGrouping
import dev.qtremors.arcile.core.ui.R
import dev.qtremors.arcile.shared.ui.ArcileFeedbackEvent
import dev.qtremors.arcile.shared.ui.ArcileFeedbackSeverity
import dev.qtremors.arcile.shared.ui.PasteConflictDialog
import dev.qtremors.arcile.shared.ui.SplitButtonGroup
import dev.qtremors.arcile.shared.ui.ToolbarAction
import dev.qtremors.arcile.shared.ui.rememberArcileHaptics
import dev.qtremors.arcile.shared.ui.dialogs.DeleteConfirmationDialog
import dev.qtremors.arcile.shared.ui.dialogs.ClipboardContentsDialog
import dev.qtremors.arcile.shared.ui.dialogs.PropertiesDialog
import dev.qtremors.arcile.shared.ui.dialogs.RenameDialog
import dev.qtremors.arcile.ui.theme.menuGroupFirst
import dev.qtremors.arcile.ui.theme.menuGroupLast
import dev.qtremors.arcile.ui.theme.menuGroupMiddle
import dev.qtremors.arcile.ui.theme.menuGroupSingle
import dev.qtremors.arcile.utils.formatFileSize
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val GALLERY_PROGRESS_PILL_TERMINAL_HOLD_MS = 800L
private const val GALLERY_PROGRESS_PILL_WIDTH_DP = 192

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

@Composable
fun ImageGalleryScreen(
    state: ImageGalleryState,
    onNavigateBack: () -> Unit,
    onOpenFile: (String) -> Unit,
    onToggleSelection: (String) -> Unit,
    onClearSelection: () -> Unit,
    onSelectAll: () -> Unit,
    onInvertSelection: () -> Unit = {},
    onSelectMultiple: (List<String>) -> Unit,
    onShareSelected: () -> Unit,
    onRequestDeleteSelected: () -> Unit,
    onConfirmDelete: () -> Unit,
    onTogglePermanentDelete: () -> Unit,
    onToggleShred: () -> Unit,
    onDismissDeleteConfirmation: () -> Unit,
    onOpenProperties: () -> Unit,
    onDismissProperties: () -> Unit,
    onRefresh: () -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onClearSearch: () -> Unit,
    onSelectAlbum: (String?) -> Unit,
    onPresentationChange: (BrowserPresentationPreferences) -> Unit,
    onShowFileDetailsChange: (Boolean) -> Unit,
    onClearError: () -> Unit,
    onCopySelected: () -> Unit = {},
    onCutSelected: () -> Unit = {},
    onPasteToAlbum: (String) -> Unit = {},
    onCancelClipboard: () -> Unit = {},
    onRemoveFromClipboard: (String) -> Unit = {},
    onClearActiveFileOperation: () -> Unit = {},
    onResolvePasteConflicts: (Map<String, ConflictResolution>) -> Unit = {},
    onDismissPasteConflictDialog: () -> Unit = {},
    onRenameFile: (String, String) -> Unit = { _, _ -> },
    onCreateZipFromSelection: () -> Unit = {},
    onSetAlbumCover: (String, String) -> Unit = { _, _ -> },
    onAspectRatioChange: (Boolean) -> Unit = {},
    onSectionedChange: (Boolean) -> Unit = {},
    onGroupingChange: (ImageGalleryGrouping) -> Unit = {},
    onDefaultTabChange: (ImageGalleryDefaultTab) -> Unit = {},
    onAlbumPresentationChange: (BrowserPresentationPreferences) -> Unit = {},
    onFeedback: (ArcileFeedbackEvent) -> Unit = {},
    nativeRequestFlow: SharedFlow<android.content.IntentSender>? = null
) {
    val haptics = rememberArcileHaptics()
    val isSelectionMode = state.selectedFiles.isNotEmpty()
    var showSearchBar by rememberSaveable { mutableStateOf(state.searchQuery.isNotEmpty()) }
    var showPresentationSheet by rememberSaveable { mutableStateOf(false) }
    var showRenameDialog by rememberSaveable { mutableStateOf(false) }
    var showClipboardContents by rememberSaveable { mutableStateOf(false) }
    var currentTab by rememberSaveable { mutableStateOf(GalleryTab.PHOTOS) }
    var defaultTabApplied by rememberSaveable { mutableStateOf(false) }
    val albumsGridState = rememberLazyGridState()
    val pagerState = rememberPagerState(
        initialPage = if (currentTab == GalleryTab.ALBUMS) 1 else 0,
        pageCount = { 2 }
    )
    val coroutineScope = rememberCoroutineScope()

    var activePhotosGridCellSize by remember(state.presentation.gridMinCellSize) {
        mutableStateOf(state.presentation.gridMinCellSize)
    }
    var activeAlbumsGridCellSize by remember(state.albumPresentation.gridMinCellSize) {
        mutableStateOf(state.albumPresentation.gridMinCellSize)
    }

    var isTopBarVisible by rememberSaveable { mutableStateOf(true) }
    val nestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (available.y < -15f) {
                    isTopBarVisible = false
                } else if (available.y > 15f) {
                    isTopBarVisible = true
                }
                return Offset.Zero
            }
        }
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            onConfirmDelete()
        }
    }

    LaunchedEffect(nativeRequestFlow) {
        nativeRequestFlow?.collect { sender ->
            launcher.launch(IntentSenderRequest.Builder(sender).build())
        }
    }

    LaunchedEffect(state.error) {
        state.error?.let { error ->
            haptics.error()
            onFeedback(ArcileFeedbackEvent(error, ArcileFeedbackSeverity.Error))
            onClearError()
        }
    }

    LaunchedEffect(state.preferencesLoaded, state.imageGalleryDefaultTab) {
        if (!defaultTabApplied && state.preferencesLoaded) {
            val targetTab = when (state.imageGalleryDefaultTab) {
                ImageGalleryDefaultTab.PHOTOS -> GalleryTab.PHOTOS
                ImageGalleryDefaultTab.ALBUMS -> GalleryTab.ALBUMS
            }
            currentTab = targetTab
            pagerState.scrollToPage(if (targetTab == GalleryTab.ALBUMS) 1 else 0)
            defaultTabApplied = true
        }
    }

    LaunchedEffect(pagerState.currentPage) {
        currentTab = if (pagerState.currentPage == 1) GalleryTab.ALBUMS else GalleryTab.PHOTOS
        if (pagerState.currentPage == 0 && state.selectedAlbumPath != null) {
            onSelectAlbum(null)
        }
    }

    var backProgress by remember { mutableStateOf(0f) }
    var isBackPredicting by remember { mutableStateOf(false) }

    PredictiveBackHandler(enabled = true) { progressFlow ->
        isBackPredicting = true
        try {
            progressFlow.collect { backEvent ->
                backProgress = backEvent.progress
            }
            when {
                isSelectionMode -> onClearSelection()
                showSearchBar -> {
                    showSearchBar = false
                    onClearSearch()
                }
                currentTab == GalleryTab.ALBUMS && state.selectedAlbumPath != null -> {
                    onSelectAlbum(null)
                }
                else -> {
                    onNavigateBack()
                }
            }
        } catch (e: Exception) {
            // Cancelled
        } finally {
            isBackPredicting = false
            backProgress = 0f
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(nestedScrollConnection)
    ) {
        val bottomPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 96.dp

        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    if (isBackPredicting) {
                        val scale = 1f - (backProgress * 0.08f)
                        scaleX = scale
                        scaleY = scale
                        translationX = backProgress * 100.dp.toPx()
                        alpha = 1f - (backProgress * 0.4f)
                    }
                }
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                userScrollEnabled = !isSelectionMode
            ) { page ->
                if (page == 0) {
                    ImageGalleryContent(
                        state = state.copy(selectedAlbumPath = null).withResolvedDisplayedFiles(),
                        gridMinCellSize = activePhotosGridCellSize,
                        onPhotosGridCellSizeChange = { activePhotosGridCellSize = it },
                        onPhotosGridCellSizeFinalized = { size ->
                            onPresentationChange(state.presentation.copy(gridMinCellSize = size))
                        },
                        contentPadding = PaddingValues(
                            top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 72.dp,
                            bottom = bottomPadding
                        ),
                        onOpenFile = onOpenFile,
                        onToggleSelection = onToggleSelection,
                        onSelectMultiple = onSelectMultiple,
                        onSelectAlbum = onSelectAlbum,
                        onRefresh = onRefresh
                    )
                } else if (state.selectedAlbumPath == null) {
                    ImageGalleryAlbumsGrid(
                        state = state,
                        gridMinCellSize = activeAlbumsGridCellSize,
                        onAlbumsGridCellSizeChange = { activeAlbumsGridCellSize = it },
                        onAlbumsGridCellSizeFinalized = { size ->
                            onAlbumPresentationChange(state.albumPresentation.copy(gridMinCellSize = size))
                        },
                        contentPadding = PaddingValues(
                            top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 72.dp,
                            bottom = bottomPadding
                        ),
                        onSelectAlbum = onSelectAlbum,
                        gridState = albumsGridState,
                        onPasteToAlbum = onPasteToAlbum
                    )
                } else {
                    ImageGalleryContent(
                        state = state,
                        gridMinCellSize = activePhotosGridCellSize,
                        onPhotosGridCellSizeChange = { activePhotosGridCellSize = it },
                        onPhotosGridCellSizeFinalized = { size ->
                            onPresentationChange(state.presentation.copy(gridMinCellSize = size))
                        },
                        contentPadding = PaddingValues(
                            top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 72.dp,
                            bottom = bottomPadding
                        ),
                        onOpenFile = onOpenFile,
                        onToggleSelection = onToggleSelection,
                        onSelectMultiple = onSelectMultiple,
                        onSelectAlbum = onSelectAlbum,
                        onRefresh = onRefresh
                    )
                }
            }
        }

        // Animated offsets & alpha for floating top bar components
        val topBarOffset by animateDpAsState(
            targetValue = if (isTopBarVisible || showSearchBar) 0.dp else (-120).dp,
            animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
            label = "topBarOffset"
        )
        val topBarAlpha by animateFloatAsState(
            targetValue = if (isTopBarVisible || showSearchBar) 1f else 0f,
            animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
            label = "topBarAlpha"
        )

        // Floating top control row
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .graphicsLayer {
                    translationY = topBarOffset.toPx()
                    alpha = topBarAlpha
                    if (isBackPredicting) {
                        if (isSelectionMode) {
                            val scale = 1f - (backProgress * 0.15f)
                            scaleX = scale
                            scaleY = scale
                            alpha = topBarAlpha * (1f - backProgress)
                            translationY = topBarOffset.toPx() - (backProgress * 40.dp.toPx())
                        } else if (showSearchBar) {
                            alpha = topBarAlpha * (1f - backProgress)
                            translationY = topBarOffset.toPx() - (backProgress * 50.dp.toPx())
                        }
                    }
                }
        ) {
            if (isSelectionMode) {
                FloatingGallerySelectionTopBar(
                    selectedCount = state.selectedFiles.size,
                    selectedSize = formatFileSize(
                        state.files.filter { state.selectedFiles.contains(it.absolutePath) }.sumOf { it.size }
                    ),
                    onClearSelection = onClearSelection,
                    onSelectAll = onSelectAll,
                    onInvertSelection = onInvertSelection,
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                FloatingGalleryTopBar(
                    state = state,
                    showSearchBar = showSearchBar,
                    currentTab = currentTab,
                    onSearchClick = { showSearchBar = true },
                    onSortClick = { showPresentationSheet = true },
                    onNavigateBack = {
                        if (currentTab == GalleryTab.ALBUMS && state.selectedAlbumPath != null) {
                            onSelectAlbum(null)
                        } else {
                            onNavigateBack()
                        }
                    },
                    onClearSearch = {
                        showSearchBar = false
                        onClearSearch()
                    },
                    onSearchQueryChange = onSearchQueryChange,
                    onShowFileDetailsChange = onShowFileDetailsChange,
                    onDefaultTabChange = onDefaultTabChange,
                    onSelectAll = onSelectAll,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        val copyDesc = stringResource(R.string.action_copy)
        val cutDesc = stringResource(R.string.action_cut)
        val deleteDesc = stringResource(R.string.action_delete_selected)
        val renameDesc = stringResource(R.string.action_rename)
        val errorColor = MaterialTheme.colorScheme.error

        val mainActions = remember(state.selectedFiles, onCopySelected, onCutSelected, onRequestDeleteSelected, copyDesc, cutDesc, deleteDesc, renameDesc, errorColor) {
            val list = mutableListOf<ToolbarAction>()
            list.add(
                ToolbarAction(
                    icon = Icons.Default.ContentCopy,
                    contentDescription = copyDesc,
                    onClick = onCopySelected
                )
            )
            list.add(
                ToolbarAction(
                    icon = Icons.Default.ContentCut,
                    contentDescription = cutDesc,
                    onClick = onCutSelected
                )
            )
            list.add(
                ToolbarAction(
                    icon = Icons.Default.Delete,
                    contentDescription = deleteDesc,
                    tint = errorColor,
                    onClick = onRequestDeleteSelected
                )
            )
            if (state.selectedFiles.size == 1) {
                list.add(
                    ToolbarAction(
                        icon = Icons.Default.Edit,
                        contentDescription = renameDesc,
                        onClick = { showRenameDialog = true }
                    )
                )
            }
            list
        }

        // 3D Flip Rotation Animation (Vertical on X-axis)
        val rotationX by animateFloatAsState(
            targetValue = if (isSelectionMode) 180f else 0f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioLowBouncy,
                stiffness = Spring.StiffnessLow
            ),
            label = "bottomNavFlip"
        )

        val density = LocalDensity.current
        val bottomBarOffset by animateDpAsState(
            targetValue = if (isTopBarVisible || isSelectionMode) 0.dp else 120.dp,
            animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
            label = "bottomBarOffset"
        )
        val bottomBarAlpha by animateFloatAsState(
            targetValue = if (isTopBarVisible || isSelectionMode) 1f else 0f,
            animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
            label = "bottomBarAlpha"
        )

        val bottomBarModifier = if (isSelectionMode) {
            Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 16.dp)
                .fillMaxWidth()
        } else {
            Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 16.dp)
                .wrapContentSize()
        }

        // Floating dynamic bottom navigation with vertical 3D Flip on selection
        Box(
            modifier = bottomBarModifier
                .graphicsLayer {
                    translationY = bottomBarOffset.toPx()
                    alpha = bottomBarAlpha
                }
                .animateContentSize(),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .graphicsLayer {
                        this.rotationX = rotationX
                        cameraDistance = 12f * density.density
                        if (isBackPredicting && isSelectionMode) {
                            val scale = 1f - (backProgress * 0.15f)
                            scaleX = scale
                            scaleY = scale
                            alpha = 1f - backProgress
                        }
                    }
                    .then(if (isSelectionMode) Modifier.fillMaxWidth() else Modifier.wrapContentSize())
            ) {
                if (rotationX <= 90f) {
                    val albumPastePath = state.selectedAlbumPath?.takeIf(::isPasteDestinationAlbumPath)
                    if (state.clipboardState != null || state.activeFileOperation != null) {
                        GalleryClipboardOperationToolbar(
                            state = state,
                            pasteDestinationPath = albumPastePath.takeIf { currentTab == GalleryTab.ALBUMS },
                            onPasteToAlbum = onPasteToAlbum,
                            onCancelClipboard = onCancelClipboard,
                            onShowClipboardContents = { showClipboardContents = true },
                            onClearActiveFileOperation = onClearActiveFileOperation
                        )
                    } else {
                        // Normal state: Photos & Albums tab bar
                        Row(
                            modifier = Modifier
                                .background(
                                    color = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.85f),
                                    shape = CircleShape
                                )
                                .padding(6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            TabItem(
                                selected = currentTab == GalleryTab.PHOTOS,
                                label = stringResource(R.string.image_gallery_tab_photos),
                                icon = Icons.Default.Image,
                                onClick = {
                                    currentTab = GalleryTab.PHOTOS
                                    onSelectAlbum(null)
                                    coroutineScope.launch { pagerState.animateScrollToPage(0) }
                                }
                            )
                            TabItem(
                                selected = currentTab == GalleryTab.ALBUMS,
                                label = stringResource(R.string.image_gallery_tab_albums),
                                icon = Icons.Default.Folder,
                                onClick = {
                                    currentTab = GalleryTab.ALBUMS
                                    coroutineScope.launch { pagerState.animateScrollToPage(1) }
                                }
                            )
                        }
                    }
                } else {
                    // Flipped selection state: Action buttons (counter-rotated vertical flip)
                    Box(
                        modifier = Modifier
                            .graphicsLayer {
                                this.rotationX = 180f
                            }
                            .fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            // Split actions button group (matching the browser style)
                            SplitButtonGroup(
                                actions = mainActions,
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                contentColor = MaterialTheme.colorScheme.onSurface,
                                height = 56.dp,
                                minWidth = 56.dp,
                                iconSize = 24.dp
                            )

                            // Detached options overflow circular button
                            var showSelectionMenu by rememberSaveable { mutableStateOf(false) }
                            Box {
                                Surface(
                                    onClick = { showSelectionMenu = true },
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                    shadowElevation = 4.dp,
                                    tonalElevation = 4.dp,
                                    modifier = Modifier.size(56.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Default.MoreVert,
                                            contentDescription = stringResource(R.string.action_more_options),
                                            modifier = Modifier.size(28.dp)
                                        )
                                    }
                                }

                                DropdownMenu(
                                    shape = MaterialTheme.shapes.extraLarge,
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                    expanded = showSelectionMenu,
                                    onDismissRequest = { showSelectionMenu = false }
                                ) {
                                    val menuActions = remember(state.selectedFiles, state.selectedAlbumPath) {
                                        val actions = mutableListOf<@Composable () -> Unit>()

                                        actions.add {
                                            DropdownMenuItem(
                                                text = { Text(stringResource(R.string.archive_compress_zip)) },
                                                leadingIcon = { Icon(Icons.Default.FolderZip, contentDescription = null) },
                                                onClick = {
                                                    showSelectionMenu = false
                                                    onCreateZipFromSelection()
                                                }
                                            )
                                        }

                                        if (state.selectedFiles.size == 1 && state.selectedAlbumPath != null && state.selectedAlbumPath != "__favorites__") {
                                            actions.add {
                                                DropdownMenuItem(
                                                    text = { Text(stringResource(R.string.image_gallery_set_as_cover)) },
                                                    leadingIcon = { Icon(Icons.Default.Image, contentDescription = null) },
                                                    onClick = {
                                                        showSelectionMenu = false
                                                        onSetAlbumCover(state.selectedAlbumPath, state.selectedFiles.first())
                                                        onClearSelection()
                                                    }
                                                )
                                            }
                                        }

                                        actions.add {
                                            DropdownMenuItem(
                                                text = { Text(stringResource(R.string.share)) },
                                                leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) },
                                                onClick = {
                                                    showSelectionMenu = false
                                                    onShareSelected()
                                                }
                                            )
                                        }

                                        actions.add {
                                            DropdownMenuItem(
                                                text = { Text(stringResource(R.string.properties_title)) },
                                                leadingIcon = { Icon(Icons.Default.Info, contentDescription = null) },
                                                onClick = {
                                                    showSelectionMenu = false
                                                    onOpenProperties()
                                                }
                                            )
                                        }

                                        actions
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
    }

    if (showRenameDialog && state.selectedFiles.size == 1) {
        val selectedPath = state.selectedFiles.first()
        val currentName = selectedPath.substringAfterLast('/')
        RenameDialog(
            currentName = currentName,
            onDismiss = {
                showRenameDialog = false
                onClearSelection()
            },
            onConfirm = { newName ->
                onRenameFile(selectedPath, newName)
                showRenameDialog = false
            }
        )
    }

    if (state.showTrashConfirmation || state.showPermanentDeleteConfirmation || state.showMixedDeleteExplanation) {
        DeleteConfirmationDialog(
            selectedCount = state.selectedFiles.size,
            isPermanentDeleteChecked = state.isPermanentDeleteChecked || state.showMixedDeleteExplanation,
            isPermanentDeleteToggleEnabled = state.isPermanentDeleteToggleEnabled && !state.showMixedDeleteExplanation,
            onConfirm = if (state.showMixedDeleteExplanation) ({}) else onConfirmDelete,
            onDismiss = onDismissDeleteConfirmation,
            onTogglePermanentDelete = onTogglePermanentDelete,
            decision = state.deleteDecision,
            isShredChecked = state.isShredChecked,
            onToggleShred = onToggleShred
        )
    }

    if (state.showConflictDialog && state.pasteConflicts.isNotEmpty()) {
        PasteConflictDialog(
            conflicts = state.pasteConflicts,
            onResolve = onResolvePasteConflicts,
            onDismiss = onDismissPasteConflictDialog
        )
    }

    if (showClipboardContents && state.clipboardState != null) {
        ClipboardContentsDialog(
            state = state.clipboardState,
            onRemoveItem = onRemoveFromClipboard,
            onDismiss = { showClipboardContents = false }
        )
    }

    if (state.isPropertiesVisible) {
        PropertiesDialog(
            properties = state.properties,
            isLoading = state.isPropertiesLoading,
            onDismiss = {
                onDismissProperties()
                onClearSelection()
            }
        )
    }

    if (showPresentationSheet) {
        GalleryViewOptionsDialog(
            currentTab = currentTab,
            photosPresentation = state.presentation,
            albumPresentation = state.albumPresentation,
            isAspectRatio = state.isAspectRatio,
            grouping = state.imageGalleryGrouping,
            showFileDetails = state.showFileDetails,
            onPhotosPresentationChange = onPresentationChange,
            onAlbumPresentationChange = onAlbumPresentationChange,
            onPhotosAspectRatioChange = onAspectRatioChange,
            onGroupingChange = onGroupingChange,
            onShowFileDetailsChange = onShowFileDetailsChange,
            onDismiss = { showPresentationSheet = false }
        )
    }
}

@Composable
private fun GalleryClipboardOperationToolbar(
    state: ImageGalleryState,
    pasteDestinationPath: String?,
    onPasteToAlbum: (String) -> Unit,
    onCancelClipboard: () -> Unit,
    onShowClipboardContents: () -> Unit,
    onClearActiveFileOperation: () -> Unit
) {
    val clipboard = state.clipboardState
    val activeOp = state.activeFileOperation

    LaunchedEffect(activeOp?.terminalStatus) {
        if (activeOp?.terminalStatus != null) {
            delay(GALLERY_PROGRESS_PILL_TERMINAL_HOLD_MS)
            onClearActiveFileOperation()
        }
    }

    val toolbarActions = when {
        activeOp != null && activeOp.terminalStatus == null -> listOf(
            ToolbarAction(
                icon = Icons.Default.Close,
                contentDescription = stringResource(R.string.action_cancel_transfer),
                containerColor = MaterialTheme.colorScheme.error,
                tint = MaterialTheme.colorScheme.onError,
                onClick = onCancelClipboard
            )
        )
        activeOp == null && clipboard != null -> buildList {
            if (pasteDestinationPath != null) {
                add(
                    ToolbarAction(
                        icon = Icons.Default.ContentPaste,
                        contentDescription = stringResource(R.string.action_paste_here),
                        onClick = { onPasteToAlbum(pasteDestinationPath) }
                    )
                )
            }
            add(
                ToolbarAction(
                    icon = Icons.Default.Close,
                    contentDescription = stringResource(R.string.action_cancel_transfer),
                    containerColor = MaterialTheme.colorScheme.error,
                    tint = MaterialTheme.colorScheme.onError,
                    onClick = onCancelClipboard
                )
            )
        }
        else -> emptyList()
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        GalleryClipboardProgressPill(
            clipboardOperation = clipboard?.operation,
            clipboardItemCount = clipboard?.files?.size ?: 0,
            clipboardTotalSize = clipboard?.totalSize ?: 0L,
            activeOperation = activeOp,
            onClick = {
                if (activeOp == null && clipboard != null) {
                    onShowClipboardContents()
                }
            }
        )
        SplitButtonGroup(
            actions = toolbarActions,
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurface,
            height = 56.dp,
            minWidth = 56.dp,
            iconSize = 24.dp
        )
    }
}

@Composable
private fun GalleryClipboardProgressPill(
    clipboardOperation: ClipboardOperation?,
    clipboardItemCount: Int,
    clipboardTotalSize: Long,
    activeOperation: ImageGalleryOperationUiState?,
    onClick: () -> Unit
) {
    val rawProgress = activeOperation?.let { operation ->
        val byteProgress = operation.totalBytes
            ?.takeIf { it > 0L }
            ?.let { total -> ((operation.bytesCopied ?: 0L).toFloat() / total.toFloat()).coerceIn(0f, 1f) }
        val itemProgress = operation.totalItems
            .takeIf { it > 0 }
            ?.let { total -> operation.completedItems.toFloat() / total.toFloat() }
            ?.coerceIn(0f, 1f)
        byteProgress ?: itemProgress
    } ?: 0f
    val displayedProgress = if (activeOperation?.terminalStatus != null) 1f else rawProgress
    val progressFillColor = when (activeOperation?.terminalStatus) {
        OperationCompletionStatus.SUCCESS -> Color(0xFF4CAF50).copy(alpha = 0.25f)
        OperationCompletionStatus.FAILED,
        OperationCompletionStatus.CANCELLED -> MaterialTheme.colorScheme.error.copy(alpha = 0.25f)
        null -> MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
    }

    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 4.dp,
        shadowElevation = 2.dp,
        modifier = Modifier
            .height(56.dp)
            .padding(end = 8.dp)
            .width(GALLERY_PROGRESS_PILL_WIDTH_DP.dp)
            .animateContentSize()
    ) {
        Box(
            contentAlignment = Alignment.CenterStart,
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (activeOperation != null) {
                        Modifier.drawBehind {
                            drawRect(
                                color = progressFillColor,
                                size = Size(size.width * displayedProgress, size.height)
                            )
                        }
                    } else {
                        Modifier
                    }
                )
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                val icon = when {
                    activeOperation?.type == BulkFileOperationType.MOVE || clipboardOperation == ClipboardOperation.CUT -> Icons.Default.ContentCut
                    activeOperation?.type == BulkFileOperationType.CREATE_ARCHIVE -> Icons.Default.FolderZip
                    else -> Icons.Default.ContentCopy
                }
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Column(
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    val itemCount = activeOperation?.totalItems ?: clipboardItemCount
                    Text(
                        text = pluralStringResource(R.plurals.clipboard_item_count, itemCount, itemCount),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    val subtitle = if (activeOperation != null) {
                        activeOperation.totalBytes
                            ?.takeIf { it > 0L }
                            ?.let { total -> formatFileSize((total - (activeOperation.bytesCopied ?: 0L)).coerceAtLeast(0L)) }
                            ?: stringResource(
                                R.string.transfer_progress_items,
                                activeOperation.completedItems,
                                activeOperation.totalItems
                            )
                    } else {
                        formatFileSize(clipboardTotalSize)
                    }
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
