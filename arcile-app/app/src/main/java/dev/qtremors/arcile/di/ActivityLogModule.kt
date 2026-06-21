package dev.qtremors.arcile.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.qtremors.arcile.core.storage.data.ActivityLogRepository
import dev.qtremors.arcile.core.storage.domain.ActivityLogStore

@Module
@InstallIn(SingletonComponent::class)
object ActivityLogModule {
    @Provides
    fun provideActivityLogStore(repository: ActivityLogRepository): ActivityLogStore = repository
}
