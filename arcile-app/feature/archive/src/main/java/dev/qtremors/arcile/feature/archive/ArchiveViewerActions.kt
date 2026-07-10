package dev.qtremors.arcile.feature.archive

import dev.qtremors.arcile.core.storage.domain.ArchiveNameEncoding
import dev.qtremors.arcile.core.storage.domain.ConflictResolution

internal data class ArchiveNavigationActions(
    val navigateBack: () -> Unit,
    val navigateUpInArchive: () -> Boolean,
    val openFolder: (String) -> Unit,
    val searchQueryChange: (String) -> Unit
)

internal data class ArchiveExtractionActions(
    val extractAll: (String?) -> Unit,
    val extractCurrentFolder: (String?) -> Unit,
    val submitPassword: (String) -> Unit,
    val selectNameEncoding: (ArchiveNameEncoding) -> Unit,
    val cancelExtraction: () -> Unit,
    val clearError: () -> Unit,
    val clearOperationStatusMessage: () -> Unit,
    val clearActiveOperation: () -> Unit
)

internal data class ArchiveConflictActions(
    val setResolution: (String, ConflictResolution) -> Unit,
    val applyResolutionToAll: (ConflictResolution) -> Unit,
    val confirmResolutions: () -> Unit,
    val dismissConflicts: () -> Unit
)

internal data class ArchiveSelectionActions(
    val toggleItem: (String) -> Unit,
    val clear: () -> Unit,
    val extractSelected: (String?) -> Unit,
    val selectAll: () -> Unit
)
