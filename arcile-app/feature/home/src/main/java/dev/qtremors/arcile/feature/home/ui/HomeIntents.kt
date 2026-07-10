package dev.qtremors.arcile.feature.home.ui

import dev.qtremors.arcile.core.storage.domain.FileModel
import dev.qtremors.arcile.core.storage.domain.StorageKind

internal data class HomeNavigationIntents(
    val openFileBrowser: () -> Unit,
    val navigateToPath: (String) -> Unit,
    val openFileWithContext: (String, List<FileModel>) -> Unit,
    val categoryClick: (String) -> Unit,
    val settingsClick: () -> Unit,
    val navigateToTools: () -> Unit,
    val navigateToAbout: () -> Unit,
    val navigateToTrash: () -> Unit,
    val navigateToRecentFiles: () -> Unit,
    val navigateToQuickAccess: () -> Unit,
    val navigateToExternalFolder: (String) -> Unit,
    val openStorageDashboard: (String?) -> Unit,
    val navigateToCleaner: () -> Unit,
    val navigateToActivity: () -> Unit
)

internal data class HomeContentIntents(
    val refresh: () -> Unit,
    val resumeRefresh: () -> Unit,
    val shareRecentFile: (String) -> Unit,
    val setVolumeClassification: (String, StorageKind) -> Unit,
    val hideClassificationPrompt: (String) -> Unit
)
