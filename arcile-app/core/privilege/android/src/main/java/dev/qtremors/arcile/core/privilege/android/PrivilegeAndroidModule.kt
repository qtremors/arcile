package dev.qtremors.arcile.core.privilege.android

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.qtremors.arcile.core.privilege.PrivilegePreferences
import dev.qtremors.arcile.core.privilege.PrivilegeCoordinator
import dev.qtremors.arcile.core.privilege.PrivilegedFileClientProvider
import dev.qtremors.arcile.core.privilege.android.root.LibsuRootFacade
import dev.qtremors.arcile.core.privilege.android.root.RootBackendConnector
import dev.qtremors.arcile.core.privilege.android.root.RootFacade
import dev.qtremors.arcile.core.privilege.android.shizuku.AndroidShizukuFacade
import dev.qtremors.arcile.core.privilege.android.shizuku.ShizukuBackendConnector
import dev.qtremors.arcile.core.privilege.android.shizuku.ShizukuFacade
import dev.qtremors.arcile.core.privilege.android.connection.BackendConnector
import dev.qtremors.arcile.core.privilege.android.connection.RootBackend
import dev.qtremors.arcile.core.privilege.android.connection.ShizukuBackend
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

    @Provides
    @Singleton
    fun provideRootFacade(facade: LibsuRootFacade): RootFacade = facade

    @Provides
    @Singleton
    fun provideShizukuFacade(facade: AndroidShizukuFacade): ShizukuFacade = facade

    @Provides
    @Singleton
    @RootBackend
    fun provideRootBackendConnector(connector: RootBackendConnector): BackendConnector = connector

    @Provides
    @Singleton
    @ShizukuBackend
    fun provideShizukuBackendConnector(connector: ShizukuBackendConnector): BackendConnector = connector

    @Provides
    @Singleton
    fun provideNormalStorageAccessGateway(
        gateway: AndroidNormalStorageAccessGateway
    ): NormalStorageAccessGateway = gateway

    @Provides
    @Singleton
    fun providePrivilegeCoordinator(
        coordinator: DefaultPrivilegeCoordinator
    ): PrivilegeCoordinator = coordinator

    @Provides
    @Singleton
    fun providePrivilegedFileClientProvider(
        coordinator: DefaultPrivilegeCoordinator
    ): PrivilegedFileClientProvider = coordinator
}
