package dev.qtremors.arcile.feature.storagecleaner.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.qtremors.arcile.core.presentation.UiText
import dev.qtremors.arcile.core.storage.domain.CleanerGroupType
import dev.qtremors.arcile.core.ui.ArcileFeedbackEvent
import dev.qtremors.arcile.core.ui.ArcileFeedbackSeverity
import dev.qtremors.arcile.core.ui.ArcilePullRefreshIndicator
import dev.qtremors.arcile.core.ui.R
import dev.qtremors.arcile.core.ui.rememberArcileHaptics
import dev.qtremors.arcile.core.ui.theme.spacing
import dev.qtremors.arcile.feature.storagecleaner.StorageCleanerState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun StorageCleanerScreen(
    state: StorageCleanerState,
    onNavigateBack: () -> Unit,
    onRefresh: () -> Unit,
    onOpenGroup: (CleanerGroupType) -> Unit = {},
    onClearThumbnailCache: () -> Unit = {},
    onUndoClean: (List<String>) -> Unit = {},
    onClearMessages: () -> Unit,
    onUnignorePath: (String) -> Unit = {},
    onFeedback: (ArcileFeedbackEvent) -> Unit = {}
) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val haptics = rememberArcileHaptics()
    var showIgnoredItems by rememberSaveable { mutableStateOf(false) }
    val isOverviewPullRefreshing = state.isPullToRefreshing &&
        state.scanningGroups == CleanerGroupType.entries.toSet()

    LaunchedEffect(state.successMessage) {
        state.successMessage?.let { message ->
            haptics.success()
            onFeedback(
                ArcileFeedbackEvent(
                    message = UiText.PluralResource(R.plurals.clean_success, message.cleanedCount, listOf(message.cleanedCount)),
                    severity = ArcileFeedbackSeverity.Success,
                    actionLabel = message.undoTrashIds.takeIf { it.isNotEmpty() }?.let {
                        UiText.StringResource(R.string.undo)
                    },
                    onAction = message.undoTrashIds.takeIf { it.isNotEmpty() }?.let { ids ->
                        { onUndoClean(ids) }
                    }
                )
            )
            onClearMessages()
        }
    }
    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let { message ->
            onFeedback(
                ArcileFeedbackEvent(
                    message = message.takeIf { it.isNotBlank() }?.let(UiText::Dynamic)
                        ?: UiText.StringResource(R.string.clean_failed),
                    severity = ArcileFeedbackSeverity.Error
                )
            )
            onClearMessages()
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.storage_cleaner_title)) },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.clip(CircleShape)
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { showIgnoredItems = true },
                        modifier = Modifier.clip(CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = stringResource(R.string.cleaner_ignored_items)
                        )
                    }
                },
                scrollBehavior = scrollBehavior
            )
        }
    ) { padding ->
        val pullRefreshState = rememberPullToRefreshState()
        PullToRefreshBox(
            isRefreshing = isOverviewPullRefreshing,
            onRefresh = {
                if (!state.isScanning && !state.isCleaning) onRefresh()
            },
            state = pullRefreshState,
            modifier = Modifier
                .fillMaxSize()
                .padding(top = padding.calculateTopPadding()),
            indicator = {
                ArcilePullRefreshIndicator(
                    isRefreshing = isOverviewPullRefreshing,
                    state = pullRefreshState
                )
            }
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    top = 12.dp,
                    bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() +
                        MaterialTheme.spacing.screenGutter
                )
            ) {
                if (state.isScanning) {
                    item {
                        CleanerScanStatus(
                            progress = state.scanProgress,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }
                }
                if (state.isPartial) {
                    item {
                        Text(
                            text = stringResource(R.string.cleaner_partial_results),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }
                }
                item {
                    CleanerThumbnailCacheCard(
                        state = state.thumbnailCache,
                        onClear = onClearThumbnailCache
                    )
                }
                items(CleanerGroupType.entries, key = { it.name }) { type ->
                    CleanerCategoryCard(
                        group = state.group(type),
                        isScanning = type in state.scanningGroups,
                        scanProgress = state.scanProgress.takeIf { type in state.scanningGroups },
                        isLoaded = type in state.loadedGroups,
                        onClick = { onOpenGroup(type) }
                    )
                }
            }
        }
    }

    if (showIgnoredItems) {
        IgnoredItemsDialog(
            ignoredPaths = state.rules.ignoredPaths,
            onUnignorePath = onUnignorePath,
            onDismiss = { showIgnoredItems = false }
        )
    }
}
