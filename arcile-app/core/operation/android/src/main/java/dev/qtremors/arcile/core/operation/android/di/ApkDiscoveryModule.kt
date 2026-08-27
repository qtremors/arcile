package dev.qtremors.arcile.core.operation.android.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.qtremors.arcile.core.operation.android.apk.ApkArchiveMetadataReader
import dev.qtremors.arcile.core.operation.android.apk.DefaultApkArchiveMetadataReader
import dev.qtremors.arcile.core.operation.android.apk.DefaultOnDeviceApkDiscovery
import dev.qtremors.arcile.core.operation.android.apk.OnDeviceApkDiscovery
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class ApkDiscoveryModule {

    @Binds
    @Singleton
    abstract fun bindApkArchiveMetadataReader(
        impl: DefaultApkArchiveMetadataReader
    ): ApkArchiveMetadataReader

    @Binds
    @Singleton
    abstract fun bindOnDeviceApkDiscovery(
        impl: DefaultOnDeviceApkDiscovery
    ): OnDeviceApkDiscovery
}
