package dev.qtremors.arcile.core.privilege.android.connection

import android.os.ParcelFileDescriptor
import android.os.RemoteException
import dev.qtremors.arcile.core.privilege.PrivilegeCapability
import dev.qtremors.arcile.core.privilege.PrivilegeServiceIdentity
import dev.qtremors.arcile.core.privilege.PrivilegeSession
import dev.qtremors.arcile.core.privilege.PrivilegeTransport
import dev.qtremors.arcile.core.privilege.PrivilegedDirectoryPage
import dev.qtremors.arcile.core.privilege.PrivilegedFileClient
import dev.qtremors.arcile.core.privilege.PrivilegedFileEntry
import dev.qtremors.arcile.core.privilege.PrivilegedFileFailure
import dev.qtremors.arcile.core.privilege.PrivilegedFileHandle
import dev.qtremors.arcile.core.privilege.PrivilegedFileType
import dev.qtremors.arcile.core.privilege.PrivilegedFilesystemStats
import dev.qtremors.arcile.core.privilege.PrivilegedHandshake
import dev.qtremors.arcile.core.privilege.PrivilegedOpenMode
import dev.qtremors.arcile.core.privilege.PrivilegedOperationId
import dev.qtremors.arcile.core.privilege.PrivilegedOperationProgress
import dev.qtremors.arcile.core.privilege.android.remote.IPrivilegedFileService
import dev.qtremors.arcile.core.privilege.android.remote.IRemoteOperationCallback
import dev.qtremors.arcile.core.privilege.android.remote.RemoteDirectoryPage
import dev.qtremors.arcile.core.privilege.android.remote.RemoteFailureCode
import dev.qtremors.arcile.core.privilege.android.remote.RemoteFileEntry
import dev.qtremors.arcile.core.privilege.android.remote.RemoteHandshake
import dev.qtremors.arcile.core.privilege.android.remote.RemoteOperationProgress
import dev.qtremors.arcile.core.privilege.android.remote.REMOTE_FAILURE_PREFIX
import dev.qtremors.arcile.core.runtime.di.ArcileDispatchers
import java.io.InputStream
import java.io.OutputStream
import kotlinx.coroutines.withContext

internal class BinderPrivilegedFileClient(
    override val session: PrivilegeSession,
    private val service: IPrivilegedFileService,
    private val dispatchers: ArcileDispatchers,
    private val closeConnection: () -> Unit
) : PrivilegedFileClient {
    override suspend fun handshake(): PrivilegedHandshake = call { service.handshake().toDomain() }.getOrThrow()

    override suspend fun canonicalizeAndLstat(path: String): Result<PrivilegedFileEntry> =
        call(path) { service.canonicalizeAndLstat(path).toDomain() }

    override suspend fun listDirectory(
        path: String,
        pageToken: String?,
        pageSize: Int
    ): Result<PrivilegedDirectoryPage> = call(path) {
        service.listDirectory(path, pageToken.orEmpty(), pageSize).toDomain()
    }

    override suspend fun filesystemStats(path: String): Result<PrivilegedFilesystemStats> = call(path) {
        service.filesystemStats(path).let {
            PrivilegedFilesystemStats(it.totalBytes, it.availableBytes, it.freeBytes)
        }
    }

    override suspend fun createFile(path: String): Result<PrivilegedFileEntry> =
        call(path) { service.createFile(path).toDomain() }

    override suspend fun createDirectory(path: String): Result<PrivilegedFileEntry> =
        call(path) { service.createDirectory(path).toDomain() }

    override suspend fun open(path: String, mode: PrivilegedOpenMode): Result<PrivilegedFileHandle> = call(path) {
        when (mode) {
            PrivilegedOpenMode.READ -> ReadHandle(service.openForReading(path))
            PrivilegedOpenMode.WRITE_TRUNCATE -> WriteHandle(service.openForWriting(path, false), mode)
            PrivilegedOpenMode.WRITE_APPEND -> WriteHandle(service.openForWriting(path, true), mode)
            PrivilegedOpenMode.READ_WRITE -> throw PrivilegedFileFailure.UnsupportedFileType(path)
        }
    }

    override suspend fun rename(sourcePath: String, destinationPath: String): Result<Unit> =
        call(sourcePath) { service.rename(sourcePath, destinationPath) }

    override suspend fun copy(
        sourcePath: String,
        destinationPath: String,
        operationId: PrivilegedOperationId,
        onProgress: ((PrivilegedOperationProgress) -> Unit)?
    ): Result<Unit> = call(sourcePath) {
        service.copy(sourcePath, destinationPath, operationId.value, progressCallback(onProgress))
    }

    override suspend fun move(
        sourcePath: String,
        destinationPath: String,
        operationId: PrivilegedOperationId,
        onProgress: ((PrivilegedOperationProgress) -> Unit)?
    ): Result<Unit> = call(sourcePath) {
        service.move(sourcePath, destinationPath, operationId.value, progressCallback(onProgress))
    }

    override suspend fun delete(path: String): Result<Unit> = call(path) { service.delete(path) }

    override suspend fun deleteRecursively(
        path: String,
        operationId: PrivilegedOperationId,
        onProgress: ((PrivilegedOperationProgress) -> Unit)?
    ): Result<Unit> = call(path) {
        service.deleteRecursively(path, operationId.value, progressCallback(onProgress))
    }

    override suspend fun secureOverwrite(
        path: String,
        operationId: PrivilegedOperationId,
        onProgress: ((PrivilegedOperationProgress) -> Unit)?
    ): Result<Unit> = call(path) {
        service.secureOverwrite(path, operationId.value, progressCallback(onProgress))
    }

    override suspend fun updateTimestamps(
        path: String,
        accessedAtMillis: Long,
        modifiedAtMillis: Long
    ): Result<Unit> = call(path) { service.updateTimestamps(path, accessedAtMillis, modifiedAtMillis) }

    override suspend fun cancel(operationId: PrivilegedOperationId): Result<Unit> =
        call { service.cancel(operationId.value) }

    override fun close() = closeConnection()

    private suspend fun <T> call(path: String? = null, block: () -> T): Result<T> = withContext(dispatchers.io) {
        try {
            Result.success(block())
        } catch (error: RemoteException) {
            Result.failure(error.toFileFailure(path))
        } catch (error: PrivilegedFileFailure) {
            Result.failure(error)
        } catch (error: Exception) {
            Result.failure(PrivilegedFileFailure.IoFailure("Privileged filesystem operation failed", error))
        }
    }

    private fun progressCallback(
        listener: ((PrivilegedOperationProgress) -> Unit)?
    ): IRemoteOperationCallback? = listener?.let {
        object : IRemoteOperationCallback.Stub() {
            override fun onProgress(progress: RemoteOperationProgress) {
                it(progress.toDomain())
            }
        }
    }
}

