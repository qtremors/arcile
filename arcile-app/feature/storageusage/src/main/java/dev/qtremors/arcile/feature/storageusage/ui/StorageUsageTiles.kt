package dev.qtremors.arcile.feature.storageusage.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import dev.qtremors.arcile.core.ui.theme.bodyLargeMedium
import dev.qtremors.arcile.core.ui.theme.bodyMediumBold
import dev.qtremors.arcile.core.ui.theme.bounceClickable
import dev.qtremors.arcile.core.presentation.formatFileSize

@Composable
internal fun StorageUsageTile(
    name: String,
    sizeBytes: Long,
    percentage: Int,
    icon: ImageVector,
    color: Color
) {
    StorageUsageTileContent(
        name = name,
        sizeBytes = sizeBytes,
        percentage = percentage,
        icon = icon,
        color = color
    )
}

@Composable
internal fun CategoryListTile(
    name: String,
    sizeBytes: Long,
    percentage: Int,
    icon: ImageVector,
    color: Color,
    onClick: () -> Unit
) {
    StorageUsageTileContent(
        name = name,
        sizeBytes = sizeBytes,
        percentage = percentage,
        icon = icon,
        color = color,
        onClick = onClick
    )
}

@Composable
private fun StorageUsageTileContent(
    name: String,
    sizeBytes: Long,
    percentage: Int,
    icon: ImageVector,
    color: Color,
    onClick: (() -> Unit)? = null
) {
    val tileModifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 16.dp, vertical = 4.dp)
        .clip(MaterialTheme.shapes.extraLarge)
        .let { modifier ->
            onClick?.let { modifier.bounceClickable(onClick = it) } ?: modifier
        }
    Surface(
        modifier = tileModifier,
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.extraLarge
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = name,
                    tint = color,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(modifier = Modifier.size(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.bodyLargeMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "$percentage%",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = formatFileSize(sizeBytes),
                style = MaterialTheme.typography.bodyMediumBold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
