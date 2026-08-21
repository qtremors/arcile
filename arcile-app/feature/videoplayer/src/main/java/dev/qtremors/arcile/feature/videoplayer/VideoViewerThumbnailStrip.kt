package dev.qtremors.arcile.feature.videoplayer

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import coil.request.ImageRequest
import dev.qtremors.arcile.core.storage.domain.FileModel
import dev.qtremors.arcile.core.ui.theme.bounceClickable

private const val VIDEO_STRIP_PAINTER_CACHE_SIZE = 64
internal const val VIDEO_STRIP_THUMBNAIL_SIZE_PX = 128

internal data class VideoStripThumbnailEntry(
    val cacheKey: String,
    val request: ImageRequest
)

@Composable
internal fun VideoViewerStripThumbnail(
    file: FileModel,
    selected: Boolean,
    entry: VideoStripThumbnailEntry?,
    painterCache: VideoStripLoadedValueCache<Painter>,
    onClick: () -> Unit
) {
    val animElevation by animateDpAsState(
        targetValue = if (selected) 6.dp else 0.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "thumbnailElevation"
    )
    val animScale by animateFloatAsState(
        targetValue = if (selected) 1f else 0.82f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "thumbnailScale"
    )
    Box(
        modifier = Modifier
            .width(36.dp)
            .height(54.dp)
            .zIndex(if (selected) 1f else 0f)
            .graphicsLayer {
                scaleX = animScale
                scaleY = animScale
            }
            .shadow(elevation = animElevation, shape = RoundedCornerShape(4.dp))
            .clip(RoundedCornerShape(4.dp))
            .border(
                width = if (selected) 2.dp else 0.dp,
                color = if (selected) Color.White else Color.Transparent,
                shape = RoundedCornerShape(4.dp)
            )
            .semantics {
                contentDescription = file.name
                this.selected = selected
            }
            .bounceClickable(onClick = onClick)
    ) {
        val loadedPainter = entry?.let { painterCache[it.cacheKey] }
        if (loadedPainter != null) {
            Image(
                painter = loadedPainter,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else if (entry != null) {
            AsyncImage(
                model = entry.request,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                onSuccess = { result -> painterCache.put(entry.cacheKey, result.painter) },
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

internal class VideoStripLoadedValueCache<T>(
    private val maxEntries: Int = VIDEO_STRIP_PAINTER_CACHE_SIZE
) {
    init {
        require(maxEntries > 0)
    }

    private val values = mutableStateMapOf<String, T>()
    private val insertionOrder = ArrayDeque<String>()

    operator fun get(cacheKey: String): T? = values[cacheKey]

    fun put(cacheKey: String, value: T) {
        if (values.containsKey(cacheKey)) {
            values[cacheKey] = value
            return
        }
        while (insertionOrder.size >= maxEntries) {
            values.remove(insertionOrder.removeFirst())
        }
        insertionOrder.addLast(cacheKey)
        values[cacheKey] = value
    }
}
