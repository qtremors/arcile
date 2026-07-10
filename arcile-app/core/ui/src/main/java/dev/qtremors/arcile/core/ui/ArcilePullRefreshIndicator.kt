package dev.qtremors.arcile.core.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.pulltorefresh.PullToRefreshState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.Spring
import dev.qtremors.arcile.core.ui.theme.ArcileMotion
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun BoxScope.ArcilePullRefreshIndicator(
    isRefreshing: Boolean,
    state: PullToRefreshState
) {
    val pullDistance = state.distanceFraction
    val yOffset = (-40.dp + (80.dp * pullDistance)).coerceIn(-40.dp, 40.dp)
    val animatedYOffset = animateDpAsState(
        targetValue = if (isRefreshing) 40.dp else yOffset,
        animationSpec = ArcileMotion.rememberSpring(stiffness = Spring.StiffnessMedium),
        label = "pull_refresh_offset"
    )

    if (isRefreshing || pullDistance > 0f) {
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .zIndex(1f)
                .graphicsLayer {
                    translationY = animatedYOffset.value.toPx()
                    alpha = if (isRefreshing) 1f else pullDistance.coerceIn(0f, 1f)
                }
                .padding(top = 8.dp)
        ) {
            Card(
                shape = CircleShape,
                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
            ) {
                Box(
                    modifier = Modifier.padding(10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    LoadingIndicator(modifier = Modifier.size(24.dp))
                }
            }
        }
    }
}
