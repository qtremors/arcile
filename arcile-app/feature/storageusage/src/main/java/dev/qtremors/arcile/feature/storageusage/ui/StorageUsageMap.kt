package dev.qtremors.arcile.feature.storageusage.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.qtremors.arcile.core.storage.domain.StorageUsageNode
import dev.qtremors.arcile.core.storage.domain.StorageUsageNodeKind
import dev.qtremors.arcile.core.storage.domain.StorageUsageScanProgress
import dev.qtremors.arcile.core.storage.domain.StorageUsageScanState
import dev.qtremors.arcile.core.ui.EmptyState
import dev.qtremors.arcile.core.ui.EmptyStateVariant
import dev.qtremors.arcile.core.ui.R
import dev.qtremors.arcile.core.ui.theme.spacing
import dev.qtremors.arcile.feature.storageusage.StorageUsageUiState

@Composable
internal fun StorageUsageMap(
    state: StorageUsageUiState,
    onSelectNode: (StorageUsageNode) -> Unit,
    onDrillInto: (StorageUsageNode) -> Unit,
    onBreadcrumbClick: (Int) -> Unit,
    onResetToOverview: () -> Unit,
    onOpenPath: (String) -> Unit,
    onOpenFile: (String) -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier
) {
    when {
        state.unavailableVolume != null -> {
            EmptyState(
                variant = EmptyStateVariant.StorageAccess,
                title = stringResource(R.string.storage_usage_map_unavailable_title),
                description = stringResource(R.string.storage_usage_map_unavailable_description),
                modifier = modifier
            )
        }
        state.scanState is StorageUsageScanState.Loading && state.currentRoot == null -> {
            Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                StorageUsageLoading(state.scanState)
            }
        }
        state.scanState is StorageUsageScanState.Error -> {
            Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                StorageUsageError(message = state.scanState.message, onRefresh = onRefresh)
            }
        }
        state.currentRoot != null -> {
            val currentRoot = state.currentRoot
            val openNode: (StorageUsageNode) -> Unit = { node ->
                when (node.kind) {
                    StorageUsageNodeKind.Folder -> onOpenPath(node.path)
                    StorageUsageNodeKind.File -> onOpenFile(node.path)
                    StorageUsageNodeKind.Grouped -> Unit
                }
            }
            Column(modifier = modifier.fillMaxSize()) {
                StorageUsageSunburst(
                    root = currentRoot,
                    selectedNode = state.selectedNode,
                    onSelectNode = onSelectNode,
                    onDrillIntoNode = onDrillInto,
                    onOpenNode = openNode,
                    onResetToOverview = onResetToOverview,
                    volumeTotalBytes = state.volumeTotalBytes,
                    volumeFreeBytes = state.volumeFreeBytes,
                    isVolumeRoot = !state.isDrilledDown && state.breadcrumbs.size <= 1
                )
                Spacer(modifier = Modifier.height(8.dp))
                StorageUsageBreadcrumbs(
                    breadcrumbs = state.breadcrumbs,
                    onBreadcrumbClick = onBreadcrumbClick
                )
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    contentPadding = PaddingValues(
                        top = 12.dp,
                        bottom = WindowInsets.navigationBars
                            .asPaddingValues()
                            .calculateBottomPadding() + MaterialTheme.spacing.screenGutter
                    ),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        StorageUsageSegmentList(
                            root = currentRoot,
                            selectedNode = state.selectedNode,
                            onSelectNode = onSelectNode,
                            onDrillIntoNode = onDrillInto,
                            onOpenNode = openNode
                        )
                    }
                }
            }
        }
        else -> {
            Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                StorageUsageLoading(
                    StorageUsageScanState.Loading(
                        StorageUsageScanProgress("", 0, 0L, null)
                    )
                )
            }
        }
    }
}
