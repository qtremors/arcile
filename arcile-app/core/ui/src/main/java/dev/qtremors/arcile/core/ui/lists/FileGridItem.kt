package dev.qtremors.arcile.core.ui.lists

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImagePainter
import coil.compose.rememberAsyncImagePainter
import dev.qtremors.arcile.core.storage.domain.FileCategories
import dev.qtremors.arcile.core.storage.domain.FileModel
import dev.qtremors.arcile.core.storage.domain.FileViewMode
import dev.qtremors.arcile.core.storage.domain.FolderStats
import dev.qtremors.arcile.core.ui.R
import dev.qtremors.arcile.core.ui.getFileIconVector
import dev.qtremors.arcile.core.ui.image.ArchiveEntryThumbnailData
import dev.qtremors.arcile.core.ui.image.ThumbnailPolicy
import dev.qtremors.arcile.core.ui.image.ThumbnailPolicyInput
import dev.qtremors.arcile.core.ui.image.ThumbnailTargetSize
import dev.qtremors.arcile.core.ui.image.buildThumbnailImageRequest
import dev.qtremors.arcile.core.ui.rememberDateTimeFormatter
import dev.qtremors.arcile.core.ui.theme.ArcileMotion
import dev.qtremors.arcile.core.ui.theme.LocalDoubleLineFilenames
import dev.qtremors.arcile.core.ui.theme.LocalMarqueeFilenames
import dev.qtremors.arcile.core.ui.theme.LocalReducedMotionEnabled

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
    presentation: FileItemPresentation = FileItemPresentation(),
    folderStats: FolderStats? = null,
    isFolderStatsLoading: Boolean = false,
    onOpenDirectly: () -> Unit = {},
    onToggleSelectionDirectly: () -> Unit = {}
) {
    val formatter = rememberDateTimeFormatter()
    val row = remember(file, formattedDate, folderStats) {
        file.toFileRowUiModel(
            formatter = formatter,
            folderStats = folderStats,
            thumbnailSizePx = ThumbnailTargetSize.fromBounds(160)
        ).copy(formattedDate = formattedDate)
    }
    FileGridItem(
        row = row,
        isSelected = isSelected,
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier,
        isInSelectionMode = isInSelectionMode,
        presentation = presentation,
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
    presentation: FileItemPresentation = FileItemPresentation(),
    itemIndex: Int = 0,
    visibleRange: IntRange? = null,
    thumbnailPolicy: ThumbnailPolicy = remember { ThumbnailPolicy() },
    isFolderStatsLoading: Boolean = false,
    onOpenDirectly: () -> Unit = {},
    onToggleSelectionDirectly: () -> Unit = {}
) {
    val file = row.file
    val context = LocalContext.current
    val openImageDescription = stringResource(R.string.open_image)
    val doubleLineEnabled = LocalDoubleLineFilenames.current
    val marqueeEnabled = LocalMarqueeFilenames.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val reducedMotion = LocalReducedMotionEnabled.current
    val scale by animateFloatAsState(
        targetValue = if (isPressed && !reducedMotion) 0.95f else 1f,
        animationSpec = ArcileMotion.rememberSpring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "gridItemScale"
    )
    val subtitleText = row.displaySubtitle(isFolderStatsLoading)
    val itemShape = if (isSelected) MaterialTheme.shapes.large else MaterialTheme.shapes.extraLarge
    val shouldOpenThumbnailInSelection = presentation.openImageFromThumbnailInSelectionMode &&
        isInSelectionMode &&
        !file.isDirectory &&
        FileCategories.getCategoryForFile(file.extension, file.mimeType) == FileCategories.Images

    Card(
        modifier = modifier.fillMaxWidth().graphicsLayer { scaleX = scale; scaleY = scale }
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
            .clip(itemShape)
            .combinedClickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                onClick = onClick,
                onLongClick = onLongClick
            ),
        shape = itemShape,
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainer
            }
        )
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            FileGridPreview(
                row = row,
                presentation = presentation,
                itemIndex = itemIndex,
                visibleRange = visibleRange,
                thumbnailPolicy = thumbnailPolicy,
                isSelected = isSelected,
                shouldOpenDirectly = shouldOpenThumbnailInSelection,
                openImageDescription = openImageDescription,
                onOpenDirectly = onOpenDirectly
            )
            if (presentation.showDetails) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp),
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
}

