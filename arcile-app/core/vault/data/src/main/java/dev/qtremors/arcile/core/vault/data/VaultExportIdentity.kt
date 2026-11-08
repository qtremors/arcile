package dev.qtremors.arcile.core.vault.data

import android.system.Os
import dev.qtremors.arcile.core.vault.domain.VaultCancellationSignal
import dev.qtremors.arcile.core.vault.domain.VaultFailure
import java.io.File
import java.nio.ByteBuffer
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.attribute.UserDefinedFileAttributeView
import java.security.MessageDigest
import java.util.UUID

/** Inode/file keys take precedence. Some JVM filesystems expose neither, so use an ownership attribute. */
@Synchronized
internal fun exportFileKey(file: File, attributes: BasicFileAttributes, createMarker: Boolean = false): String? {
    attributes.fileKey()?.toString()?.let { return it }
    runCatching { Os.lstat(file.path) }.getOrNull()?.let {
        if (it.st_ino != 0L) return "stat:${it.st_dev}:${it.st_ino}"
    }
    return runCatching {
        val view = Files.getFileAttributeView(file.toPath(), UserDefinedFileAttributeView::class.java, LinkOption.NOFOLLOW_LINKS)
            ?: return@runCatching null
        val name = "arcile.export.identity"
        if (name !in view.list()) {
            if (!createMarker) return@runCatching null
            view.write(name, ByteBuffer.wrap(UUID.randomUUID().toString().encodeToByteArray()))
        }
        val size = view.size(name)
        if (size != 36) return@runCatching null
        val bytes = ByteBuffer.allocate(size)
        view.read(name, bytes)
        val id = bytes.array().decodeToString()
        if (UUID.fromString(id).toString() == id) "marker:$id" else null
    }.getOrNull()
}

/** Content proof for an existing replacement backup when its filesystem has no stable object key. */
internal fun exportPathDigest(root: File, cancellation: VaultCancellationSignal): String {
    val digest = MessageDigest.getInstance("SHA-256")
    val pending = ArrayDeque<Pair<File, String>>()
    pending += root to ""
    var count = 0
    while (pending.isNotEmpty()) {
        cancellation.throwIfCancelled()
        if (++count > MAX_EXPORT_RETRY_RECORDS) throw VaultFailure.Unavailable("The destination proof exceeds the operation limit")
        val (file, path) = pending.removeLast()
        val entry = file.inspectDestination()
        val label = path.encodeToByteArray()
        digest.update(ByteBuffer.allocate(4).putInt(label.size).array())
        digest.update(label)
        label.fill(0)
        digest.update(if (entry.isDirectory) 1.toByte() else 0.toByte())
        if (entry.isDirectory) {
            val children = file.readLocalChildren().sortedByDescending(File::getName)
            if (pending.size + children.size + count > MAX_EXPORT_RETRY_RECORDS) {
                throw VaultFailure.Unavailable("The destination proof exceeds the operation limit")
            }
            children.forEach { pending += it to if (path.isEmpty()) it.name else "$path/${it.name}" }
        } else {
            digest.update(ByteBuffer.allocate(8).putLong(entry.size).array())
            digest.update(localDigest(file, cancellation).encodeToByteArray())
        }
    }
    return digest.digest().toHexDigest()
}
