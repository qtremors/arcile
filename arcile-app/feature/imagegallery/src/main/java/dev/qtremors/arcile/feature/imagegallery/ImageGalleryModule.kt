package dev.qtremors.arcile.feature.imagegallery

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal interface ImageGalleryModule {
    @Binds
    @Singleton
    fun bindImageGalleryRepository(repository: DefaultImageGalleryRepository): ImageGalleryRepository

    @Binds
    @Singleton
    fun bindImageMetadataRepository(
        repository: DefaultImageMetadataRepository
    ): ImageMetadataRepository
}