@Composable
private fun FileGridPreview(
    row: FileRowUiModel,
    presentation: FileItemPresentation,
    itemIndex: Int,
    visibleRange: IntRange?,
    thumbnailPolicy: ThumbnailPolicy,
    isSelected: Boolean,
    shouldOpenDirectly: Boolean,
    openImageDescription: String,
    onOpenDirectly: () -> Unit
) {
    val file = row.file
    val context = LocalContext.current
    Box(
        modifier = Modifier.then(
            if (shouldOpenDirectly) {
                Modifier.semantics { contentDescription = openImageDescription }.clickable(onClick = onOpenDirectly)
            } else {
                Modifier
            }
        )
    ) {
        val shouldLoadThumbnail = row.canShowThumbnail && thumbnailPolicy.shouldLoad(
            ThumbnailPolicyInput(
                userEnabled = presentation.showThumbnails,
                viewMode = FileViewMode.GRID,
                thumbnailSizePx = row.thumbnailSizePx,
                itemIndex = itemIndex,
                visibleRange = visibleRange,
                isOperationActive = presentation.thumbnailLoadingPaused,
                key = row.thumbnailKey
            )
        )
        if (shouldLoadThumbnail) {
            val archiveData = ArchiveEntryThumbnailData.fromVirtualPath(
                path = file.absolutePath,
                sizeBytes = file.size,
                lastModifiedMillis = file.lastModified
            )
            val cacheKey = archiveData?.cacheKey ?: row.thumbnailKey.variantKey(row.thumbnailSizePx).cacheKey
            val data = row.thumbnailRequestData(archiveData)
            val request = remember(data, cacheKey, row.thumbnailSizePx) {
                buildThumbnailImageRequest(context, data, cacheKey, row.thumbnailSizePx)
            }
            val painter = rememberAsyncImagePainter(
                model = request,
                onLoading = { thumbnailPolicy.recordInFlight(row.thumbnailKey, row.thumbnailSizePx) },
                onSuccess = {
                    thumbnailPolicy.clearFailure(row.thumbnailKey)
                    thumbnailPolicy.recordLoaded(row.thumbnailKey, row.thumbnailSizePx)
                },
                onError = { thumbnailPolicy.recordFailure(row.thumbnailKey, row.thumbnailSizePx) }
            )
            if (painter.state is AsyncImagePainter.State.Empty ||
                painter.state is AsyncImagePainter.State.Loading ||
                painter.state is AsyncImagePainter.State.Error
            ) {
                GridFileIcon(file, Modifier.fillMaxSize())
            }
            Image(
                painter = painter,
                contentDescription = null,
                modifier = Modifier.fillMaxWidth().aspectRatio(1f),
                contentScale = ContentScale.Crop
            )
        } else {
            GridFileIcon(file)
        }
        if (isSelected) {
            Surface(
                shape = androidx.compose.foundation.shape.CircleShape,
                color = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.align(Alignment.TopEnd).padding(8.dp).size(28.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = stringResource(R.string.selected),
                    modifier = Modifier.fillMaxSize().padding(4.dp)
                )
            }
        }
    }
}

@Composable
private fun GridFileIcon(
    file: FileModel,
    modifier: Modifier = Modifier.fillMaxWidth().aspectRatio(1f)
) {
    Box(modifier = modifier.padding(16.dp), contentAlignment = Alignment.Center) {
        Icon(
            imageVector = getFileIconVector(file),
            contentDescription = null,
            tint = if (file.isDirectory) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.size(48.dp)
        )
    }
}
