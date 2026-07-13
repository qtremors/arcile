package dev.qtremors.arcile.core.storage.data

import dev.qtremors.arcile.core.storage.data.provider.VolumeProvider
import dev.qtremors.arcile.core.storage.data.source.FileSystemDataSource
import dev.qtremors.arcile.core.storage.data.util.resolveVolumeForPath
import dev.qtremors.arcile.core.storage.domain.StorageVolume
import dev.qtremors.arcile.core.storage.domain.VolumeRepository
import dev.qtremors.arcile.core.runtime.di.ArcileDispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class DefaultVolumeRepository(
    private val volumeProvider: VolumeProvider,
    private val fileSystemDataSource: FileSystemDataSource,
    private val dispatchers: ArcileDispatchers
) : VolumeRepository {
    override fun observeStorageVolumes(): Flow<List<StorageVolume>> =
        volumeProvider.observeStorageVolumes()

    override suspend fun getStorageVolumes(): Result<List<StorageVolume>> =
        volumeProvider.getStorageVolumes()

    override suspend fun getVolumeForPath(path: String): Result<StorageVolume> =
        withContext(dispatchers.io) {
            try {
                val volumes = volumeProvider.currentVolumes()
                val volume = resolveVolumeForPath(path, volumes)
                when {
                    volumes.isEmpty() -> Result.failure(Exception("Could not fetch volumes"))
                    volume == null -> Result.failure(Exception("No volume found for path"))
                    else -> Result.success(volume)
                }
            } catch (error: Exception) {
                error.rethrowIfCancellation()
                Result.failure(error)
            }
        }

    override fun getStandardFolders(): Map<String, String?> =
        fileSystemDataSource.getStandardFolders()
}
