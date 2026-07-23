package dev.qtremors.arcile.core.ui
import dev.qtremors.arcile.core.ui.R
import androidx.compose.ui.res.stringResource

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.qtremors.arcile.core.ui.theme.bounceClickable
import java.io.File

@Composable
fun Breadcrumbs(
    currentPath: String,
    storageVolumes: List<dev.qtremors.arcile.core.storage.domain.StorageVolume>,
    onPathSegmentClick: (String) -> Unit
) {
    val listState = rememberLazyListState()

    val currentVolume = remember(currentPath, storageVolumes) {
        storageVolumes.find { volume ->
            currentPath == volume.path || currentPath.startsWith(volume.path + java.io.File.separator)
        }
    }

    val volumeRootPath = currentVolume?.path ?: ""
    val volumeName = currentVolume?.name ?: "Storage"

    // strip the storage root prefix to get relative segments
    val relativePath = if (volumeRootPath.isNotEmpty() && currentPath.startsWith(volumeRootPath)) {
        currentPath.removePrefix(volumeRootPath)
    } else {
        currentPath
    }

    val segments = relativePath.split("/").filter { it.isNotEmpty() }
    val segmentPaths = remember(segments, volumeRootPath) {
        var builtPath = volumeRootPath
        segments.map { segment ->
            builtPath += if (builtPath.endsWith("/")) segment else "/$segment"
            segment to builtPath
        }
    }

    LaunchedEffect(currentPath, segmentPaths.size) {
        listState.scrollToItem(segmentPaths.size + 1)
    }

    LazyRow(
        state = listState,
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp),
        contentPadding = PaddingValues(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        item(key = "volume:$volumeRootPath") {
            val isAtRoot = segmentPaths.isEmpty() && volumeRootPath.isNotEmpty()
            Text(
                text = volumeName,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = if (isAtRoot) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier
                    .padding(horizontal = 4.dp, vertical = 2.dp)
                    .clip(CircleShape)
                    .bounceClickable(enabled = !isAtRoot && volumeRootPath.isNotEmpty()) {
                        onPathSegmentClick(volumeRootPath)
                    }
            )
        }

        itemsIndexed(
            items = segmentPaths,
            key = { _, item -> item.second }
        ) { index, (segment, segmentPath) ->
            Box(contentAlignment = Alignment.CenterStart) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = stringResource(R.string.action_navigate_to),
                    modifier = Modifier.padding(horizontal = 2.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                )
                val isLast = index == segmentPaths.lastIndex
                Text(
                    text = segment,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = if (isLast) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier
                        .padding(start = 28.dp, end = 4.dp, top = 2.dp, bottom = 2.dp)
                        .clip(CircleShape)
                        .bounceClickable(enabled = !isLast) {
                            onPathSegmentClick(segmentPath)
                        }
                )
            }
        }

        item(key = "end-spacer") {
            Spacer(modifier = Modifier.width(16.dp))
        }
    }
}
