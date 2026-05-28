package dev.qtremors.arcile.core.storage.domain.usecase

import dev.qtremors.arcile.core.storage.domain.StorageVolume
import dev.qtremors.arcile.core.storage.domain.VolumeRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetStorageVolumesUseCase @Inject constructor(
    private val repository: VolumeRepository
) {
    operator fun invoke(): Flow<List<StorageVolume>> = repository.observeStorageVolumes()
}
