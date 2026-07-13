package dev.qtremors.arcile.presentation.ui

import dev.qtremors.arcile.core.storage.domain.FileModel
import dev.qtremors.arcile.navigation.AppRoutes

internal class AppDestinationMappers(
    navigateToBrowser: (AppRoutes.Main) -> Unit,
    openPath: (String) -> Unit,
    openGalleryPath: (String, List<FileModel>, Set<String>) -> Unit,
    openExternalFolder: (String) -> Unit
) {
    val archive = ArchiveDestinationMapper(navigateToBrowser)
    val gallery = GalleryDestinationMapper(openGalleryPath)
    val quickAccess = QuickAccessDestinationMapper(navigateToBrowser, openExternalFolder)
    val recentFiles = RecentFilesDestinationMapper(navigateToBrowser)
    val storageCleaner = StorageCleanerDestinationMapper(navigateToBrowser, openPath)
}
