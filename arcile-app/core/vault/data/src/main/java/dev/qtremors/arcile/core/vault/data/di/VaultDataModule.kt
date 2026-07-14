package dev.qtremors.arcile.core.vault.data.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.qtremors.arcile.core.vault.data.DefaultVaultImportCoordinator
import dev.qtremors.arcile.core.vault.data.DefaultVaultRepository
import dev.qtremors.arcile.core.vault.domain.VaultImportCoordinator
import dev.qtremors.arcile.core.vault.domain.VaultRepository
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
}
