package dev.qtremors.arcile.core.storage.data.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.qtremors.arcile.core.storage.data.BrowserPreferencesRepository
import dev.qtremors.arcile.core.storage.data.ActivityLogRepository
import dev.qtremors.arcile.core.storage.data.OnboardingPreferencesRepository
import dev.qtremors.arcile.core.storage.data.QuickAccessPreferencesRepository
import dev.qtremors.arcile.core.storage.data.StorageCleanerPreferencesRepository
import dev.qtremors.arcile.core.storage.data.UtilityPreferencesRepository
import dev.qtremors.arcile.core.storage.domain.BrowserPreferencesStore
import dev.qtremors.arcile.core.storage.domain.OnboardingPreferencesStore
import dev.qtremors.arcile.core.storage.domain.QuickAccessPreferencesStore
import dev.qtremors.arcile.core.storage.domain.StorageCleanerPreferencesStore
import dev.qtremors.arcile.core.storage.domain.UtilityPreferencesStore
import dev.qtremors.arcile.di.ArcileDispatchers
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object BrowserPrefsModule {

    @Provides
    @Singleton
    fun provideBrowserPreferencesRepository(
        @ApplicationContext context: Context,
        activityLogRepository: ActivityLogRepository,
        dispatchers: ArcileDispatchers
    ): BrowserPreferencesRepository {
        return BrowserPreferencesRepository(
            context = context,
            activityLogRepository = activityLogRepository,
            dispatchers = dispatchers
        )
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
    fun provideActivityLogRepository(
        @ApplicationContext context: Context,
        dispatchers: ArcileDispatchers
    ): ActivityLogRepository {
        return ActivityLogRepository(context, dispatchers = dispatchers)
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

    @Provides
    @Singleton
    fun provideUtilityPreferencesStore(
        @ApplicationContext context: Context,
        dispatchers: ArcileDispatchers
    ): UtilityPreferencesStore {
        return UtilityPreferencesRepository(context, dispatchers = dispatchers)
    }

    @Provides
    @Singleton
    fun provideStorageCleanerPreferencesStore(
        @ApplicationContext context: Context,
        dispatchers: ArcileDispatchers
    ): StorageCleanerPreferencesStore {
        return StorageCleanerPreferencesRepository(context, dispatchers = dispatchers)
    }
}
