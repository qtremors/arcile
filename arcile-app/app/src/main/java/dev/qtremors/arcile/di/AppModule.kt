package dev.qtremors.arcile.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.qtremors.arcile.ui.theme.ThemePreferences
import dev.qtremors.arcile.backup.PreferencesBackupManager
import dev.qtremors.arcile.core.ui.backup.PreferencesBackupGateway
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideThemePreferences(
        @ApplicationContext context: Context
    ): ThemePreferences {
        return ThemePreferences(context)
    }

    @Provides
    @Singleton
    fun providePreferencesBackupGateway(
        manager: PreferencesBackupManager
    ): PreferencesBackupGateway = manager
}
