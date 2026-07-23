package dev.qtremors.arcile.core.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ArcileStateView(
    modifier: Modifier = Modifier,
    isLoading: Boolean = false,
    isEmpty: Boolean = false,
    emptyVariant: EmptyStateVariant = EmptyStateVariant.Generic,
    emptyIcon: ImageVector? = null,
    emptyTitle: String? = null,
    emptyDescription: String? = null,
    emptyAction: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit
) {
    Box(modifier = modifier.fillMaxSize()) {
        if (isLoading) {
            LoadingIndicator(modifier = Modifier.align(Alignment.Center))
        } else if (isEmpty) {
            EmptyState(
                modifier = Modifier.align(Alignment.Center),
                variant = emptyVariant,
                icon = emptyIcon,
                title = emptyTitle,
                description = emptyDescription,
                action = emptyAction
            )
        } else {
            content()
        }
    }
}
