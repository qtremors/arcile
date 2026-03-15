package dev.qtremors.arcile.presentation.ui

import android.os.Environment
import androidx.compose.animation.animateContentSize
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
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.combinedClickable
import dev.qtremors.arcile.presentation.ui.components.TopBarAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.material.icons.filled.SdCard
import dev.qtremors.arcile.ui.theme.ExpressiveSquircleShape
import dev.qtremors.arcile.ui.theme.ExpressivePillShape
import dev.qtremors.arcile.ui.theme.LocalCategoryColors
import dev.qtremors.arcile.presentation.ui.components.lists.FileItemRow
import dev.qtremors.arcile.domain.CategoryStorage
import dev.qtremors.arcile.domain.FileCategories
import dev.qtremors.arcile.presentation.home.HomeState
import dev.qtremors.arcile.presentation.filterAndSortFiles
import dev.qtremors.arcile.presentation.ui.components.ArcileTopBar
import dev.qtremors.arcile.presentation.ui.components.ToolCard
import dev.qtremors.arcile.presentation.ui.components.ToolItem
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import dev.qtremors.arcile.presentation.ui.components.SearchTopBar
import dev.qtremors.arcile.presentation.ui.components.SearchFiltersBottomSheet
import dev.qtremors.arcile.domain.SearchFilters
import dev.qtremors.arcile.utils.formatFileSize
import dev.qtremors.arcile.utils.getCategoryColor
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.LifecycleResumeEffect
import dev.qtremors.arcile.domain.StorageKind
import dev.qtremors.arcile.domain.isIndexed
import dev.qtremors.arcile.domain.showTemporaryStorageBadge
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material.icons.filled.Info

/**
 * Dashboard screen shown when the app first launches.
 *
 * Displays a storage summary card, per-category storage breakdown, quick-access folder
 * shortcuts, a utilities tray, and a recent-files list.
 *
 * @param state Current [HomeState] providing storage info, recent files, and category data.
 * @param onOpenFileBrowser Invoked when the user wants to browse all files from the storage root.
 * @param onNavigateToPath Invoked when the user taps a quick-access folder shortcut.
 * @param onOpenFile Invoked when the user taps a recent file to open it externally.
 * @param onCategoryClick Invoked with the category name when the user taps a category tile.
 * @param onSettingsClick Navigates to the Settings screen.
 * @param onNavigateToTools Navigates to the Tools screen.
 * @param onNavigateToAbout Navigates to the About screen.
 * @param onNavigateToTrash Navigates to the Trash screen.
 * @param onNavigateToRecentFiles Navigates to the full Recent Files screen.
 * @param onOpenStorageDashboard Navigates to the Storage Dashboard screen.
 * @param onSearchQueryChange Propagates search query changes to the ViewModel.
 * @param onSearchFiltersChange Propagates updated search filter selections to the ViewModel.
 * @param onToggleSearchFilterMenu Opens or closes the search filter bottom sheet.
 */

