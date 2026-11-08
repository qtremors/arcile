package dev.qtremors.arcile.feature.home.ui

import dev.qtremors.arcile.core.ui.arcileTopAppBarNestedScroll
import dev.qtremors.arcile.core.ui.theme.spacing
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import kotlin.math.roundToInt
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import dev.qtremors.arcile.core.ui.TopBarAction
import androidx.compose.ui.unit.dp
import dev.qtremors.arcile.core.ui.theme.ExpressiveShapes
import dev.qtremors.arcile.core.ui.theme.spacing
import dev.qtremors.arcile.core.ui.theme.titleMediumBold
import dev.qtremors.arcile.feature.home.HomeState
import dev.qtremors.arcile.core.ui.ArcileTopBar
import dev.qtremors.arcile.core.ui.ArcileTopBarMenuAction
import dev.qtremors.arcile.core.storage.domain.AppStartPage
import dev.qtremors.arcile.core.storage.domain.HomeLayoutPreferences
import dev.qtremors.arcile.core.storage.domain.HomeSectionIds
import dev.qtremors.arcile.core.ui.ToolCard
import dev.qtremors.arcile.core.ui.ToolItem
import dev.qtremors.arcile.feature.home.ui.components.StorageSummaryCard
import dev.qtremors.arcile.feature.home.ui.components.CategoryGrid
import dev.qtremors.arcile.feature.home.ui.components.QuickAccessGrid
import androidx.compose.ui.res.stringResource
import dev.qtremors.arcile.core.ui.R
import dev.qtremors.arcile.core.ui.utilities.HomeUtilityCatalog
import dev.qtremors.arcile.core.ui.utilities.UtilityAction
import dev.qtremors.arcile.core.ui.EmptyState
import dev.qtremors.arcile.core.ui.EmptyStateVariant
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import coil3.imageLoader
import coil3.request.CachePolicy
import coil3.request.crossfade
import coil3.request.ImageRequest
import coil3.size.Precision
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
    appStartPage: AppStartPage = AppStartPage.HOME,
    onAppStartPageChange: (AppStartPage) -> Unit = {},
    homeRecentCarouselLimit: Int = dev.qtremors.arcile.core.storage.domain.SharedFilePreferences.DEFAULT_HOME_RECENT_CAROUSEL_LIMIT,
    onHomeLayoutPreferencesChange: (HomeLayoutPreferences) -> Unit = {},
) {
    val context = LocalContext.current
    val density = LocalDensity.current.density
    val screenWidthDp = (LocalWindowInfo.current.containerSize.width / density).roundToInt()
    val homeRecentRenderedThumbnailSizePx = remember(screenWidthDp, density) {
        homeCarouselRenderedThumbnailSizePx(
            screenWidthDp = screenWidthDp,
            density = density
        )
    }

    LifecycleResumeEffect(Unit) {
        contentIntents.resumeRefresh()
        onPauseOrDispose { contentIntents.pauseRefresh() }
    }

    val normalizedRecentLimit = dev.qtremors.arcile.core.storage.domain.SharedFilePreferences
        .normalizeHomeRecentCarouselLimit(homeRecentCarouselLimit)
    val displayedRecentFiles = state.displayState.todayRecentFiles.take(normalizedRecentLimit)
    val displayedCategoryStorages = state.categoryStorages.ifEmpty {
        state.allStorageVolumes.ifEmpty { state.storageInfo?.volumes.orEmpty() }
            .singleOrNull()?.let { state.categoryStoragesByVolume[it.id] }
            ?: emptyList()
    }
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
                        .placeholderMemoryCacheKey(thumbnailCacheKey)
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
    var showHomeLayoutDialog by rememberSaveable { mutableStateOf(false) }

    if (showHomeLayoutDialog) {
        HomeLayoutDialog(
            preferences = state.homeLayoutPreferences,
            onDismiss = { showHomeLayoutDialog = false },
            onApply = { preferences ->
                onHomeLayoutPreferencesChange(preferences)
                showHomeLayoutDialog = false
            }
        )
    }

    Scaffold(
        modifier = Modifier.arcileTopAppBarNestedScroll(scrollBehavior),
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
                menuActions = listOf(
                    ArcileTopBarMenuAction(
                        label = stringResource(R.string.home_edit_action),
                        icon = Icons.Default.Edit,
                        onClick = { showHomeLayoutDialog = true }
                    ),
                    ArcileTopBarMenuAction(
                        label = stringResource(R.string.home_title),
                        icon = Icons.Default.Home,
                        selected = appStartPage == AppStartPage.HOME,
                        onClick = { onAppStartPageChange(AppStartPage.HOME) }
                    ),
                    ArcileTopBarMenuAction(
                        label = stringResource(R.string.browse_title),
                        icon = Icons.Default.Folder,
                        selected = appStartPage == AppStartPage.BROWSER,
                        onClick = { onAppStartPageChange(AppStartPage.BROWSER) }
                    )
                ),
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

                    val displayedHomeUtilities = state.homeUtilityIds.mapNotNull { id ->
                        HomeUtilityCatalog.firstOrNull { it.id == id }
                    }
                    val visibleSectionIds = state.homeLayoutPreferences.orderedSectionIds.filter { id ->
                        id in state.homeLayoutPreferences.enabledSectionIds &&
                            (id != HomeSectionIds.RECENT_FILES || normalizedRecentLimit > 0)
                    }
                    visibleSectionIds.forEachIndexed { index, sectionId ->
                        item(key = "home_section_$sectionId") {
                            val definition = HomeSectionCatalog.first { it.id == sectionId }
                            val action = when (sectionId) {
                                HomeSectionIds.QUICK_ACCESS -> HomeSectionAction(
                                    label = stringResource(R.string.manage),
                                    onClick = navigationIntents.navigateToQuickAccess
                                )
                                HomeSectionIds.UTILITIES -> HomeSectionAction(
                                    label = stringResource(R.string.show_all),
                                    onClick = navigationIntents.navigateToTools
                                )
                                HomeSectionIds.RECENT_FILES -> HomeSectionAction(
                                    label = stringResource(R.string.see_all),
                                    onClick = navigationIntents.navigateToRecentFiles
                                )
                                else -> null
                            }
                            HomeSectionContainer(
                                title = stringResource(definition.nameRes),
                                isFirst = index == 0,
                                action = action
                            ) {
                                when (sectionId) {
                                    HomeSectionIds.STORAGE -> StorageSummaryCard(
                                        state = state,
                                        onNavigateToPath = navigationIntents.navigateToPath,
                                        onOpenStorageDashboard = navigationIntents.openStorageDashboard,
                                        onOpenFileBrowser = navigationIntents.openFileBrowser
                                    )
                                    HomeSectionIds.CATEGORIES -> if (state.hasRestoredCachedHomeData) {
                                        CategoryGrid(
                                            categoryStorages = displayedCategoryStorages,
                                            reserveSizeLine = state.isLoading ||
                                                state.isCalculatingStorage ||
                                                displayedCategoryStorages.isEmpty(),
                                            onCategoryClick = navigationIntents.categoryClick
                                        )
                                    } else {
                                        Spacer(Modifier.fillMaxWidth().height(230.dp))
                                    }
                                    HomeSectionIds.QUICK_ACCESS -> QuickAccessGrid(
                                        quickAccessItems = state.quickAccessItems,
                                        onOpenFileBrowser = navigationIntents.openFileBrowser,
                                        onNavigateToPath = navigationIntents.navigateToPath,
                                        onNavigateToSaf = navigationIntents.navigateToExternalFolder
                                    )
                                    HomeSectionIds.UTILITIES -> if (displayedHomeUtilities.isNotEmpty()) {
                                        androidx.compose.foundation.lazy.LazyRow(
                                            modifier = Modifier.fillMaxWidth(),
                                            contentPadding = PaddingValues(
                                                horizontal = MaterialTheme.spacing.medium
                                            ),
                                            horizontalArrangement = Arrangement.spacedBy(
                                                MaterialTheme.spacing.space12
                                            )
                                        ) {
                                            items(displayedHomeUtilities, key = { it.id }) { utility ->
                                                Box(modifier = Modifier.width(140.dp)) {
                                                    ToolCard(
                                                        ToolItem(
                                                            stringResource(utility.nameRes),
                                                            utility.icon
                                                        ),
                                                        onClick = {
                                                            when (utility.action) {
                                                                UtilityAction.Trash -> navigationIntents.navigateToTrash()
                                                                UtilityAction.Cleaner -> navigationIntents.navigateToCleaner()
                                                                UtilityAction.Activity -> navigationIntents.navigateToActivity()
                                                                UtilityAction.OnlyFiles -> navigationIntents.navigateToOnlyFiles()
                                                            }
                                                        }
                                                    )
                                                }
                                            }
                                        }
                                    }
                                    HomeSectionIds.RECENT_FILES -> when {
                                        displayedRecentFiles.isEmpty() && !state.isLoading -> EmptyState(
                                            variant = EmptyStateVariant.Recent,
                                            title = stringResource(R.string.no_recent_files),
                                            description = stringResource(R.string.no_recent_files_description),
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                        displayedRecentFiles.isNotEmpty() ->
                                            dev.qtremors.arcile.feature.home.ui.components.RecentFilesCarousel(
                                                files = displayedRecentFiles,
                                                onOpenFile = { path ->
                                                    navigationIntents.openFileWithContext(
                                                        path,
                                                        displayedRecentFiles
                                                    )
                                                },
                                                onNavigateToPath = navigationIntents.navigateToPath,
                                                onShareFile = contentIntents.shareRecentFile,
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                    }
                                    else -> Unit
                                }
                            }
                        }
                    }

                    item { Spacer(modifier = Modifier.height(MaterialTheme.spacing.medium)) }
                }
            }
        }
    }
}

private data class HomeSectionAction(
    val label: String,
    val onClick: () -> Unit
)

@Composable
private fun HomeSectionContainer(
    title: String,
    isFirst: Boolean,
    action: HomeSectionAction?,
    content: @Composable () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        if (isFirst && action == null) {
            Spacer(modifier = Modifier.height(MaterialTheme.spacing.medium))
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = MaterialTheme.spacing.medium,
                        top = if (isFirst) MaterialTheme.spacing.none else MaterialTheme.spacing.medium,
                        end = MaterialTheme.spacing.medium,
                        bottom = MaterialTheme.spacing.small
                    )
                    .sizeIn(minHeight = 48.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMediumBold
                )
                action?.let {
                    TextButton(
                        onClick = it.onClick,
                        shape = ExpressiveShapes.medium
                    ) {
                        Text(it.label)
                    }
                }
            }
        }
        content()
    }
}

