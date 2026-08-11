package dev.qtremors.arcile.core.privilege.android.remote

import android.os.ParcelFileDescriptor
import android.system.Os
import android.system.OsConstants
import android.system.StructStat
import dev.qtremors.arcile.core.privilege.MAX_DIRECTORY_PAGE_SIZE
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.RandomAccessFile
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

internal class PrivilegedFileEngine {
    private val cancellations = ConcurrentHashMap<String, AtomicBoolean>()

    fun canonicalizeAndLstat(path: String): RemoteFileEntry = remoteCall(path) {
        val file = validatePath(path)
        toRemoteEntry(file, Os.lstat(file.path))
    }

    fun listDirectory(path: String, pageToken: String, pageSize: Int): RemoteDirectoryPage = remoteCall(path) {
        val directory = validatePath(path)
        val stat = Os.lstat(directory.path)
        require(OsConstants.S_ISDIR(stat.st_mode)) { "Path is not a directory" }
        val offset = pageToken.ifBlank { "0" }.toIntOrNull()
            ?.takeIf { it >= 0 }
            ?: throw IllegalArgumentException("Invalid directory page token")
        val boundedSize = pageSize.coerceIn(1, MAX_DIRECTORY_PAGE_SIZE)
        val entries = ArrayList<RemoteFileEntry>(boundedSize)
        var visited = 0
        var hasMore = false

        directory.listFiles()?.asSequence()?.forEach { child ->
            if (visited++ < offset) return@forEach
            if (entries.size == boundedSize) {
                hasMore = true
                return@forEach
            }
            entries += toRemoteEntry(child, Os.lstat(child.path))
        } ?: throw SecurityException("Directory cannot be read")

        RemoteDirectoryPage(
            entries = entries,
            nextPageToken = if (hasMore) (offset + entries.size).toString() else null
        )
    }

    fun filesystemStats(path: String): RemoteFilesystemStats = remoteCall(path) {
        val file = validatePath(path)
        val stats = Os.statvfs(file.path)
        RemoteFilesystemStats(
            totalBytes = multiplySaturated(stats.f_blocks, stats.f_frsize),
            availableBytes = multiplySaturated(stats.f_bavail, stats.f_frsize),
            freeBytes = multiplySaturated(stats.f_bfree, stats.f_frsize)
        )
    }

    fun createFile(path: String): RemoteFileEntry = remoteCall(path) {
        val file = validatePath(path)
        ensureParentDirectory(file)
        if (!file.createNewFile()) {
            throw RemoteFileException(
                RemoteFailureCode.PATH_ALREADY_EXISTS,
                "Path already exists: $path"
            )
        }
        toRemoteEntry(file, Os.lstat(file.path))
    }

    fun createDirectory(path: String): RemoteFileEntry = remoteCall(path) {
        val directory = validatePath(path)
        ensureParentDirectory(directory)
        if (!directory.mkdir()) {
            if (directory.exists()) {
                throw RemoteFileException(
                    RemoteFailureCode.PATH_ALREADY_EXISTS,
                    "Path already exists: $path"
                )
            }
            throw IOException("Directory could not be created")
        }
        toRemoteEntry(directory, Os.lstat(directory.path))
    }

