package dev.qtremors.arcile.presentation.ui.components.storage

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.AssistChip
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.qtremors.arcile.R
import dev.qtremors.arcile.domain.StorageUsageNode
import dev.qtremors.arcile.domain.StorageUsageNodeKind
import dev.qtremors.arcile.domain.StorageUsageScanState
import dev.qtremors.arcile.domain.StorageUsageScanStatus
import dev.qtremors.arcile.presentation.storageusage.StorageUsageUiState
import dev.qtremors.arcile.presentation.ui.components.EmptyState
import dev.qtremors.arcile.presentation.ui.components.EmptyStateVariant
import dev.qtremors.arcile.ui.theme.bodyMediumBold
import dev.qtremors.arcile.ui.theme.bodyMediumMedium
import dev.qtremors.arcile.ui.theme.titleMediumBold
import dev.qtremors.arcile.utils.formatFileSize
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.hypot

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
                    dev.qtremors.arcile.domain.StorageUsageScanProgress("", 0, 0L, null)
                ))
            }
        }
    }
}

@Composable
private fun StorageUsageBreadcrumbs(
    breadcrumbs: List<StorageUsageNode>,
    onBreadcrumbClick: (Int) -> Unit
) {
    val scrollState = rememberScrollState()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(scrollState)
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        breadcrumbs.forEachIndexed { index, node ->
            AssistChip(
                onClick = { onBreadcrumbClick(index) },
                label = {
                    Text(
                        text = node.name,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Folder,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                }
            )
        }
    }
}

@Composable
private fun StorageUsageLoading(scanState: StorageUsageScanState.Loading) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            CircularProgressIndicator()
            Text(
                text = stringResource(R.string.storage_usage_map_scanning),
                style = MaterialTheme.typography.titleMediumBold
            )
            Text(
                text = stringResource(
                    R.string.storage_usage_map_scan_progress,
                    scanState.progress.scannedNodes,
                    formatFileSize(scanState.progress.scannedBytes)
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun StorageUsageError(
    message: String,
    onRefresh: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.errorContainer
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(R.string.storage_usage_map_error_title),
                style = MaterialTheme.typography.titleMediumBold,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            Text(
                text = message.ifBlank { stringResource(R.string.storage_usage_map_error_description) },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            OutlinedButton(onClick = onRefresh) {
                Icon(Icons.Default.Refresh, contentDescription = null)
                Spacer(modifier = Modifier.size(8.dp))
                Text(stringResource(R.string.refresh))
            }
        }
    }
}

@Composable
private fun StorageUsageSunburst(
    root: StorageUsageNode,
    selectedNode: StorageUsageNode?,
    onSelectNode: (StorageUsageNode) -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val colors = remember(colorScheme) {
        listOf(
            colorScheme.primary,
            colorScheme.tertiary,
            colorScheme.secondary,
            colorScheme.error,
            colorScheme.primaryContainer,
            colorScheme.tertiaryContainer
        )
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(360.dp)
                .padding(12.dp),
            contentAlignment = Alignment.Center
        ) {
            val chartSize = if (maxWidth < 336.dp) maxWidth else 336.dp
            val density = LocalDensity.current
            val chartSizePx = with(density) { chartSize.toPx() }
            val diameter = chartSizePx
            val centerRadius = diameter * 0.18f
            val ringCount = root.maxDepth().coerceIn(1, 5)
            val ringWidth = ((diameter / 2f) - centerRadius - 10f) / ringCount
            val segments = remember(root, colors, chartSizePx) {
                buildSegments(
                    root = root,
                    colors = colors,
                    centerRadius = centerRadius,
                    ringWidth = ringWidth,
                    maxDepth = ringCount
                )
            }
            Canvas(
                modifier = Modifier
                    .size(chartSize)
                    .pointerInput(root, segments) {
                        detectTapGestures { offset ->
                            findSegmentAt(offset, size.width.toFloat(), size.height.toFloat(), segments)
                                ?.let { onSelectNode(it.node) }
                        }
                    }
            ) {
                val center = Offset(size.width / 2f, size.height / 2f)

                drawCircle(
                    color = colorScheme.surfaceContainerHighest,
                    radius = centerRadius,
                    center = center
                )
                segments.forEach { segment ->
                    val isSelected = selectedNode?.path == segment.node.path
                    val strokeWidth = if (isSelected) ringWidth * 0.92f else ringWidth * 0.78f
                    val radius = (segment.innerRadius + segment.outerRadius) / 2f
                    val topLeft = Offset(center.x - radius, center.y - radius)
                    if (isSelected) {
                        drawArc(
                            color = colorScheme.onSurface.copy(alpha = 0.6f),
                            startAngle = segment.startAngle,
                            sweepAngle = segment.sweepAngle,
                            useCenter = false,
                            topLeft = topLeft,
                            size = Size(radius * 2f, radius * 2f),
                            style = Stroke(width = ringWidth * 0.98f, cap = StrokeCap.Butt)
                        )
                    }
                    drawArc(
                        color = segment.color.copy(alpha = if (segment.node.sizeBytes <= 0L) 0.35f else 0.92f),
                        startAngle = segment.startAngle,
                        sweepAngle = segment.sweepAngle,
                        useCenter = false,
                        topLeft = topLeft,
                        size = Size(radius * 2f, radius * 2f),
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Butt)
                    )
                }
            }

            Column(
                modifier = Modifier
                    .size(128.dp)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest, CircleShape)
                    .padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = selectedNode?.name ?: root.name,
                    style = MaterialTheme.typography.bodyMediumBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = formatFileSize((selectedNode ?: root).sizeBytes),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun StorageUsageDetails(
    root: StorageUsageNode,
    node: StorageUsageNode,
    isScanning: Boolean,
    onDrillInto: (StorageUsageNode) -> Unit,
    onOpenPath: (String) -> Unit,
    onOpenFile: (String) -> Unit,
    onRefresh: () -> Unit
) {
    val percent = if (root.sizeBytes > 0L) {
        (node.sizeBytes.toDouble() / root.sizeBytes.toDouble() * 100.0)
    } else {
        0.0
    }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (node.kind == StorageUsageNodeKind.File) Icons.Default.InsertDriveFile else Icons.Default.Folder,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.size(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = node.name,
                        style = MaterialTheme.typography.titleMediumBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = node.path,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StorageUsageMetric(stringResource(R.string.storage_usage_map_size), formatFileSize(node.sizeBytes))
                StorageUsageMetric(stringResource(R.string.storage_usage_map_share), String.format("%.1f%%", percent))
                StorageUsageMetric(stringResource(R.string.storage_usage_map_items), node.childCount.toString())
            }

            if (node.status != StorageUsageScanStatus.Ready || isScanning) {
                Text(
                    text = if (isScanning) {
                        stringResource(R.string.storage_usage_map_scan_partial_live)
                    } else {
                        stringResource(R.string.storage_usage_map_scan_partial)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (node.isContainer && node.children.isNotEmpty()) {
                    Button(
                        onClick = { onDrillInto(node) },
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Text(stringResource(R.string.storage_usage_map_drill_in))
                    }
                }
                TextButton(
                    onClick = {
                        if (node.kind == StorageUsageNodeKind.File) {
                            onOpenFile(node.path)
                        } else {
                            onOpenPath(node.path)
                        }
                    }
                ) {
                    Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.size(8.dp))
                    Text(stringResource(R.string.open))
                }
                TextButton(onClick = onRefresh) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.size(8.dp))
                    Text(stringResource(R.string.refresh))
                }
            }
        }
    }
}

