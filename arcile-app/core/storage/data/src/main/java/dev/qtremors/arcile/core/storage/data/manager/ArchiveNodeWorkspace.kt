package dev.qtremors.arcile.core.storage.data.manager

import dev.qtremors.arcile.core.operation.BulkFileOperationProgress
import dev.qtremors.arcile.core.privilege.PrivilegedFileHandle
import dev.qtremors.arcile.core.privilege.PrivilegedFileFailure
import dev.qtremors.arcile.core.privilege.PrivilegedOpenMode
import dev.qtremors.arcile.core.storage.data.rethrowIfCancellation
import dev.qtremors.arcile.core.storage.data.source.FileSystemDataSource
import dev.qtremors.arcile.core.storage.data.source.PrivilegedFileSystemDataSource
import dev.qtremors.arcile.core.storage.domain.ArchiveEntryModel
import dev.qtremors.arcile.core.storage.domain.ConflictResolution
import dev.qtremors.arcile.core.storage.domain.FileConflict
import dev.qtremors.arcile.core.storage.domain.FileModel
import dev.qtremors.arcile.core.storage.domain.StorageNodeCapabilities
import dev.qtremors.arcile.core.storage.domain.StorageNodeRef
import dev.qtremors.arcile.core.storage.domain.isPrivileged
import java.io.Closeable
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/**
 * Private, short-lived local workspace used only by archive codecs that require a
 * seekable file. Protected paths are always copied through an already-authorized
 * descriptor; the workspace never attempts to open their display paths directly.
 */
internal class ArchiveWorkspace private constructor(
    val directory: File
) : Closeable {
    val archiveInput: File get() = File(directory, "archive-input")
    val archiveOutput: File get() = File(directory, "archive-output")
    val sourceRoot: File get() = File(directory, "sources")
    val extractionRoot: File get() = File(directory, "extracted")

    fun archiveInputWithExtension(path: String): File =
        File(directory, "archive-input${archiveExtension(path)}")

    fun archiveOutputWithExtension(path: String): File =
        File(directory, "archive-output${archiveExtension(path)}")

    override fun close() {
        if (directory.exists() && !directory.deleteRecursively()) {
            throw IllegalStateException("Could not remove private archive workspace")
        }
    }

    companion object {
        fun create(root: File): ArchiveWorkspace {
            require(root.mkdirs() || root.isDirectory) {
                "Archive workspace root is unavailable"
            }
            var directory: File
            do {
                directory = File(root, "workspace-${UUID.randomUUID()}")
            } while (directory.exists())
            require(directory.mkdirs()) { "Could not create archive workspace" }
            return ArchiveWorkspace(directory)
        }

        fun removeAbandoned(root: File, olderThanMillis: Long, nowMillis: Long = System.currentTimeMillis()) {
            if (!root.isDirectory) return
            root.listFiles().orEmpty()
                .filter { it.isDirectory && it.name.startsWith("workspace-") }
                .filter { nowMillis - it.lastModified() >= olderThanMillis }
                .forEach { it.deleteRecursively() }
        }

        private fun archiveExtension(path: String): String {
            val normalized = path.lowercase()
            val supported = listOf(
                ".tar.gz", ".tar.bz2", ".tar.xz", ".tgz", ".tbz2", ".txz",
                ".zip", ".7z", ".tar", ".gz", ".bz2", ".xz"
            )
            return supported.firstOrNull(normalized::endsWith).orEmpty()
        }
    }
}

internal interface ArchiveNodeIo {
    suspend fun inspect(node: StorageNodeRef): FileModel
    suspend fun children(directory: StorageNodeRef): List<FileModel>
    suspend fun createDirectory(parent: StorageNodeRef, name: String): FileModel
    suspend fun createFile(parent: StorageNodeRef, name: String): FileModel
    suspend fun rename(node: StorageNodeRef, newName: String): FileModel
    suspend fun delete(node: StorageNodeRef)
    suspend fun openInput(node: StorageNodeRef): ArchiveInput
    suspend fun openOutput(node: StorageNodeRef): ArchiveOutput
}

internal class ArchiveInput(
    val stream: InputStream,
    private val owner: Closeable = stream
) : Closeable {
    override fun close() = owner.close()
}

internal class ArchiveOutput(
    val stream: OutputStream,
    private val owner: Closeable = stream
) : Closeable {
    override fun close() = owner.close()
}

