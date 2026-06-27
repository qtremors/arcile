package dev.qtremors.arcile.presentation.ui

import dev.qtremors.arcile.feature.archive.ArchiveDestination
import dev.qtremors.arcile.feature.imagegallery.GalleryDestination
import dev.qtremors.arcile.feature.quickaccess.QuickAccessDestination
import dev.qtremors.arcile.feature.recentfiles.RecentFilesDestination
import dev.qtremors.arcile.feature.storagecleaner.StorageCleanerDestination
import dev.qtremors.arcile.navigation.AppRoutes

internal class FeatureDestinationMapper(
    private val navigateToBrowser: (AppRoutes.Main) -> Unit,
    private val openPath: (String) -> Unit,
    private val openExternalFolder: (String) -> Unit
) {
    fun fromRecentFiles(destination: RecentFilesDestination) {
        when (destination) {
            is RecentFilesDestination.ContainingFolder -> navigateToBrowser(
                AppRoutes.Main(
                    initialPage = 1,
                    path = destination.path,
                    seedInitialPathHistory = false
                )
            )
        }
    }

    fun fromGallery(destination: GalleryDestination) {
        when (destination) {
            is GalleryDestination.ViewImage -> openPath(destination.path)
        }
    }

    fun fromStorageCleaner(destination: StorageCleanerDestination) {
        when (destination) {
            is StorageCleanerDestination.OpenFile -> openPath(destination.path)
            is StorageCleanerDestination.ContainingFolder -> navigateToBrowser(
                AppRoutes.Main(
                    initialPage = 1,
                    path = destination.path,
                    focusPath = destination.focusPath,
                    seedInitialPathHistory = false
                )
            )
        }
    }

    fun fromQuickAccess(destination: QuickAccessDestination) {
        when (destination) {
            is QuickAccessDestination.LocalPath -> navigateToBrowser(
                AppRoutes.Main(
                    initialPage = 1,
                    path = destination.path,
                    seedInitialPathHistory = false
                )
            )
            is QuickAccessDestination.ExternalFolder -> openExternalFolder(destination.uri)
        }
    }

    fun fromArchive(destination: ArchiveDestination) {
        when (destination) {
            is ArchiveDestination.OpenInBrowser -> navigateToBrowser(
                AppRoutes.Main(
                    initialPage = 1,
                    archivePath = destination.archivePath,
                    seedInitialPathHistory = false
                )
            )
        }
    }
}
