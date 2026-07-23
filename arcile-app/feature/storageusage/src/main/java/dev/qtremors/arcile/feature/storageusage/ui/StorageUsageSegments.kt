package dev.qtremors.arcile.feature.storageusage.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import dev.qtremors.arcile.core.storage.domain.StorageUsageNode
import dev.qtremors.arcile.core.storage.domain.StorageUsageNodeKind
import dev.qtremors.arcile.core.storage.domain.StorageUsageScanStatus
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

private const val MAX_SUNBURST_DEPTH = 5
private const val MAX_SUNBURST_SEGMENTS = 240
private const val MAX_SUNBURST_CHILDREN_PER_NODE = 24

internal data class RingSegment(
    val node: StorageUsageNode,
    val startAngle: Float,
    val sweepAngle: Float,
    val innerRadius: Float,
    val outerRadius: Float,
    val color: Color
)

internal fun calculateRingSizeLimits(
    totalSize: Long,
    ringWidth: Float,
    visibleDepth: Int
): List<Long> {
    if (visibleDepth <= 0 || ringWidth <= 0f) return List(visibleDepth + 1) { 0L }
    val circumferenceFactor = PI * 4.0 * ringWidth
    return List(visibleDepth + 1) { depth ->
        (totalSize.toDouble() / (circumferenceFactor * (depth + 1))).toLong().coerceAtLeast(0L)
    }
}

internal fun buildSegments(
    root: StorageUsageNode,
    colors: List<Color>,
    centerRadius: Float,
    ringWidth: Float,
    maxDepth: Int,
    maxSegments: Int,
    maxChildrenPerNode: Int,
    zoomScale: Float = 1f,
    volumeTotalBytes: Long = 0L,
    volumeFreeBytes: Long = 0L,
    isVolumeRoot: Boolean = true
): List<RingSegment> {
    val segments = mutableListOf<RingSegment>()
    val palette = colors.ifEmpty { listOf(Color.Gray) }
    val neutralColor = palette.last()
    val accentColors = palette.dropLast(1).ifEmpty { palette }
    val effectiveMaxSegments = (maxSegments * zoomScale.coerceIn(1f, 2.5f))
        .toInt()
        .coerceIn(maxSegments, 500)
    val effectiveMaxChildren = (maxChildrenPerNode * zoomScale.coerceIn(1f, 2f))
        .toInt()
        .coerceIn(maxChildrenPerNode, 48)
    val minSweepThreshold = (0.35f / zoomScale.coerceIn(1f, 4f)).coerceAtLeast(0.04f)
    val ringSizeLimits = calculateRingSizeLimits(root.sizeBytes, ringWidth, maxDepth)

    fun segmentColor(
        child: StorageUsageNode,
        depth: Int,
        branchColor: Color
    ): Color {
        val depthBlend = ((depth - 1) * 0.1f).coerceIn(0f, 0.35f)
        val depthColor = lerp(branchColor, neutralColor, depthBlend)
        return when (child.kind) {
            StorageUsageNodeKind.Folder -> depthColor
            StorageUsageNodeKind.File -> lerp(depthColor, neutralColor, 0.58f)
            StorageUsageNodeKind.Grouped -> lerp(depthColor, neutralColor, 0.78f)
        }
    }

    fun buildSubtree(
        node: StorageUsageNode,
        depth: Int,
        startAngle: Float,
        parentSweep: Float,
        inheritedColor: Color
    ) {
        if (depth > maxDepth || node.children.isEmpty() || segments.size >= effectiveMaxSegments) return

        val visibleChildBudget = min(
            effectiveMaxChildren,
            effectiveMaxSegments - segments.size
        ).coerceAtLeast(0)
        if (visibleChildBudget == 0) return

        val limitForDepth = ringSizeLimits.getOrElse(depth) { 0L }
        val filteredChildren = if (zoomScale > 1.2f || depth <= 2) {
            node.children
        } else {
            node.children.filter { child ->
                child.sizeBytes >= limitForDepth || child.kind == StorageUsageNodeKind.Folder
            }
        }
        val visibleChildren = filteredChildren.take(visibleChildBudget)
        val hiddenChildren = filteredChildren.drop(visibleChildBudget) +
            (node.children - filteredChildren.toSet())
        val chartChildren = if (hiddenChildren.isEmpty()) {
            visibleChildren
        } else {
            visibleChildren + StorageUsageNode(
                name = "Other small items",
                path = "${node.path}/Other small items",
                sizeBytes = hiddenChildren.sumOf(StorageUsageNode::sizeBytes),
                kind = StorageUsageNodeKind.Grouped,
                childCount = hiddenChildren.sumOf { max(1, it.childCount) },
                status = if (hiddenChildren.any { it.status != StorageUsageScanStatus.Ready }) {
                    StorageUsageScanStatus.Partial
                } else {
                    StorageUsageScanStatus.Ready
                }
            )
        }

        val totalSize = chartChildren.sumOf(StorageUsageNode::sizeBytes).coerceAtLeast(1L)
        var currentAngle = startAngle
        chartChildren.forEachIndexed { index, child ->
            if (segments.size >= effectiveMaxSegments) return@forEachIndexed

            val childSweep = parentSweep * (child.sizeBytes.toDouble() / totalSize.toDouble()).toFloat()
            if (childSweep < minSweepThreshold && depth > 2) {
                currentAngle += childSweep
                return@forEachIndexed
            }

            val branchColor = if (depth <= 2) {
                accentColors[index % accentColors.size]
            } else {
                inheritedColor
            }
            val innerRadius = centerRadius + (depth - 1) * ringWidth
            segments += RingSegment(
                node = child,
                startAngle = currentAngle,
                sweepAngle = childSweep,
                innerRadius = innerRadius,
                outerRadius = innerRadius + ringWidth,
                color = segmentColor(child, depth, branchColor)
            )

            if (child.kind == StorageUsageNodeKind.Folder) {
                buildSubtree(
                    node = child,
                    depth = depth + 1,
                    startAngle = currentAngle,
                    parentSweep = childSweep,
                    inheritedColor = branchColor
                )
            }
            currentAngle += childSweep
        }
    }

    if (isVolumeRoot && volumeTotalBytes > 0L) {
        val totalCapacity = volumeTotalBytes.toDouble()
        val freeBytes = volumeFreeBytes.coerceIn(0L, volumeTotalBytes)
        val usedBytes = (volumeTotalBytes - freeBytes).coerceAtLeast(0L)
        val accessibleBytes = root.sizeBytes.coerceIn(0L, usedBytes)
        val systemBytes = (usedBytes - accessibleBytes).coerceAtLeast(0L)
        val accessibleSweep = (360f * accessibleBytes.toDouble() / totalCapacity).toFloat()
        val systemSweep = (360f * systemBytes.toDouble() / totalCapacity).toFloat()
        val innerRadius = centerRadius
        val outerRadius = centerRadius + ringWidth
        var angleCursor = -90f

        if (accessibleBytes > 0L) {
            val rootColor = accentColors.first()
            segments += RingSegment(
                node = root,
                startAngle = angleCursor,
                sweepAngle = accessibleSweep,
                innerRadius = innerRadius,
                outerRadius = outerRadius,
                color = rootColor
            )
            buildSubtree(
                node = root,
                depth = 2,
                startAngle = angleCursor,
                parentSweep = accessibleSweep,
                inheritedColor = rootColor
            )
            angleCursor += accessibleSweep
        }

        if (systemBytes > 0L) {
            segments += RingSegment(
                node = StorageUsageNode(
                    name = "System & Other",
                    path = "${root.path}/__system_other__",
                    sizeBytes = systemBytes,
                    kind = StorageUsageNodeKind.Grouped,
                    childCount = 0,
                    status = StorageUsageScanStatus.Ready
                ),
                startAngle = angleCursor,
                sweepAngle = systemSweep,
                innerRadius = innerRadius,
                outerRadius = outerRadius,
                color = neutralColor
            )
        }
    } else {
        buildSubtree(
            node = root,
            depth = 1,
            startAngle = -90f,
            parentSweep = 360f,
            inheritedColor = accentColors.first()
        )
    }

    return segments
}

