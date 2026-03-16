package dev.qtremors.arcile.presentation.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.io.File

@Composable
fun Breadcrumbs(
    currentPath: String,
    storageVolumes: List<dev.qtremors.arcile.domain.StorageVolume>,
    onPathSegmentClick: (String) -> Unit
) {
    val scrollState = rememberScrollState()

    LaunchedEffect(currentPath) {
        scrollState.animateScrollTo(scrollState.maxValue)
    }

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

    Row(
        modifier = Modifier
            .horizontalScroll(scrollState)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // volume root segment
        val isAtRoot = segments.isEmpty() && volumeRootPath.isNotEmpty()
        Text(
            text = volumeName,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (isAtRoot) FontWeight.Bold else FontWeight.Normal,
            color = if (isAtRoot) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.clickable(enabled = !isAtRoot && volumeRootPath.isNotEmpty()) {
                onPathSegmentClick(volumeRootPath)
            }
        )

        var currentBuiltPath = volumeRootPath
        segments.forEachIndexed { index, segment ->
            currentBuiltPath += if (currentBuiltPath.endsWith("/")) segment else "/$segment"
            val isLast = index == segments.size - 1
            val segmentPath = currentBuiltPath

            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = "Navigate to",
                modifier = Modifier.padding(horizontal = 2.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
            )

            Text(
                text = segment,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isLast) FontWeight.Bold else FontWeight.Normal,
                color = if (isLast) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.clickable(enabled = !isLast) {
                    onPathSegmentClick(segmentPath)
                }
            )
        }

        Spacer(modifier = Modifier.width(16.dp))
    }
}
