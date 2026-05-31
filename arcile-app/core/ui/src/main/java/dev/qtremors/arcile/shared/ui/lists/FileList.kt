package dev.qtremors.arcile.shared.ui.lists

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.basicMarquee
import dev.qtremors.arcile.ui.theme.LocalDoubleLineFilenames
import dev.qtremors.arcile.ui.theme.LocalMarqueeFilenames
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import dev.qtremors.arcile.core.ui.R
import dev.qtremors.arcile.core.storage.domain.BrowserViewMode
import dev.qtremors.arcile.core.storage.domain.FileModel
import dev.qtremors.arcile.core.storage.domain.FolderStats
import dev.qtremors.arcile.image.ThumbnailPolicy
import dev.qtremors.arcile.image.ThumbnailPolicyInput
import dev.qtremors.arcile.shared.ui.getFileIconVector
import dev.qtremors.arcile.shared.ui.rememberArcileHaptics
import dev.qtremors.arcile.shared.ui.rememberDateFormatter
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FileList(
    files: List<FileModel>,
    selectedFiles: Set<String>,
    onNavigateTo: (String) -> Unit,
    onOpenFile: (String) -> Unit,
    onToggleSelection: (String) -> Unit,
    onSelectMultiple: (List<String>) -> Unit,
    modifier: Modifier = Modifier,
    listState: androidx.compose.foundation.lazy.LazyListState = androidx.compose.foundation.lazy.rememberLazyListState(),
    zoom: Float = 1f,
    showThumbnails: Boolean = true,
    thumbnailLoadingPaused: Boolean = false,
    folderStatsByPath: Map<String, FolderStats> = emptyMap(),
    folderStatsLoadingPaths: Set<String> = emptySet(),
    contentPadding: PaddingValues = PaddingValues(0.dp)
) {
    val formatter = rememberDateFormatter("MMM dd, yyyy  h:mm a")
    val thumbnailSizePx = with(LocalDensity.current) { (64.dp * zoom).roundToPx().coerceAtLeast(96) }
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
            val visible = listState.layoutInfo.visibleItemsInfo
            if (visible.isEmpty()) null else visible.first().index..visible.last().index
        }
    }
    var lastInteractedIndex by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(files) { lastInteractedIndex = null }

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        state = listState,
        contentPadding = contentPadding
    ) {
        itemsIndexed(
            items = rows,
            key = { _, row -> row.absolutePath },
            contentType = { _, row -> if (row.isDirectory) "directory" else "file" }
        ) { index, row ->
            val file = row.file
            val isSelected = selectedFiles.contains(file.absolutePath)
            FileItemRow(
                modifier = Modifier.animateItem(),
                row = row,
                isSelected = isSelected,
                isInSelectionMode = selectedFiles.isNotEmpty(),
                zoom = zoom,
                showThumbnails = showThumbnails,
                thumbnailLoadingPaused = thumbnailLoadingPaused,
                itemIndex = index,
                visibleRange = visibleRange,
                thumbnailPolicy = thumbnailPolicy,
                isFolderStatsLoading = folderStatsLoadingPaths.contains(file.absolutePath),
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
fun FileItemRow(
    file: FileModel,
    formattedDate: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    isInSelectionMode: Boolean = false,
    onOpenDirectly: () -> Unit = {},
    onToggleSelectionDirectly: () -> Unit = {},
    modifier: Modifier = Modifier,
    zoom: Float = 1f,
    showThumbnails: Boolean = true,
    thumbnailLoadingPaused: Boolean = false,
    folderStats: FolderStats? = null,
    isFolderStatsLoading: Boolean = false
) {
    val row = remember(file, formattedDate, folderStats) {
        file.toFileRowUiModel(
            formatter = SimpleDateFormat("MMM dd, yyyy  h:mm a", Locale.getDefault()),
            folderStats = folderStats,
            thumbnailSizePx = 128
        ).copy(formattedDate = formattedDate)
    }
    FileItemRow(
        row = row,
        isSelected = isSelected,
        onClick = onClick,
        onLongClick = onLongClick,
        isInSelectionMode = isInSelectionMode,
        onOpenDirectly = onOpenDirectly,
        onToggleSelectionDirectly = onToggleSelectionDirectly,
        modifier = modifier,
        zoom = zoom,
        showThumbnails = showThumbnails,
        thumbnailLoadingPaused = thumbnailLoadingPaused,
        isFolderStatsLoading = isFolderStatsLoading
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FileItemRow(
    row: FileRowUiModel,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    isInSelectionMode: Boolean = false,
    onOpenDirectly: () -> Unit = {},
    onToggleSelectionDirectly: () -> Unit = {},
    modifier: Modifier = Modifier,
    zoom: Float = 1f,
    showThumbnails: Boolean = true,
    thumbnailLoadingPaused: Boolean = false,
    itemIndex: Int = 0,
    visibleRange: IntRange? = null,
    thumbnailPolicy: ThumbnailPolicy = remember { ThumbnailPolicy() },
    isFolderStatsLoading: Boolean = false
) {
    val file = row.file
    val context = LocalContext.current
    val doubleLineEnabled = LocalDoubleLineFilenames.current
    val marqueeEnabled = LocalMarqueeFilenames.current
    val animatedHorizontalPadding by animateDpAsState(
        targetValue = if (isSelected) 8.dp else 0.dp,
        label = "listItemHPadding"
    )
    val animatedVerticalPadding by animateDpAsState(
        targetValue = if (isSelected) 4.dp else 0.dp,
        label = "listItemVPadding"
    )
    val animatedScale by animateFloatAsState(targetValue = zoom, label = "listZoom")

    val iconSize = (48.dp * animatedScale).coerceIn(40.dp, 64.dp)
    val contentPadding = (16.dp * animatedScale).coerceIn(12.dp, 20.dp)
    val titleStyle = MaterialTheme.typography.titleMedium.scaled(animatedScale).copy(fontWeight = FontWeight.Medium)
    val supportStyle = MaterialTheme.typography.bodySmall.scaled(animatedScale).copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
    val subtitleText = row.displaySubtitle(isFolderStatsLoading)

    Surface(
        shape = if (isSelected) MaterialTheme.shapes.large else MaterialTheme.shapes.extraLarge,
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
        modifier = modifier
            .padding(horizontal = animatedHorizontalPadding, vertical = animatedVerticalPadding)
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
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = contentPadding, vertical = contentPadding * 0.75f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(iconSize)
                    .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                val shouldLoadThumbnail = row.canShowThumbnail && thumbnailPolicy.shouldLoad(
                    ThumbnailPolicyInput(
                        userEnabled = showThumbnails,
                        viewMode = BrowserViewMode.LIST,
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
                        onSuccess = {
                            thumbnailPolicy.clearFailure(row.thumbnailKey)
                            thumbnailPolicy.recordLoaded(row.thumbnailKey)
                        },
                        onError = { thumbnailPolicy.recordFailure(row.thumbnailKey) },
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        imageVector = getFileIconVector(file),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(iconSize * 0.5f)
                    )
                }
                if (isSelected) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .size(22.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = stringResource(R.string.selected),
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(3.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(contentPadding))

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = file.name,
                    maxLines = if (doubleLineEnabled && !marqueeEnabled) 2 else 1,
                    overflow = if (marqueeEnabled) TextOverflow.Clip else TextOverflow.Ellipsis,
                    style = titleStyle,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = if (marqueeEnabled) Modifier.basicMarquee() else Modifier
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = subtitleText,
                        style = supportStyle,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        text = row.formattedDate,
                        style = supportStyle,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.End
                    )
                }
            }
        }
    }
}

private fun TextStyle.scaled(zoom: Float): TextStyle = copy(
    fontSize = fontSize * zoom,
    lineHeight = lineHeight * zoom
)
