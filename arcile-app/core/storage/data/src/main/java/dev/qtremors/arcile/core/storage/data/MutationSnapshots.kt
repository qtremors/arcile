package dev.qtremors.arcile.core.storage.data

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.serialization.Serializable
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.attribute.BasicFileAttributes
import java.security.MessageDigest

@Serializable
internal data class MutationPathIdentity(
    val fileKey: String?,
    val createdAt: String,
    val isDirectory: Boolean
) {
    fun matches(file: File, keyProvider: ((File) -> String?)? = null): Boolean = fileKey != null && runCatchingPreservingCancellation {
        this == read(file, keyProvider)
    }.getOrDefault(false)

    // Live rollback has just renamed a known, owned path. Its freshly read
    // creation metadata also permits rollback on JVMs without fileKey support.
    fun matchesOwned(file: File): Boolean = runCatchingPreservingCancellation { this == read(file) }.getOrDefault(false)

    companion object {
        fun read(file: File, keyProvider: ((File) -> String?)? = null): MutationPathIdentity {
            val attributes = Files.readAttributes(file.toPath(), BasicFileAttributes::class.java, LinkOption.NOFOLLOW_LINKS)
            if (attributes.isSymbolicLink || (!attributes.isDirectory && !attributes.isRegularFile)) {
                throw IOException("Unsupported recovery path: ${file.absolutePath}")
            }
            return MutationPathIdentity(
                if (keyProvider == null) attributes.fileKey()?.toString() else keyProvider(file),
                attributes.creationTime().toString(), attributes.isDirectory
            )
        }
    }
}

@Serializable
internal data class MutationTreeEntry(
    val relativePath: String,
    val identity: MutationPathIdentity,
    val size: Long = 0L,
    val modifiedAt: Long = 0L,
    val sha256: String? = null
) {
    fun resolve(root: File): File {
        if (relativePath.isEmpty()) return root
        require(!File(relativePath).isAbsolute && '\u0000' !in relativePath)
        require(relativePath.replace('\\', '/').split('/').none { it.isBlank() || it == "." || it == ".." })
        return File(root, relativePath)
    }

    fun matches(root: File, keyProvider: ((File) -> String?)? = null, requireIdentity: Boolean = true): Boolean = runCatchingPreservingCancellation {
        val file = resolve(root)
        val sameNode = if (requireIdentity) identity.matches(file, keyProvider)
        else MutationPathIdentity.read(file).isDirectory == identity.isDirectory
        sameNode && (identity.isDirectory ||
            (file.length() == size && (!requireIdentity || file.lastModified() == modifiedAt) && sha256 == hash(file)))
    }.getOrDefault(false)
}

@Serializable
internal data class MutationTreeSnapshot(val entries: List<MutationTreeEntry>) {
    fun matches(
        root: File, keyProvider: ((File) -> String?)? = null, requireIdentity: Boolean = true
    ): Boolean = runCatchingPreservingCancellation {
        if (entries.isEmpty() || entries.any { !it.matches(root, keyProvider, requireIdentity) }) return@runCatchingPreservingCancellation false
        val paths = entries.map { it.relativePath }.toSet()
        val pending = ArrayDeque<File>()
        pending += root
        var count = 0
        while (pending.isNotEmpty()) {
            val file = pending.removeFirst()
            val relative = if (file == root) "" else file.relativeTo(root).path
            if (relative !in paths || ++count > paths.size) return@runCatchingPreservingCancellation false
            if (file.isDirectory) {
                val children = file.listFiles() ?: return@runCatchingPreservingCancellation false
                children.forEach { pending += it }
            }
        }
        count == paths.size
    }.getOrDefault(false)

