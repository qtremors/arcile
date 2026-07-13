package dev.qtremors.arcile.core.ui

import android.provider.Settings
import dev.qtremors.arcile.core.ui.theme.LocalReducedMotionEnabled
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.FolderOff
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.qtremors.arcile.core.ui.R
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.LinearEasing
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

enum class EmptyStateVariant {
    Generic,
    Folder,
    Search,
    Trash,
    StorageAccess,
    Archive,
    Recent
}

private fun createMorphingShape(progress: Float): Shape {
    return object : Shape {
        override fun createOutline(
            size: Size,
            layoutDirection: LayoutDirection,
            density: Density
        ): Outline {
            val numPoints = 8
            val cx = size.width / 2f
            val cy = size.height / 2f
            val baseR = size.width.coerceAtMost(size.height) / 2f

            val points = List(numPoints) { i ->
                val angle = (i * 2.0 * PI / numPoints).toFloat()

                // Slowly morphing wave components to resemble dynamic liquid/loading shapes
                val wave = sin(angle * 3f + progress * 2f * PI.toFloat()) * 0.12f +
                           cos(angle * 2f - progress * 2f * PI.toFloat() * 0.8f) * 0.08f

                val r = baseR * (0.8f + wave)
                val x = cx + r * cos(angle)
                val y = cy + r * sin(angle)
                Offset(x, y)
            }

            val path = Path().apply {
                val firstMid = Offset(
                    (points[0].x + points[numPoints - 1].x) / 2f,
                    (points[0].y + points[numPoints - 1].y) / 2f
                )
                moveTo(firstMid.x, firstMid.y)

                for (i in 0 until numPoints) {
                    val current = points[i]
                    val next = points[(i + 1) % numPoints]
                    val mid = Offset((current.x + next.x) / 2f, (current.y + next.y) / 2f)
                    quadraticTo(current.x, current.y, mid.x, mid.y)
                }
                close()
            }
            return Outline.Generic(path)
        }
    }
}

/**
 * A minimal, context-aware empty state component for file-manager surfaces.
 */
@Composable
fun EmptyState(
    modifier: Modifier = Modifier,
    variant: EmptyStateVariant = EmptyStateVariant.Generic,
    icon: ImageVector? = null,
    title: String? = null,
    description: String? = null,
    action: (@Composable () -> Unit)? = null
) {
    val reduceMotion = LocalReducedMotionEnabled.current
    val visuals = emptyStateVisuals(variant)
    val resolvedTitle = title ?: emptyStateTitle(variant)
    val resolvedDescription = description ?: emptyStateDescription(variant)

    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.secondary
    val tertiaryColor = MaterialTheme.colorScheme.tertiary
    val titleBrush = remember(primaryColor, secondaryColor, tertiaryColor) {
        Brush.linearGradient(
            colors = listOf(primaryColor, secondaryColor, tertiaryColor)
        )
    }

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        EmptyStateBlobs()

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.45f),
            border = BorderStroke(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                if (reduceMotion) {
                    EmptyStateIcon(icon ?: visuals.icon, visuals.containerColor, visuals.iconTint)
                } else {
                    AnimatedVisibility(visible = true, enter = fadeIn(tween(durationMillis = 180))) {
                        EmptyStateIcon(icon ?: visuals.icon, visuals.containerColor, visuals.iconTint)
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = resolvedTitle,
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        brush = titleBrush
                    ),
                    textAlign = TextAlign.Center
                )

                if (resolvedDescription != null) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = resolvedDescription,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }

                if (action != null) {
                    Spacer(modifier = Modifier.height(24.dp))
                    action()
                }
            }
        }
    }
}

