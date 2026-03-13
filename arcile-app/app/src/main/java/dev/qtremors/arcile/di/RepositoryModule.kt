package dev.qtremors.arcile.di

import android.content.Context
import android.os.Environment
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.qtremors.arcile.data.BrowserPreferencesRepository
import dev.qtremors.arcile.data.LocalFileRepository
import dev.qtremors.arcile.domain.FileRepository
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object RepositoryModule {

    @Provides
    @Singleton
    fun provideFileRepository(
        @ApplicationContext context: Context
    ): FileRepository {
        return LocalFileRepository(context)
    }

    @Provides
    @Singleton
    fun provideBrowserPreferencesRepository(
        @ApplicationContext context: Context
    ): BrowserPreferencesRepository {
        return BrowserPreferencesRepository(context)
    }

    @Provides
    @Named("storageRootPath")
    fun provideStorageRootPath(): String {
        return Environment.getExternalStorageDirectory().absolutePath
    }
}

