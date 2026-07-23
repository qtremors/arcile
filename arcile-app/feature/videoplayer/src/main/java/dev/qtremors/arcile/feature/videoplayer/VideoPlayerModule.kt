package dev.qtremors.arcile.feature.videoplayer

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal abstract class VideoPlayerModule {

    @Binds
    @Singleton
    abstract fun bindVideoMetadataRepository(
        repository: DefaultVideoMetadataRepository
    ): VideoMetadataRepository
}
