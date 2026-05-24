package dev.qtremors.arcile.presentation.ui.components

import android.provider.Settings
import dev.qtremors.arcile.ui.theme.LocalReducedMotionEnabled
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
import dev.qtremors.arcile.R

enum class EmptyStateVariant {
    Generic,
    Folder,
    Search,
    Trash,
    StorageAccess,
    Archive,
    Recent
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

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 28.dp),
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

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = resolvedTitle,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )

        if (resolvedDescription != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = resolvedDescription,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
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

@Composable
private fun EmptyStateIcon(
    icon: ImageVector,
    containerColor: Color,
    iconTint: Color
) {
    Surface(
        shape = CircleShape,
        color = containerColor,
        modifier = Modifier.size(80.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(36.dp),
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
