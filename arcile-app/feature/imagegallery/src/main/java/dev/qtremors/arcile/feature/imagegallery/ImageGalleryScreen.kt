@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package dev.qtremors.arcile.feature.imagegallery

import androidx.activity.compose.BackHandler
import dev.qtremors.arcile.core.storage.domain.FileModel
import androidx.compose.foundation.layout.aspectRatio
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
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
import androidx.compose.foundation.shape.CircleShape
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
    onAspectRatioChange: (Boolean) -> Unit = {},
    onSectionedChange: (Boolean) -> Unit = {},
    onFeedback: (ArcileFeedbackEvent) -> Unit = {},
    nativeRequestFlow: SharedFlow<android.content.IntentSender>? = null
) {
    val haptics = rememberArcileHaptics()
    val isSelectionMode = state.selectedFiles.isNotEmpty()
    var showSearchBar by rememberSaveable { mutableStateOf(state.searchQuery.isNotEmpty()) }
    var showPresentationSheet by rememberSaveable { mutableStateOf(false) }
    var showRenameDialog by rememberSaveable { mutableStateOf(false) }

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

    BackHandler(enabled = isSelectionMode || showSearchBar) {
        if (isSelectionMode) {
            onClearSelection()
        } else {
            showSearchBar = false
            onClearSearch()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        val topPadding = if (isSelectionMode || showSearchBar) 88.dp else 144.dp

        ImageGalleryContent(
            state = state,
            contentPadding = PaddingValues(top = topPadding),
            onOpenFile = onOpenFile,
            onToggleSelection = onToggleSelection,
            onSelectMultiple = onSelectMultiple,
            onSelectAlbum = onSelectAlbum,
            onRefresh = onRefresh
        )

        // Floating pill top bar & chips
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
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
                    onSearchClick = { showSearchBar = true },
                    onSortClick = { showPresentationSheet = true },
                    onNavigateBack = onNavigateBack,
                    onClearSearch = {
                        showSearchBar = false
                        onClearSearch()
                    },
                    onSearchQueryChange = onSearchQueryChange,
                    onShowFileDetailsChange = onShowFileDetailsChange,
                    onSelectAll = onSelectAll,
                    modifier = Modifier.fillMaxWidth()
                )
                if (!showSearchBar) {
                    ImageGalleryAlbumChips(
                        state = state,
                        onSelectAlbum = onSelectAlbum
                    )
                }
            }
        }

        val copyDesc = stringResource(R.string.action_copy)
        val cutDesc = stringResource(R.string.action_cut)
        val deleteDesc = stringResource(R.string.action_delete_selected)
        val renameDesc = stringResource(R.string.action_rename)

        val errorColor = MaterialTheme.colorScheme.error

        // Floating Bottom Selection Bar (M3 Expressive)
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

        FloatingSelectionToolbar(
            isVisible = isSelectionMode,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(),
            actions = mainActions,
            moreContent = {
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
                        val menuActions = remember {
                            listOf<@Composable () -> Unit>(
                                {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.archive_compress_zip)) },
                                        leadingIcon = { Icon(Icons.Default.FolderZip, contentDescription = null) },
                                        onClick = {
                                            showSelectionMenu = false
                                            onCreateZipFromSelection()
                                        }
                                    )
                                },
                                {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.share)) },
                                        leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) },
                                        onClick = {
                                            showSelectionMenu = false
                                            onShareSelected()
                                        }
                                    )
                                },
                                {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.properties_title)) },
                                        leadingIcon = { Icon(Icons.Default.Info, contentDescription = null) },
                                        onClick = {
                                            showSelectionMenu = false
                                            onOpenProperties()
                                        }
                                    )
                                }
                            )
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
        )
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
            presentation = state.presentation,
            isAspectRatio = state.isAspectRatio,
            isSectioned = state.isSectioned,
            showFileDetails = state.showFileDetails,
            onPresentationChange = onPresentationChange,
            onAspectRatioChange = onAspectRatioChange,
            onSectionedChange = onSectionedChange,
            onShowFileDetailsChange = onShowFileDetailsChange,
            onDismiss = { showPresentationSheet = false }
        )
    }
}

