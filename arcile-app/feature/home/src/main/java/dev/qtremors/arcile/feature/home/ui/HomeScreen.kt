package dev.qtremors.arcile.feature.home.ui

import dev.qtremors.arcile.core.ui.theme.spacing
import android.os.Environment
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.runtime.mutableFloatStateOf
import kotlin.math.roundToInt
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import dev.qtremors.arcile.core.ui.TopBarAction
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
import dev.qtremors.arcile.core.ui.theme.LocalCategoryColors
import dev.qtremors.arcile.core.ui.theme.ExpressiveShapes
import dev.qtremors.arcile.core.ui.theme.spacing
import dev.qtremors.arcile.core.ui.theme.titleLargeBold
import dev.qtremors.arcile.core.ui.theme.titleMediumBold
import dev.qtremors.arcile.core.ui.theme.bodyMediumMedium
import dev.qtremors.arcile.core.ui.theme.bodySmallMedium
import dev.qtremors.arcile.core.ui.lists.FileItemRow
import dev.qtremors.arcile.core.storage.domain.CategoryStorage
import dev.qtremors.arcile.feature.home.HomeState
import dev.qtremors.arcile.core.ui.ArcileTopBar
import dev.qtremors.arcile.core.ui.ToolCard
import dev.qtremors.arcile.core.ui.ToolItem
import dev.qtremors.arcile.feature.home.ui.components.StorageSummaryCard
import dev.qtremors.arcile.feature.home.ui.components.CategoryGrid
import dev.qtremors.arcile.feature.home.ui.components.QuickAccessGrid
import androidx.compose.ui.res.stringResource
import dev.qtremors.arcile.core.ui.R
import dev.qtremors.arcile.core.ui.utilities.HomeUtilityCatalog
import dev.qtremors.arcile.core.ui.utilities.UtilityAction
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import dev.qtremors.arcile.core.ui.EmptyState
import dev.qtremors.arcile.core.ui.EmptyStateVariant
import dev.qtremors.arcile.core.ui.SearchTopBar
import dev.qtremors.arcile.core.ui.shimmer
import dev.qtremors.arcile.core.ui.SearchFiltersSheet
import dev.qtremors.arcile.core.presentation.formatFileSize
import dev.qtremors.arcile.core.ui.theme.getCategoryColor
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.LifecycleResumeEffect
import kotlinx.coroutines.delay
import androidx.compose.ui.unit.dp
import dev.qtremors.arcile.core.storage.domain.StorageKind
import dev.qtremors.arcile.core.storage.domain.isIndexed
import dev.qtremors.arcile.core.storage.domain.showTemporaryStorageBadge
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material.icons.filled.Info
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import coil.imageLoader
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.size.Precision
import dev.qtremors.arcile.core.ui.image.ThumbnailKey
import dev.qtremors.arcile.core.ui.image.ThumbnailType
import dev.qtremors.arcile.core.ui.ArcilePullRefreshIndicator
import dev.qtremors.arcile.feature.home.ui.components.homeCarouselRenderedThumbnailSizePx
import dev.qtremors.arcile.feature.home.ui.components.homeCarouselThumbnailSizePx
import dev.qtremors.arcile.feature.home.ui.components.homeThumbnailCacheKey
import dev.qtremors.arcile.feature.home.ui.components.homeThumbnailRequestData

