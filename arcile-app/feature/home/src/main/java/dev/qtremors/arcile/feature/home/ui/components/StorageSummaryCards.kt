package dev.qtremors.arcile.feature.home.ui.components

import dev.qtremors.arcile.ui.theme.spacing
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.togetherWith
import androidx.compose.animation.animateContentSize
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.qtremors.arcile.core.ui.R
import dev.qtremors.arcile.core.storage.domain.CategoryStorage
import dev.qtremors.arcile.core.storage.domain.StorageKind
import dev.qtremors.arcile.core.storage.domain.isIndexed
import dev.qtremors.arcile.core.storage.domain.showTemporaryStorageBadge
import dev.qtremors.arcile.feature.home.HomeState
import dev.qtremors.arcile.shared.ui.shimmer
import dev.qtremors.arcile.ui.theme.LocalCategoryColors
import dev.qtremors.arcile.ui.theme.bodyMediumMedium
import dev.qtremors.arcile.ui.theme.bodySmallMedium

import dev.qtremors.arcile.ui.theme.titleLargeBold
import dev.qtremors.arcile.ui.theme.titleMediumBold
import dev.qtremors.arcile.ui.theme.bounceCombinedClickable
import dev.qtremors.arcile.utils.formatFileSize
import dev.qtremors.arcile.utils.getCategoryColor

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun StorageSummaryCard(
    state: HomeState,
    onNavigateToPath: (String) -> Unit,
    onOpenStorageDashboard: (String?) -> Unit,
    onOpenFileBrowser: () -> Unit
) {
    val volumes = state.allStorageVolumes.ifEmpty { state.storageInfo?.volumes ?: emptyList() }
    
    if (volumes.size > 1) {
        // Multi-storage layout
        val indexedVolumes = volumes.filter { it.kind.isIndexed }
        val soleIndexedVolumeId = indexedVolumes.singleOrNull()?.id
        Column(modifier = Modifier.padding(horizontal = MaterialTheme.spacing.medium, vertical = MaterialTheme.spacing.small)) {
            volumes.forEach { volume ->
                val volumeCategories = state.categoryStoragesByVolume[volume.id]
                    ?: if (volume.id == soleIndexedVolumeId) state.categoryStorages else emptyList()
                val volumeTrashBytes = state.trashStorageUsage.byVolumeId[volume.id]
                    ?: if (volume.id == soleIndexedVolumeId) state.trashStorageUsage.totalBytes else 0L
                StorageVolumeCard(
                    volume = volume,
                    categoryStorages = volumeCategories,
                    trashBytes = volumeTrashBytes,
                    isLoading = state.isLoading || state.isCalculatingStorage,
                    onClick = { onNavigateToPath(volume.path) },
                    onLongClick = {
                        if (volume.kind.isIndexed) {
                            onOpenStorageDashboard(volume.id)
                        }
                    }
                )
                Spacer(modifier = Modifier.height(MaterialTheme.spacing.space12))
            }
        }
    } else {
        // Single storage layout (backward compatible UI)
        val primaryVolume = volumes.find { it.isPrimary } ?: volumes.firstOrNull()
        val total = primaryVolume?.totalBytes ?: 0L
        val free = primaryVolume?.freeBytes ?: 0L
        val used = total - free

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 144.dp)
                .padding(MaterialTheme.spacing.medium)
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
                val showPlaceholder = total <= 0L || state.isLoading || state.isCalculatingStorage
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
                    totalBytes = displayTotal,
                    freeBytes = displayFree,
                    categoryStorages = state.categoryStorages,
                    trashBytes = state.trashStorageUsage.totalBytes,
                    isCalculating = showPlaceholder
                )

                Spacer(modifier = Modifier.height(MaterialTheme.spacing.space12))
                
                if (!showPlaceholder && total > 0L) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "${formatFileSize(displayUsed)} used",
                            style = MaterialTheme.typography.bodyMediumMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = "${formatFileSize(displayFree)} free",
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
                                .shimmer(visible = true, highlightColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.12f))
                        )
                        Box(
                            modifier = Modifier
                                .width(96.dp)
                                .height(16.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.10f))
                                .shimmer(visible = true, highlightColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.12f))
                        )
                    }
                }

                if ((state.categoryStorages.isNotEmpty() || state.trashStorageUsage.totalBytes > 0L) && !showPlaceholder) {
                    Spacer(modifier = Modifier.height(MaterialTheme.spacing.space12))
                    val systemBytes = systemInaccessibleBytes(
                        totalBytes = total,
                        freeBytes = free,
                        categoryStorages = state.categoryStorages,
                        trashBytes = state.trashStorageUsage.totalBytes
                    )
                    CategoryLegend(
                        categoryStorages = state.categoryStorages,
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
private fun CategoryLegendPlaceholder() {
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
                    .background(MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.10f))
                    .shimmer(visible = true, highlightColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.12f))
            )
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun StorageVolumeCard(
    volume: dev.qtremors.arcile.core.storage.domain.StorageVolume,
    categoryStorages: List<CategoryStorage>,
    trashBytes: Long,
    isLoading: Boolean = false,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val used = volume.totalBytes - volume.freeBytes
    val showPlaceholder = isLoading || volume.totalBytes <= 0L
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.extraLarge)
            .bounceCombinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        colors = CardDefaults.cardColors(
            containerColor = if (volume.isPrimary) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer,
            contentColor = if (volume.isPrimary) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSecondaryContainer
        ),
        shape = MaterialTheme.shapes.extraLarge
    ) {
        Column(modifier = Modifier.padding(MaterialTheme.spacing.space20)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = volume.name,
                            style = MaterialTheme.typography.titleMediumBold
                        )
                        if (volume.kind.showTemporaryStorageBadge) {
                            Spacer(modifier = Modifier.width(MaterialTheme.spacing.small))
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                shape = CircleShape,
                                modifier = Modifier.size(24.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = if (volume.kind == StorageKind.OTG) Icons.Default.Usb else Icons.Default.Info,
                                        contentDescription = stringResource(R.string.desc_temporary),
                                        modifier = Modifier.size(14.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                    if (volume.kind.showTemporaryStorageBadge) {
                        Text(
                            text = if (volume.kind == StorageKind.OTG) stringResource(R.string.otg_usb) else "Unclassified External",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }
                Icon(
                    imageVector = when (volume.kind) {
                        StorageKind.INTERNAL -> Icons.Default.Storage
                        StorageKind.SD_CARD -> Icons.Default.SdCard
                        StorageKind.OTG -> Icons.Default.Usb
                        StorageKind.EXTERNAL_UNCLASSIFIED -> Icons.Default.SdCard
                    },
                    contentDescription = volume.name,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.height(MaterialTheme.spacing.space12))

            MultiColorStorageBar(
                totalBytes = volume.totalBytes.takeIf { it > 0L } ?: 1L,
                freeBytes = volume.freeBytes.takeIf { volume.totalBytes > 0L } ?: 1L,
                categoryStorages = categoryStorages,
                trashBytes = trashBytes,
                isCalculating = showPlaceholder
            )

            Spacer(modifier = Modifier.height(MaterialTheme.spacing.small))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "${formatFileSize(used)} / ${formatFileSize(volume.totalBytes)}",
                    style = MaterialTheme.typography.bodySmallMedium
                )
                val percent = if (volume.totalBytes > 0) (used * 100 / volume.totalBytes) else 0
                Text(
                    text = "$percent%",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}


@Composable
fun MultiColorStorageBar(
    totalBytes: Long,
    freeBytes: Long,
    categoryStorages: List<CategoryStorage>,
    trashBytes: Long = 0L,
    isCalculating: Boolean = false
) {
    val barHeight = 10.dp
    val barShape = CircleShape

    val hasData = totalBytes > 0
    
    // Global progress for the "revealing" animation when data first appears
    var animationTrigger by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(isCalculating, hasData) {
        if (!isCalculating && hasData) {
            animationTrigger = true
        } else if (isCalculating) {
            animationTrigger = false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(barHeight)
            .clip(barShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .testTag(if (isCalculating) "storage_bar_loading" else "storage_bar")
    ) {
        if (hasData || isCalculating) {
            Box(modifier = Modifier.fillMaxSize()) {
                // Data Layer (Segments / Indeterminate progress)
                val hasSegmentData = categoryStorages.any { it.sizeBytes > 0L } || trashBytes > 0L
                val showSegments = hasData && animationTrigger && hasSegmentData

                AnimatedContent(
                    targetState = showSegments,
                    transitionSpec = {
                        fadeIn(animationSpec = tween(500)) togetherWith fadeOut(animationSpec = tween(500))
                    },
                    modifier = Modifier.fillMaxSize(),
                    label = "storageBarContentTransition"
                ) { targetShowSegments ->
                    if (targetShowSegments) {
                        val rowTrigger = remember { mutableStateOf(false) }
                        LaunchedEffect(Unit) {
                            rowTrigger.value = true
                        }

                        Row(modifier = Modifier.fillMaxSize()) {
                            val actualUsedBytes = (totalBytes - freeBytes).coerceIn(0L, totalBytes)
                            val rawCategories = categoryStorages
                                .filter { it.sizeBytes > 0L }
                                .sortedByDescending { it.sizeBytes }
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

                            rawCategories.forEach { cat ->
                                val segmentBytes = (cat.sizeBytes.toDouble() * scale).toLong()
                                if (segmentBytes > 0) {
                                    val fraction = segmentBytes.toFloat() / totalBytes.toFloat()
                                    val animatedFraction by animateFloatAsState(
                                        targetValue = if (rowTrigger.value) fraction else 0f,
                                        animationSpec = spring(
                                            dampingRatio = Spring.DampingRatioLowBouncy,
                                            stiffness = Spring.StiffnessMediumLow
                                        ),
                                        label = "cat_${cat.name}_animation"
                                    )
                                    val catColor = getCategoryColor(cat.name, categoryColors, MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                                    Box(
                                        modifier = Modifier
                                            .fillMaxHeight()
                                            .weight(animatedFraction.coerceAtLeast(0.0001f))
                                            .padding(horizontal = 0.1.dp)
                                            .clip(CircleShape)
                                            .background(catColor)
                                    )
                                }
                            }

                            if (boundedTrashBytes > 0) {
                                val trashFraction = boundedTrashBytes.toFloat() / totalBytes.toFloat()
                                val animatedTrashFraction by animateFloatAsState(
                                    targetValue = if (rowTrigger.value) trashFraction else 0f,
                                    animationSpec = spring(
                                        dampingRatio = Spring.DampingRatioLowBouncy,
                                        stiffness = Spring.StiffnessMediumLow
                                    ),
                                    label = "trash_bytes_animation"
                                )
                                Box(
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .weight(animatedTrashFraction.coerceAtLeast(0.0001f))
                                        .padding(horizontal = 0.1.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.error)
                                )
                            }

                            if (otherUsedBytes > 0) {
                                val otherFraction = otherUsedBytes.toFloat() / totalBytes.toFloat()
                                val animatedOtherFraction by animateFloatAsState(
                                    targetValue = if (rowTrigger.value) otherFraction else 0f,
                                    animationSpec = spring(
                                        dampingRatio = Spring.DampingRatioLowBouncy,
                                        stiffness = Spring.StiffnessMediumLow
                                    ),
                                    label = "other_bytes_animation"
                                )

                                Box(
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .weight(animatedOtherFraction.coerceAtLeast(0.0001f))
                                        .padding(horizontal = 0.1.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                                )
                            }

                            if (freeBytes > 0) {
                                val freeFraction = freeBytes.toFloat() / totalBytes.toFloat()
                                val currentUsedFractions = (actualUsedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
                                val animatedUsedFraction by animateFloatAsState(
                                    targetValue = if (rowTrigger.value) currentUsedFractions else 0f,
                                    animationSpec = spring(
                                        dampingRatio = Spring.DampingRatioLowBouncy,
                                        stiffness = Spring.StiffnessMediumLow
                                    ),
                                    label = "used_bytes_animation"
                                )
                                val animatedFreeFraction = 1f - animatedUsedFraction

                                Box(
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .weight(animatedFreeFraction.coerceAtLeast(0.0001f))
                                        .background(Color.Transparent)
                                )
                            }
                        }
                    } else {
                        // Linear moving progress using a softer subset of categories colors
                        val categoryColors = LocalCategoryColors.current
                        val colors = listOf(
                            categoryColors.images.copy(alpha = 0.5f),
                            categoryColors.videos.copy(alpha = 0.5f),
                            categoryColors.audio.copy(alpha = 0.5f)
                        )

                        val infiniteTransition = rememberInfiniteTransition(label = "linearMovingProgress")
                        val progressOffset by infiniteTransition.animateFloat(
                            initialValue = -0.4f,
                            targetValue = 1.0f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(1500, easing = LinearEasing),
                                repeatMode = RepeatMode.Restart
                            ),
                            label = "progressOffset"
                        )

                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val width = size.width
                            val height = size.height
                            val blockWidth = width * 0.4f
                            val startX = progressOffset * width
                            
                            val gradient = Brush.linearGradient(
                                colors = colors,
                                start = Offset(startX, 0f),
                                end = Offset(startX + blockWidth, 0f)
                            )
                            
                            drawRoundRect(
                                brush = gradient,
                                topLeft = Offset(startX, 0f),
                                size = Size(blockWidth, height),
                                cornerRadius = CornerRadius(height / 2, height / 2)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CategoryLegend(
    categoryStorages: List<CategoryStorage>,
    trashBytes: Long = 0L,
    systemBytes: Long = 0L
) {
    val scrollState = rememberScrollState()
    Row(
        modifier = Modifier
            .horizontalScroll(scrollState)
            .fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.space12)
    ) {
        val categoryColors = LocalCategoryColors.current
        val sortedCategories = categoryStorages.sortedByDescending { it.sizeBytes }
        val legendItems = sortedCategories
            .filter { it.sizeBytes > 0 }
            .map { cat ->
                val catColor = getCategoryColor(cat.name, categoryColors, MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                Triple(cat.name, cat.sizeBytes, catColor)
            } + listOfNotNull(
            if (trashBytes > 0L) Triple(stringResource(R.string.trash_bin), trashBytes, MaterialTheme.colorScheme.error) else null,
            if (systemBytes > 0L) {
                Triple(
                    stringResource(R.string.other_files_system),
                    systemBytes,
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
            } else {
                null
            }
        )

        legendItems.forEach { (name, sizeBytes, color) ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraSmall)
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(color)
                )
                Text(
                    text = "$name ${formatFileSize(sizeBytes)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

fun systemInaccessibleBytes(
    totalBytes: Long,
    freeBytes: Long,
    categoryStorages: List<CategoryStorage>,
    trashBytes: Long
): Long {
    val actualUsedBytes = (totalBytes - freeBytes).coerceAtLeast(0L)
    val visibleBytes = categoryStorages.sumOf { it.sizeBytes } + trashBytes.coerceAtLeast(0L)
    return (actualUsedBytes - visibleBytes).coerceAtLeast(0L)
}
