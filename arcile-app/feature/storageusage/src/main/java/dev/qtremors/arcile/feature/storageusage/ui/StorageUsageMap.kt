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
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
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
import java.util.Locale

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
    val visibleSegments = remember(root) { root.children.take(MAX_SUNBURST_CHILDREN_PER_NODE) }
    val selectedForSemantics = selectedNode ?: root
    val context = LocalContext.current
    val chartDescription = stringResource(R.string.storage_usage_map_chart_description, root.name)
    val selectedState = stringResource(
        R.string.storage_usage_map_selected_state,
        selectedForSemantics.name,
        formatFileSize(selectedForSemantics.sizeBytes),
        selectedForSemantics.childCount
    )
    val segmentActions = remember(visibleSegments, context, onSelectNode) {
        visibleSegments.map { node ->
            CustomAccessibilityAction(
                label = context.getString(R.string.storage_usage_map_select_segment, node.name),
                action = {
                    onSelectNode(node)
                    true
                }
            )
        }
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
            val ringCount = root.maxDepth().coerceIn(1, MAX_SUNBURST_DEPTH)
            val ringWidth = ((diameter / 2f) - centerRadius - 10f) / ringCount
            val segments = remember(root, colors, chartSizePx) {
                buildSegments(
                    root = root,
                    colors = colors,
                    centerRadius = centerRadius,
                    ringWidth = ringWidth,
                    maxDepth = ringCount,
                    maxSegments = MAX_SUNBURST_SEGMENTS,
                    maxChildrenPerNode = MAX_SUNBURST_CHILDREN_PER_NODE
                )
            }
            Canvas(
                modifier = Modifier
                    .size(chartSize)
                    .semantics {
                        role = Role.Image
                        contentDescription = chartDescription
                        stateDescription = selectedState
                        customActions = segmentActions
                    }
                    .onKeyEvent { event ->
                        if (event.type != KeyEventType.KeyDown || visibleSegments.isEmpty()) {
                            return@onKeyEvent false
                        }
                        val selectedIndex = visibleSegments.indexOfFirst { it.path == selectedNode?.path }
                        when (event.key) {
                            Key.DirectionRight,
                            Key.DirectionDown -> {
                                val nextIndex = if (selectedIndex < 0) 0 else (selectedIndex + 1) % visibleSegments.size
                                onSelectNode(visibleSegments[nextIndex])
                                true
                            }
                            Key.DirectionLeft,
                            Key.DirectionUp -> {
                                val previousIndex = if (selectedIndex <= 0) {
                                    visibleSegments.lastIndex
                                } else {
                                    selectedIndex - 1
                                }
                                onSelectNode(visibleSegments[previousIndex])
                                true
                            }
                            Key.Enter,
                            Key.NumPadEnter,
                            Key.DirectionCenter -> {
                                val selectedSegment = visibleSegments.getOrNull(selectedIndex) ?: visibleSegments.first()
                                onSelectNode(selectedSegment)
                                true
                            }
                            else -> false
                        }
                    }
                    .focusable()
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
                StorageUsageMetric(stringResource(R.string.storage_usage_map_share), String.format(Locale.getDefault(), "%.1f%%", percent))
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
    maxDepth: Int,
    maxSegments: Int,
    maxChildrenPerNode: Int
): List<RingSegment> {
    val segments = mutableListOf<RingSegment>()
    fun visit(node: StorageUsageNode, depth: Int, start: Float, sweep: Float) {
        if (depth > maxDepth || node.children.isEmpty() || segments.size >= maxSegments) return
        val childTotal = node.children.sumOf { it.sizeBytes }.coerceAtLeast(1L)
        val visibleChildBudget = min(maxChildrenPerNode, maxSegments - segments.size).coerceAtLeast(0)
        if (visibleChildBudget == 0) return
        val visibleChildren = node.children.take(visibleChildBudget)
        val hiddenChildren = node.children.drop(visibleChildBudget)
        val chartChildren = if (hiddenChildren.isEmpty()) {
            visibleChildren
        } else {
            visibleChildren + StorageUsageNode(
                name = "Other small items",
                path = "${node.path}/Other small items",
                sizeBytes = hiddenChildren.sumOf { it.sizeBytes },
                kind = StorageUsageNodeKind.Grouped,
                childCount = hiddenChildren.sumOf { kotlin.math.max(1, it.childCount) },
                status = if (hiddenChildren.any { it.status != StorageUsageScanStatus.Ready }) {
                    StorageUsageScanStatus.Partial
                } else {
                    StorageUsageScanStatus.Ready
                }
            )
        }
        var childStart = start
        chartChildren.forEachIndexed { index, child ->
            if (segments.size >= maxSegments) return@forEachIndexed
            val childSweep = (sweep * (child.sizeBytes.toFloat() / childTotal.toFloat())).coerceAtLeast(0.4f)
            val inner = centerRadius + (depth - 1) * ringWidth
            val outer = inner + ringWidth
            val color = colors[(index + depth) % colors.size]
                .copy(alpha = (0.98f - depth * 0.08f).coerceAtLeast(0.62f))
            segments += RingSegment(child, childStart, childSweep, inner, outer, color)
            if (child.kind != StorageUsageNodeKind.Grouped) {
                visit(child, depth + 1, childStart, childSweep)
            }
            childStart += childSweep
        }
    }
    visit(root, depth = 1, start = -90f, sweep = 360f)
    return segments
}

internal fun boundedStorageUsageSunburstSegmentCount(root: StorageUsageNode): Int =
    buildSegments(
        root = root,
        colors = listOf(Color.Red, Color.Green, Color.Blue),
        centerRadius = 10f,
        ringWidth = 10f,
        maxDepth = MAX_SUNBURST_DEPTH,
        maxSegments = MAX_SUNBURST_SEGMENTS,
        maxChildrenPerNode = MAX_SUNBURST_CHILDREN_PER_NODE
    ).size

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
