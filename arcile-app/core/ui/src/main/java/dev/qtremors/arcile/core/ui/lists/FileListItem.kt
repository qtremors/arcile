package dev.qtremors.arcile.core.ui.lists

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
    presentation: FileItemPresentation = FileItemPresentation(),
    folderStats: FolderStats? = null,
    isFolderStatsLoading: Boolean = false
) {
    val formatter = rememberDateTimeFormatter()
    val row = remember(file, formattedDate, folderStats) {
        file.toFileRowUiModel(
            formatter = formatter,
            folderStats = folderStats,
            thumbnailSizePx = ThumbnailTargetSize.fromBounds(128)
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
        presentation = presentation,
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
    presentation: FileItemPresentation = FileItemPresentation(),
    itemIndex: Int = 0,
    visibleRange: IntRange? = null,
    thumbnailPolicy: ThumbnailPolicy = remember { ThumbnailPolicy() },
    isFolderStatsLoading: Boolean = false
) {
    val file = row.file
    val context = LocalContext.current
    val openImageDescription = stringResource(R.string.open_image)
    val doubleLineEnabled = LocalDoubleLineFilenames.current
    val marqueeEnabled = LocalMarqueeFilenames.current
    val animatedHorizontalPadding by animateDpAsState(
        targetValue = if (isSelected) 8.dp else 0.dp,
        animationSpec = ArcileMotion.rememberSpring(stiffness = Spring.StiffnessMediumLow),
        label = "listItemHPadding"
    )
    val animatedVerticalPadding by animateDpAsState(
        targetValue = if (isSelected) 4.dp else 0.dp,
        animationSpec = ArcileMotion.rememberSpring(stiffness = Spring.StiffnessMediumLow),
        label = "listItemVPadding"
    )
    val animatedScale by animateFloatAsState(targetValue = presentation.zoom, label = "listZoom")
    val iconSize = (48.dp * animatedScale).coerceIn(40.dp, 64.dp)
    val contentPadding = (16.dp * animatedScale).coerceIn(12.dp, 20.dp)
    val titleStyle = MaterialTheme.typography.titleMedium.scaled(animatedScale)
        .copy(fontWeight = FontWeight.Medium)
    val supportStyle = MaterialTheme.typography.bodySmall.scaled(animatedScale)
        .copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
    val subtitleText = row.displaySubtitle(isFolderStatsLoading)
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val reducedMotion = LocalReducedMotionEnabled.current
    val scale by animateFloatAsState(
        targetValue = if (isPressed && !reducedMotion) 0.98f else 1f,
        animationSpec = ArcileMotion.rememberSpring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "fileItemScale"
    )
    val itemShape = if (isSelected) MaterialTheme.shapes.large else MaterialTheme.shapes.extraLarge
    val shouldOpenThumbnailInSelection =
        presentation.openImageFromThumbnailInSelectionMode &&
            isInSelectionMode &&
            !file.isDirectory &&
            FileCategories.getCategoryForFile(file.extension, file.mimeType) == FileCategories.Images

    Surface(
        shape = itemShape,
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
        modifier = modifier
            .padding(
                horizontal = animatedHorizontalPadding.coerceAtLeast(0.dp),
                vertical = animatedVerticalPadding.coerceAtLeast(0.dp)
            )
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
            .clip(itemShape)
            .combinedClickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                onClick = onClick,
                onLongClick = onLongClick
            )
    ) {
        Row(
            modifier = Modifier.fillMaxWidth()
                .padding(horizontal = contentPadding, vertical = contentPadding * 0.75f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FileListThumbnail(
                row = row,
                isSelected = isSelected,
                presentation = presentation,
                itemIndex = itemIndex,
                visibleRange = visibleRange,
                thumbnailPolicy = thumbnailPolicy,
                iconSize = iconSize,
                shouldOpenDirectly = shouldOpenThumbnailInSelection,
                openImageDescription = openImageDescription,
                onOpenDirectly = onOpenDirectly
            )
            if (presentation.showDetails) {
                Spacer(modifier = Modifier.width(contentPadding))
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
                    Text(
                        text = file.name,
                        maxLines = if (doubleLineEnabled && !marqueeEnabled) 2 else 1,
                        overflow = if (marqueeEnabled) TextOverflow.Clip else TextOverflow.Ellipsis,
                        style = titleStyle,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = if (marqueeEnabled) Modifier.basicMarquee() else Modifier
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(text = subtitleText, style = supportStyle, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
}

@Composable
private fun FileListThumbnail(
    row: FileRowUiModel,
    isSelected: Boolean,
    presentation: FileItemPresentation,
    itemIndex: Int,
    visibleRange: IntRange?,
    thumbnailPolicy: ThumbnailPolicy,
    iconSize: androidx.compose.ui.unit.Dp,
    shouldOpenDirectly: Boolean,
    openImageDescription: String,
    onOpenDirectly: () -> Unit
) {
    val file = row.file
    val context = LocalContext.current
    Box(
        modifier = Modifier.size(iconSize)
            .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
            .then(
                if (shouldOpenDirectly) {
                    Modifier.semantics { contentDescription = openImageDescription }
                        .clickable(onClick = onOpenDirectly)
                } else {
                    Modifier
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        val shouldLoadThumbnail = row.canShowThumbnail && thumbnailPolicy.shouldLoad(
            ThumbnailPolicyInput(
                userEnabled = presentation.showThumbnails,
                viewMode = FileViewMode.LIST,
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
            val suppliedData = presentation.thumbnailData?.invoke(file, row.thumbnailSizePx)
            val cacheKey = (suppliedData as? dev.qtremors.arcile.core.storage.domain.SensitiveThumbnailRequest)?.memoryCacheKey
                ?: archiveData?.cacheKey ?: row.thumbnailKey.variantKey(row.thumbnailSizePx).cacheKey
            val data = suppliedData ?: row.thumbnailRequestData(archiveData)
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
                ListFileIcon(file, iconSize * 0.5f)
            }
            Image(
                painter = painter,
                contentDescription = null,
                modifier = Modifier.fillMaxSize().clip(CircleShape),
                contentScale = ContentScale.Crop
            )
        } else {
            ListFileIcon(file, iconSize * 0.5f)
        }
        if (isSelected) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.align(Alignment.BottomEnd).size(22.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = stringResource(R.string.selected),
                    modifier = Modifier.fillMaxSize().padding(3.dp)
                )
            }
        }
    }
}

@Composable
private fun ListFileIcon(file: FileModel, iconSize: androidx.compose.ui.unit.Dp) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Icon(
            imageVector = getFileIconVector(file),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(iconSize)
        )
    }
}