internal class DefaultArchiveNodeIo(
    private val fileSystem: FileSystemDataSource,
    private val privileged: PrivilegedFileSystemDataSource
) : ArchiveNodeIo {
    override suspend fun inspect(node: StorageNodeRef): FileModel =
        fileSystem.inspectNode(node).getOrThrow()

    override suspend fun children(directory: StorageNodeRef): List<FileModel> =
        fileSystem.listNodeFiles(directory).getOrThrow()

    override suspend fun createDirectory(parent: StorageNodeRef, name: String): FileModel =
        fileSystem.createNodeDirectory(parent, name).getOrThrow()

    override suspend fun createFile(parent: StorageNodeRef, name: String): FileModel =
        fileSystem.createNodeFile(parent, name).getOrThrow()

    override suspend fun rename(node: StorageNodeRef, newName: String): FileModel =
        fileSystem.renameNode(node, newName).getOrThrow()

    override suspend fun delete(node: StorageNodeRef) {
        fileSystem.deleteNodesPermanently(listOf(node)).getOrThrow()
    }

    override suspend fun openInput(node: StorageNodeRef): ArchiveInput = when {
        node.isPrivileged -> privileged.openNode(node, PrivilegedOpenMode.READ).getOrThrow().asArchiveInput()
        node.backendId == StorageNodeRef.LOCAL_BACKEND_ID -> ArchiveInput(
            FileInputStream(node.displayPath.absolutePath)
        )
        else -> throw IllegalArgumentException("Archive reading is unavailable for ${node.backendId}")
    }

    override suspend fun openOutput(node: StorageNodeRef): ArchiveOutput = when {
        node.isPrivileged -> privileged.openNode(node, PrivilegedOpenMode.WRITE_TRUNCATE).getOrThrow().asArchiveOutput()
        node.backendId == StorageNodeRef.LOCAL_BACKEND_ID -> ArchiveOutput(
            FileOutputStream(node.displayPath.absolutePath, false)
        )
        else -> throw IllegalArgumentException("Archive writing is unavailable for ${node.backendId}")
    }

    private fun PrivilegedFileHandle.asArchiveInput(): ArchiveInput =
        ArchiveInput(requireNotNull(input) { "Privileged archive handle has no input stream" }, this)

    private fun PrivilegedFileHandle.asArchiveOutput(): ArchiveOutput =
        ArchiveOutput(requireNotNull(output) { "Privileged archive handle has no output stream" }, this)
}