internal fun boundedStorageUsageSunburstSegmentCount(root: StorageUsageNode): Int =
    buildSegments(
        root = root,
        colors = listOf(Color.Red, Color.Green, Color.Blue, Color.Gray),
        centerRadius = 10f,
        ringWidth = 10f,
        maxDepth = MAX_SUNBURST_DEPTH,
        maxSegments = MAX_SUNBURST_SEGMENTS,
        maxChildrenPerNode = MAX_SUNBURST_CHILDREN_PER_NODE
    ).size

internal fun StorageUsageNode.maxDepth(): Int {
    if (children.isEmpty()) return 1
    return 1 + (children.maxOfOrNull { it.maxDepth() } ?: 0)
}

internal fun findSegmentAt(
    offset: Offset,
    width: Float,
    height: Float,
    segments: List<RingSegment>
): RingSegment? {
    val center = Offset(width / 2f, height / 2f)
    val dx = offset.x - center.x
    val dy = offset.y - center.y
    val radius = hypot(dx, dy)
    val angle = normalizeAngle(atan2(dy, dx) * 180f / PI.toFloat())
    return segments
        .filter {
            radius >= it.innerRadius &&
                radius <= it.outerRadius &&
                angleInSweep(angle, it.startAngle, it.sweepAngle)
        }
        .maxByOrNull(RingSegment::innerRadius)
}

internal fun angleInSweep(angle: Float, startAngle: Float, sweepAngle: Float): Boolean {
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

internal fun normalizeAngle(angle: Float): Float {
    val normalized = angle % 360f
    return if (normalized < 0f) normalized + 360f else normalized
}
