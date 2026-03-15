package dev.qtremors.arcile.presentation.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.qtremors.arcile.ui.theme.ExpressiveCutShape
import kotlin.math.cos
import kotlin.math.sin

/**
 * A highly expressive and modern empty state component with layered animations and graphics.
 */
@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    action: (@Composable () -> Unit)? = null
) {
    val infiniteTransition = rememberInfiniteTransition(label = "EmptyStateIdle")
    
    // Idle floating animation
    val floatOffset by infiniteTransition.animateFloat(
        initialValue = -8f,
        targetValue = 8f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "Floating"
    )

    // Slow background rotation
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(20000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "BackgroundRotation"
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(240.dp)
        ) {
            // Layer 1: Background decorative particles
            Particles(infiniteTransition)

            // Layer 2: Main Background Squircle (Rotating)
            Surface(
                modifier = Modifier
                    .size(140.dp)
                    .rotate(rotation)
                    .alpha(0.1f),
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.primary
            ) {}

            // Layer 3: Accent Cut Shape (Counter-rotating + Floating)
            Surface(
                modifier = Modifier
                    .size(100.dp)
                    .offset(y = floatOffset.dp)
                    .rotate(-rotation * 1.5f)
                    .alpha(0.15f),
                shape = ExpressiveCutShape,
                color = MaterialTheme.colorScheme.secondary,
                border = androidx.compose.foundation.BorderStroke(
                    2.dp, 
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                )
            ) {}

            // Layer 4: Main Icon Container (Entrance + Idle Float)
            androidx.compose.animation.AnimatedVisibility(
                visible = true,
                enter = fadeIn(tween(800)) + 
                        scaleIn(spring(Spring.DampingRatioLowBouncy, Spring.StiffnessLow)) +
                        slideInVertically { 40 }
            ) {
                Surface(
                    shape = MaterialTheme.shapes.extraLarge,
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    tonalElevation = 8.dp,
                    modifier = Modifier
                        .size(120.dp)
                        .offset(y = (floatOffset * 0.5f).dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )

        if (description != null) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 24.dp)
            )
        }

        if (action != null) {
            Spacer(modifier = Modifier.height(32.dp))
            action()
        }
    }
}

@Composable
private fun Particles(infiniteTransition: InfiniteTransition) {
    val progress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(10000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ParticlesProgress"
    )

    val primaryColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
    val secondaryColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f)

    Canvas(modifier = Modifier.size(240.dp).alpha(0.6f)) {
        val center = Offset(size.width / 2, size.height / 2)
        val radiusBase = size.width * 0.4f
        
        // Dynamic decorative bits
        repeat(5) { i ->
            val angle = (progress * 360f + (i * 72f)) * (Math.PI.toFloat() / 180f)
            val orbitRadius = radiusBase + (sin(progress * Math.PI.toFloat() * 2 + i) * 20f)
            
            val x = center.x + cos(angle) * orbitRadius
            val y = center.y + sin(angle) * orbitRadius
            
            drawCircle(
                color = if (i % 2 == 0) primaryColor else secondaryColor,
                radius = 8f + (sin(progress * Math.PI.toFloat() * 4 + i) * 4f),
                center = Offset(x, y)
            )
        }
    }
}
