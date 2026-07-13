package dev.qtremors.arcile.presentation.ui

import dev.qtremors.arcile.feature.archive.ArchiveDestination
import dev.qtremors.arcile.navigation.AppRoutes

internal class ArchiveDestinationMapper(
    private val navigateToBrowser: (AppRoutes.Main) -> Unit
) {
    fun map(destination: ArchiveDestination) {
        when (destination) {
            is ArchiveDestination.OpenInBrowser -> navigateToBrowser(
                AppRoutes.Main(
                    initialPage = BROWSER_PAGE,
                    archivePath = destination.archivePath,
                    seedInitialPathHistory = false
                )
            )
        }
    }
}
