package dev.qtremors.arcile.domain.usecase

import dev.qtremors.arcile.domain.ConflictResolution
import dev.qtremors.arcile.domain.FileRepository
import javax.inject.Inject

class PasteFilesUseCase @Inject constructor(
    private val repository: FileRepository
) {
    suspend operator fun invoke(
        sourcePaths: List<String>,
        destinationPath: String,
        isMove: Boolean,
        resolutions: Map<String, ConflictResolution> = emptyMap()
    ): Result<Unit> {
        return if (isMove) {
            repository.moveFiles(sourcePaths, destinationPath, resolutions)
        } else {
            repository.copyFiles(sourcePaths, destinationPath, resolutions)
        }
    }
}