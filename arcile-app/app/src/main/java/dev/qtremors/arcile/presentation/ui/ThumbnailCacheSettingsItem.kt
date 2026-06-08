package dev.qtremors.arcile.presentation.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil.annotation.ExperimentalCoilApi
import coil.imageLoader
import dev.qtremors.arcile.core.ui.R
import dev.qtremors.arcile.image.GlobalThumbnailFailureCache
import dev.qtremors.arcile.image.GlobalThumbnailLoadStateStore
import dev.qtremors.arcile.shared.ui.rememberArcileHaptics
import dev.qtremors.arcile.utils.formatFileSize
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalCoilApi::class)
@Composable
fun ThumbnailCacheSettingsItem() {
    val context = LocalContext.current
    val haptics = rememberArcileHaptics()
    val coroutineScope = rememberCoroutineScope()
    var stats by remember { mutableStateOf(loadThumbnailCacheStats(context)) }

    ListItem(
        headlineContent = { Text(stringResource(R.string.settings_thumbnail_cache)) },
        supportingContent = {
            Text(
                stringResource(
                    R.string.settings_thumbnail_cache_stats,
                    formatFileSize(stats.diskBytes),
                    stats.loadedCount,
                    stats.failedCount,
                    stats.inFlightCount
                )
            )
        },
        trailingContent = {
            TextButton(
                onClick = {
                    coroutineScope.launch {
                        haptics.selectionChanged()
                        context.imageLoader.memoryCache?.clear()
                        withContext(Dispatchers.IO) {
                            context.imageLoader.diskCache?.clear()
                            GlobalThumbnailFailureCache.clear()
                            GlobalThumbnailLoadStateStore.clear()
                        }
                        stats = withContext(Dispatchers.IO) { loadThumbnailCacheStats(context) }
                    }
                }
            ) {
                Text(stringResource(R.string.settings_clear_thumbnail_cache))
            }
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clip(MaterialTheme.shapes.medium)
    )
    HorizontalDivider(
        color = MaterialTheme.colorScheme.outlineVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
    )
}

private data class ThumbnailCacheStats(
    val diskBytes: Long,
    val loadedCount: Int,
    val failedCount: Int,
    val inFlightCount: Int
)

private fun loadThumbnailCacheStats(context: android.content.Context): ThumbnailCacheStats {
    val loadStateStats = GlobalThumbnailLoadStateStore.stats()
    return ThumbnailCacheStats(
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
