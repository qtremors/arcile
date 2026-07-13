package dev.qtremors.arcile.presentation.ui

import dev.qtremors.arcile.feature.recentfiles.RecentFilesDestination
import dev.qtremors.arcile.navigation.AppRoutes

internal class RecentFilesDestinationMapper(
    private val navigateToBrowser: (AppRoutes.Main) -> Unit
) {
    fun map(destination: RecentFilesDestination) {
        when (destination) {
            is RecentFilesDestination.ContainingFolder -> navigateToBrowser(
                AppRoutes.Main(
                    initialPage = BROWSER_PAGE,
                    path = destination.path,
                    seedInitialPathHistory = false
                )
            )
        }
    }
}
