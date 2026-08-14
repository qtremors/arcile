package dev.qtremors.arcile.core.privilege

import java.io.Closeable
import java.io.InputStream
import java.io.OutputStream

const val PRIVILEGED_FILESYSTEM_PROTOCOL_VERSION = 1
const val DEFAULT_DIRECTORY_PAGE_SIZE = 200
const val MAX_DIRECTORY_PAGE_SIZE = 250

@JvmInline
value class PrivilegedOperationId private constructor(val value: String) {
    companion object {
        fun of(value: String): PrivilegedOperationId {
            require(value.isNotBlank()) { "Operation id must not be blank" }
            return PrivilegedOperationId(value)
        }
    }
}

enum class PrivilegedFileType {
    REGULAR_FILE,
    DIRECTORY,
    SYMBOLIC_LINK,
    BLOCK_DEVICE,
    CHARACTER_DEVICE,
    FIFO,
    SOCKET,
    UNKNOWN
}

enum class PrivilegedOpenMode {
    READ,
    WRITE_TRUNCATE,
    WRITE_APPEND,
    READ_WRITE
}

data class PrivilegedHandshake(
    val protocolVersion: Int,
    val identity: PrivilegeServiceIdentity,
    val capabilities: Set<PrivilegeCapability>,
    val maximumDirectoryPageSize: Int
)

data class PrivilegedFileEntry(
    val path: String,
    val canonicalIdentity: String,
    val displayName: String,
    val type: PrivilegedFileType,
    val size: Long,
    val modifiedAtMillis: Long,
    val mode: Int,
    val readable: Boolean,
    val writable: Boolean
)

data class PrivilegedDirectoryPage(
    val entries: List<PrivilegedFileEntry>,
    val nextPageToken: String? = null
)

data class PrivilegedFilesystemStats(
    val totalBytes: Long,
    val availableBytes: Long,
    val freeBytes: Long
)

data class PrivilegedOperationProgress(
    val operationId: PrivilegedOperationId,
    val completedItems: Long,
    val totalItems: Long?,
    val completedBytes: Long,
    val totalBytes: Long?,
    val currentPath: String? = null
)

sealed class PrivilegedFileFailure(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class AccessDenied(path: String, cause: Throwable? = null) : PrivilegedFileFailure("Access denied: $path", cause)
    class PathMissing(path: String, cause: Throwable? = null) : PrivilegedFileFailure("Path does not exist: $path", cause)
    class PathAlreadyExists(path: String, cause: Throwable? = null) : PrivilegedFileFailure("Path already exists: $path", cause)
    class ReadOnlyFilesystem(path: String, cause: Throwable? = null) : PrivilegedFileFailure("Filesystem is read-only: $path", cause)
    class UnsupportedFileType(path: String, cause: Throwable? = null) : PrivilegedFileFailure("Unsupported file type: $path", cause)
    class BackendDisconnected(cause: Throwable? = null) : PrivilegedFileFailure("Privileged backend disconnected", cause)
    class OperationInterrupted(cause: Throwable? = null) : PrivilegedFileFailure("Operation was interrupted", cause)
    class InvalidPath(path: String, cause: Throwable? = null) : PrivilegedFileFailure("Invalid path: $path", cause)
    class InsufficientStorage(path: String, cause: Throwable? = null) : PrivilegedFileFailure("Insufficient storage: $path", cause)
    class IoFailure(message: String, cause: Throwable? = null) : PrivilegedFileFailure(message, cause)
}

interface PrivilegedFileHandle : Closeable {
    val mode: PrivilegedOpenMode
    val input: InputStream?
    val output: OutputStream?
}

interface PrivilegedFileClient : Closeable {
    val session: PrivilegeSession

    suspend fun handshake(): PrivilegedHandshake
    suspend fun canonicalizeAndLstat(path: String): Result<PrivilegedFileEntry>
    suspend fun listDirectory(path: String, pageToken: String? = null, pageSize: Int = DEFAULT_DIRECTORY_PAGE_SIZE): Result<PrivilegedDirectoryPage>
    suspend fun filesystemStats(path: String): Result<PrivilegedFilesystemStats>
    suspend fun createFile(path: String): Result<PrivilegedFileEntry>
    suspend fun createDirectory(path: String): Result<PrivilegedFileEntry>
    suspend fun open(path: String, mode: PrivilegedOpenMode): Result<PrivilegedFileHandle>
    suspend fun rename(sourcePath: String, destinationPath: String): Result<Unit>
    suspend fun copy(
        sourcePath: String,
        destinationPath: String,
        operationId: PrivilegedOperationId,
        onProgress: ((PrivilegedOperationProgress) -> Unit)? = null
    ): Result<Unit>
    suspend fun move(
        sourcePath: String,
        destinationPath: String,
        operationId: PrivilegedOperationId,
        onProgress: ((PrivilegedOperationProgress) -> Unit)? = null
    ): Result<Unit>
    suspend fun delete(path: String): Result<Unit>
    suspend fun deleteRecursively(
        path: String,
        operationId: PrivilegedOperationId,
        onProgress: ((PrivilegedOperationProgress) -> Unit)? = null
    ): Result<Unit>
    suspend fun secureOverwrite(
        path: String,
        operationId: PrivilegedOperationId,
        onProgress: ((PrivilegedOperationProgress) -> Unit)? = null
    ): Result<Unit>
    suspend fun updateTimestamps(path: String, accessedAtMillis: Long, modifiedAtMillis: Long): Result<Unit>
    suspend fun cancel(operationId: PrivilegedOperationId): Result<Unit>
}