@Composable
fun FloatingGalleryTopBar(
    state: ImageGalleryState,
    showSearchBar: Boolean,
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
        if (showSearchBar) {
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
        } else {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = stringResource(R.string.image_gallery_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onSearchClick) {
                        Icon(Icons.Default.Search, contentDescription = stringResource(R.string.action_search))
                    }
                    IconButton(onClick = onSortClick) {
                        Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = stringResource(R.string.action_sort))
                    }
                    Box {
                        IconButton(onClick = { showOverflowMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.action_more_options))
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
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.9f),
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        tonalElevation = 6.dp,
        shadowElevation = 6.dp,
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
            IconButton(onClick = onClearSelection) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.selected_count, selectedCount),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = selectedSize,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                )
            }
            IconButton(onClick = onSelectAll) {
                Icon(
                    imageVector = Icons.Default.GridView,
                    contentDescription = stringResource(R.string.select_all)
                )
            }
            IconButton(onClick = onInvertSelection) {
                Icon(
                    imageVector = Icons.Default.SelectAll,
                    contentDescription = stringResource(R.string.invert_selection)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GalleryViewOptionsDialog(
    presentation: BrowserPresentationPreferences,
    isAspectRatio: Boolean,
    isSectioned: Boolean,
    showFileDetails: Boolean,
    onPresentationChange: (BrowserPresentationPreferences) -> Unit,
    onAspectRatioChange: (Boolean) -> Unit,
    onSectionedChange: (Boolean) -> Unit,
    onShowFileDetailsChange: (Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    var draftPreferences by remember(presentation) {
        mutableStateOf(presentation.normalized())
    }
    var draftAspectRatio by remember(isAspectRatio) {
        mutableStateOf(isAspectRatio)
    }
    var draftSectioned by remember(isSectioned) {
        mutableStateOf(isSectioned)
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
            val liveColumnCount = kotlin.math.max(
                1,
                kotlin.math.floor(((maxWidth.value - 32f) / draftPreferences.gridMinCellSize).toDouble()).toInt()
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                Text(
                    text = stringResource(R.string.image_gallery_view_sort_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
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
                                onClick = { draftPreferences = draftPreferences.copy(viewMode = mode) },
                                selected = draftPreferences.viewMode == mode,
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
                    targetState = draftPreferences.viewMode,
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
                                        (draftPreferences.listZoom * 100).roundToInt()
                                    ),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Slider(
                                value = draftPreferences.listZoom,
                                onValueChange = {
                                    draftPreferences = draftPreferences.copy(listZoom = it)
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
                                        liveColumnCount
                                    ),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Slider(
                                value = draftPreferences.gridMinCellSize,
                                onValueChange = {
                                    draftPreferences = draftPreferences.copy(gridMinCellSize = it)
                                },
                                valueRange = BrowserPresentationPreferences.MIN_GRID_MIN_CELL_SIZE..BrowserPresentationPreferences.MAX_GRID_MIN_CELL_SIZE,
                                steps = 1
                            )
                        }
                    }
                }

                // 3. Grid Mode: Square vs Aspect Ratio (only in Grid View Mode)
                if (draftPreferences.viewMode == BrowserViewMode.GRID) {
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
                                selected = !draftAspectRatio,
                                onClick = { draftAspectRatio = false },
                                label = { Text(stringResource(R.string.image_gallery_view_mode_square)) }
                            )
                            FilterChip(
                                selected = draftAspectRatio,
                                onClick = { draftAspectRatio = true },
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
                            SortChip(FileSortOption.NAME_ASC, draftPreferences, Modifier.weight(1f)) {
                                draftPreferences = draftPreferences.copy(sortOption = it)
                            }
                            SortChip(FileSortOption.NAME_DESC, draftPreferences, Modifier.weight(1f)) {
                                draftPreferences = draftPreferences.copy(sortOption = it)
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            SortChip(FileSortOption.DATE_NEWEST, draftPreferences, Modifier.weight(1f)) {
                                draftPreferences = draftPreferences.copy(sortOption = it)
                            }
                            SortChip(FileSortOption.DATE_OLDEST, draftPreferences, Modifier.weight(1f)) {
                                draftPreferences = draftPreferences.copy(sortOption = it)
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            SortChip(FileSortOption.SIZE_LARGEST, draftPreferences, Modifier.weight(1f)) {
                                draftPreferences = draftPreferences.copy(sortOption = it)
                            }
                            SortChip(FileSortOption.SIZE_SMALLEST, draftPreferences, Modifier.weight(1f)) {
                                draftPreferences = draftPreferences.copy(sortOption = it)
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
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.image_gallery_group_time),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Switch(
                            checked = draftSectioned,
                            onCheckedChange = { draftSectioned = it }
                        )
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
                            onPresentationChange(draftPreferences.normalized())
                            onAspectRatioChange(draftAspectRatio)
                            onSectionedChange(draftSectioned)
                            onShowFileDetailsChange(draftShowDetails)
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
    val albumScrollKey = state.selectedAlbumPath ?: "__all__"

    val bottomPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() +
        if (state.selectedFiles.isNotEmpty()) MaterialTheme.spacing.toolbarBottomGap else MaterialTheme.spacing.screenGutter

    val groupedFiles = remember(state.displayedFiles, state.isSectioned) {
        if (state.isSectioned) {
            state.displayedFiles.groupBy { getTimeSection(it.lastModified) }
                .toSortedMap(compareBy { it.ordinal })
        } else {
            emptyMap()
        }
    }

    val flatUiFiles = remember(state.displayedFiles, state.isSectioned) {
        if (state.isSectioned) {
            state.displayedFiles.groupBy { getTimeSection(it.lastModified) }
                .toSortedMap(compareBy { it.ordinal })
                .values.flatten()
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
                if (state.presentation.viewMode == BrowserViewMode.GRID) {
                    if (state.isAspectRatio) {
                        LazyVerticalStaggeredGrid(
                            columns = StaggeredGridCells.Adaptive(minSize = state.presentation.gridMinCellSize.dp),
                            state = rememberLazyStaggeredGridState(),
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(
                                start = 12.dp,
                                top = contentPadding.calculateTopPadding() + 8.dp,
                                end = 12.dp,
                                bottom = bottomPadding
                            ),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalItemSpacing = 8.dp
                        ) {
                            if (state.isSectioned) {
                                groupedFiles.forEach { (section, filesInSection) ->
                                    if (filesInSection.isNotEmpty()) {
                                        item(span = StaggeredGridItemSpan.FullLine) {
                                            GallerySectionHeader(section.toDisplayString())
                                        }
                                        items(filesInSection, key = { it.absolutePath }) { file ->
                                            GalleryImageItem(
                                                file = file,
                                                isSelected = file.absolutePath in state.selectedFiles,
                                                aspectRatio = state.aspectRatios[file.absolutePath] ?: 1f,
                                                showDetails = state.showFileDetails,
                                                onClick = { onClickItem(file) },
                                                onLongClick = { onLongClickItem(file) }
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
                                        onLongClick = { onLongClickItem(file) }
                                    )
                                }
                            }
                        }
                    } else {
                        val gridState = remember(albumScrollKey) {
                            gridStatesByAlbum.getOrPut(albumScrollKey) { LazyGridState() }
                        }
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(minSize = state.presentation.gridMinCellSize.dp),
                            state = gridState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(
                                start = 12.dp,
                                top = contentPadding.calculateTopPadding() + 8.dp,
                                end = 12.dp,
                                bottom = bottomPadding
                            ),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (state.isSectioned) {
                                groupedFiles.forEach { (section, filesInSection) ->
                                    if (filesInSection.isNotEmpty()) {
                                        item(span = { GridItemSpan(maxLineSpan) }) {
                                            GallerySectionHeader(section.toDisplayString())
                                        }
                                        items(filesInSection, key = { it.absolutePath }) { file ->
                                            GalleryImageItem(
                                                file = file,
                                                isSelected = file.absolutePath in state.selectedFiles,
                                                aspectRatio = 1f,
                                                showDetails = state.showFileDetails,
                                                onClick = { onClickItem(file) },
                                                onLongClick = { onLongClickItem(file) }
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
                                        onLongClick = { onLongClickItem(file) }
                                    )
                                }
                            }
                        }
                    }
                } else {
                    val listState = remember(albumScrollKey) {
                        listStatesByAlbum.getOrPut(albumScrollKey) { LazyListState() }
                    }
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            top = contentPadding.calculateTopPadding(),
                            bottom = bottomPadding
                        )
                    ) {
                        if (state.isSectioned) {
                            groupedFiles.forEach { (section, filesInSection) ->
                                if (filesInSection.isNotEmpty()) {
                                    item {
                                        GallerySectionHeader(
                                            title = section.toDisplayString(),
                                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                        )
                                    }
                                    items(filesInSection, key = { it.absolutePath }) { file ->
                                        GalleryImageListItem(
                                            file = file,
                                            isSelected = file.absolutePath in state.selectedFiles,
                                            zoom = state.presentation.listZoom,
                                            onClick = { onClickItem(file) },
                                            onLongClick = { onLongClickItem(file) }
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
                                    onLongClick = { onLongClickItem(file) }
                                )
                            }
                        }
                    }
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
    onLongClick: () -> Unit,
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
        val request = remember(context, archiveThumbnailData, file.absolutePath, cacheKey, requestSizePx) {
            ImageRequest.Builder(context)
                .data(archiveThumbnailData ?: file.absolutePath)
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
    onLongClick: () -> Unit,
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
            val request = remember(context, archiveThumbnailData, file.absolutePath, cacheKey, thumbnailSizePx) {
                ImageRequest.Builder(context)
                    .data(archiveThumbnailData ?: file.absolutePath)
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

private const val GALLERY_MAX_THUMBNAIL_PX = 512
