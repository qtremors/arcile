package dev.qtremors.arcile.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.qtremors.arcile.data.BrowserPreferencesRepository
import dev.qtremors.arcile.data.BrowserPreferencesStore
import dev.qtremors.arcile.data.LocalFileRepository
import dev.qtremors.arcile.data.StorageClassificationRepository
import dev.qtremors.arcile.data.StorageClassificationStore
import dev.qtremors.arcile.data.manager.DefaultTrashManager
import dev.qtremors.arcile.data.manager.TrashManager
import dev.qtremors.arcile.data.provider.DefaultVolumeProvider
import dev.qtremors.arcile.data.provider.VolumeProvider
import dev.qtremors.arcile.data.source.DefaultFileSystemDataSource
import dev.qtremors.arcile.data.source.DefaultMediaStoreClient
import dev.qtremors.arcile.data.source.FileSystemDataSource
import dev.qtremors.arcile.data.source.MediaStoreClient
import dev.qtremors.arcile.domain.FileRepository
import dev.qtremors.arcile.ui.theme.ThemePreferences
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object RepositoryModule {

    @Provides
    @Singleton
    fun provideThemePreferences(
        @ApplicationContext context: Context
    ): ThemePreferences {
        return ThemePreferences(context)
    }

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
    fun provideVolumeProvider(
        @ApplicationContext context: Context,
        classificationRepository: StorageClassificationRepository
    ): VolumeProvider {
        return DefaultVolumeProvider(context, classificationRepository)
    }

    @Provides
    @Singleton
    fun provideMediaStoreClient(
        @ApplicationContext context: Context,
        volumeProvider: VolumeProvider
    ): MediaStoreClient {
        return DefaultMediaStoreClient(context, volumeProvider)
    }

    @Provides
    @Singleton
    fun provideTrashManager(
        @ApplicationContext context: Context,
        volumeProvider: VolumeProvider,
        mediaStoreClient: MediaStoreClient
    ): TrashManager {
        return DefaultTrashManager(context, volumeProvider, mediaStoreClient)
    }

    @Provides
    @Singleton
    fun provideFileSystemDataSource(
        @ApplicationContext context: Context,
        volumeProvider: VolumeProvider,
        mediaStoreClient: MediaStoreClient
    ): FileSystemDataSource {
        return DefaultFileSystemDataSource(context, volumeProvider, mediaStoreClient)
    }

    @Provides
    @Singleton
    fun provideFileRepository(
        volumeProvider: VolumeProvider,
        mediaStoreClient: MediaStoreClient,
        trashManager: TrashManager,
        fileSystemDataSource: FileSystemDataSource
    ): FileRepository {
        return LocalFileRepository(
            volumeProvider,
            mediaStoreClient,
            trashManager,
            fileSystemDataSource
        )
    }

    @Provides
    @Singleton
    fun provideBrowserPreferencesRepository(
        @ApplicationContext context: Context
    ): BrowserPreferencesRepository {
        return BrowserPreferencesRepository(context)
    }

    @Provides
    @Singleton
    fun provideBrowserPreferencesStore(
        repository: BrowserPreferencesRepository
    ): BrowserPreferencesStore {
        return repository
    }

    @Provides
    @Singleton
    fun provideQuickAccessPreferencesRepository(
        @ApplicationContext context: Context
    ): dev.qtremors.arcile.data.QuickAccessPreferencesRepository {
        return dev.qtremors.arcile.data.QuickAccessPreferencesRepository(context)
    }
}