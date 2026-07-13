package dev.qtremors.arcile.core.storage.domain

import kotlinx.coroutines.flow.Flow

interface VolumeRepository {
    fun observeStorageVolumes(): Flow<List<StorageVolume>>
    suspend fun getStorageVolumes(): Result<List<StorageVolume>>
    suspend fun getVolumeForPath(path: String): Result<StorageVolume>
    fun getStandardFolders(): Map<String, String?>
}
