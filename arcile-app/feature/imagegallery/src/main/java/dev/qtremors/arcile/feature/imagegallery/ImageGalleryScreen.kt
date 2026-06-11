@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package dev.qtremors.arcile.feature.imagegallery

import androidx.activity.compose.BackHandler
import androidx.activity.compose.PredictiveBackHandler
import dev.qtremors.arcile.core.storage.domain.FileModel
import androidx.compose.foundation.layout.aspectRatio
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.animateColorAsState
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material.icons.filled.Folder
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.TextButton
import androidx.compose.material3.FilledTonalButton
import dev.qtremors.arcile.core.storage.domain.FileSortOption
import kotlin.math.roundToInt
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.scrollBy
import dev.qtremors.arcile.shared.ui.SplitButtonGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Share
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.size.Precision
import dev.qtremors.arcile.core.storage.domain.BrowserPresentationPreferences
import dev.qtremors.arcile.core.storage.domain.BrowserViewMode
import dev.qtremors.arcile.core.storage.domain.ClipboardState
import dev.qtremors.arcile.core.storage.domain.ClipboardOperation
import dev.qtremors.arcile.core.storage.domain.ImageGalleryGrouping
import dev.qtremors.arcile.core.ui.R
import dev.qtremors.arcile.image.ArchiveEntryThumbnailData
import dev.qtremors.arcile.image.ThumbnailKey
import dev.qtremors.arcile.image.ThumbnailPolicy
import dev.qtremors.arcile.image.ThumbnailTargetSize
import dev.qtremors.arcile.shared.ui.ArcileFeedbackEvent
import dev.qtremors.arcile.shared.ui.ArcileFeedbackSeverity
import dev.qtremors.arcile.shared.ui.ArcilePullRefreshIndicator
import dev.qtremors.arcile.shared.ui.EmptyState
import dev.qtremors.arcile.shared.ui.EmptyStateVariant
import dev.qtremors.arcile.shared.ui.FloatingSelectionToolbar
import dev.qtremors.arcile.shared.ui.ToolbarAction
import dev.qtremors.arcile.shared.ui.rememberArcileHaptics
import dev.qtremors.arcile.shared.ui.rememberDateTimeFormatter
import dev.qtremors.arcile.shared.ui.dialogs.DeleteConfirmationDialog
import dev.qtremors.arcile.shared.ui.dialogs.PropertiesDialog
import dev.qtremors.arcile.shared.ui.dialogs.RenameDialog
import dev.qtremors.arcile.ui.theme.spacing
import dev.qtremors.arcile.ui.theme.menuGroupFirst
import dev.qtremors.arcile.ui.theme.menuGroupLast
import dev.qtremors.arcile.ui.theme.menuGroupMiddle
import dev.qtremors.arcile.ui.theme.menuGroupSingle
import dev.qtremors.arcile.utils.formatFileSize
import kotlinx.coroutines.flow.SharedFlow

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
    onRenameFile: (String, String) -> Unit = { _, _ -> },
    onCreateZipFromSelection: () -> Unit = {},
    onSetAlbumCover: (String, String) -> Unit = { _, _ -> },
    onAspectRatioChange: (Boolean) -> Unit = {},
    onSectionedChange: (Boolean) -> Unit = {},
    onGroupingChange: (ImageGalleryGrouping) -> Unit = {},
    onAlbumPresentationChange: (BrowserPresentationPreferences) -> Unit = {},
    onAlbumAspectRatioChange: (Boolean) -> Unit = {},
    onFeedback: (ArcileFeedbackEvent) -> Unit = {},
    nativeRequestFlow: SharedFlow<android.content.IntentSender>? = null
) {
    val haptics = rememberArcileHaptics()
    val isSelectionMode = state.selectedFiles.isNotEmpty()
    var showSearchBar by rememberSaveable { mutableStateOf(state.searchQuery.isNotEmpty()) }
    var showPresentationSheet by rememberSaveable { mutableStateOf(false) }
    var showRenameDialog by rememberSaveable { mutableStateOf(false) }
    var currentTab by rememberSaveable { mutableStateOf(GalleryTab.PHOTOS) }

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

    var backProgress by remember { mutableStateOf(0f) }
    var isBackPredicting by remember { mutableStateOf(false) }

    val backEnabled = isSelectionMode || showSearchBar || (currentTab == GalleryTab.ALBUMS && state.selectedAlbumPath != null)

    PredictiveBackHandler(enabled = backEnabled) { progressFlow ->
        isBackPredicting = true
        try {
            progressFlow.collect { backEvent ->
                backProgress = backEvent.progress
            }
            if (isSelectionMode) {
                onClearSelection()
            } else if (showSearchBar) {
                showSearchBar = false
                onClearSearch()
            } else if (currentTab == GalleryTab.ALBUMS && state.selectedAlbumPath != null) {
                onSelectAlbum(null)
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

        val viewState = when {
            currentTab == GalleryTab.PHOTOS -> GalleryViewState.PHOTOS_TAB
            state.selectedAlbumPath == null -> GalleryViewState.ALBUMS_TAB_GRID
            else -> GalleryViewState.ALBUM_PHOTOS
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    if (isBackPredicting) {
                        if (viewState == GalleryViewState.ALBUM_PHOTOS) {
                            val scale = 1f - (backProgress * 0.08f)
                            scaleX = scale
                            scaleY = scale
                            translationX = backProgress * 100.dp.toPx()
                            alpha = 1f - (backProgress * 0.4f)
                        } else if (showSearchBar || isSelectionMode) {
                            val scale = 1f - (backProgress * 0.05f)
                            scaleX = scale
                            scaleY = scale
                            alpha = 1f - (backProgress * 0.2f)
                        }
                    }
                }
        ) {
            when (viewState) {
                GalleryViewState.ALBUMS_TAB_GRID -> {
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
                        onSelectAlbum = onSelectAlbum
                    )
                }
                GalleryViewState.PHOTOS_TAB, GalleryViewState.ALBUM_PHOTOS -> {
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
            modifier = bottomBarModifier.animateContentSize(),
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
                            }
                        )
                        TabItem(
                            selected = currentTab == GalleryTab.ALBUMS,
                            label = stringResource(R.string.image_gallery_tab_albums),
                            icon = Icons.Default.Folder,
                            onClick = {
                                currentTab = GalleryTab.ALBUMS
                            }
                        )
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
            albumAspectRatio = state.albumAspectRatio,
            grouping = state.imageGalleryGrouping,
            showFileDetails = state.showFileDetails,
            onPhotosPresentationChange = onPresentationChange,
            onAlbumPresentationChange = onAlbumPresentationChange,
            onPhotosAspectRatioChange = onAspectRatioChange,
            onAlbumAspectRatioChange = onAlbumAspectRatioChange,
            onGroupingChange = onGroupingChange,
            onShowFileDetailsChange = onShowFileDetailsChange,
            onDismiss = { showPresentationSheet = false }
        )
    }
}

