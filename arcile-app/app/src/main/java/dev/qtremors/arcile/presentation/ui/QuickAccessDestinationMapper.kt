package dev.qtremors.arcile.presentation.ui

import dev.qtremors.arcile.feature.quickaccess.QuickAccessDestination
import dev.qtremors.arcile.navigation.AppRoutes

internal class QuickAccessDestinationMapper(
    private val navigateToBrowser: (AppRoutes.Main) -> Unit,
    private val openExternalFolder: (String) -> Unit
) {
    fun map(destination: QuickAccessDestination) {
        when (destination) {
            is QuickAccessDestination.LocalPath -> navigateToBrowser(
                AppRoutes.Main(
                    initialPage = BROWSER_PAGE,
                    path = destination.path,
                    seedInitialPathHistory = false
                )
            )
            is QuickAccessDestination.ExternalFolder -> openExternalFolder(destination.uri)
        }
    }
}