internal class ArchiveNodeWorkspaceBridge(
    private val io: ArchiveNodeIo,
    private val workspaceRoot: File,
    private val safetyPolicy: ArchiveSafetyPolicy = ArchiveSafetyPolicy()
) {
    init {
        ArchiveWorkspace.removeAbandoned(
            root = workspaceRoot,
            olderThanMillis = ABANDONED_WORKSPACE_AGE_MILLIS
        )
    }

    suspend fun <T> withWorkspace(block: suspend (ArchiveWorkspace) -> T): T =
        ArchiveWorkspace.create(workspaceRoot).use { workspace -> block(workspace) }

    suspend fun stageArchive(archive: StorageNodeRef, workspace: ArchiveWorkspace): File {
        require(archive.capabilities.canRead) { "Archive is not readable" }
        require(archive.capabilities.canArchive) { "This file cannot be opened as an archive" }
        val metadata = io.inspect(archive)
        require(!metadata.isDirectory) { "Archive is not available" }
        require(metadata.nodeRef.capabilities.canRead) { "Archive is not readable" }
        val target = workspace.archiveInputWithExtension(metadata.absolutePath)
        copyNodeToFile(
            source = archive,
            target = target,
            expectedBytes = metadata.size,
            budget = ArchiveWorkspaceBudget(safetyPolicy.maxUncompressedBytes)
        )
        return target
    }

    suspend fun stageSources(
        sources: Collection<StorageNodeRef>,
        workspace: ArchiveWorkspace,
        onProgress: ((BulkFileOperationProgress) -> Unit)? = null
    ): List<File> {
        require(sources.isNotEmpty()) { "Select at least one item to archive" }
        require(workspace.sourceRoot.mkdirs() || workspace.sourceRoot.isDirectory) {
            "Could not create archive source workspace"
        }
        val roots = mutableListOf<File>()
        val usedNames = mutableSetOf<String>()
        val workspaceBudget = ArchiveWorkspaceBudget(safetyPolicy.maxUncompressedBytes)
        sources.forEachIndexed { index, source ->
            currentCoroutineContext().ensureActive()
            val model = io.inspect(source)
            require(model.nodeRef.capabilities.canRead) { "${model.name} is not readable" }
            require(model.nodeRef.capabilities.canArchive) { "${model.name} cannot be archived" }
            val stagedName = uniqueLocalName(model.name, usedNames)
            val target = File(workspace.sourceRoot, stagedName)
            stageTree(
                source = model.nodeRef,
                model = model,
                target = target,
                progressIndex = index,
                totalRoots = sources.size,
                workspaceBudget = workspaceBudget,
                onProgress = onProgress
            )
            roots += target
        }
        return roots
    }

    suspend fun publishArchive(
        stagedArchive: File,
        destinationArchive: StorageNodeRef,
        onProgress: ((BulkFileOperationProgress) -> Unit)? = null
    ): StorageNodeRef {
        require(stagedArchive.isFile) { "Created archive is unavailable" }
        val parent = destinationArchive.parentReference()
        val name = destinationArchive.displayName()
        require(name.isNotBlank()) { "Archive name is missing" }
        val existing = findChild(parent, name)
        require(existing == null) { "Archive already exists" }
        val partialName = uniqueRemoteName(parent, ".$name.arcile-archive", ".partial")
        val partial = io.createFile(parent, partialName).nodeRef
        try {
            copyFileToNode(stagedArchive, partial) { bytes ->
                onProgress?.invoke(
                    BulkFileOperationProgress(
                        completedItems = 0,
                        totalItems = 1,
                        currentPath = destinationArchive.displayPath.absolutePath,
                        bytesCopied = bytes,
                        totalBytes = stagedArchive.length()
                    )
                )
            }
            val published = io.rename(partial, name).nodeRef
            onProgress?.invoke(
                BulkFileOperationProgress(
                    completedItems = 1,
                    totalItems = 1,
                    currentPath = published.displayPath.absolutePath,
                    bytesCopied = stagedArchive.length(),
                    totalBytes = stagedArchive.length()
                )
            )
            return published
        } catch (error: Throwable) {
            error.rethrowIfCancellation()
            runCatching { io.delete(partial) }
            throw error
        }
    }

    suspend fun publishExtraction(
        extractedRoot: File,
        destination: StorageNodeRef,
        resolutions: Map<String, ConflictResolution>,
        onProgress: ((BulkFileOperationProgress) -> Unit)? = null
    ) {
        require(extractedRoot.isDirectory) { "Archive extraction workspace is unavailable" }
        val destinationDirectory = ensureDirectory(destination)
        val entries = extractedRoot.walkTopDown().drop(1).toList()
        val files = entries.filter(File::isFile)
        val totalBytes = files.sumOf(File::length).coerceAtLeast(1L)
        val transaction = ArchivePublishTransaction(io)
        val aliases = linkedMapOf<String, String>()
        val skippedDirectories = linkedSetOf<String>()
        var completed = 0
        var copied = 0L
        try {
            entries.forEach { staged ->
                currentCoroutineContext().ensureActive()
                val rawRelative = staged.relativeTo(extractedRoot).invariantSeparatorsPath
                if (skippedDirectories.any { rawRelative == it || rawRelative.startsWith("$it/") }) {
                    return@forEach
                }
                val effectiveRelative = applyAlias(rawRelative, aliases)
                val parentRelative = effectiveRelative.substringBeforeLast('/', "")
                val parent = ensureRelativeDirectory(
                    destinationDirectory,
                    parentRelative,
                    transaction
                )
                val requestedName = effectiveRelative.substringAfterLast('/')
                val existing = findChild(parent, requestedName)
                val resolution = resolutions[rawRelative]
                    ?: resolutions[effectiveRelative]
                    ?: ConflictResolution.KEEP_BOTH
                val targetName = when {
                    existing == null -> requestedName
                    resolution == ConflictResolution.SKIP -> {
                        if (staged.isDirectory) skippedDirectories += rawRelative
                        return@forEach
                    }
                    resolution == ConflictResolution.REPLACE -> {
                        transaction.backup(existing.nodeRef)
                        requestedName
                    }
                    else -> uniqueRemoteName(
                        parent,
                        requestedName.substringBeforeLast('.', requestedName) + " (",
                        requestedName.substringAfterLast('.', "").let { suffix ->
                            if (suffix.isBlank()) ")" else ").$suffix"
                        },
                        numbered = true
                    )
                }
                if (staged.isDirectory) {
                    val created = io.createDirectory(parent, targetName)
                    transaction.created(created.nodeRef)
                    if (targetName != requestedName) {
                        val parentPrefix = rawRelative.substringBeforeLast('/', "")
                        aliases[rawRelative] = listOf(parentPrefix, targetName)
                            .filter(String::isNotBlank)
                            .joinToString("/")
                    }
                } else {
                    val created = io.createFile(parent, targetName)
                    transaction.created(created.nodeRef)
                    copyFileToNode(staged, created.nodeRef) { delta ->
                        onProgress?.invoke(
                            BulkFileOperationProgress(
                                completedItems = completed,
                                totalItems = files.size,
                                currentPath = rawRelative,
                                bytesCopied = copied + delta,
                                totalBytes = totalBytes
                            )
                        )
                    }
                    copied += staged.length()
                    completed += 1
                    onProgress?.invoke(
                        BulkFileOperationProgress(
                            completedItems = completed,
                            totalItems = files.size,
                            currentPath = rawRelative,
                            bytesCopied = copied.coerceAtMost(totalBytes),
                            totalBytes = totalBytes
                        )
                    )
                }
            }
            transaction.commit()
        } catch (error: Throwable) {
            error.rethrowIfCancellation()
            transaction.rollback()
            throw error
        }
    }

    suspend fun detectConflicts(
        archiveEntries: List<ArchiveEntryModel>,
        destination: StorageNodeRef,
        entryPrefix: String?
    ): List<FileConflict> {
        val destinationDirectory = inspectDirectoryIfPresent(destination) ?: return emptyList()
        val conflicts = mutableListOf<FileConflict>()
        for (entry in archiveEntries) {
            if (entry.isDirectory || !entry.path.matchesPrefix(entryPrefix)) continue
            val existing = findRelative(destinationDirectory, entry.path) ?: continue
            conflicts += FileConflict(
                    sourcePath = entry.path,
                    sourceFile = FileModel(
                        name = entry.name,
                        absolutePath = entry.path,
                        size = entry.size,
                        lastModified = entry.lastModified ?: 0L,
                        isDirectory = false,
                        extension = entry.name.substringAfterLast('.', "").lowercase(),
                        isHidden = entry.name.startsWith('.')
                    ),
                    existingFile = existing
                )
        }
        return conflicts
    }

    private suspend fun stageTree(
        source: StorageNodeRef,
        model: FileModel,
        target: File,
        progressIndex: Int,
        totalRoots: Int,
        workspaceBudget: ArchiveWorkspaceBudget,
        onProgress: ((BulkFileOperationProgress) -> Unit)?
    ) {
        currentCoroutineContext().ensureActive()
        require(model.nodeRef.capabilities.canRead) { "${model.name} is not readable" }
        require(model.nodeRef.capabilities.canArchive) { "${model.name} cannot be archived" }
        if (model.isDirectory) {
            require(target.mkdirs() || target.isDirectory) { "Could not stage ${model.name}" }
            io.children(source).forEach { child ->
                stageTree(
                    source = child.nodeRef,
                    model = child,
                    target = File(target, child.name),
                    progressIndex = progressIndex,
                    totalRoots = totalRoots,
                    workspaceBudget = workspaceBudget,
                    onProgress = onProgress
                )
            }
        } else {
            copyNodeToFile(source, target, model.size, workspaceBudget) { bytes ->
                onProgress?.invoke(
                    BulkFileOperationProgress(
                        completedItems = progressIndex,
                        totalItems = totalRoots,
                        currentPath = model.absolutePath,
                        bytesCopied = bytes,
                        totalBytes = model.size
                    )
                )
            }
        }
    }

    private suspend fun copyNodeToFile(
        source: StorageNodeRef,
        target: File,
        expectedBytes: Long,
        budget: ArchiveWorkspaceBudget,
        onProgress: ((Long) -> Unit)? = null
    ) {
        target.parentFile?.let { require(it.mkdirs() || it.isDirectory) }
        io.openInput(source).use { input ->
            FileOutputStream(target).use { output ->
                copyChecked(input.stream, output, expectedBytes, budget, onProgress)
            }
        }
    }

    private suspend fun copyFileToNode(
        source: File,
        target: StorageNodeRef,
        onProgress: ((Long) -> Unit)? = null
    ) {
        FileInputStream(source).use { input ->
            io.openOutput(target).use { output ->
                copyChecked(input, output.stream, source.length(), null, onProgress)
                output.stream.flush()
            }
        }
    }

    private suspend fun copyChecked(
        input: InputStream,
        output: OutputStream,
        expectedBytes: Long,
        budget: ArchiveWorkspaceBudget?,
        onProgress: ((Long) -> Unit)?
    ) {
        val buffer = ByteArray(ARCHIVE_BUFFER_SIZE)
        var copied = 0L
        while (true) {
            currentCoroutineContext().ensureActive()
            val count = input.read(buffer)
            if (count < 0) break
            if (count == 0) continue
            budget?.accept(count)
            copied = Math.addExact(copied, count.toLong())
            require(copied <= safetyPolicy.maxUncompressedBytes) {
                "Archive workspace exceeds the configured safety limit"
            }
            output.write(buffer, 0, count)
            onProgress?.invoke(copied)
        }
        if (expectedBytes >= 0L) {
            require(copied == expectedBytes) {
                "Archive source changed while it was being read"
            }
        }
    }

    private suspend fun ensureDirectory(directory: StorageNodeRef): StorageNodeRef {
        val present = try {
            io.inspect(directory)
        } catch (error: Throwable) {
            if (!error.isMissingArchiveNode()) throw error
            null
        }
        if (present != null) {
            require(present.isDirectory) { "Destination must be a folder" }
            require(present.nodeRef.capabilities.canWrite) { "Destination is read-only" }
            return present.nodeRef
        }
        val parent = ensureDirectory(directory.parentReference())
        return io.createDirectory(parent, directory.displayName()).nodeRef
    }

    private suspend fun inspectDirectoryIfPresent(directory: StorageNodeRef): StorageNodeRef? {
        val model = try {
            io.inspect(directory)
        } catch (error: Throwable) {
            if (error.isMissingArchiveNode()) return null
            throw error
        }
        require(model.isDirectory) { "Destination must be a folder" }
        return model.nodeRef
    }

    private suspend fun ensureRelativeDirectory(
        root: StorageNodeRef,
        relativePath: String,
        transaction: ArchivePublishTransaction
    ): StorageNodeRef {
        if (relativePath.isBlank()) return root
        var current = root
        relativePath.split('/').filter(String::isNotBlank).forEach { segment ->
            validateEntrySegment(segment)
            val existing = findChild(current, segment)
            current = when {
                existing == null -> io.createDirectory(current, segment).also {
                    transaction.created(it.nodeRef)
                }.nodeRef
                existing.isDirectory -> existing.nodeRef
                else -> throw IllegalStateException("Archive target parent is not a folder: $segment")
            }
        }
        return current
    }

    private suspend fun findRelative(root: StorageNodeRef, relativePath: String): FileModel? {
        val segments = relativePath.normalizeEntryName().split('/').filter(String::isNotBlank)
        var current = root
        var found: FileModel? = null
        segments.forEachIndexed { index, segment ->
            validateEntrySegment(segment)
            found = findChild(current, segment) ?: return null
            if (index < segments.lastIndex) {
                if (found?.isDirectory != true) return null
                current = requireNotNull(found).nodeRef
            }
        }
        return found
    }

    private suspend fun findChild(parent: StorageNodeRef, name: String): FileModel? {
        validateEntrySegment(name)
        return io.children(parent).firstOrNull { it.name == name }
    }

    private suspend fun uniqueRemoteName(
        parent: StorageNodeRef,
        prefix: String,
        suffix: String,
        numbered: Boolean = false
    ): String {
        for (index in 1..10_000) {
            val candidate = if (numbered) "$prefix$index$suffix" else "$prefix-$index$suffix"
            if (findChild(parent, candidate) == null) return candidate
        }
        throw IllegalStateException("Unable to find an available archive target name")
    }

    private fun uniqueLocalName(requested: String, used: MutableSet<String>): String {
        validateEntrySegment(requested)
        if (used.add(requested)) return requested
        val base = requested.substringBeforeLast('.', requested)
        val extension = requested.substringAfterLast('.', "").let { if (it.isBlank()) "" else ".$it" }
        for (index in 1..10_000) {
            val candidate = "$base ($index)$extension"
            if (used.add(candidate)) return candidate
        }
        throw IllegalStateException("Unable to stage duplicate archive source names")
    }

    private fun validateEntrySegment(segment: String) {
        require(segment.isNotBlank()) { "Archive entry name is blank" }
        require(segment != "." && segment != "..") { "Archive entry contains traversal" }
        require('/' !in segment && '\\' !in segment && '\u0000' !in segment) {
            "Archive entry name is unsafe"
        }
    }

    private fun applyAlias(path: String, aliases: Map<String, String>): String {
        val match = aliases.keys
            .filter { path == it || path.startsWith("$it/") }
            .maxByOrNull(String::length)
            ?: return path
        val suffix = path.removePrefix(match).trimStart('/')
        return listOf(aliases.getValue(match), suffix).filter(String::isNotBlank).joinToString("/")
    }

    companion object {
        const val ABANDONED_WORKSPACE_AGE_MILLIS = 24L * 60L * 60L * 1_000L
    }
}

