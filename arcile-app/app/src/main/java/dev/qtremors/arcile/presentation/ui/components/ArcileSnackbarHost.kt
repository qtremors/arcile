package dev.qtremors.arcile.presentation.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.qtremors.arcile.ui.theme.spacing

/**
 * A customized SnackbarHost that follows the Arcile Material 3 Expressive design language.
 * 
 * Features:
 * - Squircle shape (extraLarge)
 * - Tonal container colors (surfaceContainerHigh)
 * - Standardized spacing and typography
 */
@Composable
fun ArcileSnackbarHost(
    hostState: SnackbarHostState,
    modifier: Modifier = Modifier
) {
    SnackbarHost(
        hostState = hostState,
        modifier = modifier.padding(MaterialTheme.spacing.space12)
    ) { data ->
        Snackbar(
            snackbarData = data,
            shape = MaterialTheme.shapes.extraLarge,
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurface,
            actionColor = MaterialTheme.colorScheme.primary,
            actionContentColor = MaterialTheme.colorScheme.primary,
            dismissActionContentColor = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
