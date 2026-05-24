package dev.qtremors.arcile.presentation.ui.components.lists

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.basicMarquee
import dev.qtremors.arcile.ui.theme.LocalDoubleLineFilenames
import dev.qtremors.arcile.ui.theme.LocalMarqueeFilenames
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import dev.qtremors.arcile.R
import dev.qtremors.arcile.domain.BrowserViewMode
import dev.qtremors.arcile.domain.FileModel
import dev.qtremors.arcile.domain.FolderStats
import dev.qtremors.arcile.image.ThumbnailPolicy
import dev.qtremors.arcile.image.ThumbnailPolicyInput
import dev.qtremors.arcile.presentation.ui.components.getFileIconVector
import dev.qtremors.arcile.presentation.ui.components.rememberArcileHaptics
import dev.qtremors.arcile.presentation.utils.rememberDateFormatter
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FileGrid(
    files: List<FileModel>,
    selectedFiles: Set<String>,
    onNavigateTo: (String) -> Unit,
    onOpenFile: (String) -> Unit,
    onToggleSelection: (String) -> Unit,
    onSelectMultiple: (List<String>) -> Unit,
    modifier: Modifier = Modifier,
    gridState: androidx.compose.foundation.lazy.grid.LazyGridState = androidx.compose.foundation.lazy.grid.rememberLazyGridState(),
    minCellSize: Dp = 100.dp,
    showThumbnails: Boolean = true,
    thumbnailLoadingPaused: Boolean = false,
    folderStatsByPath: Map<String, FolderStats> = emptyMap(),
    folderStatsLoadingPaths: Set<String> = emptySet(),
    contentPadding: PaddingValues = PaddingValues(16.dp)
) {
    val formatter = rememberDateFormatter("MMM dd, yyyy  h:mm a")
    val thumbnailSizePx = with(LocalDensity.current) { minCellSize.roundToPx().coerceAtLeast(96) }
    val rows = remember(files, folderStatsByPath, formatter, thumbnailSizePx) {
        files.map { file ->
            file.toFileRowUiModel(
                formatter = formatter,
                folderStats = folderStatsByPath[file.absolutePath],
                thumbnailSizePx = thumbnailSizePx
            )
        }
    }
    val haptics = rememberArcileHaptics()
    val thumbnailPolicy = remember { ThumbnailPolicy() }
    val visibleRange by remember {
        derivedStateOf {
            val visible = gridState.layoutInfo.visibleItemsInfo
            if (visible.isEmpty()) null else visible.first().index..visible.last().index
        }
    }
    var lastInteractedIndex by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(files) { lastInteractedIndex = null }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = minCellSize),
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        state = gridState,
        modifier = modifier.fillMaxWidth()
    ) {
        itemsIndexed(
            items = rows,
            key = { _, row -> row.absolutePath },
            contentType = { _, row -> if (row.isDirectory) "directory" else "file" }
        ) { index, row ->
            val file = row.file
            val isSelected = selectedFiles.contains(file.absolutePath)
            FileGridItem(
                modifier = Modifier.animateItem(),
                row = row,
                isSelected = isSelected,
                isInSelectionMode = selectedFiles.isNotEmpty(),
                isFolderStatsLoading = folderStatsLoadingPaths.contains(file.absolutePath),
                showThumbnails = showThumbnails,
                thumbnailLoadingPaused = thumbnailLoadingPaused,
                itemIndex = index,
                visibleRange = visibleRange,
                thumbnailPolicy = thumbnailPolicy,
                onClick = {
                    if (selectedFiles.isNotEmpty()) {
                        lastInteractedIndex = index
                        onToggleSelection(file.absolutePath)
                        haptics.selectionChanged()
                    } else if (file.isDirectory) {
                        onNavigateTo(file.absolutePath)
                    } else {
                        onOpenFile(file.absolutePath)
                    }
                },
                onLongClick = {
                    if (selectedFiles.isNotEmpty() && lastInteractedIndex != null && lastInteractedIndex != index) {
                        val start = minOf(lastInteractedIndex!!, index)
                        val end = maxOf(lastInteractedIndex!!, index)
                        onSelectMultiple(files.subList(start, end + 1).map { it.absolutePath })
                        haptics.selectionChanged()
                    } else {
                        val wasEmpty = selectedFiles.isEmpty()
                        onToggleSelection(file.absolutePath)
                        if (wasEmpty) haptics.selectionStart() else haptics.selectionChanged()
                    }
                    lastInteractedIndex = index
                },
                onOpenDirectly = {
                    if (file.isDirectory) onNavigateTo(file.absolutePath) else onOpenFile(file.absolutePath)
                },
                onToggleSelectionDirectly = {
                    val wasEmpty = selectedFiles.isEmpty()
                    onToggleSelection(file.absolutePath)
                    if (wasEmpty) haptics.selectionStart() else haptics.selectionChanged()
                }
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FileGridItem(
    file: FileModel,
    formattedDate: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    isInSelectionMode: Boolean = false,
    showThumbnails: Boolean = true,
    thumbnailLoadingPaused: Boolean = false,
    folderStats: FolderStats? = null,
    isFolderStatsLoading: Boolean = false,
    onOpenDirectly: () -> Unit = {},
    onToggleSelectionDirectly: () -> Unit = {}
) {
    val row = remember(file, formattedDate, folderStats) {
        file.toFileRowUiModel(
            formatter = SimpleDateFormat("MMM dd, yyyy  h:mm a", Locale.getDefault()),
            folderStats = folderStats,
            thumbnailSizePx = 160
        ).copy(formattedDate = formattedDate)
    }
    FileGridItem(
        row = row,
        isSelected = isSelected,
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier,
        isInSelectionMode = isInSelectionMode,
        showThumbnails = showThumbnails,
        thumbnailLoadingPaused = thumbnailLoadingPaused,
        isFolderStatsLoading = isFolderStatsLoading,
        onOpenDirectly = onOpenDirectly,
        onToggleSelectionDirectly = onToggleSelectionDirectly
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FileGridItem(
    row: FileRowUiModel,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    isInSelectionMode: Boolean = false,
    showThumbnails: Boolean = true,
    thumbnailLoadingPaused: Boolean = false,
    itemIndex: Int = 0,
    visibleRange: IntRange? = null,
    thumbnailPolicy: ThumbnailPolicy = remember { ThumbnailPolicy() },
    isFolderStatsLoading: Boolean = false,
    onOpenDirectly: () -> Unit = {},
    onToggleSelectionDirectly: () -> Unit = {}
) {
    val file = row.file
    val context = LocalContext.current
    val doubleLineEnabled = LocalDoubleLineFilenames.current
    val marqueeEnabled = LocalMarqueeFilenames.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow),
        label = "gridItemScale"
    )
    val subtitleText = row.displaySubtitle(isFolderStatsLoading)

    Card(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .alpha(if (row.isHidden) 0.5f else 1f)
            .fileItemSemantics(
                file = file,
                isSelected = isSelected,
                formattedDate = row.formattedDate,
                folderStatsText = subtitleText.takeIf { file.isDirectory },
                isInSelectionMode = isInSelectionMode,
                onClick = onClick,
                onLongClick = onLongClick,
                onOpenDirectly = onOpenDirectly,
                onToggleSelectionDirectly = onToggleSelectionDirectly
            )
            .combinedClickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                onClick = onClick,
                onLongClick = onLongClick
            ),
        shape = if (isSelected) MaterialTheme.shapes.large else MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Box {
                val shouldLoadThumbnail = row.canShowThumbnail && thumbnailPolicy.shouldLoad(
                    ThumbnailPolicyInput(
                        userEnabled = showThumbnails,
                        viewMode = BrowserViewMode.GRID,
                        thumbnailSizePx = row.thumbnailSizePx,
                        itemIndex = itemIndex,
                        visibleRange = visibleRange,
                        isOperationActive = thumbnailLoadingPaused,
                        key = row.thumbnailKey
                    )
                )
                if (shouldLoadThumbnail) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(File(file.absolutePath))
                            .size(row.thumbnailSizePx)
                            .memoryCacheKey(row.thumbnailKey.cacheKey)
                            .diskCacheKey(row.thumbnailKey.cacheKey)
                            .diskCachePolicy(CachePolicy.ENABLED)
                            .memoryCachePolicy(CachePolicy.ENABLED)
                            .build(),
                        onSuccess = { thumbnailPolicy.clearFailure(row.thumbnailKey) },
                        onError = { thumbnailPolicy.recordFailure(row.thumbnailKey) },
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = getFileIconVector(file),
                            contentDescription = null,
                            tint = if (file.isDirectory) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(48.dp)
                        )
                    }
                }
                if (isSelected) {
                    Surface(
                        shape = androidx.compose.foundation.shape.CircleShape,
                        color = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                            .size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = stringResource(R.string.selected),
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(4.dp)
                        )
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = file.name,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = if (doubleLineEnabled && !marqueeEnabled) 2 else 1,
                    overflow = if (marqueeEnabled) TextOverflow.Clip else TextOverflow.Ellipsis,
                    modifier = if (marqueeEnabled) Modifier.basicMarquee() else Modifier
                )
                Text(
                    text = subtitleText,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = row.formattedDate,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
