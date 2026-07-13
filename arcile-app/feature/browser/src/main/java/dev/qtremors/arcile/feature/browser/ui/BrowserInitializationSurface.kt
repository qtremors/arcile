package dev.qtremors.arcile.feature.browser.ui

import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import dev.qtremors.arcile.core.ui.ArcileStateView
import dev.qtremors.arcile.core.ui.EmptyStateVariant
import dev.qtremors.arcile.core.ui.R
import dev.qtremors.arcile.core.ui.asString
import dev.qtremors.arcile.feature.browser.BrowserInitializationState

@Composable
internal fun BrowserInitializationSurface(
    state: BrowserInitializationState,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    when (state) {
        BrowserInitializationState.Uninitialized,
        BrowserInitializationState.Restoring -> ArcileStateView(
            modifier = modifier,
            isLoading = true,
            content = {}
        )
        BrowserInitializationState.Ready -> Unit
        is BrowserInitializationState.Failed -> ArcileStateView(
            modifier = modifier,
            isEmpty = true,
            emptyVariant = EmptyStateVariant.StorageAccess,
            emptyTitle = state.error.asString(),
            emptyAction = {
                TextButton(onClick = onRetry) {
                    Text(stringResource(R.string.browser_restore_retry))
                }
            },
            content = {}
        )
    }
}