    fun openForReading(path: String): ParcelFileDescriptor = remoteCall(path) {
        val file = validateRegularFile(path)
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    fun openForWriting(path: String, append: Boolean): ParcelFileDescriptor = remoteCall(path) {
        val file = validatePath(path)
        ensureParentDirectory(file)
        if (file.exists()) validateRegularFile(path)
        val mode = ParcelFileDescriptor.MODE_WRITE_ONLY or ParcelFileDescriptor.MODE_CREATE or
            if (append) ParcelFileDescriptor.MODE_APPEND else ParcelFileDescriptor.MODE_TRUNCATE
        ParcelFileDescriptor.open(file, mode)
    }

    fun rename(sourcePath: String, destinationPath: String) = remoteCall(sourcePath) {
        val source = validatePath(sourcePath)
        val destination = validatePath(destinationPath)
        Os.lstat(source.path)
        ensureParentDirectory(destination)
        Os.rename(source.path, destination.path)
    }

    fun copy(
        sourcePath: String,
        destinationPath: String,
        operationId: String,
        progress: (RemoteOperationProgress) -> Unit
    ) = withOperation(operationId) { cancelled ->
        remoteCall(sourcePath) {
            val source = validatePath(sourcePath)
            val destination = validatePath(destinationPath)
            require(source.path != destination.path) { "Source and destination must differ" }
            copyNode(source, destination, operationId, cancelled, progress)
        }
    }

    fun move(
        sourcePath: String,
        destinationPath: String,
        operationId: String,
        progress: (RemoteOperationProgress) -> Unit
    ) = withOperation(operationId) { cancelled ->
        remoteCall(sourcePath) {
            val source = validatePath(sourcePath)
            val destination = validatePath(destinationPath)
            require(source.path != destination.path) { "Source and destination must differ" }
            ensureNotCancelled(cancelled)
            ensureParentDirectory(destination)
            try {
                Os.rename(source.path, destination.path)
                progress(progress(operationId, 1, 1, 0, null, destination.path))
            } catch (error: android.system.ErrnoException) {
                if (error.errno != OsConstants.EXDEV) throw error
                copyNode(source, destination, operationId, cancelled, progress)
                ensureNotCancelled(cancelled)
                deleteNode(source, operationId, cancelled, progress)
            }
        }
    }

    fun delete(path: String) = remoteCall(path) {
        val file = validatePath(path)
        val stat = Os.lstat(file.path)
        removeNode(file)
    }

    fun deleteRecursively(
        path: String,
        operationId: String,
        progress: (RemoteOperationProgress) -> Unit
    ) = withOperation(operationId) { cancelled ->
        remoteCall(path) {
            val file = validatePath(path)
            require(file.path != File.separator) { "Filesystem root cannot be deleted" }
            deleteNode(file, operationId, cancelled, progress)
        }
    }

    fun secureOverwrite(
        path: String,
        operationId: String,
        progress: (RemoteOperationProgress) -> Unit
    ) = withOperation(operationId) { cancelled ->
        remoteCall(path) {
            val file = validateRegularFile(path)
            val totalBytes = Os.lstat(file.path).st_size.coerceAtLeast(0)
            RandomAccessFile(file, "rws").use { output ->
                val buffer = ByteArray(COPY_BUFFER_SIZE)
                var completed = 0L
                output.seek(0)
                while (completed < totalBytes) {
                    ensureNotCancelled(cancelled)
                    val count = minOf(buffer.size.toLong(), totalBytes - completed).toInt()
                    output.write(buffer, 0, count)
                    completed += count
                    progress(progress(operationId, 0, null, completed, totalBytes, file.path))
                }
                output.fd.sync()
            }
            removeNode(file)
            progress(progress(operationId, 1, 1, totalBytes, totalBytes, file.path))
        }
    }

    fun updateTimestamps(path: String, accessedAtMillis: Long, modifiedAtMillis: Long) = remoteCall(path) {
        val file = validatePath(path)
        Os.lstat(file.path)
        require(accessedAtMillis >= 0 && modifiedAtMillis >= 0) { "Timestamps must not be negative" }
        val updated = file.setLastModified(modifiedAtMillis)
        if (!updated) throw IOException("Timestamp could not be updated")
    }

    fun cancel(operationId: String) {
        cancellations[operationId]?.set(true)
    }

    private fun copyNode(
        source: File,
        destination: File,
        operationId: String,
        cancelled: AtomicBoolean,
        progressCallback: (RemoteOperationProgress) -> Unit
    ) {
        ensureNotCancelled(cancelled)
        val stat = Os.lstat(source.path)
        when {
            OsConstants.S_ISLNK(stat.st_mode) -> {
                ensureParentDirectory(destination)
                Os.symlink(Os.readlink(source.path), destination.path)
                progressCallback(progress(operationId, 1, 1, 0, 0, source.path))
            }
            OsConstants.S_ISREG(stat.st_mode) -> {
                ensureParentDirectory(destination)
                copyRegularFile(source, destination, stat.st_size, operationId, cancelled, progressCallback)
            }
            OsConstants.S_ISDIR(stat.st_mode) -> {
                if (!destination.exists() && !destination.mkdir()) {
                    throw IOException("Destination directory could not be created")
                }
                source.listFiles()?.forEach { child ->
                    copyNode(child, File(destination, child.name), operationId, cancelled, progressCallback)
                } ?: throw SecurityException("Source directory cannot be read")
            }
            else -> throw RemoteFileException(
                RemoteFailureCode.UNSUPPORTED_FILE_TYPE,
                "Unsupported file type: ${source.path}"
            )
        }
    }

    private fun copyRegularFile(
        source: File,
        destination: File,
        totalBytes: Long,
        operationId: String,
        cancelled: AtomicBoolean,
        progressCallback: (RemoteOperationProgress) -> Unit
    ) {
        var completed = 0L
        FileInputStream(source).use { input ->
            FileOutputStream(destination, false).use { output ->
                val buffer = ByteArray(COPY_BUFFER_SIZE)
                while (true) {
                    ensureNotCancelled(cancelled)
                    val read = input.read(buffer)
                    if (read < 0) break
                    output.write(buffer, 0, read)
                    completed += read
                    progressCallback(progress(operationId, 0, null, completed, totalBytes, source.path))
                }
                output.fd.sync()
            }
        }
        destination.setLastModified(source.lastModified())
        progressCallback(progress(operationId, 1, 1, completed, totalBytes, source.path))
    }

    private fun deleteNode(
        file: File,
        operationId: String,
        cancelled: AtomicBoolean,
        progressCallback: (RemoteOperationProgress) -> Unit
    ) {
        ensureNotCancelled(cancelled)
        val stat = Os.lstat(file.path)
        if (OsConstants.S_ISDIR(stat.st_mode)) {
            file.listFiles()?.forEach { child -> deleteNode(child, operationId, cancelled, progressCallback) }
                ?: throw SecurityException("Directory cannot be read")
            removeNode(file)
        } else {
            removeNode(file)
        }
        progressCallback(progress(operationId, 1, null, 0, null, file.path))
    }

    private fun validateRegularFile(path: String): File {
        val file = validatePath(path)
        val stat = Os.lstat(file.path)
        if (!OsConstants.S_ISREG(stat.st_mode)) {
            throw RemoteFileException(
                RemoteFailureCode.UNSUPPORTED_FILE_TYPE,
                "Path is not a regular file: $path"
            )
        }
        return file
    }

    private fun validatePath(path: String): File {
        require(path.isNotBlank() && path.indexOf('\u0000') < 0) { "Path is empty or contains NUL" }
        require(File(path).isAbsolute || path.startsWith('/')) { "Path must be absolute" }
        val segments = path.replace('\\', '/').split('/')
        require(".." !in segments) { "Path traversal is not allowed" }
        return File(path).absoluteFile.normalize()
    }

    private fun ensureParentDirectory(file: File) {
        val parent = file.parentFile ?: throw IllegalArgumentException("Path has no parent")
        val stat = Os.lstat(parent.path)
        require(OsConstants.S_ISDIR(stat.st_mode)) { "Parent is not a directory" }
    }

    private fun removeNode(file: File) {
        if (!file.delete()) throw IOException("Path could not be deleted")
    }

    private fun toRemoteEntry(file: File, stat: StructStat): RemoteFileEntry {
        val type = when {
            OsConstants.S_ISREG(stat.st_mode) -> "REGULAR_FILE"
            OsConstants.S_ISDIR(stat.st_mode) -> "DIRECTORY"
            OsConstants.S_ISLNK(stat.st_mode) -> "SYMBOLIC_LINK"
            OsConstants.S_ISBLK(stat.st_mode) -> "BLOCK_DEVICE"
            OsConstants.S_ISCHR(stat.st_mode) -> "CHARACTER_DEVICE"
            OsConstants.S_ISFIFO(stat.st_mode) -> "FIFO"
            OsConstants.S_ISSOCK(stat.st_mode) -> "SOCKET"
            else -> "UNKNOWN"
        }
        val canonicalIdentity = if (OsConstants.S_ISLNK(stat.st_mode)) {
            file.absolutePath
        } else {
            file.canonicalPath
        }
        return RemoteFileEntry(
            path = file.absolutePath,
            canonicalIdentity = canonicalIdentity,
            displayName = file.name.ifEmpty { File.separator },
            type = type,
            size = stat.st_size.coerceAtLeast(0),
            modifiedAtMillis = multiplySaturated(stat.st_mtime, 1000),
            mode = stat.st_mode,
            readable = runCatching { Os.access(file.path, OsConstants.R_OK) }.getOrDefault(false),
            writable = runCatching { Os.access(file.path, OsConstants.W_OK) }.getOrDefault(false)
        )
    }

    private inline fun withOperation(operationId: String, block: (AtomicBoolean) -> Unit) {
        require(operationId.isNotBlank()) { "Operation id must not be blank" }
        val cancelled = AtomicBoolean(false)
        check(cancellations.putIfAbsent(operationId, cancelled) == null) { "Operation id is already active" }
        try {
            block(cancelled)
        } finally {
            cancellations.remove(operationId, cancelled)
        }
    }

    private fun ensureNotCancelled(cancelled: AtomicBoolean) {
        if (cancelled.get() || Thread.currentThread().isInterrupted) {
            throw RemoteFileException(
                RemoteFailureCode.OPERATION_INTERRUPTED,
                "Privileged filesystem operation was interrupted"
            )
        }
    }

    private fun progress(
        operationId: String,
        completedItems: Long,
        totalItems: Long?,
        completedBytes: Long,
        totalBytes: Long?,
        currentPath: String
    ) = RemoteOperationProgress(
        operationId = operationId,
        completedItems = completedItems,
        totalItems = totalItems ?: 0,
        hasTotalItems = totalItems != null,
        completedBytes = completedBytes,
        totalBytes = totalBytes ?: 0,
        hasTotalBytes = totalBytes != null,
        currentPath = currentPath
    )

    private fun multiplySaturated(left: Long, right: Long): Long {
        if (left <= 0 || right <= 0) return 0
        return if (left > Long.MAX_VALUE / right) Long.MAX_VALUE else left * right
    }

    private companion object {
        const val COPY_BUFFER_SIZE = 128 * 1024
    }
}
