package dev.qtremors.arcile.presentation.ui

import android.os.Environment
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FilterNone
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import dev.qtremors.arcile.presentation.FileSortOption
import dev.qtremors.arcile.presentation.filterAndSortFiles
import dev.qtremors.arcile.presentation.ui.components.ArcileTopBar
import dev.qtremors.arcile.presentation.ui.components.ToolCard
import dev.qtremors.arcile.presentation.ui.components.ToolItem
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun HomeScreen(
    state: FileManagerState,
    onOpenFileBrowser: () -> Unit,
    onNavigateToPath: (String) -> Unit,
    onOpenFile: (String) -> Unit,
    onCategoryClick: (String) -> Unit,
    onSettingsClick: () -> Unit,
    onNavigateToTools: () -> Unit
) {
    val displayedRecentFiles = remember(state.recentFiles, state.homeSearchQuery, state.homeSortOption) {
        filterAndSortFiles(state.recentFiles, state.homeSearchQuery, state.homeSortOption)
    }

    Scaffold(
        topBar = {
            ArcileTopBar(
                title = "Arcile",
                selectionCount = 0,
                showSettingsIcon = true,
                showSearchAction = false,
                showSortAction = false,
                onSettingsClick = onSettingsClick,
                onClearSelection = {},
                onSearchClick = {},
                onSortClick = {},
                onActionSelected = {}
            )
        }
    ) { padding ->
        if (state.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                LoadingIndicator()
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize()
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
                item { CategoryGrid(state.categoryStorages, onCategoryClick) }

                item {
                    Text(
                        text = "Folders",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(start = 16.dp, top = 24.dp, end = 16.dp, bottom = 8.dp)
                    )
                }
                item {
                    MainFoldersGrid(
                        onOpenFileBrowser = onOpenFileBrowser,
                        onNavigateToPath = onNavigateToPath
                    )
                }

                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, top = 24.dp, end = 16.dp, bottom = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Utilities",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        TextButton(onClick = onNavigateToTools) {
                            Text("Show All")
                        }
                    }
                }

                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Box(modifier = Modifier.weight(1f)) {
                                ToolCard(ToolItem("FTP Server", Icons.Default.WifiTethering))
                            }
                            Box(modifier = Modifier.weight(1f)) {
                                ToolCard(ToolItem("Analyze Storage", Icons.Default.PieChart))
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Box(modifier = Modifier.weight(1f)) {
                                ToolCard(ToolItem("Clean Junk", Icons.Default.CleaningServices))
                            }
                            Box(modifier = Modifier.weight(1f)) {
                                ToolCard(ToolItem("Duplicates", Icons.Default.FilterNone))
                            }
                        }
                    }
                }

                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, top = 24.dp, end = 16.dp, bottom = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Recent Files",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                if (displayedRecentFiles.isEmpty()) {
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
                    val formatter = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
                    items(displayedRecentFiles, key = { it.absolutePath }) { file ->
                        FileItemRow(
                            file = file,
                            formattedDate = formatter.format(Date(file.lastModified)),
                            isSelected = false,
                            onClick = { onOpenFile(file.absolutePath) },
                            onLongClick = {}
                        )
                    }
                }

                item { Spacer(modifier = Modifier.height(16.dp)) }
            }
        }
    }
}
}

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
                Icon(Icons.Default.Storage, contentDescription = "Storage")
            }

            if (total > 0) {
                Spacer(modifier = Modifier.height(16.dp))

                MultiColorStorageBar(
                    totalBytes = total,
                    freeBytes = free,
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
    freeBytes: Long,
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
                val categorizedBytes = categoryStorages.sumOf { it.sizeBytes }
                val actualUsedBytes = totalBytes - freeBytes
                val otherUsedBytes = (actualUsedBytes - categorizedBytes).coerceAtLeast(0)

                val sortedCategories = categoryStorages.sortedByDescending { it.sizeBytes }
                sortedCategories.forEach { cat ->
                    if (cat.sizeBytes > 0) {
                        val fraction = cat.sizeBytes.toFloat() / totalBytes.toFloat()
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .weight(fraction.coerceAtLeast(0.005f))
                                .background(Color(cat.color))
                        )
                    }
                }

                if (otherUsedBytes > 0) {
                    val otherFraction = otherUsedBytes.toFloat() / totalBytes.toFloat()
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .weight(otherFraction.coerceAtLeast(0.005f))
                            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                    )
                }

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
        val sortedCategories = categoryStorages.sortedByDescending { it.sizeBytes }
        sortedCategories.filter { it.sizeBytes > 0 }.forEach { cat ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(Color(cat.color))
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

@Composable
fun CategoryGrid(
    categoryStorages: List<CategoryStorage>,
    onCategoryClick: (String) -> Unit
) {
    data class CategoryDisplay(
        val name: String,
        val icon: ImageVector,
        val color: Color,
        val sizeBytes: Long
    )

    val categories = listOf(
        CategoryDisplay("Images", Icons.Default.Image, Color(FileCategories.Images.color), categoryStorages.find { it.name == "Images" }?.sizeBytes ?: 0),
        CategoryDisplay("Videos", Icons.Default.VideoFile, Color(FileCategories.Videos.color), categoryStorages.find { it.name == "Videos" }?.sizeBytes ?: 0),
        CategoryDisplay("Audio", Icons.Default.AudioFile, Color(FileCategories.Audio.color), categoryStorages.find { it.name == "Audio" }?.sizeBytes ?: 0),
        CategoryDisplay("Docs", Icons.Default.Description, Color(FileCategories.Documents.color), categoryStorages.find { it.name == "Docs" }?.sizeBytes ?: 0),
        CategoryDisplay("Archives", Icons.Default.FolderZip, Color(FileCategories.Archives.color), categoryStorages.find { it.name == "Archives" }?.sizeBytes ?: 0),
        CategoryDisplay("APKs", Icons.Default.Android, Color(FileCategories.APKs.color), categoryStorages.find { it.name == "APKs" }?.sizeBytes ?: 0),
    ).sortedByDescending { it.sizeBytes }

    Column(modifier = Modifier.padding(horizontal = 8.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            categories.take(3).forEach { cat ->
                CategoryItem(
                    name = cat.name,
                    icon = cat.icon,
                    color = cat.color,
                    sizeBytes = cat.sizeBytes,
                    modifier = Modifier.weight(1f),
                    onClick = { onCategoryClick(cat.name) }
                )
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            categories.drop(3).take(3).forEach { cat ->
                CategoryItem(
                    name = cat.name,
                    icon = cat.icon,
                    color = cat.color,
                    sizeBytes = cat.sizeBytes,
                    modifier = Modifier.weight(1f),
                    onClick = { onCategoryClick(cat.name) }
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
            color = MaterialTheme.colorScheme.secondaryContainer,
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

@Composable
fun MainFoldersGrid(
    onOpenFileBrowser: () -> Unit,
    onNavigateToPath: (String) -> Unit
) {
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

