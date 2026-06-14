package dev.qtremors.arcile.feature.storagecleaner.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil.annotation.ExperimentalCoilApi
import coil.imageLoader
import dev.qtremors.arcile.core.ui.R
import dev.qtremors.arcile.image.GlobalThumbnailFailureCache
import dev.qtremors.arcile.image.GlobalThumbnailLoadStateStore
import dev.qtremors.arcile.image.GlobalThumbnailStatePersistence
import dev.qtremors.arcile.shared.ui.rememberArcileHaptics
import dev.qtremors.arcile.ui.theme.bodyLargeMedium
import dev.qtremors.arcile.utils.formatFileSize
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalCoilApi::class)
@Composable
internal fun CleanerThumbnailCacheCard() {
    val context = LocalContext.current
    val haptics = rememberArcileHaptics()
    val coroutineScope = rememberCoroutineScope()
    var stats by remember { mutableStateOf(loadCleanerThumbnailCacheStats(context)) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        shape = MaterialTheme.shapes.extraLarge
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Image,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.settings_thumbnail_cache),
                    style = MaterialTheme.typography.bodyLargeMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = stringResource(
                        R.string.settings_thumbnail_cache_stats,
                        formatFileSize(stats.diskBytes),
                        stats.loadedCount,
                        stats.failedCount,
                        stats.inFlightCount
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            TextButton(
                onClick = {
                    coroutineScope.launch {
                        haptics.selectionChanged()
                        context.imageLoader.memoryCache?.clear()
                        withContext(Dispatchers.IO) {
                            context.imageLoader.diskCache?.clear()
                            GlobalThumbnailFailureCache.clear()
                            GlobalThumbnailLoadStateStore.clear()
                            GlobalThumbnailStatePersistence.delegate?.clear()
                        }
                        stats = withContext(Dispatchers.IO) {
                            loadCleanerThumbnailCacheStats(context)
                        }
                    }
                }
            ) {
                Text(stringResource(R.string.settings_clear_thumbnail_cache))
            }
        }
    }
}

private data class CleanerThumbnailCacheStats(
    val diskBytes: Long,
    val loadedCount: Int,
    val failedCount: Int,
    val inFlightCount: Int
)

private fun loadCleanerThumbnailCacheStats(context: android.content.Context): CleanerThumbnailCacheStats {
    val loadStateStats = GlobalThumbnailLoadStateStore.stats()
    return CleanerThumbnailCacheStats(
        diskBytes = context.cacheDir.resolve("image_cache").directorySize(),
        loadedCount = loadStateStats.loadedCount,
        failedCount = loadStateStats.failedCount,
        inFlightCount = loadStateStats.inFlightCount
    )
}

private fun File.directorySize(): Long {
    if (!exists()) return 0L
    if (isFile) return length()
    return listFiles()?.sumOf { it.directorySize() } ?: 0L
}
