package dev.qtremors.arcile.core.vault.data.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.qtremors.arcile.core.vault.data.DefaultVaultImportCoordinator
import dev.qtremors.arcile.core.vault.data.DefaultVaultRepository
import dev.qtremors.arcile.core.vault.domain.VaultImportCoordinator
import dev.qtremors.arcile.core.vault.domain.VaultRepository
import dev.qtremors.arcile.core.vault.domain.VaultHealthService
import dev.qtremors.arcile.core.vault.domain.VaultFileSystem
import dev.qtremors.arcile.core.vault.domain.VaultCatalog
import dev.qtremors.arcile.core.vault.domain.VaultSessionManager
import dev.qtremors.arcile.core.vault.domain.VaultTransferCoordinator
import dev.qtremors.arcile.core.vault.domain.VaultExternalAccessManager
import dev.qtremors.arcile.core.vault.data.DefaultVaultExternalAccessManager
import dev.qtremors.arcile.core.vault.data.DefaultVaultSecurityPreferences
import dev.qtremors.arcile.core.vault.domain.VaultSecurityPreferences
import dev.qtremors.arcile.core.vault.data.DefaultVaultThumbnailCache
import dev.qtremors.arcile.core.vault.domain.VaultThumbnailCache
import dev.qtremors.arcile.core.vault.data.DefaultVaultBoundaryTransferCoordinator
import dev.qtremors.arcile.core.vault.domain.VaultBoundaryTransferCoordinator
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class VaultDataModule {
    @Binds
    @Singleton
    abstract fun bindVaultRepository(implementation: DefaultVaultRepository): VaultRepository

    @Binds
    @Singleton
    abstract fun bindVaultImportCoordinator(implementation: DefaultVaultImportCoordinator): VaultImportCoordinator

    @Binds
    @Singleton
    abstract fun bindVaultHealthService(implementation: DefaultVaultRepository): VaultHealthService

    @Binds
    @Singleton
    abstract fun bindVaultFileSystem(implementation: DefaultVaultRepository): VaultFileSystem

    @Binds
    @Singleton
    abstract fun bindVaultCatalog(implementation: DefaultVaultRepository): VaultCatalog

    @Binds
    @Singleton
    abstract fun bindVaultSessionManager(implementation: DefaultVaultRepository): VaultSessionManager

    @Binds
    @Singleton
    abstract fun bindVaultTransferCoordinator(implementation: DefaultVaultRepository): VaultTransferCoordinator

    @Binds
    @Singleton
    abstract fun bindVaultExternalAccessManager(
        implementation: DefaultVaultExternalAccessManager
    ): VaultExternalAccessManager

    @Binds
    @Singleton
    abstract fun bindVaultSecurityPreferences(
        implementation: DefaultVaultSecurityPreferences
    ): VaultSecurityPreferences

    @Binds
    @Singleton
    abstract fun bindVaultThumbnailCache(implementation: DefaultVaultThumbnailCache): VaultThumbnailCache

    @Binds
    @Singleton
    abstract fun bindVaultBoundaryTransferCoordinator(
        implementation: DefaultVaultBoundaryTransferCoordinator
    ): VaultBoundaryTransferCoordinator
}
