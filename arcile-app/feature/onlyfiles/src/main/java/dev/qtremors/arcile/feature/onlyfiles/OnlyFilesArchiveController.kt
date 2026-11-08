package dev.qtremors.arcile.feature.onlyfiles

import dev.qtremors.arcile.core.storage.domain.ArchiveFormat
import dev.qtremors.arcile.core.storage.domain.ArchiveNameEncoding
import dev.qtremors.arcile.core.storage.domain.ArchiveCompressionLevel
import dev.qtremors.arcile.core.archive.ArchiveSafetyPolicy
import dev.qtremors.arcile.core.archive.TarArchiveWriter
import dev.qtremors.arcile.core.archive.SevenZipArchiveWriter
import dev.qtremors.arcile.core.archive.ZipArchiveWriter
import dev.qtremors.arcile.core.vault.domain.ArchiveRejection
import dev.qtremors.arcile.core.vault.domain.DirectoryId
import dev.qtremors.arcile.core.vault.domain.VAULT_ARCHIVE_MAX_BYTES
import dev.qtremors.arcile.core.vault.domain.VAULT_ARCHIVE_MAX_ENTRIES
import dev.qtremors.arcile.core.vault.domain.VaultArchiveProgress
import dev.qtremors.arcile.core.vault.domain.VaultBatchResult
import dev.qtremors.arcile.core.vault.domain.VaultArchiveDestinationResult
import dev.qtremors.arcile.core.vault.domain.VaultId
import dev.qtremors.arcile.core.vault.domain.VaultFailure
import dev.qtremors.arcile.core.vault.domain.VaultFileSystem
import dev.qtremors.arcile.core.vault.domain.VaultListOptions
import dev.qtremors.arcile.core.vault.domain.VaultNodeMetadata
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.zip.Deflater
import java.io.FilterInputStream
import java.io.InputStream
import java.nio.channels.Channels
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.sevenz.SevenZArchiveEntry
import java.util.Date

internal data class VaultZipSelection(
    val folder: List<String>,
    val selected: Set<List<String>>,
    val requiresPassword: Boolean = false,
    val encoding: ArchiveNameEncoding = ArchiveNameEncoding.UTF_8
) {
    fun relativePath(segments: List<String>): List<String>? {
        if (segments.isEmpty() || segments.size < folder.size || segments.take(folder.size) != folder) return null
        if (selected.isNotEmpty() && selected.none { segments.take(it.size) == it }) return null
        return segments // Browser preserves the archive hierarchy for both full and selected extraction.
    }

}

internal enum class VaultArchiveAction { CREATE, EXTRACT }

internal data class OnlyFilesArchiveProgress(val action: VaultArchiveAction, val progress: VaultArchiveProgress?)

