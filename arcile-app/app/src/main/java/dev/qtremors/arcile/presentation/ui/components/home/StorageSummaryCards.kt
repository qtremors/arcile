package dev.qtremors.arcile.presentation.ui.components.home

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.qtremors.arcile.R
import dev.qtremors.arcile.domain.CategoryStorage
import dev.qtremors.arcile.domain.StorageKind
import dev.qtremors.arcile.domain.isIndexed
import dev.qtremors.arcile.domain.showTemporaryStorageBadge
import dev.qtremors.arcile.presentation.home.HomeState
import dev.qtremors.arcile.presentation.ui.components.shimmer
import dev.qtremors.arcile.ui.theme.LocalCategoryColors
import dev.qtremors.arcile.ui.theme.bodyMediumMedium
import dev.qtremors.arcile.ui.theme.bodySmallMedium
import dev.qtremors.arcile.ui.theme.spacing
import dev.qtremors.arcile.ui.theme.titleLargeBold
import dev.qtremors.arcile.ui.theme.titleMediumBold
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
        Column(modifier = Modifier.padding(horizontal = MaterialTheme.spacing.medium, vertical = MaterialTheme.spacing.small)) {
            volumes.forEach { volume ->
                StorageVolumeCard(
                    volume = volume,
                    categoryStorages = state.categoryStoragesByVolume[volume.id] ?: emptyList(),
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
                .padding(MaterialTheme.spacing.medium)
                .clip(MaterialTheme.shapes.extraLarge)
                    .combinedClickable(
                        onClick = onOpenFileBrowser,
                        onLongClick = { onOpenStorageDashboard(primaryVolume?.id) }
                    ),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            ),
            shape = MaterialTheme.shapes.extraLarge
        ) {
            Column(modifier = Modifier.padding(MaterialTheme.spacing.large)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = primaryVolume?.name ?: stringResource(R.string.internal_storage),
                        style = MaterialTheme.typography.titleLargeBold
                    )
                    Icon(Icons.Default.Storage, contentDescription = "Storage")
                }

                if (total > 0 || state.isCalculatingStorage) {
                    Spacer(modifier = Modifier.height(16.dp))

                    MultiColorStorageBar(
                        totalBytes = total,
                        freeBytes = free,
                        categoryStorages = state.categoryStorages,
                        isCalculating = state.isCalculatingStorage
                    )

                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "${formatFileSize(used)} used",
                            style = MaterialTheme.typography.bodyMediumMedium
                        )
                        Text(
                            text = "${formatFileSize(free)} free",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }

                    if (state.categoryStorages.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(MaterialTheme.spacing.space12))
                        CategoryLegend(state.categoryStorages)
                    }
                } else {
                    Spacer(modifier = Modifier.height(MaterialTheme.spacing.small))
                    Text(
                        text = stringResource(R.string.tap_to_browse),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun StorageVolumeCard(
    volume: dev.qtremors.arcile.domain.StorageVolume,
    categoryStorages: List<CategoryStorage>,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val used = volume.totalBytes - volume.freeBytes
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.extraLarge)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        colors = CardDefaults.cardColors(
            containerColor = if (volume.isPrimary) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer,
            contentColor = if (volume.isPrimary) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSecondaryContainer
        ),
        shape = MaterialTheme.shapes.extraLarge
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
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
                                        contentDescription = "Temporary",
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

            Spacer(modifier = Modifier.height(12.dp))

            MultiColorStorageBar(
                totalBytes = volume.totalBytes,
                freeBytes = volume.freeBytes,
                categoryStorages = categoryStorages,
                isCalculating = false // Individual volume status can be secondary or tied to global
            )

            Spacer(modifier = Modifier.height(8.dp))
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
    isCalculating: Boolean = false
) {
    val barHeight = 10.dp
    val barShape = CircleShape

    val hasData = totalBytes > 0
    
    // Global progress for the "revealing" animation when data first appears
    var animationTrigger by remember { mutableStateOf(false) }

    LaunchedEffect(isCalculating, hasData) {
        if (!isCalculating && hasData) {
            animationTrigger = true
        } else if (isCalculating) {
            animationTrigger = false
        }
    }

    val revealProgress by animateFloatAsState(
        targetValue = if (animationTrigger) 1f else 0f,
        animationSpec = tween(1000, easing = LinearEasing),
        label = "storageBarReveal"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(barHeight)
            .clip(barShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .shimmer(visible = isCalculating, highlightColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f))
    ) {
        if (hasData || isCalculating) {
            Box(modifier = Modifier.fillMaxSize()) {
                // Indeterminate Layer (Flowing colors)
                AnimatedVisibility(
                    visible = isCalculating,
                    enter = fadeIn(),
                    exit = fadeOut(animationSpec = tween(1000))
                ) {
                    val infiniteTransition = rememberInfiniteTransition(label = "placeholderFlow")
                    val offset by infiniteTransition.animateFloat(
                        initialValue = 0f,
                        targetValue = 1000f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(3000, easing = LinearEasing),
                            repeatMode = RepeatMode.Restart
                        ),
                        label = "offset"
                    )
                    
                    val placeholderColors = listOf(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                        MaterialTheme.colorScheme.secondary.copy(alpha = 0.4f),
                        MaterialTheme.colorScheme.tertiary.copy(alpha = 0.4f),
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                    )
                    
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.linearGradient(
                                    colors = placeholderColors,
                                    start = Offset(offset, 0f),
                                    end = Offset(offset + 500f, 0f),
                                    tileMode = androidx.compose.ui.graphics.TileMode.Repeated
                                )
                            )
                    )
                }

                // Data Layer (Segments)
                if (hasData && animationTrigger) {
                    Row(modifier = Modifier.fillMaxSize()) {
                        val categorizedBytes = categoryStorages.sumOf { it.sizeBytes }
                        val actualUsedBytes = totalBytes - freeBytes
                        val otherUsedBytes = (actualUsedBytes - categorizedBytes).coerceAtLeast(0)

                        val categoryColors = LocalCategoryColors.current
                        val sortedCategories = categoryStorages.sortedByDescending { it.sizeBytes }
                        
                        sortedCategories.forEach { cat ->
                            if (cat.sizeBytes > 0) {
                                val fraction = cat.sizeBytes.toFloat() / totalBytes.toFloat()
                                val animatedFraction by animateFloatAsState(
                                    targetValue = fraction * revealProgress,
                                    animationSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessLow),
                                    label = "cat_${cat.name}_animation"
                                )
                                val catColor = getCategoryColor(cat.name, categoryColors, MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                                Box(
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .weight(animatedFraction.coerceAtLeast(0.005f)) // Minimum 0.5% weight for visibility
                                        .padding(horizontal = 0.1.dp) // Even smaller gap
                                        .clip(CircleShape)
                                        .background(catColor)
                                )
                            }
                        }

                        if (otherUsedBytes > 0) {
                            val otherFraction = otherUsedBytes.toFloat() / totalBytes.toFloat()
                            val animatedOtherFraction by animateFloatAsState(
                                targetValue = otherFraction * revealProgress,
                                animationSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessLow),
                                label = "other_bytes_animation"
                            )
                            
                            // Dynamic color logic for unindexed volumes (no categories)
                            val otherColor = if (categoryStorages.isEmpty()) {
                                val usedPercent = (actualUsedBytes * 100) / totalBytes
                                when {
                                    usedPercent >= 90 -> Color(0xFFF44336) // Red
                                    usedPercent >= 70 -> Color(0xFFFF9800) // Orange
                                    else -> Color(0xFF4CAF50) // Green
                                }
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            }

                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .weight(animatedOtherFraction.coerceAtLeast(0.005f))
                                    .padding(horizontal = 0.1.dp)
                                    .clip(CircleShape)
                                    .background(otherColor)
                            )
                        }

                        if (freeBytes > 0) {
                            val freeFraction = freeBytes.toFloat() / totalBytes.toFloat()
                            // Free fraction grows oppositely if we want the bar to fill, 
                            // but since it's a Row, if Used parts grow, Free part will naturally shrink.
                            // We need used + free to always = 1.0 weight in the Row.
                            // So we explicitly animate the free fraction to maintain the 1.0 sum.
                            val currentUsedFractions = (categoryStorages.sumOf { it.sizeBytes }.toFloat() + otherUsedBytes.toFloat()) / totalBytes.toFloat()
                            val animatedUsedFractions = currentUsedFractions * revealProgress
                            val animatedFreeFraction = 1f - animatedUsedFractions
                            
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .weight(animatedFreeFraction.coerceAtLeast(0.0001f))
                                    .background(Color.Transparent)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CategoryLegend(categoryStorages: List<CategoryStorage>) {
    val scrollState = rememberScrollState()
    Row(
        modifier = Modifier
            .horizontalScroll(scrollState)
            .fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.space12)
    ) {
        val categoryColors = LocalCategoryColors.current
        val sortedCategories = categoryStorages.sortedByDescending { it.sizeBytes }
        sortedCategories.filter { it.sizeBytes > 0 }.forEach { cat ->
            val catColor = getCategoryColor(cat.name, categoryColors, MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraSmall)
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(catColor)
                )
                Text(
                    text = "${cat.name} ${formatFileSize(cat.sizeBytes)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}
