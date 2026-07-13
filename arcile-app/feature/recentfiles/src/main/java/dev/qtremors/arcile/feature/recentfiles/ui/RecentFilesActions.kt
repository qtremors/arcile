package dev.qtremors.arcile.feature.recentfiles.ui

import dev.qtremors.arcile.core.storage.domain.FileListingPreferences
import dev.qtremors.arcile.core.storage.domain.SearchFilters
import dev.qtremors.arcile.core.ui.ArcileFeedbackEvent

internal data class RecentNavigationActions(
    val navigateBack: () -> Unit,
    val openFile: (String) -> Unit,
    val openContainingFolder: (String) -> Unit
)

internal data class RecentSelectionActions(
    val toggle: (String) -> Unit,
    val clear: () -> Unit,
    val share: () -> Unit,
    val selectAll: () -> Unit,
    val selectMultiple: (List<String>) -> Unit,
    val openProperties: () -> Unit,
    val dismissProperties: () -> Unit
)

internal data class RecentDeleteActions(
    val request: () -> Unit,
    val confirm: () -> Unit,
    val togglePermanent: () -> Unit,
    val toggleShred: () -> Unit,
    val dismissConfirmation: () -> Unit
)

internal data class RecentSearchActions(
    val queryChange: (String) -> Unit,
    val clear: () -> Unit,
    val filtersChange: (SearchFilters) -> Unit,
    val presentationChange: (FileListingPreferences) -> Unit,
    val loadMore: () -> Unit
)

internal data class RecentContentActions(
    val refresh: () -> Unit,
    val clearError: () -> Unit,
    val feedback: (ArcileFeedbackEvent) -> Unit
)
