package dev.qtremors.arcile.presentation.ui

import android.os.Environment
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.History
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.geometry.Offset
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
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.material.icons.filled.SdCard
import dev.qtremors.arcile.ui.theme.LocalCategoryColors
import dev.qtremors.arcile.ui.theme.spacing
import dev.qtremors.arcile.ui.theme.titleLargeBold
import dev.qtremors.arcile.ui.theme.titleMediumBold
import dev.qtremors.arcile.ui.theme.bodyMediumMedium
import dev.qtremors.arcile.ui.theme.bodySmallMedium
import dev.qtremors.arcile.presentation.ui.components.lists.FileItemRow
import dev.qtremors.arcile.domain.CategoryStorage
import dev.qtremors.arcile.domain.FileCategories
import dev.qtremors.arcile.presentation.home.HomeState
import dev.qtremors.arcile.presentation.filterAndSortFiles
import dev.qtremors.arcile.presentation.ui.components.ArcileTopBar
import dev.qtremors.arcile.presentation.ui.components.ToolCard
import dev.qtremors.arcile.presentation.ui.components.ToolItem
import dev.qtremors.arcile.presentation.ui.components.home.StorageSummaryCard
import dev.qtremors.arcile.presentation.ui.components.home.CategoryGrid
import dev.qtremors.arcile.presentation.ui.components.home.MainFoldersGrid
import androidx.compose.ui.res.stringResource
import dev.qtremors.arcile.R
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import dev.qtremors.arcile.presentation.ui.components.EmptyState
import dev.qtremors.arcile.presentation.ui.components.SearchTopBar
import dev.qtremors.arcile.presentation.ui.components.shimmer
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
            .padding(MaterialTheme.spacing.medium),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    ) {
        Column(modifier = Modifier.padding(MaterialTheme.spacing.space20)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(MaterialTheme.spacing.space12))
                Text(
                    text = stringResource(R.string.new_storage_detected),
                    style = MaterialTheme.typography.titleMediumBold
                )
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Text(
                text = stringResource(R.string.how_should_be_treated, volume.name),
                style = MaterialTheme.typography.bodyMedium
            )
            
            Spacer(modifier = Modifier.height(MaterialTheme.spacing.medium))
            
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small)) {
                Column(modifier = Modifier.weight(1f)) {
                    Button(
                        onClick = { onClassify(StorageKind.SD_CARD) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = CircleShape
                    ) {
                        Text(stringResource(R.string.sd_card), maxLines = 1)
                    }
                    Text(
                        stringResource(R.string.sd_card_description),
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(top = MaterialTheme.spacing.extraSmall),
                        textAlign = TextAlign.Center
                    )
                }
                
                Column(modifier = Modifier.weight(1f)) {
                    OutlinedButton(
                        onClick = { onClassify(StorageKind.OTG) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = CircleShape
                    ) {
                        Text(stringResource(R.string.otg_usb), maxLines = 1)
                    }
                    Text(
                        stringResource(R.string.otg_usb_description),
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(top = 4.dp),
                        textAlign = TextAlign.Center
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(MaterialTheme.spacing.medium))
            
            TextButton(
                onClick = onDecideLater,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            ) {
                Text(stringResource(R.string.decide_later))
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
                title = stringResource(R.string.app_name),
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
                        text = stringResource(R.string.categories),
                        style = MaterialTheme.typography.titleMediumBold,
                        modifier = Modifier.padding(start = MaterialTheme.spacing.medium, top = MaterialTheme.spacing.medium, end = MaterialTheme.spacing.medium, bottom = MaterialTheme.spacing.small)
                    )
                }
                item { CategoryGrid(state.categoryStorages, onCategoryClick) }

                item {
                    Text(
                        text = stringResource(R.string.folders),
                        style = MaterialTheme.typography.titleMediumBold,
                        modifier = Modifier.padding(start = MaterialTheme.spacing.medium, top = MaterialTheme.spacing.large, end = MaterialTheme.spacing.medium, bottom = MaterialTheme.spacing.small)
                    )
                }
                item {
                    MainFoldersGrid(
                        standardFolders = state.standardFolders,
                        onOpenFileBrowser = onOpenFileBrowser,
                        onNavigateToPath = onNavigateToPath
                    )
                }

                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = MaterialTheme.spacing.medium, top = MaterialTheme.spacing.large, end = MaterialTheme.spacing.medium, bottom = MaterialTheme.spacing.small),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.utilities),
                            style = MaterialTheme.typography.titleMediumBold
                        )
                        TextButton(onClick = onNavigateToTools) {
                            Text(stringResource(R.string.show_all))
                        }
                    }
                }

                item {
                    androidx.compose.foundation.lazy.LazyRow(
                        modifier = Modifier
                            .fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = MaterialTheme.spacing.medium),
                        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.space12)
                    ) {
                        item {
                            Box(modifier = Modifier.width(140.dp)) {
                                ToolCard(ToolItem(stringResource(R.string.trash_bin), Icons.Default.Delete, isImplemented = true), onClick = onNavigateToTrash)
                            }
                        }
                        item {
                            Box(modifier = Modifier.width(140.dp)) {
                                ToolCard(ToolItem(stringResource(R.string.placeholder_onlyfiles), Icons.Default.Lock, isImplemented = false))
                            }
                        }
                        item {
                            Box(modifier = Modifier.width(140.dp)) {
                                ToolCard(ToolItem(stringResource(R.string.placeholder_large_files), Icons.Default.ZoomIn, isImplemented = false))
                            }
                        }
                        item {
                            Box(modifier = Modifier.width(140.dp)) {
                                ToolCard(ToolItem(stringResource(R.string.placeholder_ftp_server), Icons.Default.WifiTethering, isImplemented = false))
                            }
                        }
                    }
                }

                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = MaterialTheme.spacing.medium, top = MaterialTheme.spacing.large, end = MaterialTheme.spacing.medium, bottom = MaterialTheme.spacing.small),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.recent_files),
                            style = MaterialTheme.typography.titleMediumBold
                        )
                        TextButton(onClick = onNavigateToRecentFiles) {
                            Text(stringResource(R.string.see_all))
                        }
                    }
                }

                if (displayedRecentFiles.isEmpty()) {
                    item {
                        EmptyState(
                            icon = Icons.Default.History,
                            title = stringResource(R.string.no_recent_files),
                            description = stringResource(R.string.no_recent_files_description),
                            modifier = Modifier.fillMaxWidth()
                        )
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

