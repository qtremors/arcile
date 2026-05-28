package dev.qtremors.arcile.core.storage.data.manager

import dev.qtremors.arcile.core.storage.data.source.FileConflictNameGenerator
import java.io.File
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

internal const val ARCHIVE_BUFFER_SIZE = 128 * 1024

internal class ArchiveExtractionContext(
    private val safetyPolicy: ArchiveSafetyPolicy,
    private val validateMutationPath: (File) -> Result<Unit>
) {
    fun resolveTarget(destination: File, rawEntryName: String, directory: Boolean): File {
        ArchiveSafetyTally(safetyPolicy).accept(rawEntryName, 0L, null)
        val normalized = rawEntryName.normalizeEntryName()
        require(normalized.isNotBlank()) { "Archive contains an empty entry name" }
        require(!File(normalized).isAbsolute && !normalized.startsWith("/")) { "Archive contains unsafe absolute paths" }
        require(normalized.split('/').none { it == ".." }) { "Archive contains unsafe relative paths" }

        val base = destination.canonicalFile
        val requested = File(base, normalized).canonicalFile
        require(requested.path == base.path || requested.path.startsWith(base.path + File.separator)) {
            "Archive entry escapes the destination folder"
        }

        val target = if (directory || !requested.exists()) {
            requested
        } else {
            FileConflictNameGenerator.generateKeepBothTarget(
                requireNotNull(requested.parentFile) { "Archive target has no parent folder" },
                requested
            )
        }
        validateMutationPath(target).getOrThrow()
        return target
    }
}

internal class ArchiveSafetyTally(private val safetyPolicy: ArchiveSafetyPolicy) {
    private var entries = 0
    private var uncompressedBytes = 0L

    fun accept(rawName: String, uncompressedSize: Long, compressedSize: Long?) {
        val normalized = rawName.normalizeEntryName()
        require(normalized.length <= safetyPolicy.maxEntryPathLength) { "Archive entry path is too long" }
        val segments = normalized.split('/').filter { it.isNotBlank() }
        require(segments.size <= safetyPolicy.maxNestedDepth) { "Archive nesting is too deep" }

        entries += 1
        require(entries <= safetyPolicy.maxEntries) { "Archive contains too many entries" }

        val safeUncompressedSize = uncompressedSize.coerceAtLeast(0L)
        uncompressedBytes = Math.addExact(uncompressedBytes, safeUncompressedSize)
        require(uncompressedBytes <= safetyPolicy.maxUncompressedBytes) { "Archive is too large to process safely" }

        if (safeUncompressedSize > 0L && compressedSize != null) {
            val safeCompressedSize = compressedSize.coerceAtLeast(0L)
            val ratio = if (safeCompressedSize == 0L) {
                Double.POSITIVE_INFINITY
            } else {
                safeUncompressedSize.toDouble() / safeCompressedSize.toDouble()
            }
            require(ratio <= safetyPolicy.maxCompressionRatio) { "Archive compression ratio is too high" }
        }
    }
}

internal fun rememberCreatedOutput(target: File, createdOutputs: MutableSet<File>) {
    if (!target.exists()) {
        createdOutputs += target
    }
}

internal fun cleanupCreatedOutputs(createdOutputs: Set<File>) {
    createdOutputs.toList().asReversed().forEach { output ->
        runCatching {
            if (output.exists()) {
                if (output.isDirectory) output.deleteRecursively() else output.delete()
            }
        }
    }
}

internal fun File.walkArchiveFiles(validateMutationPath: (File) -> Result<Unit>): List<Pair<File, File>> {
    validateMutationPath(this).getOrThrow()
    require(exists()) { "File does not exist: $name" }
    return if (isDirectory) {
        listOf(this to this) + walkTopDown().drop(1).map { this to it }
    } else {
        listOf(this to this)
    }
}

internal fun archiveEntryName(sourceRoot: File, file: File): String =
    if (sourceRoot == file) {
        sourceRoot.name
    } else {
        sourceRoot.name + "/" + file.relativeTo(sourceRoot).path.replace(File.separatorChar, '/')
    }.normalizeEntryName()

internal fun String.normalizeEntryName(): String =
    replace('\\', '/').trimStart('/')

internal fun String.matchesPrefix(prefix: String?): Boolean {
    val normalizedPrefix = prefix?.normalizeEntryName()?.trimEnd('/')?.takeIf { it.isNotBlank() } ?: return true
    val normalized = normalizeEntryName()
    return normalized == normalizedPrefix || normalized.startsWith("$normalizedPrefix/")
}

internal suspend fun copyWithProgress(
    read: (ByteArray) -> Int,
    write: (ByteArray, Int, Int) -> Unit,
    onDelta: suspend (Long) -> Unit
): Long {
    val buffer = ByteArray(ARCHIVE_BUFFER_SIZE)
    var copied = 0L
    while (true) {
        currentCoroutineContext().ensureActive()
        val count = read(buffer)
        if (count < 0) break
        write(buffer, 0, count)
        copied += count
        onDelta(copied)
    }
    return copied
}
