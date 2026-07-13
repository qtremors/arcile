package dev.qtremors.arcile.feature.imagegallery

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.size.Precision
import dev.qtremors.arcile.core.storage.domain.FileModel
import dev.qtremors.arcile.core.ui.image.ArchiveEntryThumbnailData
import dev.qtremors.arcile.core.ui.image.ThumbnailKey
import dev.qtremors.arcile.core.ui.image.ThumbnailPolicy
import dev.qtremors.arcile.core.ui.image.ThumbnailTargetSize
import dev.qtremors.arcile.core.presentation.formatFileSize
import kotlin.math.roundToInt

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun GalleryImageListItem(
    file: FileModel,
    isSelected: Boolean,
    isSelectionMode: Boolean = false,
    zoom: Float,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    onOpenDirectly: () -> Unit = onClick,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val thumbnailPolicy = remember { ThumbnailPolicy() }
    val thumbnailKey = remember(file) { ThumbnailKey.from(file) }
    val thumbnailSizePx = ThumbnailTargetSize.fromBounds((48 * zoom).roundToInt())
    val scale by animateFloatAsState(
        targetValue = if (isSelected) 0.98f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "scale"
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer(scaleX = scale, scaleY = scale)
            .clip(MaterialTheme.shapes.extraLarge)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .background(
                if (isSelected) {
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                } else {
                    Color.Transparent
                }
            )
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
            var showPlaceholder by remember(
                file.absolutePath,
                file.size,
                file.lastModified
            ) { mutableStateOf(true) }
            val archiveThumbnailData = remember(file.absolutePath, file.size, file.lastModified) {
                ArchiveEntryThumbnailData.fromVirtualPath(
                    path = file.absolutePath,
                    sizeBytes = file.size,
                    lastModifiedMillis = file.lastModified
                )
            }
            val cacheKey = remember(archiveThumbnailData, thumbnailKey, thumbnailSizePx) {
                archiveThumbnailData?.cacheKey
                    ?: thumbnailKey.variantKey(thumbnailSizePx).cacheKey
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
            if (isSelectionMode) {
                GalleryOpenImageAction(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(4.dp),
                    onClick = onOpenDirectly,
                    size = 28.dp,
                    iconSize = 16.dp
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
