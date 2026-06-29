package dev.qtremors.arcile.core.ui.storage

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.qtremors.arcile.core.storage.domain.CategoryStorage
import dev.qtremors.arcile.ui.theme.LocalCategoryColors
import dev.qtremors.arcile.utils.getCategoryColor

@Composable
fun MultiColorStorageBar(
    totalBytes: Long,
    freeBytes: Long,
    categoryStorages: List<CategoryStorage>,
    trashBytes: Long = 0L,
    isCalculating: Boolean = false
) {
    val hasData = totalBytes > 0
    var animationTrigger by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(isCalculating, hasData) {
        animationTrigger = !isCalculating && hasData
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(10.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .testTag(if (isCalculating) "storage_bar_loading" else "storage_bar")
    ) {
        if (hasData || isCalculating) {
            val hasSegmentData = categoryStorages.any { it.sizeBytes > 0L } || trashBytes > 0L
            val showSegments = hasData && animationTrigger && hasSegmentData
            AnimatedContent(
                targetState = showSegments,
                transitionSpec = {
                    fadeIn(animationSpec = tween(500)) togetherWith
                        fadeOut(animationSpec = tween(500))
                },
                modifier = Modifier.fillMaxSize(),
                label = "storageBarContentTransition"
            ) { targetShowSegments ->
                if (targetShowSegments) {
                    StorageBarSegments(totalBytes, freeBytes, categoryStorages, trashBytes)
                } else {
                    StorageBarLoadingIndicator()
                }
            }
        }
    }
}

@Composable
private fun StorageBarSegments(
    totalBytes: Long,
    freeBytes: Long,
    categoryStorages: List<CategoryStorage>,
    trashBytes: Long
) {
    var revealed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { revealed = true }

    val actualUsedBytes = (totalBytes - freeBytes).coerceIn(0L, totalBytes)
    val rawCategories = categoryStorages.filter { it.sizeBytes > 0L }.sortedByDescending { it.sizeBytes }
    val rawSegmentBytes = rawCategories.sumOf { it.sizeBytes } + trashBytes.coerceAtLeast(0L)
    val scale = if (rawSegmentBytes > actualUsedBytes && rawSegmentBytes > 0L) {
        actualUsedBytes.toDouble() / rawSegmentBytes.toDouble()
    } else {
        1.0
    }
    val boundedTrashBytes = (trashBytes.coerceAtLeast(0L).toDouble() * scale).toLong()
    val categorizedBytes = rawCategories.sumOf { (it.sizeBytes.toDouble() * scale).toLong() }
    val otherUsedBytes = (actualUsedBytes - categorizedBytes - boundedTrashBytes).coerceAtLeast(0L)
    val categoryColors = LocalCategoryColors.current

    Row(modifier = Modifier.fillMaxSize()) {
        rawCategories.forEach { category ->
            val segmentBytes = (category.sizeBytes.toDouble() * scale).toLong()
            if (segmentBytes > 0L) {
                val fraction = animatedStorageFraction(
                    target = segmentBytes.toFloat() / totalBytes.toFloat(),
                    revealed = revealed,
                    label = "cat_${category.name}_animation"
                )
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .weight(fraction)
                        .padding(horizontal = 0.1.dp)
                        .clip(CircleShape)
                        .background(
                            getCategoryColor(
                                category.name,
                                categoryColors,
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            )
                        )
                )
            }
        }
        if (boundedTrashBytes > 0L) {
            StorageBarSegment(
                fraction = animatedStorageFraction(
                    target = boundedTrashBytes.toFloat() / totalBytes.toFloat(),
                    revealed = revealed,
                    label = "trash_bytes_animation"
                ),
                color = MaterialTheme.colorScheme.error
            )
        }
        if (otherUsedBytes > 0L) {
            StorageBarSegment(
                fraction = animatedStorageFraction(
                    target = otherUsedBytes.toFloat() / totalBytes.toFloat(),
                    revealed = revealed,
                    label = "other_bytes_animation"
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
        }
        if (freeBytes > 0L) {
            val usedFraction = animatedStorageFraction(
                target = (actualUsedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f),
                revealed = revealed,
                label = "used_bytes_animation"
            )
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .weight((1f - usedFraction).coerceAtLeast(MinimumStorageBarWeight))
                    .background(Color.Transparent)
            )
        }
    }
}

@Composable
private fun RowScope.StorageBarSegment(fraction: Float, color: Color) {
    Box(
        modifier = Modifier
            .fillMaxHeight()
            .weight(fraction)
            .padding(horizontal = 0.1.dp)
            .clip(CircleShape)
            .background(color)
    )
}

@Composable
private fun animatedStorageFraction(target: Float, revealed: Boolean, label: String): Float {
    val fraction by animateFloatAsState(
        targetValue = if (revealed) target else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = label
    )
    return fraction.coerceAtLeast(MinimumStorageBarWeight)
}

@Composable
private fun StorageBarLoadingIndicator() {
    val categoryColors = LocalCategoryColors.current
    val infiniteTransition = rememberInfiniteTransition(label = "linearMovingProgress")
    val progressOffset by infiniteTransition.animateFloat(
        initialValue = -0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "progressOffset"
    )
    Canvas(modifier = Modifier.fillMaxSize()) {
        val blockWidth = size.width * 0.4f
        val startX = progressOffset * size.width
        drawRoundRect(
            brush = Brush.linearGradient(
                colors = listOf(
                    categoryColors.images.copy(alpha = 0.5f),
                    categoryColors.videos.copy(alpha = 0.5f),
                    categoryColors.audio.copy(alpha = 0.5f)
                ),
                start = Offset(startX, 0f),
                end = Offset(startX + blockWidth, 0f)
            ),
            topLeft = Offset(startX, 0f),
            size = Size(blockWidth, size.height),
            cornerRadius = CornerRadius(size.height / 2, size.height / 2)
        )
    }
}

private const val MinimumStorageBarWeight = 0.0001f