@Composable
fun FloatingGalleryTopBar(
    state: ImageGalleryState,
    showSearchBar: Boolean,
    currentTab: GalleryTab,
    onSearchClick: () -> Unit,
    onSortClick: () -> Unit,
    onNavigateBack: () -> Unit,
    onClearSearch: () -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onShowFileDetailsChange: (Boolean) -> Unit,
    onSelectAll: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showOverflowMenu by rememberSaveable { mutableStateOf(false) }

    if (showSearchBar) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.85f),
            contentColor = MaterialTheme.colorScheme.onSurface,
            tonalElevation = 4.dp,
            shadowElevation = 4.dp,
            modifier = modifier
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .height(56.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onClearSearch) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                }
                androidx.compose.foundation.text.BasicTextField(
                    value = state.searchQuery,
                    onValueChange = onSearchQueryChange,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp),
                    singleLine = true,
                    decorationBox = { innerTextField ->
                        if (state.searchQuery.isEmpty()) {
                            Text(
                                text = stringResource(R.string.image_gallery_search_placeholder),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            )
                        }
                        innerTextField()
                    }
                )
                if (state.searchQuery.isNotEmpty()) {
                    IconButton(onClick = { onSearchQueryChange("") }) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.action_clear))
                    }
                }
            }
        }
    } else {
        Box(
            modifier = modifier
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .height(56.dp)
        ) {
            Surface(
                onClick = onNavigateBack,
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.85f),
                contentColor = MaterialTheme.colorScheme.onSurface,
                tonalElevation = 4.dp,
                shadowElevation = 4.dp,
                modifier = Modifier
                    .size(48.dp)
                    .align(Alignment.CenterStart)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.back),
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.85f),
                contentColor = MaterialTheme.colorScheme.onSurface,
                tonalElevation = 4.dp,
                shadowElevation = 4.dp,
                modifier = Modifier
                    .height(48.dp)
                    .align(Alignment.CenterEnd)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 4.dp)
                ) {
                    IconButton(onClick = onSearchClick) {
                        Icon(Icons.Default.Search, contentDescription = stringResource(R.string.action_search), modifier = Modifier.size(22.dp))
                    }
                    IconButton(onClick = onSortClick) {
                        Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = stringResource(R.string.action_sort), modifier = Modifier.size(22.dp))
                    }
                    Box {
                        IconButton(onClick = { showOverflowMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.action_more_options), modifier = Modifier.size(22.dp))
                        }
                        DropdownMenu(
                            shape = MaterialTheme.shapes.extraLarge,
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                            expanded = showOverflowMenu,
                            onDismissRequest = { showOverflowMenu = false }
                        ) {
                            val menuActions = remember(state.presentation.viewMode, state.showFileDetails, state.displayedFiles.isNotEmpty()) {
                                mutableListOf<@Composable () -> Unit>().apply {
                                    if (state.presentation.viewMode == BrowserViewMode.GRID) {
                                        add {
                                            DropdownMenuItem(
                                                text = {
                                                    Column {
                                                        Text(stringResource(R.string.image_gallery_show_file_details))
                                                        Text(
                                                            text = stringResource(R.string.image_gallery_show_file_details_description),
                                                            style = MaterialTheme.typography.bodySmall,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                                        )
                                                    }
                                                },
                                                trailingIcon = {
                                                    Switch(
                                                        checked = state.showFileDetails,
                                                        onCheckedChange = null
                                                    )
                                                },
                                                onClick = {
                                                    onShowFileDetailsChange(!state.showFileDetails)
                                                    showOverflowMenu = false
                                                }
                                            )
                                        }
                                    }
                                    if (state.displayedFiles.isNotEmpty()) {
                                        add {
                                            DropdownMenuItem(
                                                text = { Text(stringResource(R.string.select_all)) },
                                                leadingIcon = { Icon(Icons.Default.SelectAll, contentDescription = null) },
                                                onClick = {
                                                    onSelectAll()
                                                    showOverflowMenu = false
                                                }
                                            )
                                        }
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

@Composable
fun FloatingGallerySelectionTopBar(
    selectedCount: Int,
    selectedSize: String,
    onClearSelection: () -> Unit,
    onSelectAll: () -> Unit,
    onInvertSelection: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .height(56.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.9f),
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            tonalElevation = 6.dp,
            shadowElevation = 6.dp,
            modifier = Modifier
                .height(48.dp)
                .align(Alignment.CenterStart)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = 4.dp, end = 16.dp)
            ) {
                IconButton(onClick = onClearSelection) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back), modifier = Modifier.size(24.dp))
                }
                Spacer(modifier = Modifier.width(4.dp))
                Column(verticalArrangement = Arrangement.Center) {
                    Text(
                        text = stringResource(R.string.selected_count, selectedCount),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = selectedSize,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    )
                }
            }
        }

        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.9f),
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            tonalElevation = 6.dp,
            shadowElevation = 6.dp,
            modifier = Modifier
                .height(48.dp)
                .align(Alignment.CenterEnd)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 4.dp)
            ) {
                IconButton(onClick = onSelectAll) {
                    Icon(
                        imageVector = Icons.Default.GridView,
                        contentDescription = stringResource(R.string.select_all),
                        modifier = Modifier.size(22.dp)
                    )
                }
                IconButton(onClick = onInvertSelection) {
                    Icon(
                        imageVector = Icons.Default.SelectAll,
                        contentDescription = stringResource(R.string.invert_selection),
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GalleryViewOptionsDialog(
    currentTab: GalleryTab,
    photosPresentation: BrowserPresentationPreferences,
    albumPresentation: BrowserPresentationPreferences,
    isAspectRatio: Boolean,
    albumAspectRatio: Boolean,
    grouping: ImageGalleryGrouping,
    showFileDetails: Boolean,
    onPhotosPresentationChange: (BrowserPresentationPreferences) -> Unit,
    onAlbumPresentationChange: (BrowserPresentationPreferences) -> Unit,
    onPhotosAspectRatioChange: (Boolean) -> Unit,
    onAlbumAspectRatioChange: (Boolean) -> Unit,
    onGroupingChange: (ImageGalleryGrouping) -> Unit,
    onShowFileDetailsChange: (Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    var draftPhotosPreferences by remember(photosPresentation) {
        mutableStateOf(photosPresentation.normalized())
    }
    var draftAlbumPreferences by remember(albumPresentation) {
        mutableStateOf(albumPresentation.normalized())
    }
    var draftPhotosAspectRatio by remember(isAspectRatio) {
        mutableStateOf(isAspectRatio)
    }
    var draftAlbumAspectRatio by remember(albumAspectRatio) {
        mutableStateOf(albumAspectRatio)
    }
    var draftGrouping by remember(grouping) {
        mutableStateOf(grouping)
    }
    var draftShowDetails by remember(showFileDetails) {
        mutableStateOf(showFileDetails)
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                Text(
                    text = if (currentTab == GalleryTab.PHOTOS) stringResource(R.string.image_gallery_view_sort_title) else "View and sort albums",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )

                if (currentTab == GalleryTab.PHOTOS) {
                    val livePhotosColumnCount = kotlin.math.max(
                        1,
                        kotlin.math.floor(((this@BoxWithConstraints.maxWidth.value - 32f) / draftPhotosPreferences.gridMinCellSize).toDouble()).toInt()
                    )

                    // 1. Layout View Mode (List vs Grid)
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = stringResource(R.string.browser_layout_view_mode),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium
                        )
                        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                            BrowserViewMode.entries.forEachIndexed { index, mode ->
                                SegmentedButton(
                                    shape = SegmentedButtonDefaults.itemShape(
                                        index = index,
                                        count = BrowserViewMode.entries.size
                                    ),
                                    onClick = { draftPhotosPreferences = draftPhotosPreferences.copy(viewMode = mode) },
                                    selected = draftPhotosPreferences.viewMode == mode,
                                    icon = {
                                        Icon(
                                            imageVector = if (mode == BrowserViewMode.LIST) {
                                                Icons.AutoMirrored.Filled.ViewList
                                            } else {
                                                Icons.Default.GridView
                                            },
                                            contentDescription = null
                                        )
                                    },
                                    label = {
                                        Text(
                                            stringResource(
                                                if (mode == BrowserViewMode.LIST) R.string.list_view else R.string.grid_view
                                            )
                                        )
                                    }
                                )
                            }
                        }
                    }

                    // 2. Zoom / Column size sliders
                    AnimatedContent(
                        targetState = draftPhotosPreferences.viewMode,
                        label = "gallery_layout_controls"
                    ) { mode ->
                        if (mode == BrowserViewMode.LIST) {
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = stringResource(R.string.browser_layout_list_zoom),
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    Text(
                                        text = stringResource(
                                            R.string.browser_layout_list_zoom_value,
                                            (draftPhotosPreferences.listZoom * 100).roundToInt()
                                        ),
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                Slider(
                                    value = draftPhotosPreferences.listZoom,
                                    onValueChange = {
                                        draftPhotosPreferences = draftPhotosPreferences.copy(listZoom = it)
                                    },
                                    valueRange = BrowserPresentationPreferences.MIN_LIST_ZOOM..BrowserPresentationPreferences.MAX_LIST_ZOOM,
                                    steps = 7
                                )
                            }
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = stringResource(R.string.browser_layout_grid_size),
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    Text(
                                        text = stringResource(
                                            R.string.browser_layout_grid_columns_value,
                                            livePhotosColumnCount
                                        ),
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                Slider(
                                    value = draftPhotosPreferences.gridMinCellSize,
                                    onValueChange = {
                                        draftPhotosPreferences = draftPhotosPreferences.copy(gridMinCellSize = it)
                                    },
                                    valueRange = BrowserPresentationPreferences.MIN_GRID_MIN_CELL_SIZE..BrowserPresentationPreferences.MAX_GRID_MIN_CELL_SIZE,
                                    steps = 1
                                )
                            }
                        }
                    }

                    // 3. Grid Mode: Square vs Aspect Ratio (only in Grid View Mode)
                    if (draftPhotosPreferences.viewMode == BrowserViewMode.GRID) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = stringResource(R.string.image_gallery_grid_mode),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Medium
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                FilterChip(
                                    selected = !draftPhotosAspectRatio,
                                    onClick = { draftPhotosAspectRatio = false },
                                    label = { Text(stringResource(R.string.image_gallery_view_mode_square)) }
                                )
                                FilterChip(
                                    selected = draftPhotosAspectRatio,
                                    onClick = { draftPhotosAspectRatio = true },
                                    label = { Text(stringResource(R.string.image_gallery_view_mode_aspect)) }
                                )
                            }
                        }
                    }

                    // 4. Sort Options Section
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = stringResource(R.string.action_sort),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                SortChip(FileSortOption.NAME_ASC, draftPhotosPreferences, Modifier.weight(1f)) {
                                    draftPhotosPreferences = draftPhotosPreferences.copy(sortOption = it)
                                }
                                SortChip(FileSortOption.NAME_DESC, draftPhotosPreferences, Modifier.weight(1f)) {
                                    draftPhotosPreferences = draftPhotosPreferences.copy(sortOption = it)
                                }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                SortChip(FileSortOption.DATE_NEWEST, draftPhotosPreferences, Modifier.weight(1f)) {
                                    draftPhotosPreferences = draftPhotosPreferences.copy(sortOption = it)
                                }
                                SortChip(FileSortOption.DATE_OLDEST, draftPhotosPreferences, Modifier.weight(1f)) {
                                    draftPhotosPreferences = draftPhotosPreferences.copy(sortOption = it)
                                }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                SortChip(FileSortOption.SIZE_LARGEST, draftPhotosPreferences, Modifier.weight(1f)) {
                                    draftPhotosPreferences = draftPhotosPreferences.copy(sortOption = it)
                                }
                                SortChip(FileSortOption.SIZE_SMALLEST, draftPhotosPreferences, Modifier.weight(1f)) {
                                    draftPhotosPreferences = draftPhotosPreferences.copy(sortOption = it)
                                }
                            }
                        }
                    }

                    // 5. Grouping Section
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = stringResource(R.string.image_gallery_grouping),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            ImageGalleryGrouping.entries.forEach { mode ->
                                FilterChip(
                                    selected = draftGrouping == mode,
                                    onClick = { draftGrouping = mode },
                                    label = {
                                        Text(
                                            text = when (mode) {
                                                ImageGalleryGrouping.NONE -> "None"
                                                ImageGalleryGrouping.DAY -> "Day"
                                                ImageGalleryGrouping.WEEK -> "Week"
                                                ImageGalleryGrouping.MONTH -> "Month"
                                            }
                                        )
                                    }
                                )
                            }
                        }
                    }

                    // 6. Details Section
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.image_gallery_show_file_details),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = stringResource(R.string.image_gallery_show_file_details_description),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = draftShowDetails,
                            onCheckedChange = { draftShowDetails = it }
                        )
                    }
                } else {
                    val liveAlbumsColumnCount = kotlin.math.max(
                        1,
                        kotlin.math.floor(((this@BoxWithConstraints.maxWidth.value - 32f) / draftAlbumPreferences.gridMinCellSize).toDouble()).toInt()
                    )

                    // 1. Column size slider
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.browser_layout_grid_size),
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = stringResource(
                                    R.string.browser_layout_grid_columns_value,
                                    liveAlbumsColumnCount
                                ),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Slider(
                            value = draftAlbumPreferences.gridMinCellSize,
                            onValueChange = {
                                draftAlbumPreferences = draftAlbumPreferences.copy(gridMinCellSize = it)
                            },
                            valueRange = BrowserPresentationPreferences.MIN_GRID_MIN_CELL_SIZE..BrowserPresentationPreferences.MAX_GRID_MIN_CELL_SIZE,
                            steps = 1
                        )
                    }

                    // 2. Aspect Ratio Selector
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = stringResource(R.string.image_gallery_grid_mode),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            FilterChip(
                                selected = !draftAlbumAspectRatio,
                                onClick = { draftAlbumAspectRatio = false },
                                label = { Text(stringResource(R.string.image_gallery_view_mode_square)) }
                            )
                            FilterChip(
                                selected = draftAlbumAspectRatio,
                                onClick = { draftAlbumAspectRatio = true },
                                label = { Text(stringResource(R.string.image_gallery_view_mode_aspect)) }
                            )
                        }
                    }

                    // 3. Sort Options (Name / Count)
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = stringResource(R.string.action_sort),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                SortChip(FileSortOption.NAME_ASC, draftAlbumPreferences, Modifier.weight(1f)) {
                                    draftAlbumPreferences = draftAlbumPreferences.copy(sortOption = it)
                                }
                                SortChip(FileSortOption.NAME_DESC, draftAlbumPreferences, Modifier.weight(1f)) {
                                    draftAlbumPreferences = draftAlbumPreferences.copy(sortOption = it)
                                }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                SortChip(FileSortOption.SIZE_LARGEST, draftAlbumPreferences, Modifier.weight(1f)) {
                                    draftAlbumPreferences = draftAlbumPreferences.copy(sortOption = it)
                                }
                                SortChip(FileSortOption.SIZE_SMALLEST, draftAlbumPreferences, Modifier.weight(1f)) {
                                    draftAlbumPreferences = draftAlbumPreferences.copy(sortOption = it)
                                }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                SortChip(FileSortOption.DATE_NEWEST, draftAlbumPreferences, Modifier.weight(1f)) {
                                    draftAlbumPreferences = draftAlbumPreferences.copy(sortOption = it)
                                }
                                SortChip(FileSortOption.DATE_OLDEST, draftAlbumPreferences, Modifier.weight(1f)) {
                                    draftAlbumPreferences = draftAlbumPreferences.copy(sortOption = it)
                                }
                            }
                        }
                    }
                }

                // 7. Action buttons (Apply/Cancel)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.cancel))
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    FilledTonalButton(
                        onClick = {
                            if (currentTab == GalleryTab.PHOTOS) {
                                onPhotosPresentationChange(draftPhotosPreferences.normalized())
                                onPhotosAspectRatioChange(draftPhotosAspectRatio)
                                onGroupingChange(draftGrouping)
                                onShowFileDetailsChange(draftShowDetails)
                            } else {
                                onAlbumPresentationChange(draftAlbumPreferences.normalized())
                                onAlbumAspectRatioChange(draftAlbumAspectRatio)
                            }
                            onDismiss()
                        }
                    ) {
                        Text(stringResource(R.string.apply))
                    }
                }
            }
        }
    }
}

