package dev.qtremors.arcile.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.qtremors.arcile.data.BrowserPreferencesRepository
import dev.qtremors.arcile.data.LocalFileRepository
import dev.qtremors.arcile.data.StorageClassificationRepository
import dev.qtremors.arcile.data.StorageClassificationStore
import dev.qtremors.arcile.domain.FileRepository
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object RepositoryModule {

    @Provides
    @Singleton
    fun provideStorageClassificationRepository(
        @ApplicationContext context: Context
    ): StorageClassificationRepository {
        return StorageClassificationRepository(context)
    }

    @Provides
    @Singleton
    fun provideStorageClassificationStore(
        repository: StorageClassificationRepository
    ): StorageClassificationStore {
        return repository
    }

    @Provides
    @Singleton
    fun provideFileRepository(
        @ApplicationContext context: Context,
        classificationRepository: StorageClassificationRepository
    ): FileRepository {
        return LocalFileRepository(context, classificationRepository)
    }

    @Provides
    @Singleton
    fun provideBrowserPreferencesRepository(
        @ApplicationContext context: Context
    ): BrowserPreferencesRepository {
        return BrowserPreferencesRepository(context)
    }
}

