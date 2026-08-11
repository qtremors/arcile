package dev.qtremors.arcile.core.privilege.android

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.qtremors.arcile.core.privilege.PrivilegePreferences
import dev.qtremors.arcile.core.runtime.di.ArcileDispatchers
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object PrivilegeAndroidModule {
    @Provides
    @Singleton
    fun providePrivilegePreferences(
        @ApplicationContext context: Context,
        dispatchers: ArcileDispatchers
    ): PrivilegePreferences = AndroidPrivilegePreferences(context, dispatchers)
}
