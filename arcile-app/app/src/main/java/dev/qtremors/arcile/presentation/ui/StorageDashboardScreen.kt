package dev.qtremors.arcile.presentation.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.qtremors.arcile.ui.theme.ExpressiveSquircleShape
import dev.qtremors.arcile.ui.theme.LocalCategoryColors
import androidx.compose.ui.graphics.Color
import dev.qtremors.arcile.utils.formatFileSize
import dev.qtremors.arcile.utils.getCategoryColor
import dev.qtremors.arcile.presentation.home.HomeState
import dev.qtremors.arcile.presentation.ui.MultiColorStorageBar
import dev.qtremors.arcile.domain.CategoryStorage
import dev.qtremors.arcile.domain.isIndexed

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StorageDashboardScreen(
    state: HomeState,
    selectedVolumeId: String? = null,
    onNavigateBack: () -> Unit,
    onCategoryClick: (String, String?) -> Unit
) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    val volumes = if (selectedVolumeId != null) {
        state.storageInfo?.volumes?.filter { it.id == selectedVolumeId }.orEmpty()
    } else {
        state.storageInfo?.volumes?.filter { it.kind.isIndexed }.orEmpty()
    }
    val categoryStorages = selectedVolumeId?.let { state.categoryStoragesByVolume[it] } ?: state.categoryStorages
    val totalBytes = volumes.sumOf { it.totalBytes }
    val freeBytes = volumes.sumOf { it.freeBytes }
    val hasTemporaryMountedVolumes = state.allStorageVolumes.any { !it.kind.isIndexed }
    
    val categoryColors = LocalCategoryColors.current
    val unassignedColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)

    val sortedCategories = categoryStorages.sortedByDescending { it.sizeBytes }
    val displayCategories = sortedCategories.map { cat ->
        val icon = when (cat.name) {
            "Images" -> Icons.Default.Image
            "Videos" -> Icons.Default.VideoFile
            "Audio" -> Icons.Default.AudioFile
            "Docs" -> Icons.Default.Description
            "Archives" -> Icons.Default.FolderZip
            "APKs" -> Icons.Default.Android
            else -> Icons.Default.Description
        }
        val color = getCategoryColor(cat.name, categoryColors, unassignedColor)
        Triple(cat, icon, color)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Storage Dashboard") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                scrollBehavior = scrollBehavior
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 32.dp)
        ) {
            if (selectedVolumeId == null && hasTemporaryMountedVolumes) {
                item {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        shape = ExpressiveSquircleShape,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh
                    ) {
                        Text(
                            text = "Temporary external storage is mounted but excluded from indexed dashboard insights.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            }

            items(volumes) { volume ->
                val used = volume.totalBytes - volume.freeBytes
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = volume.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${formatFileSize(used)} / ${formatFileSize(volume.totalBytes)}",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    if (volume.totalBytes > 0) {
                        MultiColorStorageBar(
                            totalBytes = volume.totalBytes,
                            freeBytes = volume.freeBytes,
                            categoryStorages = state.categoryStoragesByVolume[volume.id] ?: emptyList()
                        )
                    }
                }
                androidx.compose.material3.HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                    thickness = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant
                )
            }

            item {
                Text(
                    text = "Categories",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
                )
            }

            items(displayCategories.size) { index ->
                val (cat, icon, color) = displayCategories[index]
                if (cat.sizeBytes > 0 || totalBytes == 0L) {
                    val percentage = if (totalBytes > 0) {
                        (cat.sizeBytes.toFloat() / totalBytes.toFloat() * 100).toInt()
                    } else {
                        0
                    }
                    
                    CategoryListTile(
                        name = cat.name,
                        sizeBytes = cat.sizeBytes,
                        percentage = percentage,
                        icon = icon,
                        color = color,
                        onClick = { onCategoryClick(cat.name, selectedVolumeId) }
                    )
                }
            }
            
            item {
                val categorizedBytes = categoryStorages.sumOf { it.sizeBytes }
                val actualUsedBytes = totalBytes - freeBytes
                val otherUsedBytes = (actualUsedBytes - categorizedBytes).coerceAtLeast(0)
                
                if (otherUsedBytes > 0) {
                    val percentage = if (totalBytes > 0) {
                        (otherUsedBytes.toFloat() / totalBytes.toFloat() * 100).toInt()
                    } else {
                        0
                    }
                    
                    CategoryListTile(
                        name = "Other Files & System",
                        sizeBytes = otherUsedBytes,
                        percentage = percentage,
                        icon = Icons.Default.Android,
                        color = unassignedColor,
                        onClick = { } // System/Other typically isn't browsable via MediaStore easily
                    )
                }
            }
        }
    }
}

@Composable
fun CategoryListTile(
    name: String,
    sizeBytes: Long,
    percentage: Int,
    icon: ImageVector,
    color: Color,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(ExpressiveSquircleShape)
            .clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = ExpressiveSquircleShape
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = name,
                    tint = color,
                    modifier = Modifier.size(24.dp)
                )
            }
            
            Spacer(modifier = Modifier.size(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "$percentage%",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            Text(
                text = formatFileSize(sizeBytes),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