@Composable
fun StorageClassificationPrompt(
    volume: dev.qtremors.arcile.domain.StorageVolume,
    onClassify: (dev.qtremors.arcile.domain.StorageKind) -> Unit,
    onDecideLater: () -> Unit
) {
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        shape = ExpressiveSquircleShape,
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "New Storage Detected",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Text(
                text = "How should \"${volume.name}\" be treated?",
                style = MaterialTheme.typography.bodyMedium
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(modifier = Modifier.weight(1f)) {
                    Button(
                        onClick = { onClassify(StorageKind.SD_CARD) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = ExpressivePillShape
                    ) {
                        Text("SD Card", maxLines = 1)
                    }
                    Text(
                        "Permanent, indexed, supports trash",
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(top = 4.dp),
                        textAlign = TextAlign.Center
                    )
                }
                
                Column(modifier = Modifier.weight(1f)) {
                    OutlinedButton(
                        onClick = { onClassify(StorageKind.OTG) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = ExpressivePillShape
                    ) {
                        Text("OTG / USB", maxLines = 1)
                    }
                    Text(
                        "Temporary, browsable only, no trash",
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(top = 4.dp),
                        textAlign = TextAlign.Center
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            TextButton(
                onClick = onDecideLater,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            ) {
                Text("Decide later")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun HomeScreen(
    state: HomeState,
    onOpenFileBrowser: () -> Unit,
    onNavigateToPath: (String) -> Unit,
    onOpenFile: (String) -> Unit,
    onCategoryClick: (String) -> Unit,
    onSettingsClick: () -> Unit,
    onNavigateToTools: () -> Unit,
    onNavigateToAbout: () -> Unit,
    onNavigateToTrash: () -> Unit,
    onNavigateToRecentFiles: () -> Unit,
    onOpenStorageDashboard: (String?) -> Unit,
    onSearchQueryChange: (String) -> Unit = {},
    onSearchFiltersChange: (SearchFilters) -> Unit = {},
    onToggleSearchFilterMenu: (Boolean) -> Unit = {},
    onRefresh: () -> Unit = {},
    onResumeRefresh: () -> Unit = {},
    onSetVolumeClassification: (String, dev.qtremors.arcile.domain.StorageKind) -> Unit = { _, _ -> },
    onHideClassificationPrompt: (String) -> Unit = {}
) {
    LifecycleResumeEffect(Unit) {
        onResumeRefresh()
        onPauseOrDispose { }
    }

    val displayedRecentFiles = remember(state.recentFiles, state.homeSearchQuery, state.homeSortOption) {
        val cal = java.util.Calendar.getInstance()
        cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
        cal.set(java.util.Calendar.MINUTE, 0)
        cal.set(java.util.Calendar.SECOND, 0)
        cal.set(java.util.Calendar.MILLISECOND, 0)
        val todayStart = cal.timeInMillis
        val todayFiles = state.recentFiles.filter { it.lastModified >= todayStart }
        filterAndSortFiles(todayFiles, state.homeSearchQuery, state.homeSortOption)
    }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            ArcileTopBar(
                title = "Arcile",
                selectionCount = 0,
                showSettingsIcon = true,
                showSearchAction = false,
                showSortAction = false,
                    showNewFolderAction = false,
                    showSettingsMenuAction = false,
                    showAboutAction = true,
                    scrollBehavior = scrollBehavior,
                    onSettingsClick = onSettingsClick,
                    onClearSelection = {},
                    onSearchClick = {},
                    onSortClick = {},
                    onActionSelected = { action ->
                        when (action) {
                            TopBarAction.Settings -> onSettingsClick()
                            TopBarAction.About -> onNavigateToAbout()
                            else -> {}
                        }
                    }
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
            val pullRefreshState = rememberPullToRefreshState()
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                PullToRefreshBox(
                    isRefreshing = state.isPullToRefreshing,
                    onRefresh = onRefresh,
                    state = pullRefreshState,
                    modifier = Modifier.fillMaxSize(),
                    indicator = {
                        val pullDistance = pullRefreshState.distanceFraction
                        val yOffset = (-40.dp + (80.dp * pullDistance)).coerceIn(-40.dp, 40.dp)

                        if (state.isPullToRefreshing || pullDistance > 0f) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopCenter)
                                    .graphicsLayer {
                                        translationY = if (state.isPullToRefreshing) 40.dp.toPx() else yOffset.toPx()
                                        alpha = if (state.isPullToRefreshing) 1f else pullDistance.coerceIn(0f, 1f)
                                    }
                                    .padding(top = 8.dp)
                            ) {
                                Card(
                                    shape = androidx.compose.foundation.shape.CircleShape,
                                    elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
                                ) {
                                    Box(
                                        modifier = Modifier.padding(10.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        LoadingIndicator(modifier = Modifier.size(24.dp))
                                    }
                                }
                            }
                        }
                    }
                ) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize()
                ) {

                if (state.showClassificationPrompt && state.unclassifiedVolumes.isNotEmpty()) {
                    val volume = state.unclassifiedVolumes.first()
                    item(key = "classification_prompt_${volume.id}") {
                        StorageClassificationPrompt(
                            volume = volume,
                            onClassify = { kind -> onSetVolumeClassification(volume.storageKey, kind) },
                            onDecideLater = { onHideClassificationPrompt(volume.storageKey) }
                        )
                    }
                }

                item {
                    StorageSummaryCard(
                        state = state,
                        onNavigateToPath = onNavigateToPath,
                        onOpenStorageDashboard = onOpenStorageDashboard,
                        onOpenFileBrowser = onOpenFileBrowser
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
                                ToolCard(ToolItem("Trash Bin", Icons.Default.Delete, isImplemented = true), onClick = onNavigateToTrash)
                            }
                            Box(modifier = Modifier.weight(1f)) {
                                ToolCard(ToolItem("Analyze Storage", Icons.Default.PieChart, isImplemented = false))
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Box(modifier = Modifier.weight(1f)) {
                                ToolCard(ToolItem("FTP Server", Icons.Default.WifiTethering, isImplemented = false))
                            }
                            Box(modifier = Modifier.weight(1f)) {
                                ToolCard(ToolItem("Clean Junk", Icons.Default.CleaningServices, isImplemented = false))
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
                        TextButton(onClick = onNavigateToRecentFiles) {
                            Text("See All")
                        }
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
                                color = MaterialTheme.colorScheme.onSurface
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
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun StorageSummaryCard(
    state: HomeState,
    onNavigateToPath: (String) -> Unit,
    onOpenStorageDashboard: (String?) -> Unit,
    onOpenFileBrowser: () -> Unit
) {
    val volumes = state.allStorageVolumes.ifEmpty { state.storageInfo?.volumes ?: emptyList() }
    
    if (volumes.size > 1) {
        // Multi-storage layout
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            volumes.forEach { volume ->
                StorageVolumeCard(
                    volume = volume,
                    categoryStorages = state.categoryStoragesByVolume[volume.id] ?: emptyList(),
                    onClick = { onNavigateToPath(volume.path) },
                    onLongClick = {
                        if (volume.kind.isIndexed) {
                            onOpenStorageDashboard(volume.id)
                        }
                    }
                )
                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    } else {
        // Single storage layout (backward compatible UI)
        val primaryVolume = volumes.find { it.isPrimary } ?: volumes.firstOrNull()
        val total = primaryVolume?.totalBytes ?: 0L
        val free = primaryVolume?.freeBytes ?: 0L
        val used = total - free

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .clip(ExpressiveSquircleShape)
                    .combinedClickable(
                        onClick = onOpenFileBrowser,
                        onLongClick = { onOpenStorageDashboard(primaryVolume?.id) }
                    ),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            ),
            shape = ExpressiveSquircleShape
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = primaryVolume?.name ?: "Internal Storage",
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
                            color = MaterialTheme.colorScheme.onPrimaryContainer
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
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun StorageVolumeCard(
    volume: dev.qtremors.arcile.domain.StorageVolume,
    categoryStorages: List<CategoryStorage>,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val used = volume.totalBytes - volume.freeBytes
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(ExpressiveSquircleShape)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        colors = CardDefaults.cardColors(
            containerColor = if (volume.isPrimary) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer,
            contentColor = if (volume.isPrimary) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSecondaryContainer
        ),
        shape = ExpressiveSquircleShape
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = volume.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        if (volume.kind.showTemporaryStorageBadge) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                shape = CircleShape,
                                modifier = Modifier.size(24.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = if (volume.kind == StorageKind.OTG) Icons.Default.Usb else Icons.Default.Info,
                                        contentDescription = "Temporary",
                                        modifier = Modifier.size(14.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                    if (volume.kind.showTemporaryStorageBadge) {
                        Text(
                            text = if (volume.kind == StorageKind.OTG) "Temporary USB" else "Unclassified External",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }
                Icon(
                    imageVector = when (volume.kind) {
                        StorageKind.INTERNAL -> Icons.Default.Storage
                        StorageKind.SD_CARD -> Icons.Default.SdCard
                        StorageKind.OTG -> Icons.Default.Usb
                        StorageKind.EXTERNAL_UNCLASSIFIED -> Icons.Default.SdCard
                    },
                    contentDescription = volume.name,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            MultiColorStorageBar(
                totalBytes = volume.totalBytes,
                freeBytes = volume.freeBytes,
                categoryStorages = categoryStorages
            )

            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "${formatFileSize(used)} / ${formatFileSize(volume.totalBytes)}",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium
                )
                val percent = if (volume.totalBytes > 0) (used * 100 / volume.totalBytes) else 0
                Text(
                    text = "$percent%",
                    style = MaterialTheme.typography.bodySmall
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
    val barShape = ExpressivePillShape

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

                val categoryColors = LocalCategoryColors.current
                val sortedCategories = categoryStorages.sortedByDescending { it.sizeBytes }
                sortedCategories.forEach { cat ->
                    if (cat.sizeBytes > 0) {
                        val fraction = cat.sizeBytes.toFloat() / totalBytes.toFloat()
                        val catColor = getCategoryColor(cat.name, categoryColors, MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .weight(fraction.coerceAtLeast(0.005f))
                                .background(catColor)
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
        val categoryColors = LocalCategoryColors.current
        val sortedCategories = categoryStorages.sortedByDescending { it.sizeBytes }
        sortedCategories.filter { it.sizeBytes > 0 }.forEach { cat ->
            val catColor = getCategoryColor(cat.name, categoryColors, MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(catColor)
                )
                Text(
                    text = "${cat.name} ${formatFileSize(cat.sizeBytes)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface
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

    val catColors = LocalCategoryColors.current

    val categories = listOf(
        CategoryDisplay("Images", Icons.Default.Image, catColors.images, categoryStorages.find { it.name == "Images" }?.sizeBytes ?: 0),
        CategoryDisplay("Videos", Icons.Default.VideoFile, catColors.videos, categoryStorages.find { it.name == "Videos" }?.sizeBytes ?: 0),
        CategoryDisplay("Audio", Icons.Default.AudioFile, catColors.audio, categoryStorages.find { it.name == "Audio" }?.sizeBytes ?: 0),
        CategoryDisplay("Docs", Icons.Default.Description, catColors.docs, categoryStorages.find { it.name == "Docs" }?.sizeBytes ?: 0),
        CategoryDisplay("Archives", Icons.Default.FolderZip, catColors.archives, categoryStorages.find { it.name == "Archives" }?.sizeBytes ?: 0),
        CategoryDisplay("APKs", Icons.Default.Android, catColors.apks, categoryStorages.find { it.name == "APKs" }?.sizeBytes ?: 0),
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
    val interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.90f else 1f,
        animationSpec = spring(
            dampingRatio = 0.8f,
            stiffness = 380f
        ),
        label = "categoryScale"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
            .clickable(
                interactionSource = interactionSource,
                indication = androidx.compose.foundation.LocalIndication.current,
                onClick = onClick
            )
            .padding(8.dp)
    ) {
        Surface(
            shape = ExpressiveSquircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
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
                        shape = ExpressivePillShape,
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

