package dev.qtremors.arcile.domain.usecase

import dev.qtremors.arcile.domain.StorageVolume
import dev.qtremors.arcile.domain.VolumeRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetStorageVolumesUseCase @Inject constructor(
    private val repository: VolumeRepository
) {
    operator fun invoke(): Flow<List<StorageVolume>> = repository.observeStorageVolumes()
}
