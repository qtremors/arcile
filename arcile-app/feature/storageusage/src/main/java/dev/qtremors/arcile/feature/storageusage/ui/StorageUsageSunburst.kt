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

@Composable
fun StorageUsageSunburst(
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
    val chartDescription = stringResource(R.string.storage_usage_map_chart_description, root.name)
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

            val animatedSelectionExtraWidth = animateFloatAsState(
                targetValue = if (selectedNode != null) 1f else 0f,
                animationSpec = ArcileMotion.rememberSpring(stiffness = Spring.StiffnessMedium),
                label = "sunburst_selection"
            )

            key(root.path) {
                var startAnim by remember { mutableStateOf(false) }
                LaunchedEffect(Unit) {
                    startAnim = true
                }
                
                val revealProgress = animateFloatAsState(
                    targetValue = if (startAnim) 1f else 0f,
                    animationSpec = ArcileMotion.rememberSpring(stiffness = Spring.StiffnessLow),
                    label = "sunburst_reveal"
                )
                val rotationAngle = animateFloatAsState(
                    targetValue = if (startAnim) 0f else -45f,
                    animationSpec = ArcileMotion.rememberSpring(stiffness = Spring.StiffnessMediumLow),
                    label = "sunburst_rotation"
                )
                val zoomFactor = animateFloatAsState(
                    targetValue = if (startAnim) 1f else 0.85f,
                    animationSpec = ArcileMotion.rememberSpring(stiffness = Spring.StiffnessMediumLow),
                    label = "sunburst_zoom"
                )

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
                        .graphicsLayer {
                            rotationZ = rotationAngle.value
                            scaleX = zoomFactor.value
                            scaleY = zoomFactor.value
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
                        val strokeMultiplier = if (isSelected) {
                            0.78f + (0.14f * animatedSelectionExtraWidth.value)
                        } else {
                            0.78f
                        }
                        val strokeWidth = ringWidth * strokeMultiplier
                        val radius = (segment.innerRadius + segment.outerRadius) / 2f
                        val topLeft = Offset(center.x - radius, center.y - radius)
                        if (isSelected) {
                            drawArc(
                                color = colorScheme.onSurface.copy(alpha = 0.6f * animatedSelectionExtraWidth.value),
                                startAngle = segment.startAngle,
                                sweepAngle = segment.sweepAngle * revealProgress.value,
                                useCenter = false,
                                topLeft = topLeft,
                                size = Size(radius * 2f, radius * 2f),
                                style = Stroke(width = ringWidth * 0.98f * animatedSelectionExtraWidth.value, cap = StrokeCap.Butt)
                            )
                        }
                        drawArc(
                            color = segment.color.copy(alpha = if (segment.node.sizeBytes <= 0L) 0.35f else 0.92f),
                            startAngle = segment.startAngle,
                            sweepAngle = segment.sweepAngle * revealProgress.value,
                            useCenter = false,
                            topLeft = topLeft,
                            size = Size(radius * 2f, radius * 2f),
                            style = Stroke(width = strokeWidth, cap = StrokeCap.Butt)
                        )
                    }
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

