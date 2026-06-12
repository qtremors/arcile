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


data class RingSegment(
    val node: StorageUsageNode,
    val startAngle: Float,
    val sweepAngle: Float,
    val innerRadius: Float,
    val outerRadius: Float,
    val color: Color
)

fun buildSegments(
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

fun StorageUsageNode.maxDepth(): Int {
    if (children.isEmpty()) return 1
    return 1 + (children.maxOfOrNull { it.maxDepth() } ?: 0)
}

fun findSegmentAt(
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

fun angleInSweep(angle: Float, startAngle: Float, sweepAngle: Float): Boolean {
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

fun normalizeAngle(angle: Float): Float {
    val normalized = angle % 360f
    return if (normalized < 0f) normalized + 360f else normalized
}