@Composable
private fun SortChip(
    option: FileSortOption,
    preferences: BrowserPresentationPreferences,
    modifier: Modifier = Modifier,
    onSelect: (FileSortOption) -> Unit
) {
    FilterChip(
        selected = preferences.sortOption == option,
        onClick = { onSelect(option) },
        label = {
            Text(
                text = stringResource(
                    when (option) {
                        FileSortOption.NAME_ASC -> R.string.sort_name_asc
                        FileSortOption.NAME_DESC -> R.string.sort_name_desc
                        FileSortOption.DATE_NEWEST -> R.string.sort_date_newest
                        FileSortOption.DATE_OLDEST -> R.string.sort_date_oldest
                        FileSortOption.SIZE_LARGEST -> R.string.sort_size_largest
                        FileSortOption.SIZE_SMALLEST -> R.string.sort_size_smallest
                    }
                ),
                modifier = Modifier.fillMaxWidth(),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        },
        modifier = modifier
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ImageGalleryContent(
    state: ImageGalleryState,
    gridMinCellSize: Float,
    onPhotosGridCellSizeChange: (Float) -> Unit,
    onPhotosGridCellSizeFinalized: (Float) -> Unit,
    contentPadding: PaddingValues,
    onOpenFile: (String) -> Unit,
    onToggleSelection: (String) -> Unit,
    onSelectMultiple: (List<String>) -> Unit,
    onSelectAlbum: (String?) -> Unit,
    onRefresh: () -> Unit
) {
    val pullRefreshState = androidx.compose.material3.pulltorefresh.rememberPullToRefreshState()
    val gridStatesByAlbum = remember { mutableMapOf<String, LazyGridState>() }
    val listStatesByAlbum = remember { mutableMapOf<String, LazyListState>() }
    val staggeredGridStatesByAlbum = remember { mutableMapOf<String, LazyStaggeredGridState>() }
    val albumScrollKey = state.selectedAlbumPath ?: "__all__"

    val bottomPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 96.dp

    val groupedFiles = remember(state.displayedFiles, state.imageGalleryGrouping) {
        if (state.imageGalleryGrouping != ImageGalleryGrouping.NONE) {
            val dayFormatter = java.text.SimpleDateFormat("MMMM d, yyyy", java.util.Locale.getDefault())
            val monthFormatter = java.text.SimpleDateFormat("MMMM yyyy", java.util.Locale.getDefault())

            state.displayedFiles.groupBy { file ->
                val lastMod = file.lastModified
                val label = when (state.imageGalleryGrouping) {
                    ImageGalleryGrouping.DAY -> dayFormatter.format(java.util.Date(lastMod))
                    ImageGalleryGrouping.WEEK -> getWeekLabel(lastMod)
                    ImageGalleryGrouping.MONTH -> monthFormatter.format(java.util.Date(lastMod))
                    ImageGalleryGrouping.NONE -> ""
                }

                val cal = java.util.Calendar.getInstance()
                cal.timeInMillis = lastMod
                when (state.imageGalleryGrouping) {
                    ImageGalleryGrouping.DAY -> {
                        cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
                        cal.set(java.util.Calendar.MINUTE, 0)
                        cal.set(java.util.Calendar.SECOND, 0)
                        cal.set(java.util.Calendar.MILLISECOND, 0)
                    }
                    ImageGalleryGrouping.WEEK -> {
                        cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
                        cal.set(java.util.Calendar.MINUTE, 0)
                        cal.set(java.util.Calendar.SECOND, 0)
                        cal.set(java.util.Calendar.MILLISECOND, 0)
                        val firstDayOfWeek = cal.firstDayOfWeek
                        while (cal.get(java.util.Calendar.DAY_OF_WEEK) != firstDayOfWeek) {
                            cal.add(java.util.Calendar.DAY_OF_MONTH, -1)
                        }
                    }
                    ImageGalleryGrouping.MONTH -> {
                        cal.set(java.util.Calendar.DAY_OF_MONTH, 1)
                        cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
                        cal.set(java.util.Calendar.MINUTE, 0)
                        cal.set(java.util.Calendar.SECOND, 0)
                        cal.set(java.util.Calendar.MILLISECOND, 0)
                    }
                    else -> {}
                }
                GroupKey(label, cal.timeInMillis)
            }.toSortedMap()
        } else {
            emptyMap()
        }
    }

    val flatUiFiles = remember(state.displayedFiles, state.imageGalleryGrouping, groupedFiles) {
        if (state.imageGalleryGrouping != ImageGalleryGrouping.NONE) {
            groupedFiles.values.flatten()
        } else {
            state.displayedFiles
        }
    }

    var lastInteractedIndex by rememberSaveable { mutableStateOf<Int?>(null) }
    LaunchedEffect(state.selectedFiles.isEmpty()) {
        if (state.selectedFiles.isEmpty()) {
            lastInteractedIndex = null
        }
    }

    val haptics = rememberArcileHaptics()

    val onClickItem = remember(flatUiFiles, state.selectedFiles, onOpenFile, onToggleSelection) {
        { file: FileModel ->
            val index = flatUiFiles.indexOf(file)
            if (state.selectedFiles.isNotEmpty()) {
                lastInteractedIndex = index
                onToggleSelection(file.absolutePath)
                haptics.selectionChanged()
            } else {
                onOpenFile(file.absolutePath)
            }
        }
    }

    val onLongClickItem = remember(flatUiFiles, state.selectedFiles, onToggleSelection, onSelectMultiple) {
        { file: FileModel ->
            val index = flatUiFiles.indexOf(file)
            if (state.selectedFiles.isNotEmpty() && lastInteractedIndex != null && lastInteractedIndex != index) {
                val start = minOf(lastInteractedIndex!!, index)
                val end = maxOf(lastInteractedIndex!!, index)
                val rangePaths = flatUiFiles.subList(start, end + 1).map { it.absolutePath }
                onSelectMultiple(rangePaths)
                haptics.selectionChanged()
            } else {
                val wasEmpty = state.selectedFiles.isEmpty()
                onToggleSelection(file.absolutePath)
                if (wasEmpty) haptics.selectionStart() else haptics.selectionChanged()
            }
            lastInteractedIndex = index
        }
    }

    androidx.compose.material3.pulltorefresh.PullToRefreshBox(
        isRefreshing = state.isRefreshing,
        onRefresh = onRefresh,
        state = pullRefreshState,
        modifier = Modifier.fillMaxSize(),
        indicator = {
            ArcilePullRefreshIndicator(
                isRefreshing = state.isRefreshing,
                state = pullRefreshState
            )
        }
    ) {
        when {
            state.isLoading && state.files.isEmpty() -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    LoadingIndicator()
                }
            }
            state.files.isEmpty() && state.searchQuery.isBlank() -> {
                EmptyState(
                    variant = EmptyStateVariant.Search,
                    title = stringResource(R.string.image_gallery_empty_title),
                    description = stringResource(R.string.image_gallery_empty_description),
                    modifier = Modifier.fillMaxSize()
                )
            }
            state.displayedFiles.isEmpty() -> {
                EmptyState(
                    variant = EmptyStateVariant.Search,
                    title = stringResource(R.string.no_results_found),
                    description = stringResource(R.string.no_results_description, state.searchQuery),
                    modifier = Modifier.fillMaxSize()
                )
            }
            else -> {
                val scrollbarState = when {
                    state.presentation.viewMode == BrowserViewMode.GRID && state.isAspectRatio -> {
                        val staggeredGridState = remember(albumScrollKey) {
                            staggeredGridStatesByAlbum.getOrPut(albumScrollKey) { LazyStaggeredGridState() }
                        }
                        LazyStaggeredGridScrollbarState(staggeredGridState)
                    }
                    state.presentation.viewMode == BrowserViewMode.GRID -> {
                        val gridState = remember(albumScrollKey) {
                            gridStatesByAlbum.getOrPut(albumScrollKey) { LazyGridState() }
                        }
                        LazyGridScrollbarState(gridState)
                    }
                    else -> {
                        val listState = remember(albumScrollKey) {
                            listStatesByAlbum.getOrPut(albumScrollKey) { LazyListState() }
                        }
                        LazyListScrollbarState(listState)
                    }
                }

                Box(modifier = Modifier.fillMaxSize()) {
                    if (state.presentation.viewMode == BrowserViewMode.GRID) {
                        if (state.isAspectRatio) {
                            val staggeredGridState = (scrollbarState as LazyStaggeredGridScrollbarState).state
                            LazyVerticalStaggeredGrid(
                                columns = StaggeredGridCells.Adaptive(minSize = gridMinCellSize.dp),
                                state = staggeredGridState,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .pinchToResize(
                                        currentCellSize = gridMinCellSize,
                                        onSizeChanged = onPhotosGridCellSizeChange,
                                        onSizeFinalized = onPhotosGridCellSizeFinalized
                                    ),
                                contentPadding = PaddingValues(
                                    start = 12.dp,
                                    top = contentPadding.calculateTopPadding() + 8.dp,
                                    end = 12.dp,
                                    bottom = bottomPadding
                                ),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalItemSpacing = 8.dp
                            ) {
                                if (state.imageGalleryGrouping != ImageGalleryGrouping.NONE) {
                                    groupedFiles.forEach { (section, filesInSection) ->
                                        if (filesInSection.isNotEmpty()) {
                                            item(span = StaggeredGridItemSpan.FullLine) {
                                                GallerySectionHeader(section.label)
                                            }
                                            items(filesInSection, key = { it.absolutePath }) { file ->
                                                GalleryImageItem(
                                                    file = file,
                                                    isSelected = file.absolutePath in state.selectedFiles,
                                                    aspectRatio = state.aspectRatios[file.absolutePath] ?: 1f,
                                                    showDetails = state.showFileDetails,
                                                    onClick = { onClickItem(file) },
                                                    onLongClick = { onLongClickItem(file) },
                                                    modifier = Modifier.animateItem()
                                                )
                                            }
                                        }
                                    }
                                } else {
                                    items(state.displayedFiles, key = { it.absolutePath }) { file ->
                                        GalleryImageItem(
                                            file = file,
                                            isSelected = file.absolutePath in state.selectedFiles,
                                            aspectRatio = state.aspectRatios[file.absolutePath] ?: 1f,
                                            showDetails = state.showFileDetails,
                                            onClick = { onClickItem(file) },
                                            onLongClick = { onLongClickItem(file) },
                                            modifier = Modifier.animateItem()
                                        )
                                    }
                                }
                            }
                        } else {
                            val gridState = (scrollbarState as LazyGridScrollbarState).state
                            LazyVerticalGrid(
                                columns = GridCells.Adaptive(minSize = gridMinCellSize.dp),
                                state = gridState,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .pinchToResize(
                                        currentCellSize = gridMinCellSize,
                                        onSizeChanged = onPhotosGridCellSizeChange,
                                        onSizeFinalized = onPhotosGridCellSizeFinalized
                                    ),
                                contentPadding = PaddingValues(
                                    start = 12.dp,
                                    top = contentPadding.calculateTopPadding() + 8.dp,
                                    end = 12.dp,
                                    bottom = bottomPadding
                                ),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                if (state.imageGalleryGrouping != ImageGalleryGrouping.NONE) {
                                    groupedFiles.forEach { (section, filesInSection) ->
                                        if (filesInSection.isNotEmpty()) {
                                            item(span = { GridItemSpan(maxLineSpan) }) {
                                                GallerySectionHeader(section.label)
                                            }
                                            items(filesInSection, key = { it.absolutePath }) { file ->
                                                GalleryImageItem(
                                                    file = file,
                                                    isSelected = file.absolutePath in state.selectedFiles,
                                                    aspectRatio = 1f,
                                                    showDetails = state.showFileDetails,
                                                    onClick = { onClickItem(file) },
                                                    onLongClick = { onLongClickItem(file) },
                                                    modifier = Modifier.animateItem()
                                                )
                                            }
                                        }
                                    }
                                } else {
                                    items(state.displayedFiles, key = { it.absolutePath }) { file ->
                                        GalleryImageItem(
                                            file = file,
                                            isSelected = file.absolutePath in state.selectedFiles,
                                            aspectRatio = 1f,
                                            showDetails = state.showFileDetails,
                                            onClick = { onClickItem(file) },
                                            onLongClick = { onLongClickItem(file) },
                                            modifier = Modifier.animateItem()
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        val listState = (scrollbarState as LazyListScrollbarState).state
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(
                                top = contentPadding.calculateTopPadding(),
                                bottom = bottomPadding
                            )
                        ) {
                            if (state.imageGalleryGrouping != ImageGalleryGrouping.NONE) {
                                groupedFiles.forEach { (section, filesInSection) ->
                                    if (filesInSection.isNotEmpty()) {
                                        item {
                                            GallerySectionHeader(
                                                title = section.label,
                                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                            )
                                        }
                                        items(filesInSection, key = { it.absolutePath }) { file ->
                                            GalleryImageListItem(
                                                file = file,
                                                isSelected = file.absolutePath in state.selectedFiles,
                                                zoom = state.presentation.listZoom,
                                                onClick = { onClickItem(file) },
                                                onLongClick = { onLongClickItem(file) },
                                                modifier = Modifier.animateItem()
                                            )
                                        }
                                    }
                                }
                            } else {
                                items(state.displayedFiles, key = { it.absolutePath }) { file ->
                                    GalleryImageListItem(
                                        file = file,
                                        isSelected = file.absolutePath in state.selectedFiles,
                                        zoom = state.presentation.listZoom,
                                        onClick = { onClickItem(file) },
                                        onLongClick = { onLongClickItem(file) },
                                        modifier = Modifier.animateItem()
                                    )
                                }
                            }
                        }
                    }

                    FastScrollbar(
                        scrollbarState = scrollbarState,
                        displayedFiles = state.displayedFiles,
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .fillMaxHeight(),
                        contentPadding = PaddingValues(
                            top = contentPadding.calculateTopPadding() + 8.dp,
                            bottom = bottomPadding + 16.dp
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun ImageGalleryAlbumChips(
    state: ImageGalleryState,
    onSelectAlbum: (String?) -> Unit
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            FilterChip(
                selected = state.selectedAlbumPath == null,
                onClick = { onSelectAlbum(null) },
                label = { Text(stringResource(R.string.image_gallery_all_album, state.files.size)) },
                leadingIcon = {
                    Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(18.dp))
                }
            )
        }
        items(state.albums, key = { it.path ?: it.label }) { album ->
            FilterChip(
                selected = state.selectedAlbumPath == album.path,
                onClick = { onSelectAlbum(album.path) },
                label = {
                    Text(
                        text = "${album.label} (${album.count})",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GalleryImageItem(
    file: FileModel,
    isSelected: Boolean,
    aspectRatio: Float,
    showDetails: Boolean,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val haptics = rememberArcileHaptics()
    val formatter = rememberDateTimeFormatter()
    val thumbnailPolicy = remember { ThumbnailPolicy() }
    val thumbnailKey = remember(file) { ThumbnailKey.from(file) }

    val scale by animateFloatAsState(
        targetValue = if (isSelected) 0.92f else 1.0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "scale"
    )

    if (showDetails) {
        Card(
            shape = RoundedCornerShape(8.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer
            ),
            modifier = modifier
                .fillMaxWidth()
                .graphicsLayer(scaleX = scale, scaleY = scale)
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick
                )
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(aspectRatio)
                ) {
                    GalleryThumbnail(
                        file = file,
                        thumbnailKey = thumbnailKey,
                        thumbnailPolicy = thumbnailPolicy,
                        modifier = Modifier.fillMaxSize()
                    )

                    if (isSelected) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
                        )
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier
                                .padding(8.dp)
                                .size(24.dp)
                                .align(Alignment.TopEnd)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = file.name,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = formatFileSize(file.size),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = formatter.format(file.lastModified),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    } else {
        Box(
            modifier = modifier
                .aspectRatio(aspectRatio)
                .graphicsLayer(scaleX = scale, scaleY = scale)
                .clip(RoundedCornerShape(8.dp))
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick
                )
        ) {
            GalleryThumbnail(
                file = file,
                thumbnailKey = thumbnailKey,
                thumbnailPolicy = thumbnailPolicy,
                modifier = Modifier.fillMaxSize()
            )

            if (isSelected) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
                )
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier
                        .padding(8.dp)
                        .size(24.dp)
                        .align(Alignment.TopEnd)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GalleryThumbnail(
    file: FileModel,
    thumbnailKey: ThumbnailKey,
    thumbnailPolicy: ThumbnailPolicy,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    BoxWithConstraints(
        modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        val requestSizePx = remember(maxWidth, maxHeight, density) {
            with(density) {
                ThumbnailTargetSize.fromBounds(
                    widthPx = maxWidth.roundToPx(),
                    heightPx = maxHeight.roundToPx(),
                    maxPx = GALLERY_MAX_THUMBNAIL_PX
                )
            }
        }
        val archiveThumbnailData = remember(file.absolutePath, file.size, file.lastModified) {
            ArchiveEntryThumbnailData.fromVirtualPath(
                path = file.absolutePath,
                sizeBytes = file.size,
                lastModifiedMillis = file.lastModified
            )
        }
        val cacheKey = remember(archiveThumbnailData, thumbnailKey, requestSizePx) {
            archiveThumbnailData?.cacheKey ?: thumbnailKey.variantKey(requestSizePx).cacheKey
        }
        val requestData = remember(file, archiveThumbnailData) {
            galleryThumbnailRequestDataFor(file, archiveThumbnailData)
        }
        val request = remember(context, requestData, cacheKey, requestSizePx) {
            ImageRequest.Builder(context)
                .data(requestData)
                .size(requestSizePx)
                .precision(Precision.INEXACT)
                .memoryCacheKey(cacheKey)
                .diskCacheKey(cacheKey)
                .diskCachePolicy(CachePolicy.ENABLED)
                .memoryCachePolicy(CachePolicy.ENABLED)
                .networkCachePolicy(CachePolicy.DISABLED)
                .build()
        }
        Icon(
            imageVector = Icons.Default.Image,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        )
        AsyncImage(
            model = request,
            onLoading = { thumbnailPolicy.recordInFlight(thumbnailKey, requestSizePx) },
            onSuccess = {
                thumbnailPolicy.clearFailure(thumbnailKey)
                thumbnailPolicy.recordLoaded(thumbnailKey, requestSizePx)
            },
            onError = { thumbnailPolicy.recordFailure(thumbnailKey, requestSizePx) },
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GalleryImageListItem(
    file: FileModel,
    isSelected: Boolean,
    zoom: Float,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val haptics = rememberArcileHaptics()
    val thumbnailPolicy = remember { ThumbnailPolicy() }
    val thumbnailKey = remember(file) { ThumbnailKey.from(file) }
    val thumbnailSizePx = ThumbnailTargetSize.fromBounds((48 * zoom).roundToInt())

    val scale by animateFloatAsState(
        targetValue = if (isSelected) 0.98f else 1.0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "scale"
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer(scaleX = scale, scaleY = scale)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else Color.Transparent)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size((48 * zoom).dp)
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            val archiveThumbnailData = remember(file.absolutePath, file.size, file.lastModified) {
                ArchiveEntryThumbnailData.fromVirtualPath(
                    path = file.absolutePath,
                    sizeBytes = file.size,
                    lastModifiedMillis = file.lastModified
                )
            }
            val cacheKey = remember(archiveThumbnailData, thumbnailKey, thumbnailSizePx) {
                archiveThumbnailData?.cacheKey ?: thumbnailKey.variantKey(thumbnailSizePx).cacheKey
            }
            val requestData = remember(file, archiveThumbnailData) {
                galleryThumbnailRequestDataFor(file, archiveThumbnailData)
            }
            val request = remember(context, requestData, cacheKey, thumbnailSizePx) {
                ImageRequest.Builder(context)
                    .data(requestData)
                    .size(thumbnailSizePx)
                    .precision(Precision.INEXACT)
                    .memoryCacheKey(cacheKey)
                    .diskCacheKey(cacheKey)
                    .diskCachePolicy(CachePolicy.ENABLED)
                    .memoryCachePolicy(CachePolicy.ENABLED)
                    .networkCachePolicy(CachePolicy.DISABLED)
                    .build()
            }
            Icon(
                imageVector = Icons.Default.Image,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(18.dp)
            )
            AsyncImage(
                model = request,
                onLoading = { thumbnailPolicy.recordInFlight(thumbnailKey, thumbnailSizePx) },
                onSuccess = {
                    thumbnailPolicy.clearFailure(thumbnailKey)
                    thumbnailPolicy.recordLoaded(thumbnailKey, thumbnailSizePx)
                },
                onError = { thumbnailPolicy.recordFailure(thumbnailKey, thumbnailSizePx) },
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            if (isSelected) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
                )
            }
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = file.name,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = formatFileSize(file.size),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (isSelected) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@Composable
fun GallerySectionHeader(
    title: String,
    modifier: Modifier = Modifier
) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    )
}

enum class TimeSection {
    TODAY, WEEK, MONTH, OLDER
}

@Composable
fun TimeSection.toDisplayString(): String = when (this) {
    TimeSection.TODAY -> stringResource(R.string.image_gallery_section_today)
    TimeSection.WEEK -> stringResource(R.string.image_gallery_section_week)
    TimeSection.MONTH -> stringResource(R.string.image_gallery_section_month)
    TimeSection.OLDER -> stringResource(R.string.image_gallery_section_older)
}

fun getTimeSection(lastModified: Long, now: Long = System.currentTimeMillis()): TimeSection {
    val diff = now - lastModified
    val oneDay = 24 * 60 * 60 * 1000L
    val oneWeek = 7 * oneDay
    val oneMonth = 30 * oneDay
    return when {
        diff < oneDay -> TimeSection.TODAY
        diff < oneWeek -> TimeSection.WEEK
        diff < oneMonth -> TimeSection.MONTH
        else -> TimeSection.OLDER
    }
}

data class GroupKey(val label: String, val timestamp: Long) : Comparable<GroupKey> {
    override fun compareTo(other: GroupKey): Int {
        return other.timestamp.compareTo(this.timestamp)
    }
}

fun getWeekLabel(timestamp: Long): String {
    val cal = java.util.Calendar.getInstance()
    cal.timeInMillis = timestamp
    cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
    cal.set(java.util.Calendar.MINUTE, 0)
    cal.set(java.util.Calendar.SECOND, 0)
    cal.set(java.util.Calendar.MILLISECOND, 0)
    
    val firstDayOfWeek = cal.firstDayOfWeek
    while (cal.get(java.util.Calendar.DAY_OF_WEEK) != firstDayOfWeek) {
        cal.add(java.util.Calendar.DAY_OF_MONTH, -1)
    }
    val startDate = cal.time
    
    cal.add(java.util.Calendar.DAY_OF_MONTH, 6)
    val endDate = cal.time
    
    val weekFormatter = java.text.SimpleDateFormat("MMM d", java.util.Locale.getDefault())
    val yearFormatter = java.text.SimpleDateFormat("yyyy", java.util.Locale.getDefault())
    
    return "${weekFormatter.format(startDate)} - ${weekFormatter.format(endDate)}, ${yearFormatter.format(startDate)}"
}

private const val GALLERY_MAX_THUMBNAIL_PX = 512

enum class GalleryTab {
    PHOTOS, ALBUMS
}

@Composable
fun TabItem(
    selected: Boolean,
    label: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    val backgroundColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
        label = "tabBg"
    )
    val contentColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
        label = "tabContent"
    )
    val horizontalPadding by animateDpAsState(
        targetValue = if (selected) 16.dp else 12.dp,
        label = "tabPadding"
    )

    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = backgroundColor,
        contentColor = contentColor,
        modifier = Modifier.height(44.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = horizontalPadding)
        ) {
            Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(20.dp))
            AnimatedVisibility(
                visible = selected,
                enter = expandHorizontally() + fadeIn(),
                exit = shrinkHorizontally() + fadeOut()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
private fun ImageGalleryAlbumsGrid(
    state: ImageGalleryState,
    gridMinCellSize: Float,
    onAlbumsGridCellSizeChange: (Float) -> Unit,
    onAlbumsGridCellSizeFinalized: (Float) -> Unit,
    contentPadding: PaddingValues,
    onSelectAlbum: (String?) -> Unit,
    modifier: Modifier = Modifier
) {
    val thumbnailPolicy = remember { ThumbnailPolicy() }
    val sortedAlbums = remember(state.albums, state.albumPresentation.sortOption) {
        when (state.albumPresentation.sortOption) {
            FileSortOption.NAME_ASC -> state.albums.sortedBy { it.label.lowercase() }
            FileSortOption.NAME_DESC -> state.albums.sortedByDescending { it.label.lowercase() }
            FileSortOption.SIZE_LARGEST -> state.albums.sortedByDescending { it.count }
            FileSortOption.SIZE_SMALLEST -> state.albums.sortedBy { it.count }
            FileSortOption.DATE_NEWEST -> state.albums.sortedByDescending { it.lastModified }
            FileSortOption.DATE_OLDEST -> state.albums.sortedBy { it.lastModified }
        }
    }

    val favoritesLabel = stringResource(R.string.image_gallery_favorites_folder)
    val albumsList = remember(sortedAlbums, state.files, state.favoriteFiles, favoritesLabel) {
        buildVisibleAlbumTiles(
            sortedAlbums = sortedAlbums,
            files = state.files,
            favoriteFiles = state.favoriteFiles,
            favoritesLabel = favoritesLabel
        )
    }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = gridMinCellSize.dp),
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = modifier
            .fillMaxSize()
            .pinchToResize(
                currentCellSize = gridMinCellSize,
                onSizeChanged = onAlbumsGridCellSizeChange,
                onSizeFinalized = onAlbumsGridCellSizeFinalized
            )
            .padding(horizontal = 16.dp)
    ) {
        items(albumsList, key = { it.path ?: it.label }) { album ->
            val coverFile = remember(album.path, state.files, state.favoriteFiles, state.albumCovers) {
                resolveAlbumCoverFile(
                    albumPath = album.path,
                    files = state.files,
                    favoriteFiles = state.favoriteFiles,
                    albumCovers = state.albumCovers
                )
            }
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                ),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelectAlbum(album.path) }
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    val coverRatio = if (state.albumAspectRatio && coverFile != null) {
                        state.aspectRatios[coverFile.absolutePath] ?: 1f
                    } else {
                        1f
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(coverRatio)
                            .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        if (coverFile != null) {
                            GalleryThumbnail(
                                file = coverFile,
                                thumbnailKey = ThumbnailKey.from(coverFile),
                                thumbnailPolicy = thumbnailPolicy,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Folder,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                    modifier = Modifier.size(48.dp)
                                )
                            }
                        }

                        if (album.path == "__favorites__") {
                            Box(
                                modifier = Modifier
                                    .padding(8.dp)
                                    .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                                    .size(32.dp)
                                    .align(Alignment.TopStart),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Favorite,
                                    contentDescription = null,
                                    tint = Color.Red,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp)
                    ) {
                        Text(
                            text = album.label,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = stringResource(R.string.image_gallery_album_count, album.count),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

interface ScrollbarState {
    val firstVisibleItemIndex: Int
    val totalItemsCount: Int
    val firstVisibleItemScrollOffset: Int
    suspend fun scrollToItem(index: Int)
    suspend fun scrollBy(value: Float): Float
}

class LazyListScrollbarState(val state: LazyListState) : ScrollbarState {
    override val firstVisibleItemIndex: Int get() = state.firstVisibleItemIndex
    override val totalItemsCount: Int get() = state.layoutInfo.totalItemsCount
    override val firstVisibleItemScrollOffset: Int get() = state.firstVisibleItemScrollOffset
    override suspend fun scrollToItem(index: Int) = state.scrollToItem(index)
    override suspend fun scrollBy(value: Float): Float = state.scrollBy(value)
}

class LazyGridScrollbarState(val state: LazyGridState) : ScrollbarState {
    override val firstVisibleItemIndex: Int get() = state.firstVisibleItemIndex
    override val totalItemsCount: Int get() = state.layoutInfo.totalItemsCount
    override val firstVisibleItemScrollOffset: Int get() = state.firstVisibleItemScrollOffset
    override suspend fun scrollToItem(index: Int) = state.scrollToItem(index)
    override suspend fun scrollBy(value: Float): Float = state.scrollBy(value)
}

class LazyStaggeredGridScrollbarState(val state: LazyStaggeredGridState) : ScrollbarState {
    override val firstVisibleItemIndex: Int get() = state.firstVisibleItemIndex
    override val totalItemsCount: Int get() = state.layoutInfo.totalItemsCount
    override val firstVisibleItemScrollOffset: Int get() = state.firstVisibleItemScrollOffset
    override suspend fun scrollToItem(index: Int) = state.scrollToItem(index)
    override suspend fun scrollBy(value: Float): Float = state.scrollBy(value)
}

@Composable
fun FastScrollbar(
    scrollbarState: ScrollbarState,
    displayedFiles: List<FileModel>,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues()
) {
    val totalItems = scrollbarState.totalItemsCount
    if (totalItems <= 1) return

    val firstVisibleIndex = scrollbarState.firstVisibleItemIndex
    val firstVisibleOffset = scrollbarState.firstVisibleItemScrollOffset

    var isDragging by remember { mutableStateOf(false) }
    var dragPositionFraction by remember { mutableStateOf(0f) }

    val coroutineScope = rememberCoroutineScope()

    val scrollFraction = remember(firstVisibleIndex, firstVisibleOffset, totalItems) {
        if (totalItems > 1) {
            firstVisibleIndex.toFloat() / (totalItems - 1).toFloat()
        } else {
            0f
        }
    }

    val activeFraction = if (isDragging) dragPositionFraction else scrollFraction

    val targetIndex = (activeFraction * (displayedFiles.size - 1)).toInt().coerceIn(0, displayedFiles.size - 1)
    val targetFile = displayedFiles.getOrNull(targetIndex)
    val formatter = remember { java.text.SimpleDateFormat("MMMM yyyy", java.util.Locale.getDefault()) }
    val dateText = remember(targetFile) {
        if (targetFile != null) {
            formatter.format(java.util.Date(targetFile.lastModified))
        } else {
            ""
        }
    }

    val thumbWidth by animateDpAsState(
        targetValue = if (isDragging) 10.dp else 6.dp,
        label = "scrollbarThumbWidth"
    )
    val thumbColor by animateColorAsState(
        targetValue = if (isDragging) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
        label = "scrollbarThumbColor"
    )

    val tooltipAlpha by animateFloatAsState(
        targetValue = if (isDragging && dateText.isNotEmpty()) 1f else 0f,
        label = "scrollbarTooltipAlpha"
    )

    BoxWithConstraints(
        modifier = modifier
            .width(64.dp)
            .padding(contentPadding)
    ) {
        val trackHeight = maxHeight
        val density = LocalDensity.current

        val dragModifier = Modifier.pointerInput(totalItems) {
            detectDragGestures(
                onDragStart = { offset ->
                    isDragging = true
                    val y = offset.y.coerceIn(0f, size.height.toFloat())
                    dragPositionFraction = y / size.height.toFloat()
                    val targetIdx = (dragPositionFraction * (totalItems - 1)).toInt().coerceIn(0, totalItems - 1)
                    coroutineScope.launch {
                        scrollbarState.scrollToItem(targetIdx)
                    }
                },
                onDragEnd = {
                    isDragging = false
                },
                onDragCancel = {
                    isDragging = false
                },
                onDrag = { change, dragAmount ->
                    change.consume()
                    val currentY = change.position.y.coerceIn(0f, size.height.toFloat())
                    dragPositionFraction = currentY / size.height.toFloat()
                    val targetIdx = (dragPositionFraction * (totalItems - 1)).toInt().coerceIn(0, totalItems - 1)
                    coroutineScope.launch {
                        scrollbarState.scrollToItem(targetIdx)
                    }
                }
            )
        }.pointerInput(totalItems) {
            detectTapGestures { offset ->
                val y = offset.y.coerceIn(0f, size.height.toFloat())
                val tapFraction = y / size.height.toFloat()
                val targetIdx = (tapFraction * (totalItems - 1)).toInt().coerceIn(0, totalItems - 1)
                coroutineScope.launch {
                    scrollbarState.scrollToItem(targetIdx)
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(48.dp)
                .align(Alignment.CenterEnd)
                .then(dragModifier)
        ) {
            if (isDragging) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(4.dp)
                        .align(Alignment.Center)
                        .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.1f), CircleShape)
                )
            }

            val thumbHeight = 48.dp
            val maxOffset = trackHeight - thumbHeight
            val thumbOffset = maxOffset * activeFraction

            Box(
                modifier = Modifier
                    .offset(y = thumbOffset)
                    .width(thumbWidth)
                    .height(thumbHeight)
                    .align(Alignment.TopCenter)
                    .background(thumbColor, CircleShape)
            )

            if (tooltipAlpha > 0f) {
                val tooltipOffset = thumbOffset + (thumbHeight / 2) - 20.dp
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    ),
                    shape = RoundedCornerShape(12.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                    modifier = Modifier
                        .offset(
                            x = (-100).dp,
                            y = tooltipOffset
                        )
                        .graphicsLayer {
                            alpha = tooltipAlpha
                            scaleX = tooltipAlpha
                            scaleY = tooltipAlpha
                        }
                        .align(Alignment.TopCenter)
                ) {
                    Text(
                        text = dateText,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }
        }
    }
}

enum class GalleryViewState {
    PHOTOS_TAB, ALBUMS_TAB_GRID, ALBUM_PHOTOS
}

fun Modifier.pinchToResize(
    currentCellSize: Float,
    minSize: Float = 96f,
    maxSize: Float = 256f,
    onSizeChanged: (Float) -> Unit,
    onSizeFinalized: (Float) -> Unit
): Modifier = this.pointerInput(currentCellSize) {
    var accumulatedScale = 1f
    var startCellSize = currentCellSize
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false)
        accumulatedScale = 1f
        startCellSize = currentCellSize
        var isPinching = false

        do {
            val event = awaitPointerEvent()
            val zoom = event.calculateZoom()
            if (event.changes.size >= 2) {
                isPinching = true
                accumulatedScale *= zoom
                val nextSize = (startCellSize * accumulatedScale).coerceIn(minSize, maxSize)
                onSizeChanged(nextSize)
                event.changes.forEach { it.consume() }
            }
        } while (event.changes.any { it.pressed })

        if (isPinching) {
            val finalSize = (startCellSize * accumulatedScale).coerceIn(minSize, maxSize)
            onSizeFinalized(finalSize)
        }
    }
}
