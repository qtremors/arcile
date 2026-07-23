package dev.qtremors.arcile.feature.storageusage.ui

import android.os.SystemClock
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.qtremors.arcile.core.presentation.formatFileSize
import dev.qtremors.arcile.core.storage.domain.StorageUsageNode
import dev.qtremors.arcile.core.storage.domain.StorageUsageNodeKind
import dev.qtremors.arcile.core.ui.R
import dev.qtremors.arcile.core.ui.theme.bodyMediumBold
import dev.qtremors.arcile.core.ui.theme.titleMediumBold

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun StorageUsageSegmentList(
    root: StorageUsageNode,
    selectedNode: StorageUsageNode?,
    onSelectNode: (StorageUsageNode) -> Unit,
    onDrillIntoNode: ((StorageUsageNode) -> Unit)? = null,
    onOpenNode: ((StorageUsageNode) -> Unit)? = null
) {
    if (root.children.isEmpty()) return
    val locale = LocalLocale.current.platformLocale
    val doubleTapTimeoutMillis = LocalViewConfiguration.current.doubleTapTimeoutMillis
    val openLabel = stringResource(R.string.open)
    val itemColors = ListItemDefaults.segmentedColors(
        containerColor = MaterialTheme.colorScheme.surfaceContainer
    )
    var lastTappedPath by remember(root.path) { mutableStateOf<String?>(null) }
    var lastTapUptimeMillis by remember(root.path) { mutableLongStateOf(0L) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = stringResource(R.string.storage_usage_map_segments_heading),
            style = MaterialTheme.typography.titleMediumBold
        )
        Column(
            verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)
        ) {
            root.children.forEachIndexed { index, node ->
                val isSelected = selectedNode?.path == node.path
                val share = if (root.sizeBytes > 0L) {
                    node.sizeBytes.toDouble() / root.sizeBytes.toDouble() * 100.0
                } else {
                    0.0
                }
                val sizeText = formatFileSize(node.sizeBytes)
                val shareText = String.format(locale, "%.1f%%", share)
                val segmentDescription = stringResource(
                    R.string.storage_usage_map_segment_description,
                    node.name,
                    formatFileSize(node.sizeBytes),
                    node.childCount
                )
                val canOpen = node.kind != StorageUsageNodeKind.Grouped && onOpenNode != null
                SegmentedListItem(
                    selected = isSelected,
                    onClick = {
                        val now = SystemClock.uptimeMillis()
                        val isDoubleTap = lastTappedPath == node.path &&
                            now - lastTapUptimeMillis <= doubleTapTimeoutMillis
                        if (isDoubleTap) {
                            lastTappedPath = null
                            lastTapUptimeMillis = 0L
                            if (node.isContainer && node.children.isNotEmpty()) {
                                onDrillIntoNode?.invoke(node)
                            } else {
                                onSelectNode(node)
                            }
                        } else {
                            lastTappedPath = node.path
                            lastTapUptimeMillis = now
                            onSelectNode(node)
                        }
                    },
                    onLongClick = if (canOpen) {
                        { onOpenNode?.invoke(node) }
                    } else {
                        null
                    },
                    onLongClickLabel = if (canOpen) openLabel else null,
                    shapes = ListItemDefaults.segmentedShapes(
                        index = index,
                        count = root.children.size
                    ),
                    colors = itemColors,
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics {
                            contentDescription = segmentDescription
                        },
                    leadingContent = {
                        Icon(
                            imageVector = if (node.kind == StorageUsageNodeKind.File) {
                                Icons.AutoMirrored.Filled.InsertDriveFile
                            } else {
                                Icons.Default.Folder
                            },
                            contentDescription = null
                        )
                    },
                    supportingContent = {
                        Text(
                            text = sizeText,
                            style = MaterialTheme.typography.bodySmall
                        )
                    },
                    trailingContent = {
                        Text(
                            text = shareText,
                            style = MaterialTheme.typography.bodyMediumBold
                        )
                    },
                    content = {
                        Text(
                            text = node.name,
                            style = MaterialTheme.typography.bodyMediumBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                )
            }
        }
    }
}
