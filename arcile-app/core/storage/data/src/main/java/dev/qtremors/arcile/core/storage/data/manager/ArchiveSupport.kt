package dev.qtremors.arcile.core.storage.data.manager

import dev.qtremors.arcile.core.storage.data.source.FileConflictNameGenerator
import dev.qtremors.arcile.core.storage.domain.ArchiveNameEncoding
import dev.qtremors.arcile.core.storage.domain.ConflictResolution
import java.io.File
import java.nio.charset.Charset
import java.util.UUID
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

internal const val ARCHIVE_BUFFER_SIZE = 128 * 1024
internal const val ARCHIVE_CREATION_PRE_SCAN_MAX_ENTRIES = 2_048
internal const val ARCHIVE_CREATION_PRE_SCAN_MAX_BYTES = 512L * 1024L * 1024L

internal data class ArchiveSourceEntry(
    val sourceRoot: File,
    val file: File
)

internal data class ArchiveCreationScan(
    val entries: List<ArchiveSourceEntry>?,
    val totalItems: Int,
    val totalBytes: Long?
) {
    val isDeterminate: Boolean get() = entries != null
}

internal class ArchiveExtractionContext(
    private val safetyPolicy: ArchiveSafetyPolicy,
    private val validateMutationPath: (File) -> Result<Unit>
) {
    fun resolveRequestedTarget(destination: File, rawEntryName: String): File {
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

        return requested
    }

    fun resolveTarget(
        destination: File,
        rawEntryName: String,
        directory: Boolean,
        resolutions: Map<String, ConflictResolution> = emptyMap()
    ): File? {
        val requested = resolveRequestedTarget(destination, rawEntryName)
        val normalized = rawEntryName.normalizeEntryName()
        val target = when {
            directory || !requested.exists() -> requested
            resolutions[normalized] == ConflictResolution.SKIP -> return null
            resolutions[normalized] == ConflictResolution.REPLACE -> requested
            else -> FileConflictNameGenerator.generateKeepBothTarget(
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

internal fun rememberReplacementBackup(target: File, replacementBackups: MutableMap<File, File>) {
    if (!target.exists() || !target.isFile || replacementBackups.containsKey(target)) return
    val parent = requireNotNull(target.parentFile) { "Archive target has no parent folder" }
    var backup: File
    do {
        backup = File(parent, ".${target.name}.arcile-replace-${UUID.randomUUID()}.tmp")
    } while (backup.exists())
    target.copyTo(backup, overwrite = false)
    replacementBackups[target] = backup
}

internal fun cleanupReplacementBackups(replacementBackups: Map<File, File>) {
    replacementBackups.values.forEach { backup ->
        runCatching {
            if (backup.exists()) backup.delete()
        }
    }
}

internal fun restoreReplacementBackups(replacementBackups: Map<File, File>) {
    replacementBackups.entries.toList().asReversed().forEach { (target, backup) ->
        runCatching {
            if (backup.exists()) {
                backup.copyTo(target, overwrite = true)
                backup.delete()
            }
        }
    }
}

internal fun archiveSourceEntries(
    sources: List<File>,
    validateMutationPath: (File) -> Result<Unit>
): Flow<ArchiveSourceEntry> = flow {
    traverseArchiveSources(sources, validateMutationPath) { entry ->
        emit(entry)
        true
    }
}

internal suspend fun traverseArchiveSources(
    sources: List<File>,
    validateMutationPath: (File) -> Result<Unit>,
    onEntry: suspend (ArchiveSourceEntry) -> Boolean
) {
    for (source in sources) {
        currentCoroutineContext().ensureActive()
        validateMutationPath(source).getOrThrow()
        require(source.exists()) { "File does not exist: ${source.name}" }
        if (!onEntry(ArchiveSourceEntry(source, source))) return
        if (source.isDirectory) {
            val iterator = source.walkTopDown().drop(1).iterator()
            while (iterator.hasNext()) {
                currentCoroutineContext().ensureActive()
                if (!onEntry(ArchiveSourceEntry(source, iterator.next()))) return
            }
        }
    }
}

internal suspend fun scanArchiveCreationEntries(
    sources: List<File>,
    validateMutationPath: (File) -> Result<Unit>,
    safetyPolicy: ArchiveSafetyPolicy
): ArchiveCreationScan {
    val safety = ArchiveSafetyTally(safetyPolicy)
    val entries = mutableListOf<ArchiveSourceEntry>()
    var totalBytes = 0L
    var totalItems = 0
    var exceededLimit = false
    traverseArchiveSources(sources, validateMutationPath) { entry ->
        currentCoroutineContext().ensureActive()
        safety.accept(
            rawName = archiveEntryName(entry.sourceRoot, entry.file),
            uncompressedSize = if (entry.file.isFile) entry.file.length() else 0L,
            compressedSize = null
        )
        totalItems += 1
        if (entry.file.isFile) {
            totalBytes = Math.addExact(totalBytes, entry.file.length())
        }
        if (totalItems > ARCHIVE_CREATION_PRE_SCAN_MAX_ENTRIES || totalBytes > ARCHIVE_CREATION_PRE_SCAN_MAX_BYTES) {
            exceededLimit = true
            return@traverseArchiveSources false
        }
        entries += entry
        true
    }
    return if (exceededLimit) {
        ArchiveCreationScan(entries = null, totalItems = 0, totalBytes = null)
    } else {
        ArchiveCreationScan(entries = entries, totalItems = totalItems, totalBytes = totalBytes.coerceAtLeast(1L))
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

internal fun ArchiveNameEncoding.charset(): Charset = Charset.forName(charsetName)

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
