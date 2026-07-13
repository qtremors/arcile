package dev.qtremors.arcile.core.storage.domain

import kotlinx.coroutines.flow.StateFlow

interface ClipboardRepository {
    val clipboardState: StateFlow<ClipboardState?>
    fun setClipboardState(state: ClipboardState?)
    fun clearClipboardState() = setClipboardState(null)
    suspend fun detectCopyConflicts(
        sourcePaths: List<String>,
        destinationPath: String
    ): Result<List<FileConflict>>
    suspend fun copyFiles(
        sourcePaths: List<String>,
        destinationPath: String,
        resolutions: Map<String, ConflictResolution> = emptyMap(),
        onProgress: ((FileOperationProgress) -> Unit)? = null
    ): Result<Unit>
    suspend fun moveFiles(
        sourcePaths: List<String>,
        destinationPath: String,
        resolutions: Map<String, ConflictResolution> = emptyMap(),
        onProgress: ((FileOperationProgress) -> Unit)? = null
    ): Result<Unit>
}
