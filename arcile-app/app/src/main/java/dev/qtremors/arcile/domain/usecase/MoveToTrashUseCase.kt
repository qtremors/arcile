package dev.qtremors.arcile.domain.usecase

import dev.qtremors.arcile.domain.FileRepository
import javax.inject.Inject

class MoveToTrashUseCase @Inject constructor(
    private val repository: FileRepository
) {
    suspend operator fun invoke(paths: List<String>): Result<Unit> {
        return repository.moveToTrash(paths)
    }
}