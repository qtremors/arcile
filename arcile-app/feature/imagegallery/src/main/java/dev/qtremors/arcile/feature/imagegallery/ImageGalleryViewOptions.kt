@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package dev.qtremors.arcile.feature.imagegallery

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
import dev.qtremors.arcile.ui.theme.bounceClickable
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.foundation.layout.BoxWithConstraints
import dev.qtremors.arcile.shared.ui.ExpressiveFilterChip
import dev.qtremors.arcile.shared.ui.ExpressiveSegmentedRow
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi

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
import dev.qtremors.arcile.ui.theme.ExpressiveShapes
import dev.qtremors.arcile.ui.theme.menuGroupFirst
import dev.qtremors.arcile.ui.theme.menuGroupLast
import androidx.compose.material.icons.filled.Check
import dev.qtremors.arcile.ui.theme.menuGroupMiddle
import dev.qtremors.arcile.ui.theme.menuGroupSingle
import dev.qtremors.arcile.utils.formatFileSize
import kotlinx.coroutines.flow.SharedFlow

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

@Composable
fun GalleryViewOptionsDialog(
    currentTab: GalleryTab,
    photosPresentation: BrowserPresentationPreferences,
    albumPresentation: BrowserPresentationPreferences,
    isAspectRatio: Boolean,
    grouping: ImageGalleryGrouping,
    showFileDetails: Boolean,
    onPhotosPresentationChange: (BrowserPresentationPreferences) -> Unit,
    onAlbumPresentationChange: (BrowserPresentationPreferences) -> Unit,
    onPhotosAspectRatioChange: (Boolean) -> Unit,
    onGroupingChange: (ImageGalleryGrouping) -> Unit,
    onShowFileDetailsChange: (Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    val haptics = rememberArcileHaptics()
    var draftPhotosPreferences by remember(photosPresentation) {
        mutableStateOf(photosPresentation.normalized())
    }
    var draftAlbumPreferences by remember(albumPresentation) {
        mutableStateOf(albumPresentation.normalized())
    }
    var draftPhotosAspectRatio by remember(isAspectRatio) {
        mutableStateOf(isAspectRatio)
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
                    .verticalScroll(rememberScrollState())
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
                        ExpressiveSegmentedRow(
                            options = BrowserViewMode.entries,
                            selectedOption = draftPhotosPreferences.viewMode,
                            onOptionSelected = { mode -> draftPhotosPreferences = draftPhotosPreferences.copy(viewMode = mode) },
                            modifier = Modifier.fillMaxWidth()
                        ) { mode ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = if (mode == BrowserViewMode.LIST) {
                                        Icons.AutoMirrored.Filled.ViewList
                                    } else {
                                        Icons.Default.GridView
                                    },
                                    contentDescription = null
                                )
                                Text(
                                    stringResource(
                                        if (mode == BrowserViewMode.LIST) R.string.list_view else R.string.grid_view
                                    )
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
                                ExpressiveFilterChip(
                                    selected = !draftPhotosAspectRatio,
                                    onClick = { draftPhotosAspectRatio = false },
                                    label = { Text(stringResource(R.string.image_gallery_view_mode_square)) }
                                )
                                ExpressiveFilterChip(
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
                                ExpressiveFilterChip(
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

                    // 2. Sort Options (Name / Count)
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
                    val cancelClick = {
                        haptics.selectionChanged()
                        onDismiss()
                    }
                    val applyClick = {
                        haptics.selectionChanged()
                        if (currentTab == GalleryTab.PHOTOS) {
                            onPhotosPresentationChange(draftPhotosPreferences.normalized())
                            onPhotosAspectRatioChange(draftPhotosAspectRatio)
                            onGroupingChange(draftGrouping)
                            onShowFileDetailsChange(draftShowDetails)
                        } else {
                            onAlbumPresentationChange(draftAlbumPreferences.normalized())
                        }
                        onDismiss()
                    }
                    TextButton(
                        onClick = cancelClick,
                        shape = ExpressiveShapes.medium
                    ) {
                        Text(stringResource(R.string.cancel))
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    FilledTonalButton(
                        onClick = applyClick,
                        shape = ExpressiveShapes.medium
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.apply))
                    }
                }
            }
        }
    }
}

@Composable
fun SortChip(
    option: FileSortOption,
    preferences: BrowserPresentationPreferences,
    modifier: Modifier = Modifier,
    onSelect: (FileSortOption) -> Unit
) {
    ExpressiveFilterChip(
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

