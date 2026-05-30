package dev.qtremors.arcile.core.storage.data.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.qtremors.arcile.core.storage.data.BrowserPreferencesRepository
import dev.qtremors.arcile.core.storage.data.OnboardingPreferencesRepository
import dev.qtremors.arcile.core.storage.data.QuickAccessPreferencesRepository
import dev.qtremors.arcile.core.storage.domain.BrowserPreferencesStore
import dev.qtremors.arcile.core.storage.domain.OnboardingPreferencesStore
import dev.qtremors.arcile.core.storage.domain.QuickAccessPreferencesStore
import dev.qtremors.arcile.di.ArcileDispatchers
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object BrowserPrefsModule {

    @Provides
    @Singleton
    fun provideBrowserPreferencesRepository(
        @ApplicationContext context: Context,
        dispatchers: ArcileDispatchers
    ): BrowserPreferencesRepository {
        return BrowserPreferencesRepository(context, dispatchers = dispatchers)
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
    fun provideOnboardingPreferencesRepository(
        @ApplicationContext context: Context,
        dispatchers: ArcileDispatchers
    ): OnboardingPreferencesRepository {
        return OnboardingPreferencesRepository(context, dispatchers = dispatchers)
    }

    @Provides
    @Singleton
    fun provideOnboardingPreferencesStore(
        repository: OnboardingPreferencesRepository
    ): OnboardingPreferencesStore {
        return repository
    }

    @Provides
    @Singleton
    fun provideQuickAccessPreferencesRepository(
        @ApplicationContext context: Context
    ): QuickAccessPreferencesStore {
        return QuickAccessPreferencesRepository(context)
    }
}
