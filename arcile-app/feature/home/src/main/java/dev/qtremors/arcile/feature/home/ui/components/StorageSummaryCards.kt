package dev.qtremors.arcile.feature.home.ui.components

import dev.qtremors.arcile.core.ui.theme.spacing
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.SdCard
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.qtremors.arcile.core.ui.R
import dev.qtremors.arcile.core.storage.domain.CategoryStorage
import dev.qtremors.arcile.core.storage.domain.StorageKind
import dev.qtremors.arcile.core.storage.domain.StorageVolume
import dev.qtremors.arcile.core.storage.domain.isIndexed
import dev.qtremors.arcile.core.storage.domain.showTemporaryStorageBadge
import dev.qtremors.arcile.feature.home.HomeState
import dev.qtremors.arcile.core.ui.theme.LocalCategoryColors
import dev.qtremors.arcile.core.ui.theme.bodyMediumMedium
import dev.qtremors.arcile.core.ui.theme.bodySmallMedium

import dev.qtremors.arcile.core.ui.theme.titleLargeBold
import dev.qtremors.arcile.core.ui.theme.titleMediumBold
import dev.qtremors.arcile.core.ui.theme.bounceCombinedClickable
import dev.qtremors.arcile.core.presentation.formatFileSize
import dev.qtremors.arcile.core.ui.theme.getCategoryColor

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
internal fun StorageSummaryCard(
    state: HomeState,
    onNavigateToPath: (String) -> Unit,
    onOpenStorageDashboard: (String?) -> Unit,
    onOpenFileBrowser: () -> Unit
) {
    val volumes = state.allStorageVolumes.ifEmpty { state.storageInfo?.volumes ?: emptyList() }

    MountedStorageSummaryCard(
        state = state,
        volumes = volumes,
        onNavigateToPath = onNavigateToPath,
        onOpenStorageDashboard = onOpenStorageDashboard,
        onOpenFileBrowser = onOpenFileBrowser,
        modifier = Modifier.fillMaxWidth()
    )
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun MountedStorageSummaryCard(
    state: HomeState,
    volumes: List<StorageVolume>,
    onNavigateToPath: (String) -> Unit,
    onOpenStorageDashboard: (String?) -> Unit,
    onOpenFileBrowser: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (volumes.size > 1) {
        // Multi-storage layout
        val indexedVolumes = volumes.filter { it.kind.isIndexed }
        val soleIndexedVolumeId = indexedVolumes.singleOrNull()?.id
        Column(modifier = modifier.padding(horizontal = MaterialTheme.spacing.medium)) {
            volumes.forEachIndexed { index, volume ->
                val volumeCategories = state.categoryStoragesByVolume[volume.id]
                    ?: if (volume.id == soleIndexedVolumeId) state.categoryStorages else emptyList()
                val volumeTrashBytes = state.trashStorageUsage.byVolumeId[volume.id]
                    ?: if (volume.id == soleIndexedVolumeId) state.trashStorageUsage.totalBytes else 0L
                StorageVolumeCard(
                    volume = volume,
                    categoryStorages = volumeCategories,
                    trashBytes = volumeTrashBytes,
                    isRefreshing = state.isPullToRefreshing,
                    onClick = { onNavigateToPath(volume.path) },
                    onLongClick = {
                        if (volume.kind.isIndexed) {
                            onOpenStorageDashboard(volume.id)
                        }
                    }
                )
                if (index < volumes.lastIndex) {
                    Spacer(modifier = Modifier.height(MaterialTheme.spacing.space12))
                }
            }
        }
    } else {
        // Single storage layout (backward compatible UI)
        val primaryVolume = volumes.find { it.isPrimary } ?: volumes.firstOrNull()
        val total = primaryVolume?.totalBytes ?: 0L
        val free = primaryVolume?.freeBytes ?: 0L
        val used = total - free
        val categoryStorages = state.categoryStorages.ifEmpty {
            primaryVolume?.let { state.categoryStoragesByVolume[it.id] } ?: emptyList()
        }

        Card(
            modifier = modifier
                .fillMaxWidth()
                .heightIn(min = 144.dp)
                .padding(horizontal = MaterialTheme.spacing.medium)
                .clip(MaterialTheme.shapes.extraLarge)
                .bounceCombinedClickable(
                    onClick = onOpenFileBrowser,
                    onLongClick = { onOpenStorageDashboard(primaryVolume?.id) }
                )
                .animateContentSize(
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessLow
                    )
                ),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            ),
            shape = MaterialTheme.shapes.extraLarge
        ) {
            Column(modifier = Modifier.padding(MaterialTheme.spacing.large)) {
                val displayTotal = total.takeIf { it > 0L } ?: 1L
                val displayFree = free.takeIf { total > 0L } ?: 1L
                val displayUsed = used.takeIf { total > 0L } ?: 0L
                val showPlaceholder = total <= 0L
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = primaryVolume?.name ?: stringResource(R.string.internal_storage),
                        style = MaterialTheme.typography.titleLargeBold
                    )
                    Icon(Icons.Default.Storage, contentDescription = stringResource(R.string.desc_storage))
                }

                Spacer(modifier = Modifier.height(MaterialTheme.spacing.medium))

                MultiColorStorageBar(
                    totalBytes = total,
                    freeBytes = free,
                    categoryStorages = categoryStorages,
                    trashBytes = state.trashStorageUsage.totalBytes,
                    isRefreshing = state.isPullToRefreshing
                )

                Spacer(modifier = Modifier.height(MaterialTheme.spacing.space12))
                
                if (!showPlaceholder && total > 0L) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "${formatFileSize(androidx.compose.ui.platform.LocalContext.current, displayUsed)} used",
                            style = MaterialTheme.typography.bodyMediumMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = "${formatFileSize(androidx.compose.ui.platform.LocalContext.current, displayFree)} free",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Box(
                            modifier = Modifier
                                .width(96.dp)
                                .height(16.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.10f))
                        )
                        Box(
                            modifier = Modifier
                                .width(96.dp)
                                .height(16.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.10f))
                        )
                    }
                }

                if ((categoryStorages.isNotEmpty() || state.trashStorageUsage.totalBytes > 0L) && !showPlaceholder) {
                    Spacer(modifier = Modifier.height(MaterialTheme.spacing.space12))
                    val systemBytes = systemInaccessibleBytes(
                        totalBytes = total,
                        freeBytes = free,
                        categoryStorages = categoryStorages,
                        trashBytes = state.trashStorageUsage.totalBytes
                    )
                    CategoryLegend(
                        categoryStorages = categoryStorages,
                        trashBytes = state.trashStorageUsage.totalBytes,
                        systemBytes = systemBytes
                    )
                } else {
                    Spacer(modifier = Modifier.height(MaterialTheme.spacing.space12))
                    CategoryLegendPlaceholder()
                }
            }
        }
    }
}