@Composable
private fun EmptyStateIcon(
    icon: ImageVector,
    containerColor: Color,
    iconTint: Color
) {
    val reduceMotion = LocalReducedMotionEnabled.current

    val transition = rememberInfiniteTransition(label = "iconPulse")
    val scale = if (reduceMotion) 1f else {
        transition.animateFloat(
            initialValue = 0.96f,
            targetValue = 1.04f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 3000, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "scale"
        ).value
    }

    val rotation = if (reduceMotion) 0f else {
        transition.animateFloat(
            initialValue = -3f,
            targetValue = 3f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 4000, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "rotation"
        ).value
    }

    val morphProgress = if (reduceMotion) 0f else {
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 8000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "morphProgress"
        ).value
    }

    val colors = MaterialTheme.colorScheme
    val primColor = colors.primary
    val secColor = colors.secondary
    val tertColor = colors.tertiary
    val borderBrush = remember(primColor, secColor, tertColor) {
        Brush.sweepGradient(
            colors = listOf(
                primColor.copy(alpha = 0.7f),
                secColor.copy(alpha = 0.2f),
                tertColor.copy(alpha = 0.7f),
                primColor.copy(alpha = 0.7f)
            )
        )
    }

    val morphingShape = remember(morphProgress) {
        createMorphingShape(morphProgress)
    }

    Surface(
        shape = morphingShape,
        color = containerColor.copy(alpha = 0.25f),
        border = BorderStroke(1.5.dp, borderBrush),
        modifier = Modifier
            .size(96.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                rotationZ = rotation
            }
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(38.dp),
                tint = iconTint
            )
        }
    }
}

private data class EmptyStateVisuals(
    val icon: ImageVector,
    val containerColor: Color,
    val iconTint: Color
)

@Composable
private fun emptyStateVisuals(variant: EmptyStateVariant): EmptyStateVisuals {
    val colors = MaterialTheme.colorScheme
    val quietContainer = colors.surfaceContainerHighest
    val quietTint = colors.onSurfaceVariant
    return when (variant) {
        EmptyStateVariant.Generic -> EmptyStateVisuals(Icons.Default.FolderOff, quietContainer, quietTint)
        EmptyStateVariant.Folder -> EmptyStateVisuals(Icons.Default.FolderOff, quietContainer, colors.primary)
        EmptyStateVariant.Search -> EmptyStateVisuals(Icons.Default.SearchOff, quietContainer, colors.secondary)
        EmptyStateVariant.Trash -> EmptyStateVisuals(Icons.Default.DeleteSweep, quietContainer, colors.error)
        EmptyStateVariant.StorageAccess -> EmptyStateVisuals(Icons.Default.Storage, quietContainer, colors.tertiary)
        EmptyStateVariant.Archive -> EmptyStateVisuals(Icons.Default.FolderZip, quietContainer, colors.primary)
        EmptyStateVariant.Recent -> EmptyStateVisuals(Icons.Default.History, quietContainer, quietTint)
    }
}

@Composable
private fun emptyStateTitle(variant: EmptyStateVariant): String =
    when (variant) {
        EmptyStateVariant.Generic -> stringResource(R.string.empty_state_generic_title)
        EmptyStateVariant.Folder -> stringResource(R.string.empty_directory)
        EmptyStateVariant.Search -> stringResource(R.string.no_results_found)
        EmptyStateVariant.Trash -> stringResource(R.string.trash_is_empty)
        EmptyStateVariant.StorageAccess -> stringResource(R.string.storage_management_empty_title)
        EmptyStateVariant.Archive -> stringResource(R.string.archive_empty_title)
        EmptyStateVariant.Recent -> stringResource(R.string.no_recent_files)
    }

@Composable
private fun emptyStateDescription(variant: EmptyStateVariant): String? =
    when (variant) {
        EmptyStateVariant.Generic -> stringResource(R.string.empty_state_generic_description)
        EmptyStateVariant.Folder -> stringResource(R.string.empty_directory_description)
        EmptyStateVariant.Search -> null
        EmptyStateVariant.Trash -> stringResource(R.string.trash_empty_description)
        EmptyStateVariant.StorageAccess -> stringResource(R.string.storage_management_empty_description)
        EmptyStateVariant.Archive -> stringResource(R.string.archive_empty_description)
        EmptyStateVariant.Recent -> stringResource(R.string.no_recent_files_description)
    }
