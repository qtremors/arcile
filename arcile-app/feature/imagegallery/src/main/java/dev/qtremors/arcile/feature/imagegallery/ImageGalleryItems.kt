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
import androidx.compose.foundation.clickable
import dev.qtremors.arcile.ui.theme.bounceClickable
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
fun GalleryThumbnail(
    file: FileModel,
    thumbnailKey: ThumbnailKey,
    thumbnailPolicy: ThumbnailPolicy,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    var showPlaceholder by remember(file.absolutePath, file.size, file.lastModified) { mutableStateOf(true) }
    BoxWithConstraints(
        modifier = modifier.background(MaterialTheme.colorScheme.surface),
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
        if (showPlaceholder) {
            Icon(
                imageVector = Icons.Default.Image,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
        }
        AsyncImage(
            model = request,
            onLoading = {
                showPlaceholder = true
                thumbnailPolicy.recordInFlight(thumbnailKey, requestSizePx)
            },
            onSuccess = {
                showPlaceholder = false
                thumbnailPolicy.clearFailure(thumbnailKey)
                thumbnailPolicy.recordLoaded(thumbnailKey, requestSizePx)
            },
            onError = {
                showPlaceholder = true
                thumbnailPolicy.recordFailure(thumbnailKey, requestSizePx)
            },
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
                .background(MaterialTheme.colorScheme.surface),
            contentAlignment = Alignment.Center
        ) {
            var showPlaceholder by remember(file.absolutePath, file.size, file.lastModified) { mutableStateOf(true) }
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
            if (showPlaceholder) {
                Icon(
                    imageVector = Icons.Default.Image,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.size(18.dp)
                )
            }
            AsyncImage(
                model = request,
                onLoading = {
                    showPlaceholder = true
                    thumbnailPolicy.recordInFlight(thumbnailKey, thumbnailSizePx)
                },
                onSuccess = {
                    showPlaceholder = false
                    thumbnailPolicy.clearFailure(thumbnailKey)
                    thumbnailPolicy.recordLoaded(thumbnailKey, thumbnailSizePx)
                },
                onError = {
                    showPlaceholder = true
                    thumbnailPolicy.recordFailure(thumbnailKey, thumbnailSizePx)
                },
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

