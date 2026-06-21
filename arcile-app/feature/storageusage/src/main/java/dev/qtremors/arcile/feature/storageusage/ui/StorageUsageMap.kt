package dev.qtremors.arcile.feature.storageusage.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.Spring
import androidx.compose.ui.graphics.graphicsLayer
import dev.qtremors.arcile.ui.theme.ArcileMotion
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.qtremors.arcile.core.ui.R
import dev.qtremors.arcile.core.storage.domain.StorageUsageNode
import dev.qtremors.arcile.core.storage.domain.StorageUsageNodeKind
import dev.qtremors.arcile.core.storage.domain.StorageUsageScanState
import dev.qtremors.arcile.core.storage.domain.StorageUsageScanStatus
import dev.qtremors.arcile.feature.storageusage.StorageUsageUiState
import dev.qtremors.arcile.shared.ui.EmptyState
import dev.qtremors.arcile.shared.ui.EmptyStateVariant
import dev.qtremors.arcile.ui.theme.bodyMediumBold
import dev.qtremors.arcile.ui.theme.bodyMediumMedium
import dev.qtremors.arcile.ui.theme.titleMediumBold
import dev.qtremors.arcile.utils.formatFileSize
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.min

private const val MAX_SUNBURST_DEPTH = 3
private const val MAX_SUNBURST_SEGMENTS = 160
private const val MAX_SUNBURST_CHILDREN_PER_NODE = 18


@Composable
fun StorageUsageMap(
    state: StorageUsageUiState,
    onSelectNode: (StorageUsageNode) -> Unit,
    onDrillInto: (StorageUsageNode) -> Unit,
    onBreadcrumbClick: (Int) -> Unit,
    onOpenPath: (String) -> Unit,
    onOpenFile: (String) -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        when {
            state.unavailableVolume != null -> {
                EmptyState(
                    variant = EmptyStateVariant.StorageAccess,
                    title = stringResource(R.string.storage_usage_map_unavailable_title),
                    description = stringResource(R.string.storage_usage_map_unavailable_description)
                )
            }
            state.scanState is StorageUsageScanState.Loading && state.currentRoot == null -> {
                StorageUsageLoading(state.scanState)
            }
            state.scanState is StorageUsageScanState.Error -> {
                StorageUsageError(message = state.scanState.message, onRefresh = onRefresh)
            }
            state.currentRoot != null -> {
                StorageUsageBreadcrumbs(
                    breadcrumbs = state.breadcrumbs,
                    onBreadcrumbClick = onBreadcrumbClick
                )
                StorageUsageSunburst(
                    root = state.currentRoot,
                    selectedNode = state.selectedNode,
                    onSelectNode = onSelectNode
                )
                StorageUsageSegmentList(
                    root = state.currentRoot,
                    selectedNode = state.selectedNode,
                    onSelectNode = onSelectNode
                )
                StorageUsageDetails(
                    root = state.currentRoot,
                    node = state.selectedNode ?: state.currentRoot,
                    isScanning = state.scanState is StorageUsageScanState.Loading,
                    onDrillInto = onDrillInto,
                    onOpenPath = onOpenPath,
                    onOpenFile = onOpenFile,
                    onRefresh = onRefresh
                )
            }
            else -> {
                StorageUsageLoading(StorageUsageScanState.Loading(
                    dev.qtremors.arcile.core.storage.domain.StorageUsageScanProgress("", 0, 0L, null)
                ))
            }
        }
    }
}