internal class ArchiveWorkspaceBudget(
    private val maximumBytes: Long
) {
    private var acceptedBytes = 0L

    fun accept(byteCount: Int) {
        require(byteCount >= 0) { "Archive workspace byte count cannot be negative" }
        acceptedBytes = try {
            Math.addExact(acceptedBytes, byteCount.toLong())
        } catch (_: ArithmeticException) {
            throw IllegalArgumentException("Archive workspace exceeds the configured safety limit")
        }
        require(acceptedBytes <= maximumBytes) {
            "Archive workspace exceeds the configured safety limit"
        }
    }
}

internal class ArchivePublishTransaction(
    private val io: ArchiveNodeIo
) {
    private val createdNodes = mutableListOf<StorageNodeRef>()
    private val replacements = mutableListOf<ArchiveReplacement>()

    fun created(node: StorageNodeRef) {
        createdNodes += node
    }

    suspend fun backup(node: StorageNodeRef) {
        val originalName = node.displayName()
        val backupName = ".$originalName.arcile-replace-${UUID.randomUUID()}.tmp"
        val backup = io.rename(node, backupName).nodeRef
        replacements += ArchiveReplacement(
            originalName = originalName,
            backup = backup
        )
    }

    suspend fun commit() {
        replacements.forEach { replacement -> io.delete(replacement.backup) }
        replacements.clear()
        createdNodes.clear()
    }

    suspend fun rollback() {
        createdNodes.asReversed().forEach { node -> runCatching { io.delete(node) } }
        replacements.asReversed().forEach { replacement ->
            runCatching { io.rename(replacement.backup, replacement.originalName) }
        }
        replacements.clear()
        createdNodes.clear()
    }
}

