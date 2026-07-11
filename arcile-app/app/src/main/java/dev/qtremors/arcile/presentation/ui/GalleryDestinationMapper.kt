package dev.qtremors.arcile.presentation.ui

import dev.qtremors.arcile.feature.imagegallery.GalleryDestination

internal class GalleryDestinationMapper(
    private val openPath: (String) -> Unit
) {
    fun map(destination: GalleryDestination) {
        when (destination) {
            is GalleryDestination.ViewImage -> openPath(destination.path)
        }
    }
}
