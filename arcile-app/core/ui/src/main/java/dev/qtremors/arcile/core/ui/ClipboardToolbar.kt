package dev.qtremors.arcile.core.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
fun ClipboardToolbar(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onStatusClick: () -> Unit,
    onPaste: (() -> Unit)?,
    onCancel: (() -> Unit)?,
    modifier: Modifier = Modifier,
    progress: Float? = null,
    progressColor: Color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
    iconTint: Color = MaterialTheme.colorScheme.primary
) {
    val actions = buildList {
        if (onPaste != null) add(ToolbarAction(
            Icons.Default.ContentPaste, stringResource(R.string.action_paste_here), onClick = onPaste
        ))
        if (onCancel != null) add(ToolbarAction(
            Icons.Default.Close, stringResource(R.string.action_cancel_transfer),
            tint = Color.White, containerColor = MaterialTheme.colorScheme.error, onClick = onCancel
        ))
    }
    FloatingSelectionToolbar(
        isVisible = true,
        modifier = modifier,
        actions = actions,
        startContent = {
            Surface(
                onClick = onStatusClick,
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                contentColor = MaterialTheme.colorScheme.onSurface,
                tonalElevation = 4.dp,
                shadowElevation = 2.dp,
                modifier = Modifier.height(56.dp).padding(end = 8.dp).width(192.dp).animateContentSize()
            ) {
                Box(
                    contentAlignment = Alignment.CenterStart,
                    modifier = Modifier.fillMaxSize().drawBehind {
                        if (progress != null) {
                            drawRect(progressColor, size = Size(size.width * progress.coerceIn(0f, 1f), size.height))
                        }
                    }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(20.dp))
                        Column(verticalArrangement = Arrangement.Center, modifier = Modifier.weight(1f, fill = false)) {
                            Text(
                                title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold,
                                maxLines = 1, overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                subtitle, style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1, overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    )
}
