package dev.qtremors.arcile.core.vault.crypto

import java.io.Closeable
import java.io.File
import java.io.RandomAccessFile
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.io.FileOutputStream
import java.security.MessageDigest

interface VaultRandomAccess : Closeable {
    var position: Long
    val length: Long
    fun setLength(value: Long)
    fun readFully(target: ByteArray)
    fun read(target: ByteArray, offset: Int, length: Int): Int
    fun readInt(): Int
    fun readLong(): Long
    fun write(source: ByteArray)
    fun writeInt(value: Int)
    fun writeLong(value: Long)
    fun sync()
}

interface VaultDirectoryAccess {
    val stableId: String
    fun exists(relativePath: String): Boolean
    fun readBytes(relativePath: String): ByteArray
    fun writeAtomic(relativePath: String, bytes: ByteArray)
    fun createDirectory(relativePath: String): Boolean
    fun openRandom(relativePath: String, writable: Boolean): VaultRandomAccess
    fun delete(relativePath: String): Boolean
    fun rename(fromRelativePath: String, toName: String): Boolean
    fun listFiles(relativeDirectory: String): List<VaultPhysicalEntry>
}

data class VaultPhysicalEntry(
    val relativePath: String,
    val isDirectory: Boolean,
    val length: Long,
    val modifiedAtMillis: Long
)

class FileVaultDirectory(val directory: File) : VaultDirectoryAccess {
    override val stableId: String get() = directory.canonicalPath

    override fun exists(relativePath: String): Boolean = resolve(relativePath).exists()

    override fun readBytes(relativePath: String): ByteArray = resolve(relativePath).readBytes()

    override fun writeAtomic(relativePath: String, bytes: ByteArray) = atomicWrite(resolve(relativePath), bytes)

    override fun createDirectory(relativePath: String): Boolean {
        val target = resolve(relativePath)
        return target.isDirectory || target.mkdirs()
    }

    override fun openRandom(relativePath: String, writable: Boolean): VaultRandomAccess {
        val file = resolve(relativePath)
        if (writable) file.parentFile?.mkdirs()
        return FileVaultRandomAccess(file, writable)
    }

    override fun delete(relativePath: String): Boolean = resolve(relativePath).delete()

    override fun rename(fromRelativePath: String, toName: String): Boolean {
        require('/' !in toName && '\\' !in toName && toName.isNotBlank())
        val source = resolve(fromRelativePath)
        val target = File(requireNotNull(source.parentFile), toName)
        return try {
            Files.move(source.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE)
            true
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(source.toPath(), target.toPath())
            true
        }
    }

    override fun listFiles(relativeDirectory: String): List<VaultPhysicalEntry> {
        val base = resolve(relativeDirectory)
        if (!base.isDirectory) return emptyList()
        return base.walkTopDown().drop(1).map { file ->
            VaultPhysicalEntry(
                relativePath = file.relativeTo(directory).invariantSeparatorsPath,
                isDirectory = file.isDirectory,
                length = if (file.isFile) file.length() else 0L,
                modifiedAtMillis = file.lastModified()
            )
        }.toList()
    }

    private fun resolve(relativePath: String): File {
        val normalized = relativePath.replace('\\', '/').trim('/')
        require(normalized.isNotBlank())
        require(normalized.split('/').none { it.isBlank() || it == "." || it == ".." })
        val target = File(directory, normalized).canonicalFile
        require(target.toPath().startsWith(directory.canonicalFile.toPath()))
        return target
    }
}

private class FileVaultRandomAccess(file: File, writable: Boolean) : VaultRandomAccess {
    private val delegate = RandomAccessFile(file, if (writable) "rw" else "r")
    override var position: Long
        get() = delegate.filePointer
        set(value) = delegate.seek(value)
    override val length: Long get() = delegate.length()
    override fun setLength(value: Long) = delegate.setLength(value)
    override fun readFully(target: ByteArray) = delegate.readFully(target)
    override fun read(target: ByteArray, offset: Int, length: Int): Int = delegate.read(target, offset, length)
    override fun readInt(): Int = delegate.readInt()
    override fun readLong(): Long = delegate.readLong()
    override fun write(source: ByteArray) = delegate.write(source)
    override fun writeInt(value: Int) = delegate.writeInt(value)
    override fun writeLong(value: Long) = delegate.writeLong(value)
    override fun sync() = delegate.fd.sync()
    override fun close() = delegate.close()
}

fun VaultDirectoryAccess.sha256(relativePath: String): ByteArray {
    val digest = MessageDigest.getInstance("SHA-256")
    val buffer = ByteArray(256 * 1024)
    openRandom(relativePath, writable = false).use { input ->
        input.position = 0L
        while (true) {
            val count = input.read(buffer, 0, buffer.size)
            if (count < 0) break
            if (count == 0) continue
            digest.update(buffer, 0, count)
        }
    }
    buffer.fill(0)
    return digest.digest()
}

internal fun atomicWrite(target: File, bytes: ByteArray) {
    target.parentFile?.mkdirs()
    val temporary = File(requireNotNull(target.parentFile), ".${target.name}.${System.nanoTime()}.tmp")
    try {
        FileOutputStream(temporary).use { output ->
            output.write(bytes)
            output.flush()
            output.fd.sync()
        }
        try {
            Files.move(
                temporary.toPath(),
                target.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING
            )
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    } finally {
        temporary.delete()
    }
}
