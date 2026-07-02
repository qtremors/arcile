package dev.qtremors.arcile.core.storage.data

import dev.qtremors.arcile.core.storage.data.source.FileSystemDataSource
import dev.qtremors.arcile.core.storage.domain.ClipboardRepository
import dev.qtremors.arcile.core.storage.domain.ClipboardState
import dev.qtremors.arcile.core.storage.domain.ConflictResolution
import dev.qtremors.arcile.core.storage.domain.FileConflict
import dev.qtremors.arcile.core.storage.domain.FileOperationProgress
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class DefaultClipboardRepository(
    private val fileSystemDataSource: FileSystemDataSource
) : ClipboardRepository {
    private val mutableClipboardState = MutableStateFlow<ClipboardState?>(null)
    override val clipboardState: StateFlow<ClipboardState?> =
        mutableClipboardState.asStateFlow()

    override fun setClipboardState(state: ClipboardState?) {
        mutableClipboardState.value = state
    }

    override suspend fun detectCopyConflicts(
        sourcePaths: List<String>,
        destinationPath: String
    ): Result<List<FileConflict>> =
        fileSystemDataSource.detectCopyConflicts(sourcePaths, destinationPath)

    override suspend fun copyFiles(
        sourcePaths: List<String>,
        destinationPath: String,
        resolutions: Map<String, ConflictResolution>,
        onProgress: ((FileOperationProgress) -> Unit)?
    ): Result<Unit> =
        fileSystemDataSource.copyFiles(sourcePaths, destinationPath, resolutions, onProgress)

    override suspend fun moveFiles(
        sourcePaths: List<String>,
        destinationPath: String,
        resolutions: Map<String, ConflictResolution>,
        onProgress: ((FileOperationProgress) -> Unit)?
    ): Result<Unit> =
        fileSystemDataSource.moveFiles(sourcePaths, destinationPath, resolutions, onProgress)
}