@Composable
internal fun CategoryLegendPlaceholder(
    contentColor: Color = MaterialTheme.colorScheme.onPrimaryContainer
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.space12)
    ) {
        repeat(3) {
            Box(
                modifier = Modifier
                    .width(88.dp)
                    .height(14.dp)
                    .clip(CircleShape)
                    .background(contentColor.copy(alpha = 0.10f))
            )
        }
    }
}

@Composable
internal fun MultiColorStorageBar(
    totalBytes: Long,
    freeBytes: Long,
    categoryStorages: List<CategoryStorage>,
    trashBytes: Long = 0L,
    isRefreshing: Boolean = false
) {
    val hasData = totalBytes > 0L
    val hasBreakdown = categoryStorages.isNotEmpty()
    val showLoading = isRefreshing || !hasData || !hasBreakdown
    var hasShownLoading by remember { mutableStateOf(showLoading) }
    LaunchedEffect(showLoading) {
        if (showLoading) hasShownLoading = true
    }

    Box(
        modifier = Modifier.fillMaxWidth().height(10.dp).clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .testTag(if (showLoading) "storage_bar_loading" else "storage_bar")
    ) {
        if (hasData && hasBreakdown || showLoading) {
            AnimatedContent(
                targetState = showLoading,
                transitionSpec = { fadeIn(tween(500)) togetherWith fadeOut(tween(500)) },
                modifier = Modifier.fillMaxSize(),
                label = "storageBarContentTransition"
            ) { loading ->
                if (loading) {
                    StorageBarLoadingIndicator(
                        Modifier.fillMaxSize().testTag("storage_bar_multicolor_loading")
                    )
                } else if (hasData && hasBreakdown) {
                    StorageBarSegments(
                        totalBytes, freeBytes, categoryStorages, trashBytes,
                        animateReveal = hasShownLoading
                    )
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
    trashBytes: Long,
    animateReveal: Boolean
) {
    var revealed by remember { mutableStateOf(!animateReveal) }
    LaunchedEffect(animateReveal) { revealed = true }

    val used = (totalBytes - freeBytes).coerceIn(0L, totalBytes)
    val categories = categoryStorages.filter { it.sizeBytes > 0L }
        .sortedByDescending { it.sizeBytes }
    val rawBytes = categories.sumOf { it.sizeBytes } + trashBytes.coerceAtLeast(0L)
    val scale = if (rawBytes > used && rawBytes > 0L) used.toDouble() / rawBytes else 1.0
    val trash = (trashBytes.coerceAtLeast(0L) * scale).toLong()
    val categorized = categories.sumOf { (it.sizeBytes * scale).toLong() }
    val other = (used - categorized - trash).coerceAtLeast(0L)
    val colors = LocalCategoryColors.current

    val categorySegments = categories.mapNotNull { category ->
        val bytes = (category.sizeBytes * scale).toLong()
        if (bytes <= 0L) return@mapNotNull null
        AnimatedStorageBarSegment(
            animatedStorageFraction(bytes.toFloat() / totalBytes, revealed, category.name),
            getCategoryColor(category.name, colors,
                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
        )
    }
    val trashSegment = if (trash > 0L) AnimatedStorageBarSegment(
        animatedStorageFraction(trash.toFloat() / totalBytes, revealed, "trash"),
        MaterialTheme.colorScheme.error
    ) else null
    val otherSegment = if (other > 0L) AnimatedStorageBarSegment(
        animatedStorageFraction(other.toFloat() / totalBytes, revealed, "other"),
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
    ) else null
    val segments = categorySegments + listOfNotNull(trashSegment, otherSegment)

    Canvas(Modifier.fillMaxSize().testTag("storage_bar_segments")) {
        val inset = 0.1.dp.toPx()
        var left = 0f
        segments.forEach { segment ->
            val width = (segment.fraction.value * size.width).coerceAtLeast(0f)
            val paintedWidth = (width - inset * 2f).coerceAtLeast(0f)
            if (paintedWidth > 0f) {
                drawRoundRect(
                    color = segment.color,
                    topLeft = Offset(left + inset, 0f),
                    size = Size(paintedWidth, size.height),
                    cornerRadius = CornerRadius(size.height / 2f)
                )
            }
            left += width
        }
    }
}

private data class AnimatedStorageBarSegment(val fraction: State<Float>, val color: Color)

@Composable
private fun animatedStorageFraction(target: Float, revealed: Boolean, label: String): State<Float> =
    animateFloatAsState(
        targetValue = if (revealed) target else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "storage_${label}_animation"
    )

@Composable
private fun StorageBarLoadingIndicator(modifier: Modifier = Modifier) {
    val colors = LocalCategoryColors.current
    val transition = rememberInfiniteTransition(label = "storageBarLoading")
    val offset by transition.animateFloat(
        initialValue = -0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1500, easing = LinearEasing), RepeatMode.Restart),
        label = "storageBarLoadingOffset"
    )
    Canvas(modifier) {
        val width = size.width * 0.4f
        val x = offset * size.width
        drawRoundRect(
            brush = Brush.linearGradient(
                listOf(colors.images, colors.videos, colors.audio).map { it.copy(alpha = 0.5f) },
                start = Offset(x, 0f),
                end = Offset(x + width, 0f)
            ),
            topLeft = Offset(x, 0f),
            size = Size(width, size.height),
            cornerRadius = CornerRadius(size.height / 2f)
        )
    }
}
