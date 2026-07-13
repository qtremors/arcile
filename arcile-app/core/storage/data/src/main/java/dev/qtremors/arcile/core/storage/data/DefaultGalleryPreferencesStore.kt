package dev.qtremors.arcile.core.storage.data

import dev.qtremors.arcile.core.storage.domain.FileListingPreferences
import dev.qtremors.arcile.core.storage.domain.GalleryPreferencesStore
import dev.qtremors.arcile.core.storage.domain.ImageGalleryDefaultTab
import dev.qtremors.arcile.core.storage.domain.ImageGalleryGrouping

class DefaultGalleryPreferencesStore(
    private val dataSource: BrowserPreferencesDataSource
) : GalleryPreferencesStore {
    override val galleryPreferencesFlow = dataSource.galleryPreferencesFlow

    override suspend fun updateImageGalleryPresentation(presentation: FileListingPreferences) =
        dataSource.updateImageGalleryPresentation(presentation)

    override suspend fun updateGalleryScrollbarEnabled(enabled: Boolean) =
        dataSource.updateGalleryScrollbarEnabled(enabled)

    override suspend fun updateImageGalleryShowFileDetails(show: Boolean) =
        dataSource.updateImageGalleryShowFileDetails(show)

    override suspend fun updateImageGalleryAspectRatio(enabled: Boolean) =
        dataSource.updateImageGalleryAspectRatio(enabled)

    override suspend fun updateImageGallerySectioned(enabled: Boolean) =
        dataSource.updateImageGallerySectioned(enabled)

    override suspend fun updateImageGalleryGrouping(grouping: ImageGalleryGrouping) =
        dataSource.updateImageGalleryGrouping(grouping)

    override suspend fun updateImageGalleryDefaultTab(tab: ImageGalleryDefaultTab) =
        dataSource.updateImageGalleryDefaultTab(tab)

    override suspend fun updateAlbumPresentation(presentation: FileListingPreferences) =
        dataSource.updateAlbumPresentation(presentation)

    override suspend fun updateAlbumAspectRatio(enabled: Boolean) =
        dataSource.updateAlbumAspectRatio(enabled)

    override suspend fun updateFavorite(path: String, isFavorite: Boolean) =
        dataSource.updateFavorite(path, isFavorite)

    override suspend fun updatePinnedAlbum(albumPath: String, isPinned: Boolean) =
        dataSource.updatePinnedAlbum(albumPath, isPinned)

    override suspend fun updateAlbumCover(albumPath: String, coverPath: String) =
        dataSource.updateAlbumCover(albumPath, coverPath)
}
