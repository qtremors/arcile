package dev.qtremors.arcile.core.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.Alignment
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.qtremors.arcile.core.ui.theme.bounceClickable
import dev.qtremors.arcile.core.ui.theme.spacing

enum class ArcileFeedbackSeverity {
    Success,
    Error,
    Warning,
    Info
}

/**
 * A customized SnackbarHost that follows the Arcile Material 3 Expressive design language.
 * 
 * Features:
 * - Squircle shape (extraLarge)
 * - Tonal container colors (surfaceContainerHigh)
 * - Content-sized feedback bubbles with a capped long-message width
 * - Standardized spacing and typography
 */
@Composable
fun ArcileSnackbarHost(
    hostState: SnackbarHostState,
    modifier: Modifier = Modifier,
    severityFor: (SnackbarData) -> ArcileFeedbackSeverity = { ArcileFeedbackSeverity.Info }
) {
    SnackbarHost(
        hostState = hostState,
        modifier = modifier
            .wrapContentWidth(Alignment.CenterHorizontally)
            .padding(MaterialTheme.spacing.space12)
    ) { data ->
        val severity = severityFor(data)
        Box(
            modifier = Modifier.wrapContentWidth(Alignment.CenterHorizontally),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                contentColor = MaterialTheme.colorScheme.onSurface,
                tonalElevation = 6.dp,
                shadowElevation = 3.dp,
                modifier = Modifier
                    .widthIn(max = 520.dp)
                    .wrapContentWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = severity.icon(),
                        contentDescription = null,
                        tint = severity.tint(),
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = data.visuals.message,
                        overflow = TextOverflow.Ellipsis,
                        maxLines = 3,
                        modifier = Modifier.widthIn(max = 360.dp)
                    )
                    data.visuals.actionLabel?.let { label ->
                        TextButton(
                            onClick = data::performAction,
                            shape = MaterialTheme.shapes.medium,
                            modifier = Modifier.bounceClickable(onClick = data::performAction)
                        ) {
                            Text(
                                text = label,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    if (data.visuals.withDismissAction) {
                        IconButton(
                            onClick = data::dismiss,
                            modifier = Modifier
                                .clip(CircleShape)
                                .bounceClickable(onClick = data::dismiss)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

fun ArcileFeedbackSeverity.icon(): ImageVector =
    when (this) {
        ArcileFeedbackSeverity.Success -> Icons.Default.CheckCircle
        ArcileFeedbackSeverity.Error -> Icons.Default.Error
        ArcileFeedbackSeverity.Warning -> Icons.Default.Warning
        ArcileFeedbackSeverity.Info -> Icons.Default.Info
    }

@Composable
private fun ArcileFeedbackSeverity.tint() =
    when (this) {
        ArcileFeedbackSeverity.Success -> MaterialTheme.colorScheme.primary
        ArcileFeedbackSeverity.Error -> MaterialTheme.colorScheme.error
        ArcileFeedbackSeverity.Warning -> MaterialTheme.colorScheme.tertiary
        ArcileFeedbackSeverity.Info -> MaterialTheme.colorScheme.primary
    }
