package dev.qtremors.arcile.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.qtremors.arcile.core.ui.theme.UiPreferencesStore
import dev.qtremors.arcile.backup.PreferencesBackupManager
import dev.qtremors.arcile.core.ui.backup.PreferencesBackupGateway
import dev.qtremors.arcile.core.storage.domain.AppVersionCodeProvider
import dev.qtremors.arcile.BuildConfig
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideUiPreferencesStore(
        @ApplicationContext context: Context
    ): UiPreferencesStore {
        return UiPreferencesStore(context)
    }

    @Provides
    @Singleton
    fun providePreferencesBackupGateway(
        manager: PreferencesBackupManager
    ): PreferencesBackupGateway = manager

    @Provides
    fun provideAppVersionCode(): AppVersionCodeProvider =
        AppVersionCodeProvider { BuildConfig.VERSION_CODE }
}
