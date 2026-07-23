package dev.qtremors.arcile.feature.storageusage.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.qtremors.arcile.core.presentation.formatFileSize
import dev.qtremors.arcile.core.storage.domain.StorageUsageNode
import dev.qtremors.arcile.core.ui.R
import dev.qtremors.arcile.core.ui.theme.ArcileMotion
import dev.qtremors.arcile.core.ui.theme.bodyMediumBold

private const val MAX_SUNBURST_DEPTH = 5
private const val MAX_SUNBURST_SEGMENTS = 240
private const val MAX_SUNBURST_CHILDREN_PER_NODE = 24

@Composable
internal fun StorageUsageSunburst(
    root: StorageUsageNode,
    selectedNode: StorageUsageNode?,
    onSelectNode: (StorageUsageNode) -> Unit,
    onDrillIntoNode: ((StorageUsageNode) -> Unit)? = null,
    onOpenNode: ((StorageUsageNode) -> Unit)? = null,
    onResetToOverview: (() -> Unit)? = null,
    volumeTotalBytes: Long = 0L,
    volumeFreeBytes: Long = 0L,
    isVolumeRoot: Boolean = true
) {
    val currentOnOpenNode by rememberUpdatedState(onOpenNode)
    val colorScheme = MaterialTheme.colorScheme
    val colors = remember(colorScheme) {
        listOf(
            colorScheme.primary,
            colorScheme.tertiary,
            colorScheme.secondary,
            colorScheme.error,
            colorScheme.primaryContainer,
            colorScheme.tertiaryContainer,
            colorScheme.surfaceVariant
        )
    }
    val visibleSegments = remember(root, isVolumeRoot) {
        if (isVolumeRoot) {
            (listOf(root) + root.children).take(MAX_SUNBURST_CHILDREN_PER_NODE)
        } else {
            root.children.take(MAX_SUNBURST_CHILDREN_PER_NODE)
        }
    }
    val selectedForSemantics = selectedNode ?: root
    val chartDescription = stringResource(R.string.storage_usage_map_chart_description, root.name)
    val resetOverviewLabel = stringResource(R.string.storage_usage_map_reset_overview)
    val selectedState = stringResource(
        R.string.storage_usage_map_selected_state,
        selectedForSemantics.name,
        formatFileSize(selectedForSemantics.sizeBytes),
        selectedForSemantics.childCount
    )
    val segmentActionLabels = visibleSegments.map { node ->
        stringResource(R.string.storage_usage_map_select_segment, node.name)
    }
    val segmentActions = remember(visibleSegments, segmentActionLabels, onSelectNode) {
        visibleSegments.zip(segmentActionLabels).map { (node, label) ->
            CustomAccessibilityAction(
                label = label,
                action = {
                    onSelectNode(node)
                    true
                }
            )
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f),
            shape = MaterialTheme.shapes.extraLarge,
            color = colorScheme.surfaceContainer
        ) {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp),
                contentAlignment = Alignment.Center
            ) {
                val chartSize = if (maxWidth < maxHeight) maxWidth else maxHeight
                val chartSizePx = with(LocalDensity.current) { chartSize.toPx() }
                val centerRadius = chartSizePx * 0.18f
                val maxAvailableRadius = (chartSizePx / 2f) - centerRadius - 8f
                val ringWidth = maxAvailableRadius / MAX_SUNBURST_DEPTH.toFloat()
                var pinchScale by remember { mutableFloatStateOf(1f) }
                var panOffset by remember { mutableStateOf(Offset.Zero) }
                val animatedSelectionExtraWidth by animateFloatAsState(
                    targetValue = if (selectedNode != null) 1f else 0f,
                    animationSpec = ArcileMotion.rememberSpring(stiffness = Spring.StiffnessMedium),
                    label = "sunburst_selection"
                )

                key(root.path, isVolumeRoot, volumeTotalBytes) {
                    var startAnimation by remember { mutableStateOf(false) }
                    LaunchedEffect(Unit) {
                        startAnimation = true
                    }
                    val revealProgress by animateFloatAsState(
                        targetValue = if (startAnimation) 1f else 0f,
                        animationSpec = ArcileMotion.rememberSpring(stiffness = Spring.StiffnessLow),
                        label = "sunburst_reveal"
                    )
                    val rotationAngle by animateFloatAsState(
                        targetValue = if (startAnimation) 0f else -45f,
                        animationSpec = ArcileMotion.rememberSpring(stiffness = Spring.StiffnessMediumLow),
                        label = "sunburst_rotation"
                    )
                    val zoomFactor by animateFloatAsState(
                        targetValue = if (startAnimation) 1f else 0.85f,
                        animationSpec = ArcileMotion.rememberSpring(stiffness = Spring.StiffnessMediumLow),
                        label = "sunburst_zoom"
                    )
                    val segments = remember(
                        root,
                        colors,
                        chartSizePx,
                        pinchScale,
                        volumeTotalBytes,
                        volumeFreeBytes,
                        isVolumeRoot
                    ) {
                        buildSegments(
                            root = root,
                            colors = colors,
                            centerRadius = centerRadius,
                            ringWidth = ringWidth,
                            maxDepth = MAX_SUNBURST_DEPTH,
                            maxSegments = MAX_SUNBURST_SEGMENTS,
                            maxChildrenPerNode = MAX_SUNBURST_CHILDREN_PER_NODE,
                            zoomScale = pinchScale,
                            volumeTotalBytes = volumeTotalBytes,
                            volumeFreeBytes = volumeFreeBytes,
                            isVolumeRoot = isVolumeRoot
                        )
                    }

                    Canvas(
                        modifier = Modifier
                            .size(chartSize)
                            .graphicsLayer {
                                rotationZ = rotationAngle
                                scaleX = zoomFactor * pinchScale
                                scaleY = zoomFactor * pinchScale
                                translationX = panOffset.x
                                translationY = panOffset.y
                            }
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
                                val selectedIndex = visibleSegments.indexOfFirst {
                                    it.path == selectedNode?.path
                                }
                                when (event.key) {
                                    Key.DirectionRight,
                                    Key.DirectionDown -> {
                                        val nextIndex = if (selectedIndex < 0) {
                                            0
                                        } else {
                                            (selectedIndex + 1) % visibleSegments.size
                                        }
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
                                        val node = visibleSegments.getOrNull(selectedIndex)
                                            ?: visibleSegments.first()
                                        if (node.isContainer && node.children.isNotEmpty()) {
                                            onDrillIntoNode?.invoke(node)
                                        } else {
                                            onSelectNode(node)
                                        }
                                        true
                                    }
                                    else -> false
                                }
                            }
                            .focusable()
                            .pointerInput(Unit) {
                                detectTransformGestures { _, pan, zoom, _ ->
                                    pinchScale = (pinchScale * zoom).coerceIn(1f, 5f)
                                    panOffset = if (pinchScale > 1f) {
                                        panOffset + pan
                                    } else {
                                        Offset.Zero
                                    }
                                }
                            }
                            .pointerInput(
                                root,
                                segments
                            ) {
                                detectTapGestures(
                                    onTap = { offset ->
                                        findSegmentAt(
                                            offset,
                                            size.width.toFloat(),
                                            size.height.toFloat(),
                                            segments
                                        )?.let { onSelectNode(it.node) }
                                    },
                                    onDoubleTap = { offset ->
                                        findSegmentAt(
                                            offset,
                                            size.width.toFloat(),
                                            size.height.toFloat(),
                                            segments
                                        )?.node?.let { node ->
                                            if (node.isContainer && node.children.isNotEmpty()) {
                                                onDrillIntoNode?.invoke(node)
                                            } else {
                                                onSelectNode(node)
                                            }
                                        }
                                    },
                                    onLongPress = { offset ->
                                        findSegmentAt(
                                            offset,
                                            size.width.toFloat(),
                                            size.height.toFloat(),
                                            segments
                                        )?.node?.let { node ->
                                            currentOnOpenNode?.invoke(node)
                                        }
                                    }
                                )
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
                            val expansion = if (isSelected) {
                                6.dp.toPx() * animatedSelectionExtraWidth
                            } else {
                                0f
                            }
                            val innerRadius = (segment.innerRadius - expansion * 0.3f)
                                .coerceAtLeast(centerRadius)
                            val outerRadius = segment.outerRadius + expansion
                            val sweepAngle = segment.sweepAngle * revealProgress
                            if (sweepAngle <= 0.05f) return@forEach

                            val outerRect = Rect(
                                center.x - outerRadius,
                                center.y - outerRadius,
                                center.x + outerRadius,
                                center.y + outerRadius
                            )
                            val innerRect = Rect(
                                center.x - innerRadius,
                                center.y - innerRadius,
                                center.x + innerRadius,
                                center.y + innerRadius
                            )
                            val wedgePath = Path().apply {
                                arcTo(
                                    rect = outerRect,
                                    startAngleDegrees = segment.startAngle,
                                    sweepAngleDegrees = sweepAngle,
                                    forceMoveTo = true
                                )
                                arcTo(
                                    rect = innerRect,
                                    startAngleDegrees = segment.startAngle + sweepAngle,
                                    sweepAngleDegrees = -sweepAngle,
                                    forceMoveTo = false
                                )
                                close()
                            }
                            drawPath(
                                path = wedgePath,
                                color = segment.color.copy(
                                    alpha = when {
                                        segment.node.sizeBytes <= 0L -> 0.3f
                                        selectedNode == null -> 0.82f
                                        isSelected -> 1f
                                        else -> 0.56f
                                    }
                                ),
                                style = Fill
                            )
                            drawPath(
                                path = wedgePath,
                                color = colorScheme.surface.copy(alpha = 0.72f),
                                style = Stroke(width = 0.8.dp.toPx())
                            )
                            if (isSelected) {
                                drawPath(
                                    path = wedgePath,
                                    color = colorScheme.onSurface.copy(
                                        alpha = 0.75f * animatedSelectionExtraWidth
                                    ),
                                    style = Stroke(width = 2.5.dp.toPx())
                                )
                            }
                        }
                    }
                }

                val centerNode = selectedNode ?: root
                val centerTitle = if (
                    centerNode.name == "0" ||
                    centerNode.path.endsWith("/0")
                ) {
                    stringResource(R.string.internal_storage)
                } else {
                    centerNode.name
                }
                Column(
                    modifier = Modifier
                        .size(chartSize * 0.36f)
                        .clip(CircleShape)
                        .background(colorScheme.surfaceContainerHighest, CircleShape)
                        .combinedClickable(
                            onClick = {
                                pinchScale = 1f
                                panOffset = Offset.Zero
                            },
                            onLongClick = onResetToOverview?.let { resetOverview ->
                                {
                                    pinchScale = 1f
                                    panOffset = Offset.Zero
                                    resetOverview()
                                }
                            },
                            onLongClickLabel = resetOverviewLabel
                        )
                        .padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = centerTitle,
                        style = MaterialTheme.typography.bodyMediumBold,
                        color = colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = formatFileSize(centerNode.sizeBytes),
                        style = MaterialTheme.typography.labelMedium,
                        color = colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}
