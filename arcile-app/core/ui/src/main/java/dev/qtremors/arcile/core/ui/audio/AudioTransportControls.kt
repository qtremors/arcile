package dev.qtremors.arcile.core.ui.audio

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun AudioTransportControls(
    isPlaying: Boolean,
    previousLabel: String,
    playPauseLabel: String,
    nextLabel: String,
    onPrevious: (() -> Unit)?,
    onTogglePlayback: () -> Unit,
    onNext: (() -> Unit)?,
    playingContent: (@Composable () -> Unit)? = null
) {
    BoxWithConstraints(Modifier.fillMaxWidth().widthIn(max = 420.dp)) {
        val iconSize = if (maxWidth < 300.dp) 28.dp else 32.dp
        val buttonHeight = 68.dp
        val colors = MaterialTheme.colorScheme
        val previousInteraction = remember { MutableInteractionSource() }
        val playInteraction = remember { MutableInteractionSource() }
        val nextInteraction = remember { MutableInteractionSource() }
        val previousPressed by previousInteraction.collectIsPressedAsState()
        val playPressed by playInteraction.collectIsPressedAsState()
        val nextPressed by nextInteraction.collectIsPressedAsState()
        val widthAnimation = spring<Float>(dampingRatio = 0.6f, stiffness = 500f)
        val previousWeight by animateFloatAsState(
            if (previousPressed) 0.65f else if (playPressed) 0.35f else 0.45f,
            widthAnimation, label = "previous button width"
        )
        val playWeight by animateFloatAsState(
            if (playPressed) 1.9f else if (previousPressed || nextPressed) 1.1f else 1.3f,
            widthAnimation, label = "play button width"
        )
        val nextWeight by animateFloatAsState(
            if (nextPressed) 0.65f else if (playPressed) 0.35f else 0.45f,
            widthAnimation, label = "next button width"
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AudioTransportButton(
                Icons.Default.SkipPrevious, previousLabel, Modifier.weight(previousWeight).height(buttonHeight),
                colors.surfaceVariant, colors.onSurfaceVariant, previousInteraction, onPrevious, iconSize
            )
            AudioTransportButton(
                if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                playPauseLabel, Modifier.weight(playWeight).height(buttonHeight),
                colors.primary, colors.onPrimary, playInteraction, onTogglePlayback, iconSize,
                content = playingContent.takeIf { isPlaying }
            )
            AudioTransportButton(
                Icons.Default.SkipNext, nextLabel, Modifier.weight(nextWeight).height(buttonHeight),
                colors.surfaceVariant, colors.onSurfaceVariant, nextInteraction, onNext, iconSize
            )
        }
    }
}

@Composable
private fun AudioTransportButton(
    icon: ImageVector,
    description: String,
    modifier: Modifier,
    containerColor: Color,
    contentColor: Color,
    interactionSource: MutableInteractionSource,
    onClick: (() -> Unit)?,
    iconSize: Dp,
    content: (@Composable () -> Unit)? = null
) {
    Surface(
        onClick = { onClick?.invoke() },
        enabled = onClick != null,
        interactionSource = interactionSource,
        shape = CircleShape,
        color = containerColor,
        contentColor = if (onClick == null) contentColor.copy(alpha = 0.38f) else contentColor,
        modifier = modifier.semantics { contentDescription = description }
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            if (content != null) {
                content()
            } else {
                AnimatedContent(
                    targetState = icon,
                    transitionSpec = {
                        (fadeIn() + scaleIn(initialScale = 0.75f)) togetherWith
                            (fadeOut() + scaleOut(targetScale = 0.75f))
                    },
                    label = "transport icon"
                ) { displayedIcon ->
                    Icon(displayedIcon, contentDescription = null, modifier = Modifier.size(iconSize))
                }
            }
        }
    }
}