@Composable
private fun StorageUsageMetric(label: String, value: String) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHighest
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMediumMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

private data class RingSegment(
    val node: StorageUsageNode,
    val startAngle: Float,
    val sweepAngle: Float,
    val innerRadius: Float,
    val outerRadius: Float,
    val color: Color
)

private fun buildSegments(
    root: StorageUsageNode,
    colors: List<Color>,
    centerRadius: Float,
    ringWidth: Float,
    maxDepth: Int
): List<RingSegment> {
    val segments = mutableListOf<RingSegment>()
    fun visit(node: StorageUsageNode, depth: Int, start: Float, sweep: Float) {
        if (depth > maxDepth || node.children.isEmpty()) return
        val childTotal = node.children.sumOf { it.sizeBytes }.coerceAtLeast(1L)
        var childStart = start
        node.children.forEachIndexed { index, child ->
            val childSweep = (sweep * (child.sizeBytes.toFloat() / childTotal.toFloat())).coerceAtLeast(0.4f)
            val inner = centerRadius + (depth - 1) * ringWidth
            val outer = inner + ringWidth
            val color = colors[(index + depth) % colors.size]
                .copy(alpha = (0.98f - depth * 0.08f).coerceAtLeast(0.62f))
            segments += RingSegment(child, childStart, childSweep, inner, outer, color)
            visit(child, depth + 1, childStart, childSweep)
            childStart += childSweep
        }
    }
    visit(root, depth = 1, start = -90f, sweep = 360f)
    return segments
}

private fun StorageUsageNode.maxDepth(): Int {
    if (children.isEmpty()) return 1
    return 1 + (children.maxOfOrNull { it.maxDepth() } ?: 0)
}

private fun findSegmentAt(
    offset: Offset,
    width: Float,
    height: Float,
    segments: List<RingSegment>
): RingSegment? {
    val center = Offset(width / 2f, height / 2f)
    val dx = offset.x - center.x
    val dy = offset.y - center.y
    val radius = hypot(dx, dy)
    val angle = normalizeAngle((atan2(dy, dx) * 180f / PI.toFloat()))
    return segments
        .filter { radius >= it.innerRadius && radius <= it.outerRadius && angleInSweep(angle, it.startAngle, it.sweepAngle) }
        .maxByOrNull { it.innerRadius }
}

private fun angleInSweep(angle: Float, startAngle: Float, sweepAngle: Float): Boolean {
    val start = normalizeAngle(startAngle)
    val end = normalizeAngle(startAngle + sweepAngle)
    return if (sweepAngle >= 360f) {
        true
    } else if (start <= end) {
        angle in start..end
    } else {
        angle >= start || angle <= end
    }
}

private fun normalizeAngle(angle: Float): Float {
    val normalized = angle % 360f
    return if (normalized < 0f) normalized + 360f else normalized
}