private class ReadHandle(descriptor: ParcelFileDescriptor) : PrivilegedFileHandle {
    override val mode = PrivilegedOpenMode.READ
    override val input: InputStream = ParcelFileDescriptor.AutoCloseInputStream(descriptor)
    override val output: OutputStream? = null
    override fun close() = input.close()
}

private class WriteHandle(
    descriptor: ParcelFileDescriptor,
    override val mode: PrivilegedOpenMode
) : PrivilegedFileHandle {
    override val input: InputStream? = null
    override val output: OutputStream = ParcelFileDescriptor.AutoCloseOutputStream(descriptor)
    override fun close() = output.close()
}

internal fun RemoteHandshake.toDomain(): PrivilegedHandshake {
    val domainTransport = PrivilegeTransport.valueOf(transport)
    return PrivilegedHandshake(
        protocolVersion = protocolVersion,
        identity = PrivilegeServiceIdentity(effectiveUid, pid, domainTransport, selinuxContext),
        capabilities = capabilities.mapNotNullTo(mutableSetOf()) {
            runCatching { PrivilegeCapability.valueOf(it) }.getOrNull()
        },
        maximumDirectoryPageSize = maximumDirectoryPageSize
    )
}

private fun RemoteDirectoryPage.toDomain() = PrivilegedDirectoryPage(
    entries = entries.map(RemoteFileEntry::toDomain),
    nextPageToken = nextPageToken
)

private fun RemoteFileEntry.toDomain() = PrivilegedFileEntry(
    path = path,
    canonicalIdentity = canonicalIdentity,
    displayName = displayName,
    type = runCatching { PrivilegedFileType.valueOf(type) }.getOrDefault(PrivilegedFileType.UNKNOWN),
    size = size,
    modifiedAtMillis = modifiedAtMillis,
    mode = mode,
    readable = readable,
    writable = writable
)

private fun RemoteOperationProgress.toDomain() = PrivilegedOperationProgress(
    operationId = PrivilegedOperationId.of(operationId),
    completedItems = completedItems,
    totalItems = totalItems.takeIf { hasTotalItems },
    completedBytes = completedBytes,
    totalBytes = totalBytes.takeIf { hasTotalBytes },
    currentPath = currentPath
)

private fun RemoteException.toFileFailure(path: String?): PrivilegedFileFailure {
    val encoded = message.orEmpty()
    if (!encoded.startsWith(REMOTE_FAILURE_PREFIX)) {
        return PrivilegedFileFailure.BackendDisconnected(this)
    }
    val code = encoded.substringAfter(REMOTE_FAILURE_PREFIX).substringBefore(':').toIntOrNull()
        ?: return PrivilegedFileFailure.IoFailure("Privileged filesystem operation failed", this)
    return when (code) {
    RemoteFailureCode.ACCESS_DENIED -> PrivilegedFileFailure.AccessDenied(path.orEmpty(), this)
    RemoteFailureCode.PATH_MISSING -> PrivilegedFileFailure.PathMissing(path.orEmpty(), this)
    RemoteFailureCode.PATH_ALREADY_EXISTS -> PrivilegedFileFailure.PathAlreadyExists(path.orEmpty(), this)
    RemoteFailureCode.READ_ONLY_FILESYSTEM -> PrivilegedFileFailure.ReadOnlyFilesystem(path.orEmpty(), this)
    RemoteFailureCode.UNSUPPORTED_FILE_TYPE -> PrivilegedFileFailure.UnsupportedFileType(path.orEmpty(), this)
    RemoteFailureCode.BACKEND_DISCONNECTED -> PrivilegedFileFailure.BackendDisconnected(this)
    RemoteFailureCode.OPERATION_INTERRUPTED -> PrivilegedFileFailure.OperationInterrupted(this)
    RemoteFailureCode.INVALID_PATH -> PrivilegedFileFailure.InvalidPath(path.orEmpty(), this)
    RemoteFailureCode.INSUFFICIENT_STORAGE -> PrivilegedFileFailure.InsufficientStorage(path.orEmpty(), this)
        else -> PrivilegedFileFailure.IoFailure(message ?: "Privileged filesystem operation failed", this)
    }
}
