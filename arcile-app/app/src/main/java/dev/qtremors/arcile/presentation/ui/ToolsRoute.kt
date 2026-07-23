package dev.qtremors.arcile.presentation.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
internal fun ToolsRoute(
    onNavigateBack: () -> Unit,
    onNavigateToCleaner: () -> Unit,
    onNavigateToTrash: () -> Unit,
    onNavigateToActivity: () -> Unit,
    onNavigateToOnlyFiles: () -> Unit
) {
    val viewModel = hiltViewModel<UtilityPreferencesViewModel>()
    val homeUtilityIds by viewModel.homeUtilityIds.collectAsStateWithLifecycle()

    ToolsScreen(
        onNavigateBack = onNavigateBack,
        onNavigateToCleaner = onNavigateToCleaner,
        onNavigateToTrash = onNavigateToTrash,
        onNavigateToActivity = onNavigateToActivity,
        onNavigateToOnlyFiles = onNavigateToOnlyFiles,
        homeUtilityIds = homeUtilityIds,
        onUtilityHomeVisibilityChange = viewModel::setUtilityShownOnHome,
        onMoveUtility = viewModel::moveUtility
    )
}