    companion object {
        fun capture(root: File, keyProvider: ((File) -> String?)? = null): MutationTreeSnapshot {
            val entries = mutableListOf<MutationTreeEntry>()
            val pending = ArrayDeque<File>()
            pending += root
            while (pending.isNotEmpty()) {
                if (entries.size >= MAX_SNAPSHOT_ENTRIES) throw IOException("Too many paths for safe automatic recovery")
                val file = pending.removeFirst()
                val identity = MutationPathIdentity.read(file, keyProvider)
                val entry = MutationTreeEntry(
                    relativePath = if (file == root) "" else file.relativeTo(root).path,
                    identity = identity,
                    size = if (identity.isDirectory) 0L else file.length(),
                    modifiedAt = if (identity.isDirectory) 0L else file.lastModified(),
                    sha256 = if (identity.isDirectory) null else hash(file)
                )
                entries += entry
                if (identity.isDirectory) {
                    val children = file.listFiles() ?: throw IOException("Unable to enumerate recovery path: ${file.absolutePath}")
                    children.forEach { pending += it }
                }
                if (!identity.matches(file, keyProvider) && identity.fileKey != null) throw IOException("Recovery path changed while recording")
                if (!identity.isDirectory && (file.length() != entry.size || file.lastModified() != entry.modifiedAt)) {
                    throw IOException("Recovery file changed while recording")
                }
            }
            return MutationTreeSnapshot(entries)
        }
    }
}

@Serializable
internal data class SourceCleanupSnapshot(
    val source: MutationTreeSnapshot,
    val destination: MutationTreeSnapshot
) {
    companion object {
        fun capture(source: File, destination: File, keyProvider: ((File) -> String?)? = null): SourceCleanupSnapshot {
            val sourceSnapshot = MutationTreeSnapshot.capture(source, keyProvider)
            val destinationSnapshot = MutationTreeSnapshot.capture(destination, keyProvider)
            val destinations = destinationSnapshot.entries.associateBy { it.relativePath }
            if (sourceSnapshot.entries.size != destinations.size || sourceSnapshot.entries.any { entry ->
                val target = destinations[entry.relativePath]
                target == null || target.identity.isDirectory != entry.identity.isDirectory ||
                    (!entry.identity.isDirectory && (target.size != entry.size || target.sha256 != entry.sha256))
            }) throw IOException("Source and destination changed before cleanup could be recorded")
            return SourceCleanupSnapshot(sourceSnapshot, destinationSnapshot)
        }
    }
}

internal suspend fun cleanupVerifiedSource(
    source: File, destination: File, snapshot: SourceCleanupSnapshot, keyProvider: ((File) -> String?)? = null
) {
    val sourceEntries = snapshot.source.entries.associateBy { it.relativePath }
    val destinationEntries = snapshot.destination.entries.associateBy { it.relativePath }
    fun parentsMatch(entry: MutationTreeEntry): Boolean {
        var relative = entry.relativePath
        while (true) {
            val sourceEntry = sourceEntries[relative] ?: return false
            val destinationEntry = destinationEntries[relative] ?: return false
            if (!sourceEntry.identity.matches(sourceEntry.resolve(source), keyProvider) ||
                !destinationEntry.identity.matches(destinationEntry.resolve(destination), keyProvider)) return false
            if (relative.isEmpty()) return true
            relative = File(relative).parent.orEmpty()
        }
    }
    // Delete only recorded nodes. An added child prevents an empty-directory
    // delete and keeps the recovery record pending, without recursive deletion.
    for (entry in snapshot.source.entries.asReversed()) {
        currentCoroutineContext().ensureActive()
        val file = entry.resolve(source)
        if (!file.exists()) continue
        val target = destinationEntries[entry.relativePath] ?: continue
        if (parentsMatch(entry) && entry.matches(source, keyProvider) && target.matches(destination, keyProvider)) file.delete()
    }
}

private fun hash(file: File): String {
    val digest = MessageDigest.getInstance("SHA-256")
    file.inputStream().use { input ->
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            digest.update(buffer, 0, count)
        }
    }
    return digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
}

private const val MAX_SNAPSHOT_ENTRIES = 4096
