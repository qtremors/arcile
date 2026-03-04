package dev.qtremors.arcile.presentation.ui

import android.os.Environment
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.qtremors.arcile.domain.CategoryStorage
import dev.qtremors.arcile.domain.FileCategories
import dev.qtremors.arcile.presentation.FileManagerState
import dev.qtremors.arcile.presentation.ui.components.ArcileTopBar
import java.io.File

@Composable
fun HomeScreen(
    state: FileManagerState,
    onOpenFileBrowser: () -> Unit,
    onNavigateToPath: (String) -> Unit,
    onSettingsClick: () -> Unit
) {
    Scaffold(
        topBar = {
            ArcileTopBar(
                title = "Arcile",
                selectionCount = 0,
                showSettingsIcon = true,
                onSettingsClick = onSettingsClick,
                onClearSelection = {},
                onSearchClick = {},
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
                item {
                    StorageSummaryCard(
                        state = state,
                        onClick = onOpenFileBrowser
                    )
                }

                item {
                    Text(
                        text = "Categories",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 8.dp)
                    )
                }
                item { CategoryGrid(state.categoryStorages) }

                item {
                    Text(
                        text = "Folders",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(start = 16.dp, top = 24.dp, end = 16.dp, bottom = 8.dp)
                    )
                }
                item {
                    MainFoldersRow(
                        onOpenFileBrowser = onOpenFileBrowser,
                        onNavigateToPath = onNavigateToPath
                    )
                }

                item {
                    Text(
                        text = "Recent Files",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(start = 16.dp, top = 24.dp, end = 16.dp, bottom = 8.dp)
                    )
                }

                if (state.recentFiles.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No recent files",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    items(state.recentFiles, key = { it.absolutePath }) { file ->
                        FileItemRow(
                            file = file,
                            isSelected = false,
                            onClick = {},
                            onLongClick = {}
                        )
                    }
                }

                item { Spacer(modifier = Modifier.height(16.dp)) }
            }
        }
    }
}

// --- storage card with multi-colored progress bar ---

@Composable
fun StorageSummaryCard(
    state: FileManagerState,
    onClick: () -> Unit
) {
    val total = state.storageInfo?.totalBytes ?: 0L
    val free = state.storageInfo?.freeBytes ?: 0L
    val used = total - free

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        onClick = onClick,
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

            if (total > 0) {
                Spacer(modifier = Modifier.height(16.dp))

                // multi-colored storage bar
                MultiColorStorageBar(
                    totalBytes = total,
                    categoryStorages = state.categoryStorages
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

                // category legend below the bar
                if (state.categoryStorages.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    CategoryLegend(state.categoryStorages)
                }
            } else {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Tap to browse storage",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                )
            }
        }
    }
}

@Composable
fun MultiColorStorageBar(
    totalBytes: Long,
    categoryStorages: List<CategoryStorage>
) {
    val barHeight = 10.dp
    val barShape = RoundedCornerShape(barHeight / 2)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(barHeight)
            .clip(barShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        if (totalBytes > 0 && categoryStorages.isNotEmpty()) {
            Row(modifier = Modifier.fillMaxSize()) {
                categoryStorages.forEach { cat ->
                    if (cat.sizeBytes > 0) {
                        val fraction = cat.sizeBytes.toFloat() / totalBytes.toFloat()
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .weight(fraction.coerceAtLeast(0.005f))
                                .background(cat.color)
                        )
                    }
                }

                // remaining "other used" space (used minus all categories)
                val categorizedBytes = categoryStorages.sumOf { it.sizeBytes }
                val freeBytes = (totalBytes - categorizedBytes).coerceAtLeast(0)
                if (freeBytes > 0) {
                    val freeFraction = freeBytes.toFloat() / totalBytes.toFloat()
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .weight(freeFraction.coerceAtLeast(0.01f))
                            .background(Color.Transparent)
                    )
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
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        categoryStorages.filter { it.sizeBytes > 0 }.forEach { cat ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(cat.color)
                )
                Text(
                    text = "${cat.name} ${formatFileSize(cat.sizeBytes)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                )
            }
        }
    }
}

// --- category grid with colors and sizes ---

