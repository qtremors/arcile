package dev.qtremors.arcile.core.privilege.android.remote

import android.os.Process
import android.os.RemoteException
import dev.qtremors.arcile.core.privilege.MAX_DIRECTORY_PAGE_SIZE
import dev.qtremors.arcile.core.privilege.PRIVILEGED_FILESYSTEM_PROTOCOL_VERSION
import dev.qtremors.arcile.core.privilege.PrivilegeCapability
import dev.qtremors.arcile.core.privilege.PrivilegeTransport
import java.io.File

open class PrivilegedFileServiceBinder(
    private val transport: PrivilegeTransport,
    private val onDestroyService: () -> Unit = {}
) : IPrivilegedFileService.Stub() {
    private val engine = PrivilegedFileEngine()

    override fun destroy() {
        onDestroyService()
    }

    override fun handshake(): RemoteHandshake = RemoteHandshake(
        protocolVersion = PRIVILEGED_FILESYSTEM_PROTOCOL_VERSION,
        effectiveUid = Process.myUid(),
        pid = Process.myPid(),
        transport = transport.name,
        selinuxContext = readSelinuxContext(),
        capabilities = PrivilegeCapability.entries.map(Enum<*>::name),
        maximumDirectoryPageSize = MAX_DIRECTORY_PAGE_SIZE
    )

    override fun canonicalizeAndLstat(path: String): RemoteFileEntry = binderCall {
        engine.canonicalizeAndLstat(path)
    }

    override fun listDirectory(path: String, pageToken: String?, pageSize: Int): RemoteDirectoryPage =
        binderCall { engine.listDirectory(path, pageToken.orEmpty(), pageSize) }

    override fun filesystemStats(path: String): RemoteFilesystemStats = binderCall { engine.filesystemStats(path) }

    override fun createFile(path: String): RemoteFileEntry = binderCall { engine.createFile(path) }

    override fun createDirectory(path: String): RemoteFileEntry = binderCall { engine.createDirectory(path) }

    override fun openForReading(path: String) = binderCall { engine.openForReading(path) }

    override fun openForWriting(path: String, append: Boolean) = binderCall {
        engine.openForWriting(path, append)
    }

    override fun rename(sourcePath: String, destinationPath: String) =
        binderCall { engine.rename(sourcePath, destinationPath) }

    override fun copy(
        sourcePath: String,
        destinationPath: String,
        operationId: String,
        callback: IRemoteOperationCallback?
    ) = binderCall { engine.copy(sourcePath, destinationPath, operationId) { callback?.onProgress(it) } }

    override fun move(
        sourcePath: String,
        destinationPath: String,
        operationId: String,
        callback: IRemoteOperationCallback?
    ) = binderCall { engine.move(sourcePath, destinationPath, operationId) { callback?.onProgress(it) } }

    override fun delete(path: String) = binderCall { engine.delete(path) }

    override fun deleteRecursively(
        path: String,
        operationId: String,
        callback: IRemoteOperationCallback?
    ) = binderCall { engine.deleteRecursively(path, operationId) { callback?.onProgress(it) } }

    override fun secureOverwrite(
        path: String,
        operationId: String,
        callback: IRemoteOperationCallback?
    ) = binderCall { engine.secureOverwrite(path, operationId) { callback?.onProgress(it) } }

    override fun updateTimestamps(path: String, accessedAtMillis: Long, modifiedAtMillis: Long) =
        binderCall { engine.updateTimestamps(path, accessedAtMillis, modifiedAtMillis) }

    override fun cancel(operationId: String) = engine.cancel(operationId)

    private fun readSelinuxContext(): String? = runCatching {
        File("/proc/self/attr/current").readText().trim().takeIf(String::isNotBlank)
    }.getOrNull()

    private inline fun <T> binderCall(block: () -> T): T = try {
        block()
    } catch (error: RemoteFileException) {
        throw RemoteException(
            "$REMOTE_FAILURE_PREFIX${error.failureCode}:${error.message.orEmpty()}"
        )
    }
}