internal class OnlyFilesArchiveController(
    private val fileSystem: VaultFileSystem,
    private val state: MutableStateFlow<OnlyFilesUiState>,
    private val scope: CoroutineScope,
    private val reload: () -> Unit,
    private val showError: (Throwable) -> Unit,
    private val onFoldersDeleted: suspend (VaultId, Set<DirectoryId>) -> Unit = { _, _ -> }
) {
    private var operation: Job? = null
    val conflictReview = OnlyFilesConflictReview(state)

    fun create(
        nodes: List<VaultNodeMetadata>, name: String, level: Int, successMessage: String, password: CharArray? = null
    ) {
        val snapshot = state.value
        val vault = snapshot.selectedVaultId
        val parent = snapshot.currentDirectory?.id
        if (vault == null || parent == null || nodes.isEmpty() ||
            nodes.any { it.ref.vaultId != vault || !it.ref.capabilities.canRead }
        ) {
            password?.fill('\u0000')
            return
        }
        runArchive(VaultArchiveAction.CREATE, successMessage, password) { progress ->
            val items = planZip(nodes)
            val coroutine = currentCoroutineContext()
            val format = ArchiveFormat.fromPath(name) ?: ArchiveFormat.ZIP
            if (format == ArchiveFormat.SEVEN_Z) {
                return@runArchive fileSystem.createSeekableArchiveFile(vault, parent, name, "application/x-7z-compressed") { channel ->
                    SevenZipArchiveWriter.open(channel, password, level.toArchiveCompressionLevel(),
                        ArchiveSafetyPolicy(maxEntries = VAULT_ARCHIVE_MAX_ENTRIES, maxUncompressedBytes = VAULT_ARCHIVE_MAX_BYTES),
                        closeChannel = false).use { archive ->
                        items.forEachIndexed { index, item ->
                            coroutine.ensureActive()
                            progress(VaultArchiveProgress(index, items.size, item.node.name))
                            val entry = SevenZArchiveEntry().apply {
                                this.name = item.path
                                isDirectory = item.node.isDirectory
                                size = if (isDirectory) 0L else item.node.sizeBytes
                                lastModifiedDate = Date(item.node.modifiedAtMillis)
                            }
                            archive.add(entry, { coroutine.ensureActive() }) { openArchiveInput(item.node) { coroutine.ensureActive() } }
                            progress(VaultArchiveProgress(index + 1, items.size, null))
                        }
                    }
                }.getOrThrow()
            }
            if (format != ArchiveFormat.ZIP) {
                require(format in TarArchiveWriter.formats && password?.isNotEmpty() != true) { "Unsupported archive creation options" }
                val mime = when (format) {
                    ArchiveFormat.TAR -> "application/x-tar"
                    ArchiveFormat.TAR_GZIP, ArchiveFormat.TGZ -> "application/gzip"
                    ArchiveFormat.TAR_BZIP2, ArchiveFormat.TBZ2 -> "application/x-bzip2"
                    else -> "application/x-xz"
                }
                return@runArchive fileSystem.createArchiveFile(vault, parent, name, mime) { output ->
                    TarArchiveWriter.open(output, format, level.toArchiveCompressionLevel(),
                        ArchiveSafetyPolicy(maxEntries = VAULT_ARCHIVE_MAX_ENTRIES, maxUncompressedBytes = VAULT_ARCHIVE_MAX_BYTES)).use { tar ->
                        items.forEachIndexed { index, item ->
                            coroutine.ensureActive()
                            progress(VaultArchiveProgress(index, items.size, item.node.name))
                            val entry = TarArchiveEntry(item.path).apply {
                                size = if (item.node.isDirectory) 0L else item.node.sizeBytes
                                setModTime(item.node.modifiedAtMillis)
                            }
                            tar.add(entry, { coroutine.ensureActive() }) { openArchiveInput(item.node) { coroutine.ensureActive() } }
                            progress(VaultArchiveProgress(index + 1, items.size, null))
                        }
                    }
                }.getOrThrow()
            }
            fileSystem.createZip(vault, parent, name) { output ->
                ZipArchiveWriter.open(output, password, level = level.toArchiveCompressionLevel(),
                    policy = ArchiveSafetyPolicy(maxEntries = VAULT_ARCHIVE_MAX_ENTRIES,
                        maxUncompressedBytes = VAULT_ARCHIVE_MAX_BYTES)).use { zip ->
                    items.forEachIndexed { index, item ->
                        coroutine.ensureActive()
                        progress(VaultArchiveProgress(index, items.size, item.node.name))
                        zip.add(item.path, if (item.node.isDirectory) 0L else item.node.sizeBytes,
                            item.node.isDirectory, item.node.modifiedAtMillis, { coroutine.ensureActive() }) {
                            openArchiveInput(item.node) { coroutine.ensureActive() }
                        }
                        progress(VaultArchiveProgress(index + 1, items.size, null))
                    }
                }
            }.getOrThrow()
        }
    }

    fun extract(
        node: VaultNodeMetadata,
        parent: DirectoryId,
        selection: VaultZipSelection,
        name: String,
        password: CharArray? = null,
        intoExisting: Boolean = false
    ) {
        val format = ArchiveFormat.fromPath(node.name)
        if (state.value.selectedVaultId != node.ref.vaultId || !node.ref.capabilities.canRead ||
            format?.canBrowse != true
        ) {
            password?.fill('\u0000')
            return
        }
        // A named folder is reused when it already exists, matching Browser; entries then go through conflict review.
        extractInto(node, parent, selection, password, name.takeUnless { intoExisting })
    }

    fun cancel() {
        operation?.cancel()
        conflictReview.dismiss()
    }

    private fun extractInto(node: VaultNodeMetadata, parent: DirectoryId, selection: VaultZipSelection,
        password: CharArray?, folderName: String?) {
        val mount = state.value.mountGeneration
        val conflicts = conflictReview.resolver(mount, exporting = false) { operation?.isCancelled == true }
        runArchiveOperation(VaultArchiveAction.EXTRACT, password, execute = { progress ->
            readVaultArchive(node, fileSystem::openReader, password, selection.encoding) { archive ->
                if (folderName != null) fileSystem.extractArchiveToNamedFolder(node.ref.vaultId, parent, folderName,
                    archive.vaultSources(selection), conflicts, progress).getOrThrow()
                else VaultArchiveDestinationResult(parent, fileSystem.extractArchiveInto(node.ref.vaultId, parent,
                    archive.vaultSources(selection), conflicts, progress).getOrThrow())
            }
        }) { completed: VaultArchiveDestinationResult, completedMount ->
            val result = completed.result
            val removed = result.items.flatMapTo(mutableSetOf()) { it.removedFolders }
            if (removed.isNotEmpty()) onFoldersDeleted(node.ref.vaultId, removed)
            // Replacing the archive itself is a valid conflict choice. Do not retain its stale reader UI.
            val sourceAvailable = if (state.value.mountGeneration == completedMount && state.value.viewer?.ref == node.ref) {
                withContext(Dispatchers.IO) { fileSystem.openReader(node.ref).getOrNull()?.use { true } ?: false }
            } else true
            state.update { current ->
                if (current.mountGeneration != completedMount) current else current.copy(
                    selectedNodeIds = emptySet(), message = null, completedArchive = null,
                    viewer = current.viewer.takeIf { sourceAvailable || it?.ref != node.ref },
                    viewerQueue = if (!sourceAvailable && current.viewer?.ref == node.ref) emptyList() else current.viewerQueue,
                    viewerSelectionEnabled = current.viewerSelectionEnabled && sourceAvailable,
                    operationFeedback = vaultArchiveFeedback(result, completedMount,
                        OnlyFilesOperationDestination.VaultDirectory(node.ref.vaultId, completed.directory))
                )
            }
        }
    }

    private fun openArchiveInput(node: VaultNodeMetadata, checkActive: () -> Unit): InputStream {
        val reader = fileSystem.openReader(node.ref).getOrThrow()
        try {
            if (reader.sizeBytes != node.sizeBytes) throw VaultFailure.SourceChanged()
            return object : FilterInputStream(Channels.newInputStream(VaultArchiveChannel(reader, node.sizeBytes, checkActive))) {
                override fun close() { try { super.close() } finally { reader.close() } }
            }
        } catch (error: Throwable) { reader.close(); throw error }
    }

    private fun runArchive(
        action: VaultArchiveAction,
        successMessage: String,
        password: CharArray?,
        execute: suspend ((VaultArchiveProgress) -> Unit) -> VaultNodeMetadata
    ) = runArchiveOperation(action, password, execute) { completed, mount ->
        state.update {
            if (it.mountGeneration != mount) it else it.copy(selectedNodeIds = emptySet(), message = successMessage,
                completedArchive = completed)
        }
    }

    private fun <T> runArchiveOperation(
        action: VaultArchiveAction,
        password: CharArray?,
        execute: suspend ((VaultArchiveProgress) -> Unit) -> T,
        onCompleted: suspend (T, Long) -> Unit
    ) {
        val snapshot = state.value
        if (snapshot.selectedVaultId == null || snapshot.busy || operation != null) {
            password?.fill('\u0000')
            return
        }
        val mount = snapshot.mountGeneration
        state.update {
            it.copy(busy = true, message = null, operationFeedback = null, completedArchive = null,
                archiveProgress = OnlyFilesArchiveProgress(action, null))
        }
        val job = scope.launch(start = CoroutineStart.LAZY) {
            try {
                val completed = execute { progress ->
                    state.update {
                        if (it.mountGeneration != mount) it
                        else it.copy(archiveProgress = OnlyFilesArchiveProgress(action, progress))
                    }
                }
                if (state.value.mountGeneration == mount) {
                    onCompleted(completed, mount)
                    reload()
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (state.value.mountGeneration == mount) showError(error)
            }
        }
        operation = job
        job.invokeOnCompletion {
            password?.fill('\u0000')
            if (operation === job) {
                operation = null
                state.update { if (it.mountGeneration == mount) it.copy(busy = false, archiveProgress = null) else it }
            }
        }
        job.start()
    }

    private suspend fun planZip(nodes: List<VaultNodeMetadata>): List<ZipSource> {
        val sources = mutableListOf<ZipSource>()
        val visited = mutableSetOf<DirectoryId>()
        var totalBytes = 0L

        suspend fun add(node: VaultNodeMetadata, parentPath: String) {
            currentCoroutineContext().ensureActive()
            if (sources.size >= VAULT_ARCHIVE_MAX_ENTRIES || node.sizeBytes > VAULT_ARCHIVE_MAX_BYTES - totalBytes) {
                throw VaultFailure.ArchiveRejected(ArchiveRejection.TOO_MANY_ENTRIES)
            }
            if (safeZipSegments(node.name) != listOf(node.name) || parentPath.count { it == '/' } >= 64) {
                throw VaultFailure.InvalidPath("A selected file name cannot be stored in a ZIP")
            }
            val path = parentPath + node.name + if (node.isDirectory) "/" else ""
            sources += ZipSource(node, path)
            totalBytes += node.sizeBytes
            val directory = node.ref.directoryId ?: return
            if (!visited.add(directory)) throw VaultFailure.InvalidPath("A folder occurs more than once in the selection")
            var token: String? = null
            do {
                val page = fileSystem.listDirectory(node.ref.vaultId, directory, VaultListOptions(pageToken = token)).getOrThrow()
                for (child in page.items) add(child, path)
                token = page.nextPageToken
            } while (token != null)
        }

        if (nodes.map { it.name.lowercase() }.distinct().size != nodes.size) {
            throw VaultFailure.ArchiveRejected(ArchiveRejection.DUPLICATE_NAMES)
        }
        nodes.forEach { add(it, "") }
        return sources
    }

    private data class ZipSource(val node: VaultNodeMetadata, val path: String)
}

private fun Int.toArchiveCompressionLevel(): ArchiveCompressionLevel = when (this) {
    Deflater.NO_COMPRESSION -> ArchiveCompressionLevel.STORE
    Deflater.BEST_SPEED -> ArchiveCompressionLevel.FAST
    Deflater.BEST_COMPRESSION -> ArchiveCompressionLevel.MAXIMUM
    else -> ArchiveCompressionLevel.DEFAULT
}