private data class ArchiveReplacement(
    val originalName: String,
    val backup: StorageNodeRef
)

internal fun StorageNodeRef.parentReference(): StorageNodeRef {
    val path = displayPath.absolutePath.trimEnd('/')
    val parent = path.substringBeforeLast('/', missingDelimiterValue = "/").ifBlank { "/" }
    require(parent != path) { "Storage root has no parent" }
    return referenceAt(parent)
}

internal fun StorageNodeRef.referenceAt(path: String): StorageNodeRef = when {
    isPrivileged -> StorageNodeRef.privileged(
        backendId = backendId,
        displayPath = path,
        remoteCanonicalIdentity = path,
        volumeId = volumeId?.value,
        capabilities = capabilities
    )
    backendId == StorageNodeRef.LOCAL_BACKEND_ID -> StorageNodeRef.local(
        path = path,
        volumeId = volumeId?.value,
        capabilities = capabilities
    )
    else -> copy(
        displayPath = dev.qtremors.arcile.core.storage.domain.StorageNodePath.of(path),
        backendIdentity = path
    )
}

internal fun StorageNodeRef.displayName(): String =
    displayPath.absolutePath.trimEnd('/').substringAfterLast('/')

private fun Throwable.isMissingArchiveNode(): Boolean =
    this is PrivilegedFileFailure.PathMissing ||
        message.orEmpty().contains("does not exist", ignoreCase = true) ||
        message.orEmpty().contains("path is missing", ignoreCase = true)
