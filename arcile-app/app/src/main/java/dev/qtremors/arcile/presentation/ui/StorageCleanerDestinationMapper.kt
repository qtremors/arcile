package dev.qtremors.arcile.presentation.ui

import dev.qtremors.arcile.feature.storagecleaner.StorageCleanerDestination
import dev.qtremors.arcile.navigation.AppRoutes

internal class StorageCleanerDestinationMapper(
    private val navigateToBrowser: (AppRoutes.Main) -> Unit,
    private val openPath: (String) -> Unit
) {
    fun map(destination: StorageCleanerDestination) {
        when (destination) {
            is StorageCleanerDestination.OpenFile -> openPath(destination.path)
            is StorageCleanerDestination.ContainingFolder -> navigateToBrowser(
                AppRoutes.Main(
                    initialPage = BROWSER_PAGE,
                    path = destination.path,
                    focusPath = destination.focusPath,
                    seedInitialPathHistory = false
                )
            )
        }
    }
}
