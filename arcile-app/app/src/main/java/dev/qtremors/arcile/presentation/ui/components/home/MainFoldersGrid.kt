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

@Composable
fun MainFoldersGrid(
    standardFolders: Map<String, String?>,
    onOpenFileBrowser: () -> Unit,
    onNavigateToPath: (String) -> Unit
) {
    data class FolderShortcut(val name: String, val icon: ImageVector, val path: String?)

    val folders = listOf(
        FolderShortcut("DCIM", Icons.Default.CameraAlt, standardFolders["DCIM"]),
        FolderShortcut("Downloads", Icons.Default.Download, standardFolders["Downloads"]),
        FolderShortcut("Pictures", Icons.Default.Image, standardFolders["Pictures"]),
        FolderShortcut("Documents", Icons.Default.Description, standardFolders["Documents"]),
        FolderShortcut("Music", Icons.Default.MusicNote, standardFolders["Music"]),
        FolderShortcut("Movies", Icons.Default.Movie, standardFolders["Movies"]),
        FolderShortcut("All Files", Icons.Default.Folder, null)
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        val rows = listOf(
            folders.subList(0, 3), 
            folders.subList(3, 6), 
            folders.subList(6, 7)
        )

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
                            if (folder.path != null) {
                                onNavigateToPath(folder.path)
                            } else {
                                onOpenFileBrowser()
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
                            Icon(folder.icon, contentDescription = folder.name, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                            Text(text = folder.name, style = MaterialTheme.typography.labelLarge, maxLines = 1)
                        }
                    }
                }
                
                // Add invisible spacer blocks to balance rows that aren't fully populated
                if (rowFolders.size < 3) {
                    repeat(3 - rowFolders.size) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}
