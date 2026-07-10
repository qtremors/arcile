package dev.qtremors.arcile.core.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import dev.qtremors.arcile.core.ui.theme.LocalReducedMotionEnabled
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

private class BouncingBlob(
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    val radius: Float,
    val color: Color,
    var scaleX: Float = 1f,
    var scaleY: Float = 1f,
    var targetScaleX: Float = 1f,
    var targetScaleY: Float = 1f,
    var squashVelocityX: Float = 0f,
    var squashVelocityY: Float = 0f,
    var phaseSeed: Float = 0f
)

@Composable
fun EmptyStateBlobs(
    modifier: Modifier = Modifier,
    color1: Color = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
    color2: Color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.10f),
    color3: Color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.10f)
) {
    val reducedMotion = LocalReducedMotionEnabled.current
    if (reducedMotion) return

    var size by remember { mutableStateOf(IntSize.Zero) }

    val blobs = remember(size) {
        if (size.width > 0 && size.height > 0) {
            val minDimension = size.width.coerceAtMost(size.height)
            val radius1 = minDimension * 0.18f
            val radius2 = minDimension * 0.15f
            val radius3 = minDimension * 0.12f

            // Initialize randomly to prevent overlapping static starting position
            val random = Random(42) // Stable seed to avoid jumping on layout cycles

            listOf(
                BouncingBlob(
                    x = size.width * 0.25f,
                    y = size.height * 0.3f,
                    vx = size.width * 0.08f + random.nextFloat() * 20f,
                    vy = size.height * 0.06f + random.nextFloat() * 20f,
                    radius = radius1,
                    color = color1,
                    phaseSeed = 0f
                ),
                BouncingBlob(
                    x = size.width * 0.75f,
                    y = size.height * 0.45f,
                    vx = -(size.width * 0.07f + random.nextFloat() * 20f),
                    vy = size.height * 0.08f + random.nextFloat() * 20f,
                    radius = radius2,
                    color = color2,
                    phaseSeed = 2f
                ),
                BouncingBlob(
                    x = size.width * 0.45f,
                    y = size.height * 0.75f,
                    vx = size.width * 0.06f + random.nextFloat() * 20f,
                    vy = -(size.height * 0.07f + random.nextFloat() * 20f),
                    radius = radius3,
                    color = color3,
                    phaseSeed = 4f
                )
            )
        } else {
            emptyList()
        }
    }

    var tick by remember { mutableStateOf(0L) }

    LaunchedEffect(size) {
        if (size.width <= 0 || size.height <= 0) return@LaunchedEffect
        var lastTime = withFrameNanos { it }

        while (true) {
            withFrameNanos { frameTime ->
                val dt = ((frameTime - lastTime) / 1_000_000_000f).coerceIn(0f, 0.05f)
                lastTime = frameTime

                blobs.forEach { blob ->
                    // 1. Position update
                    blob.x += blob.vx * dt
                    blob.y += blob.vy * dt

                    // 2. Slow morphing phase increment
                    blob.phaseSeed += dt * 1.0f

                    // 3. Spring squash/stretch physics calculations
                    val stiffness = 250f
                    val damping = 12f

                    val ax = -stiffness * (blob.scaleX - blob.targetScaleX) - damping * blob.squashVelocityX
                    blob.squashVelocityX += ax * dt
                    blob.scaleX += blob.squashVelocityX * dt

                    val ay = -stiffness * (blob.scaleY - blob.targetScaleY) - damping * blob.squashVelocityY
                    blob.squashVelocityY += ay * dt
                    blob.scaleY += blob.squashVelocityY * dt

                    // Slowly decay target scaling back to 1.0f
                    blob.targetScaleX = blob.targetScaleX + (1f - blob.targetScaleX) * dt * 5f
                    blob.targetScaleY = blob.targetScaleY + (1f - blob.targetScaleY) * dt * 5f

                    // 4. Boundary Collision Checks and Dynamic Squashing
                    // Left edge boundary
                    if (blob.x - blob.radius < 0) {
                        blob.x = blob.radius
                        blob.vx = -blob.vx
                        blob.targetScaleX = 0.65f
                        blob.targetScaleY = 1.35f
                    }
                    // Right edge boundary
                    else if (blob.x + blob.radius > size.width) {
                        blob.x = size.width - blob.radius
                        blob.vx = -blob.vx
                        blob.targetScaleX = 0.65f
                        blob.targetScaleY = 1.35f
                    }

                    // Top edge boundary
                    if (blob.y - blob.radius < 0) {
                        blob.y = blob.radius
                        blob.vy = -blob.vy
                        blob.targetScaleY = 0.65f
                        blob.targetScaleX = 1.35f
                    }
                    // Bottom edge boundary
                    else if (blob.y + blob.radius > size.height) {
                        blob.y = size.height - blob.radius
                        blob.vy = -blob.vy
                        blob.targetScaleY = 0.65f
                        blob.targetScaleX = 1.35f
                    }
                }
                tick = frameTime
            }
        }
    }

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { size = it }
    ) {
        // Reference tick to trigger drawing updates each frame
        val currentTick = tick

        blobs.forEach { blob ->
            val numPoints = 8
            val points = List(numPoints) { i ->
                val angle = (i * 2.0 * PI / numPoints).toFloat()

                // Construct continuous organic waves around the perimeter
                val wave = sin(angle * 3f + blob.phaseSeed) * 0.14f +
                           cos(angle * 2f - blob.phaseSeed * 0.8f) * 0.08f

                val r = blob.radius * (1f + wave)
                val localX = r * cos(angle) * blob.scaleX
                val localY = r * sin(angle) * blob.scaleY

                Offset(blob.x + localX, blob.y + localY)
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

            drawPath(
                path = path,
                color = blob.color
            )
        }
    }
}
