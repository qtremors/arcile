package dev.qtremors.arcile.feature.gallery

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal interface MediaGalleryModule {
    @Binds
    @Singleton
    fun bindMediaGalleryRepository(repository: DefaultMediaGalleryRepository): MediaGalleryRepository

    @Binds
    @Singleton
    fun bindImageMetadataRepository(
        repository: DefaultImageMetadataRepository
    ): ImageMetadataRepository
}
