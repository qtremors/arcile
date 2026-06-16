package dev.qtremors.arcile.feature.onboarding.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.CornerRounding
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.circle
import androidx.graphics.shapes.star
import androidx.graphics.shapes.toPath
import dev.qtremors.arcile.ui.theme.LocalReducedMotionEnabled

@Composable
fun MorphingBackgroundIcon(
    modifier: Modifier = Modifier,
    icon: @Composable () -> Unit
) {
    // Define the polygons for morphing
    val starPolygon = remember {
        RoundedPolygon.star(
            numVerticesPerRadius = 8,
            innerRadius = 0.55f,
            radius = 1.0f,
            rounding = CornerRounding(radius = 0.15f)
        )
    }
    val circlePolygon = remember {
        RoundedPolygon.circle(
            numVertices = 8
        )
    }
    val morph = remember { Morph(starPolygon, circlePolygon) }

    val primaryColor = MaterialTheme.colorScheme.primary
    val tertiaryColor = MaterialTheme.colorScheme.tertiary
    val secondaryColor = MaterialTheme.colorScheme.secondaryContainer
    val reducedMotion = LocalReducedMotionEnabled.current

    Box(
        modifier = modifier.size(160.dp),
        contentAlignment = Alignment.Center
    ) {
        if (reducedMotion) {
            MorphingBackgroundCanvas(
                morph = morph,
                progress = 0.5f,
                rotationOuter = 0f,
                rotationInner = 0f,
                primaryColor = primaryColor,
                tertiaryColor = tertiaryColor,
                secondaryColor = secondaryColor
            )
        } else {
            AnimatedMorphingBackgroundCanvas(
                morph = morph,
                primaryColor = primaryColor,
                tertiaryColor = tertiaryColor,
                secondaryColor = secondaryColor
            )
        }

        // Foreground App Icon
        Box(modifier = Modifier.size(64.dp)) {
            icon()
        }
    }
}

@Composable
private fun AnimatedMorphingBackgroundCanvas(
    morph: Morph,
    primaryColor: Color,
    tertiaryColor: Color,
    secondaryColor: Color
) {
    val infiniteTransition = rememberInfiniteTransition(label = "morphing_bg")

    val progress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 4000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "morph_progress"
    )

    val rotationOuter by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 20000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation_outer"
    )

    val rotationInner by infiniteTransition.animateFloat(
        initialValue = 360f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 14000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation_inner"
    )

    MorphingBackgroundCanvas(
        morph = morph,
        progress = progress,
        rotationOuter = rotationOuter,
        rotationInner = rotationInner,
        primaryColor = primaryColor,
        tertiaryColor = tertiaryColor,
        secondaryColor = secondaryColor
    )
}

@Composable
private fun MorphingBackgroundCanvas(
    morph: Morph,
    progress: Float,
    rotationOuter: Float,
    rotationInner: Float,
    primaryColor: Color,
    tertiaryColor: Color,
    secondaryColor: Color
) {
    Canvas(modifier = Modifier.size(140.dp)) {
        val path = morph.toPath(progress = progress).asComposePath()

        val maxScale = size.minDimension / 2f
        val centerX = size.width / 2f
        val centerY = size.height / 2f

        withTransform({
            translate(centerX, centerY)
            rotate(rotationOuter)
            scale(maxScale, maxScale, pivot = Offset(0f, 0f))
        }) {
            drawPath(
                path = path,
                brush = Brush.linearGradient(
                    colors = listOf(primaryColor.copy(alpha = 0.12f), tertiaryColor.copy(alpha = 0.08f))
                )
            )
        }

        withTransform({
            translate(centerX, centerY)
            rotate(rotationInner)
            scale(maxScale * 0.75f, maxScale * 0.75f, pivot = Offset(0f, 0f))
        }) {
            drawPath(
                path = path,
                brush = Brush.linearGradient(
                    colors = listOf(tertiaryColor.copy(alpha = 0.2f), secondaryColor.copy(alpha = 0.25f))
                )
            )
        }
    }
}
