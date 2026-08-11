package dev.qtremors.arcile.core.storage.domain

import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

interface ClipboardRepository {
    val clipboardState: StateFlow<ClipboardState?>
    fun setClipboardState(state: ClipboardState?)
    fun clearClipboardState() = setClipboardState(null)
    fun clearClipboardState(sessionId: String): Boolean {
        if (clipboardState.value?.sessionId != sessionId) return false
        setClipboardState(null)
        return true
    }
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

    suspend fun detectNodeCopyConflicts(
        sources: List<StorageNodeRef>,
        destination: StorageNodeRef
    ): Result<List<FileConflict>> = detectCopyConflicts(
        sources.map { it.displayPath.absolutePath },
        destination.displayPath.absolutePath
    )

    suspend fun copyNodes(
        sources: List<StorageNodeRef>,
        destination: StorageNodeRef,
        resolutions: Map<String, ConflictResolution> = emptyMap(),
        onProgress: ((FileOperationProgress) -> Unit)? = null
    ): Result<Unit> = copyFiles(
        sources.map { it.displayPath.absolutePath },
        destination.displayPath.absolutePath,
        resolutions,
        onProgress
    )

    suspend fun moveNodes(
        sources: List<StorageNodeRef>,
        destination: StorageNodeRef,
        resolutions: Map<String, ConflictResolution> = emptyMap(),
        onProgress: ((FileOperationProgress) -> Unit)? = null
    ): Result<Unit> = moveFiles(
        sources.map { it.displayPath.absolutePath },
        destination.displayPath.absolutePath,
        resolutions,
        onProgress
    )
}

object NoOpClipboardRepository : ClipboardRepository {
    private val state = MutableStateFlow<ClipboardState?>(null)
    override val clipboardState: StateFlow<ClipboardState?> = state.asStateFlow()

    override fun setClipboardState(state: ClipboardState?) = Unit

    override suspend fun detectCopyConflicts(
        sourcePaths: List<String>,
        destinationPath: String
    ): Result<List<FileConflict>> = Result.success(emptyList())

    override suspend fun copyFiles(
        sourcePaths: List<String>,
        destinationPath: String,
        resolutions: Map<String, ConflictResolution>,
        onProgress: ((FileOperationProgress) -> Unit)?
    ): Result<Unit> = Result.failure(UnsupportedOperationException("Clipboard operations are unavailable"))

    override suspend fun moveFiles(
        sourcePaths: List<String>,
        destinationPath: String,
        resolutions: Map<String, ConflictResolution>,
        onProgress: ((FileOperationProgress) -> Unit)?
    ): Result<Unit> = Result.failure(UnsupportedOperationException("Clipboard operations are unavailable"))
}
