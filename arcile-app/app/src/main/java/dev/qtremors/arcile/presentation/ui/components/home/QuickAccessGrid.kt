package dev.qtremors.arcile.presentation.ui.components.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import dev.qtremors.arcile.domain.QuickAccessItem
import dev.qtremors.arcile.domain.QuickAccessType

@Composable
fun QuickAccessGrid(
    quickAccessItems: List<QuickAccessItem>,
    onOpenFileBrowser: () -> Unit,
    onNavigateToPath: (String) -> Unit,
    onNavigateToSaf: (String) -> Unit
) {
    fun getIconForLabel(label: String): ImageVector {
        return when (label.lowercase()) {
            "dcim" -> Icons.Default.CameraAlt
            "downloads", "download" -> Icons.Default.Download
            "pictures", "images" -> Icons.Default.Image
            "documents", "docs" -> Icons.Default.Description
            "music", "audio" -> Icons.Default.MusicNote
            "movies", "videos", "video" -> Icons.Default.Movie
            else -> Icons.Default.Folder
        }
    }

    // Include "All Files" as the final fallback action
    val allFilesItem = QuickAccessItem(
        id = "internal_all_files",
        label = "All Files",
        path = "",
        type = QuickAccessType.STANDARD,
        isPinned = true,
        isEnabled = true
    )
    
    val folders = quickAccessItems.filter { it.isPinned } + allFilesItem

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        val rows = folders.chunked(3)

        rows.forEach { rowFolders ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                rowFolders.forEach { folder ->
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        onClick = {
                            if (folder.id == "internal_all_files") {
                                onOpenFileBrowser()
                            } else if (folder.type == QuickAccessType.SAF_TREE) {
                                onNavigateToSaf(folder.path)
                            } else {
                                onNavigateToPath(folder.path)
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = getIconForLabel(folder.label),
                                contentDescription = folder.label,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            val displayLabel = folder.label.trimEnd('/').let { cleaned ->
                                val lastSlash = cleaned.lastIndexOf('/')
                                val lastColon = cleaned.lastIndexOf(':')
                                val index = maxOf(lastSlash, lastColon)
                                if (index != -1 && index < cleaned.length - 1) {
                                    cleaned.substring(index + 1)
                                } else {
                                    cleaned
                                }
                            }
                            Text(text = displayLabel, style = MaterialTheme.typography.labelLarge, maxLines = 1)
                        }
                    }
                }
                
                if (rowFolders.size < 3) {
                    repeat(3 - rowFolders.size) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}
