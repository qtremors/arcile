package dev.qtremors.arcile.domain.usecase

import dev.qtremors.arcile.domain.FileRepository
import dev.qtremors.arcile.domain.StorageVolume
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetStorageVolumesUseCase @Inject constructor(
    private val repository: FileRepository
) {
    operator fun invoke(): Flow<List<StorageVolume>> = repository.observeStorageVolumes()
}