package dev.qtremors.arcile.presentation.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.qtremors.arcile.presentation.FileManagerState
import dev.qtremors.arcile.presentation.ui.components.ArcileTopBar

@Composable
fun HomeScreen(
    state: FileManagerState,
    onMenuClick: () -> Unit,
    onOpenFileBrowser: () -> Unit
) {
    Scaffold(
        topBar = {
            ArcileTopBar(
                title = "Arcile",
                selectionCount = 0,
                onMenuClick = onMenuClick,
                onClearSelection = {},
                onSearchClick = {}, // TODO: Implement search
                onSortClick = {},
                onActionSelected = {}
            )
        }
    ) { padding ->
        if (state.isLoading) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                item { StorageSummaryCard(state) }

                item {
                    Text(
                        text = "Categories",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 8.dp)
                    )
                }
                item { QuickAccessGrid() }

                item {
                    Text(
                        text = "Folders",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(start = 16.dp, top = 24.dp, end = 16.dp, bottom = 8.dp)
                    )
                }
                item { MainFoldersRow(onOpenFileBrowser) }

                item {
                    Text(
                        text = "Recent Files",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(start = 16.dp, top = 24.dp, end = 16.dp, bottom = 8.dp)
                    )
                }

                items(state.recentFiles, key = { it.absolutePath }) { file ->
                    FileItemRow(
                        file = file,
                        isSelected = false,
                        onClick = {},
                        onLongClick = {}
                    )
                }
                
                item { Spacer(modifier = Modifier.height(16.dp)) }
            }
        }
    }
}

@Composable
fun StorageSummaryCard(state: FileManagerState) {
    val total = state.storageInfo?.totalBytes ?: 1L
    val free = state.storageInfo?.freeBytes ?: 0L
    val used = total - free
    val usedPercent = if (total > 0) used.toFloat() / total.toFloat() else 0f

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
        ),
        shape = MaterialTheme.shapes.extraLarge
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Internal Storage",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Icon(Icons.Default.Storage, contentDescription = null)
            }
            Spacer(modifier = Modifier.height(16.dp))
            LinearProgressIndicator(
                progress = { usedPercent },
                modifier = Modifier.fillMaxWidth().height(8.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                strokeCap = androidx.compose.ui.graphics.StrokeCap.Round // Wait, API level might need check depending on Compose version. Rounded is nice though.
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "${formatFileSize(used)} used",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "${formatFileSize(free)} free",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                )
            }
        }
    }
}

@Composable
fun QuickAccessGrid() {
    val categories = listOf(
        Pair("Images", Icons.Default.Image),
        Pair("Audio", Icons.Default.AudioFile),
        Pair("Video", Icons.Default.VideoFile),
        Pair("Docs", Icons.Default.Description),
        Pair("ZIPs", Icons.Default.FolderZip),
        Pair("Downloads", Icons.Default.Download)
    )

    Column(modifier = Modifier.padding(horizontal = 8.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            categories.take(3).forEach { (name, icon) ->
                CategoryItem(name, icon, modifier = Modifier.weight(1f))
            }
        }
        Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            categories.drop(3).take(3).forEach { (name, icon) ->
                CategoryItem(name, icon, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
fun CategoryItem(name: String, icon: ImageVector, modifier: Modifier = Modifier, onClick: () -> Unit = {}) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .clickable(onClick = onClick)
            .padding(8.dp)
    ) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.secondaryContainer,
            modifier = Modifier.size(64.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = name,
                modifier = Modifier.padding(16.dp),
                tint = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = name, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun MainFoldersRow(onOpenFileBrowser: () -> Unit) {
    val scrollState = rememberScrollState()
    val folders = listOf(
        Pair("DCIM", Icons.Default.CameraAlt),
        Pair("Downloads", Icons.Default.Download),
        Pair("Pictures", Icons.Default.Image),
        Pair("All Files", Icons.Default.Folder)
    )

    Row(
        modifier = Modifier
            .horizontalScroll(scrollState)
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        folders.forEach { (name, icon) ->
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceVariant,
                onClick = onOpenFileBrowser, // For now, all point to general browser to show MVP capability
                modifier = Modifier.width(120.dp)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    Text(text = name, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}