private const val HomeRecentFilesPreloadLimit = 6
/**
 * Dashboard screen shown when the app first launches.
 *
 * Displays a storage summary card, per-category storage breakdown, quick-access folder
 * shortcuts, a utilities tray, and a recent-files list.
 *
 * @param state Current [HomeState] providing storage info, recent files, and category data.
 * [navigationIntents] emits navigation requests without depending on app routes.
 * [contentIntents] contains Home-owned refresh, sharing, and classification actions.
 */

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun HomeScreen(
    state: HomeState,
    navigationIntents: HomeNavigationIntents,
    contentIntents: HomeContentIntents,
    homeRecentCarouselLimit: Int = dev.qtremors.arcile.core.storage.domain.BrowserPreferences.DEFAULT_HOME_RECENT_CAROUSEL_LIMIT,
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current.density
    val homeRecentRenderedThumbnailSizePx = remember(configuration.screenWidthDp, density) {
        homeCarouselRenderedThumbnailSizePx(
            screenWidthDp = configuration.screenWidthDp,
            density = density
        )
    }

    LifecycleResumeEffect(Unit) {
        contentIntents.resumeRefresh()
        onPauseOrDispose { }
    }

    val normalizedRecentLimit = dev.qtremors.arcile.core.storage.domain.BrowserPreferences
        .normalizeHomeRecentCarouselLimit(homeRecentCarouselLimit)
    val displayedRecentFiles = state.displayState.todayRecentFiles.take(normalizedRecentLimit)
    LaunchedEffect(displayedRecentFiles, homeRecentRenderedThumbnailSizePx) {
        if (normalizedRecentLimit == 0) return@LaunchedEffect
        displayedRecentFiles
            .asSequence()
            .filter { file ->
                !file.isDirectory && ThumbnailKey.from(file).type != ThumbnailType.Unsupported
            }
            .take(HomeRecentFilesPreloadLimit)
            .forEach { file ->
                val thumbnailKey = ThumbnailKey.from(file)
                val thumbnailSizePx = homeCarouselThumbnailSizePx(
                    renderedSizePx = homeRecentRenderedThumbnailSizePx,
                    type = thumbnailKey.type
                )
                val thumbnailData = homeThumbnailRequestData(file, thumbnailKey)
                val thumbnailCacheKey = homeThumbnailCacheKey(file, thumbnailSizePx)
                context.imageLoader.enqueue(
                    ImageRequest.Builder(context)
                        .data(thumbnailData)
                        .size(thumbnailSizePx)
                        .precision(Precision.INEXACT)
                        .memoryCacheKey(thumbnailCacheKey)
                        .diskCacheKey(thumbnailCacheKey)
                        .memoryCachePolicy(CachePolicy.ENABLED)
                        .diskCachePolicy(CachePolicy.ENABLED)
                        .networkCachePolicy(CachePolicy.DISABLED)
                        .crossfade(false)
                        .build()
                )
            }
    }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val dateFormatter = dev.qtremors.arcile.core.ui.rememberDateFormatter("MMM dd, yyyy")

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            ArcileTopBar(
                title = stringResource(R.string.app_name),
                selectionCount = 0,
                options = dev.qtremors.arcile.core.ui.ArcileTopBarOptions(
                    showSettingsIcon = true,
                    showSearchAction = false,
                    showSortAction = false,
                    showNewFolderAction = false,
                    showSettingsMenuAction = false,
                    showAboutAction = true
                ),
                scrollBehavior = scrollBehavior,
                actions = dev.qtremors.arcile.core.ui.ArcileTopBarActions(
                    onSettingsClick = navigationIntents.settingsClick,
                    onClearSelection = {},
                    onSearchClick = {},
                    onSortClick = {},
                    onActionSelected = { action ->
                        when (action) {
                            TopBarAction.Settings -> navigationIntents.settingsClick()
                            TopBarAction.About -> navigationIntents.navigateToAbout()
                            else -> {}
                        }
                    }
                )
                )
        }
    ) { padding ->

        val pullRefreshState = rememberPullToRefreshState()
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            PullToRefreshBox(
                isRefreshing = state.isPullToRefreshing,
                onRefresh = contentIntents.refresh,
                state = pullRefreshState,
                modifier = Modifier.fillMaxSize(),
                indicator = {
                    ArcilePullRefreshIndicator(
                        isRefreshing = state.isPullToRefreshing,
                        state = pullRefreshState
                    )
                }
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + MaterialTheme.spacing.screenGutter
                    )
                ) {

                    if (state.showClassificationPrompt && state.unclassifiedVolumes.isNotEmpty()) {
                        val volume = state.unclassifiedVolumes.first()
                        item(key = "classification_prompt_${volume.id}") {
                            StorageClassificationPrompt(
                                volume = volume,
                                onClassify = { kind ->
                                    contentIntents.setVolumeClassification(
                                        volume.storageKey,
                                        kind
                                    )
                                },
                                onDecideLater = { contentIntents.hideClassificationPrompt(volume.storageKey) }
                            )
                        }
                    }

                    item {
                        StorageSummaryCard(
                            state = state,
                            onNavigateToPath = navigationIntents.navigateToPath,
                            onOpenStorageDashboard = navigationIntents.openStorageDashboard,
                            onOpenFileBrowser = navigationIntents.openFileBrowser
                        )
                    }

                    item {
                        Text(
                            text = stringResource(R.string.categories),
                            style = MaterialTheme.typography.titleMediumBold,
                            modifier = Modifier.padding(
                                start = MaterialTheme.spacing.medium,
                                top = MaterialTheme.spacing.medium,
                                end = MaterialTheme.spacing.medium,
                                bottom = MaterialTheme.spacing.small
                            )
                        )
                    }
                    item {
                        CategoryGrid(
                            categoryStorages = state.categoryStorages,
                            reserveSizeLine = state.isLoading || state.isCalculatingStorage || state.categoryStorages.isEmpty(),
                            onCategoryClick = navigationIntents.categoryClick
                        )
                    }

                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(
                                    start = MaterialTheme.spacing.medium,
                                    top = MaterialTheme.spacing.large,
                                    end = MaterialTheme.spacing.medium,
                                    bottom = MaterialTheme.spacing.small
                                ),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.quick_access),
                                style = MaterialTheme.typography.titleMediumBold
                            )
                            TextButton(
                                onClick = navigationIntents.navigateToQuickAccess,
                                shape = ExpressiveShapes.medium
                            ) {
                                Text(stringResource(R.string.manage))
                            }
                        }
                    }
                    item {
                        QuickAccessGrid(
                            quickAccessItems = state.quickAccessItems,
                            onOpenFileBrowser = navigationIntents.openFileBrowser,
                            onNavigateToPath = navigationIntents.navigateToPath,
                            onNavigateToSaf = navigationIntents.navigateToExternalFolder
                        )
                    }

                    val displayedHomeUtilities = HomeUtilityCatalog.filter { it.id in state.homeUtilityIds }
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(
                                    start = MaterialTheme.spacing.medium,
                                    top = MaterialTheme.spacing.large,
                                    end = MaterialTheme.spacing.medium,
                                    bottom = MaterialTheme.spacing.small
                                ),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.utilities),
                                style = MaterialTheme.typography.titleMediumBold
                            )
                            TextButton(
                                onClick = navigationIntents.navigateToTools,
                                shape = ExpressiveShapes.medium
                            ) {
                                Text(stringResource(R.string.show_all))
                            }
                        }
                    }

                    if (displayedHomeUtilities.isNotEmpty()) {
                        item {
                            androidx.compose.foundation.lazy.LazyRow(
                                modifier = Modifier
                                    .fillMaxWidth(),
                                contentPadding = PaddingValues(horizontal = MaterialTheme.spacing.medium),
                                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.space12)
                            ) {
                                items(displayedHomeUtilities, key = { it.id }) { definition ->
                                    Box(modifier = Modifier.width(140.dp)) {
                                        ToolCard(
                                            ToolItem(
                                                stringResource(definition.nameRes),
                                                definition.icon,
                                                isImplemented = definition.isImplemented
                                            ),
                                            onClick = {
                                                when (definition.action) {
                                                    UtilityAction.Trash -> navigationIntents.navigateToTrash()
                                                    UtilityAction.Cleaner -> navigationIntents.navigateToCleaner()
                                                    UtilityAction.Activity -> navigationIntents.navigateToActivity()
                                                    UtilityAction.None -> Unit
                                                }
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    if (normalizedRecentLimit > 0) {
                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(
                                        start = MaterialTheme.spacing.medium,
                                        top = MaterialTheme.spacing.large,
                                        end = MaterialTheme.spacing.medium,
                                        bottom = MaterialTheme.spacing.small
                                    ),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = stringResource(R.string.recent_files),
                                    style = MaterialTheme.typography.titleMediumBold
                                )
                                TextButton(
                                    onClick = navigationIntents.navigateToRecentFiles,
                                    shape = ExpressiveShapes.medium
                                ) {
                                    Text(stringResource(R.string.see_all))
                                }
                            }
                        }

                        if (displayedRecentFiles.isEmpty() && !state.isLoading) {
                            item {
                                EmptyState(
                                    variant = EmptyStateVariant.Recent,
                                    title = stringResource(R.string.no_recent_files),
                                    description = stringResource(R.string.no_recent_files_description),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        } else if (displayedRecentFiles.isNotEmpty()) {
                            item {
                                dev.qtremors.arcile.feature.home.ui.components.RecentFilesCarousel(
                                    files = displayedRecentFiles,
                                    onOpenFile = { path ->
                                        navigationIntents.openFileWithContext(path, displayedRecentFiles)
                                    },
                                    onNavigateToPath = navigationIntents.navigateToPath,
                                    onShareFile = contentIntents.shareRecentFile,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }

                    item { Spacer(modifier = Modifier.height(MaterialTheme.spacing.medium)) }
                }
            }
        }
    }
}