@Composable
fun CategoryGrid(categoryStorages: List<CategoryStorage>) {
    // merge categories with their icons and sizes
    data class CategoryDisplay(
        val name: String,
        val icon: ImageVector,
        val color: Color,
        val sizeBytes: Long
    )

    val categories = listOf(
        CategoryDisplay("Images", Icons.Default.Image, FileCategories.Images.color, categoryStorages.find { it.name == "Images" }?.sizeBytes ?: 0),
        CategoryDisplay("Videos", Icons.Default.VideoFile, FileCategories.Videos.color, categoryStorages.find { it.name == "Videos" }?.sizeBytes ?: 0),
        CategoryDisplay("Audio", Icons.Default.AudioFile, FileCategories.Audio.color, categoryStorages.find { it.name == "Audio" }?.sizeBytes ?: 0),
        CategoryDisplay("Docs", Icons.Default.Description, FileCategories.Documents.color, categoryStorages.find { it.name == "Docs" }?.sizeBytes ?: 0),
        CategoryDisplay("Archives", Icons.Default.FolderZip, FileCategories.Archives.color, categoryStorages.find { it.name == "Archives" }?.sizeBytes ?: 0),
        CategoryDisplay("APKs", Icons.Default.Android, FileCategories.APKs.color, categoryStorages.find { it.name == "APKs" }?.sizeBytes ?: 0),
    )

    Column(modifier = Modifier.padding(horizontal = 8.dp)) {
        // first row: 3 items
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            categories.take(3).forEach { cat ->
                CategoryItem(
                    name = cat.name,
                    icon = cat.icon,
                    color = cat.color,
                    sizeBytes = cat.sizeBytes,
                    modifier = Modifier.weight(1f)
                )
            }
        }
        // second row: 3 items
        Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            categories.drop(3).take(3).forEach { cat ->
                CategoryItem(
                    name = cat.name,
                    icon = cat.icon,
                    color = cat.color,
                    sizeBytes = cat.sizeBytes,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
fun CategoryItem(
    name: String,
    icon: ImageVector,
    color: Color,
    sizeBytes: Long,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {}
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .clickable(onClick = onClick)
            .padding(8.dp)
    ) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = color.copy(alpha = 0.15f),
            modifier = Modifier.size(64.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = name,
                modifier = Modifier.padding(16.dp),
                tint = color
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = name,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium
        )
        if (sizeBytes > 0) {
            Text(
                text = formatFileSize(sizeBytes),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// --- folder shortcuts ---

@Composable
fun MainFoldersRow(
    onOpenFileBrowser: () -> Unit,
    onNavigateToPath: (String) -> Unit
) {
    val scrollState = rememberScrollState()
    val root = Environment.getExternalStorageDirectory()

    data class FolderShortcut(val name: String, val icon: ImageVector, val path: String?)

    val folders = listOf(
        FolderShortcut("DCIM", Icons.Default.CameraAlt, File(root, Environment.DIRECTORY_DCIM).absolutePath),
        FolderShortcut("Downloads", Icons.Default.Download, File(root, Environment.DIRECTORY_DOWNLOADS).absolutePath),
        FolderShortcut("Pictures", Icons.Default.Image, File(root, Environment.DIRECTORY_PICTURES).absolutePath),
        FolderShortcut("Documents", Icons.Default.Description, File(root, Environment.DIRECTORY_DOCUMENTS).absolutePath),
        FolderShortcut("Music", Icons.Default.MusicNote, File(root, Environment.DIRECTORY_MUSIC).absolutePath),
        FolderShortcut("Movies", Icons.Default.Movie, File(root, Environment.DIRECTORY_MOVIES).absolutePath),
        FolderShortcut("All Files", Icons.Default.Folder, null)
    )

    Row(
        modifier = Modifier
            .horizontalScroll(scrollState)
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        folders.forEach { folder ->
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceVariant,
                onClick = {
                    if (folder.path != null) {
                        onNavigateToPath(folder.path)
                    } else {
                        onOpenFileBrowser()
                    }
                },
                modifier = Modifier
                    .width(130.dp)
                    .height(48.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(folder.icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    Text(text = folder.name, style = MaterialTheme.typography.labelLarge, maxLines = 1)
                }
            }
        }
    }
}
