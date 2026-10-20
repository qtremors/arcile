package dev.qtremors.arcile.feature.onlyfiles

import dev.qtremors.arcile.core.storage.domain.ArchiveFormat
import dev.qtremors.arcile.core.vault.domain.DirectoryId
import dev.qtremors.arcile.core.vault.domain.VAULT_ARCHIVE_MAX_BYTES
import dev.qtremors.arcile.core.vault.domain.VAULT_ARCHIVE_MAX_ENTRIES
import dev.qtremors.arcile.core.vault.domain.VaultArchiveProgress
import dev.qtremors.arcile.core.vault.domain.VaultArchiveSource
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
import net.lingala.zip4j.io.outputstream.ZipOutputStream
import net.lingala.zip4j.model.ZipParameters
import net.lingala.zip4j.model.enums.AesKeyStrength
import net.lingala.zip4j.model.enums.CompressionLevel
import net.lingala.zip4j.model.enums.CompressionMethod
import net.lingala.zip4j.model.enums.EncryptionMethod
import org.apache.commons.compress.archivers.zip.ZipFile
import java.util.zip.Deflater
import java.nio.channels.SeekableByteChannel

internal data class VaultZipSelection(
    val folder: List<String>,
    val selected: Set<List<String>>,
    val requiresPassword: Boolean = false
) {
    fun relativePath(segments: List<String>): List<String>? {
        if (segments.size <= folder.size || segments.take(folder.size) != folder) return null
        if (selected.isNotEmpty() && selected.none { segments.take(it.size) == it }) return null
        return segments.drop(folder.size)
    }

    fun safeRelativePath(name: String): List<String>? {
        val segments = safeZipSegments(name)
        if (segments != null) return relativePath(segments)
        if (folder.isEmpty() && selected.isEmpty()) {
            throw VaultFailure.InvalidPath("Archive contains an unsafe entry path")
        }
        return null
    }
}

internal enum class VaultArchiveAction { CREATE, EXTRACT }

internal data class OnlyFilesArchiveProgress(val action: VaultArchiveAction, val progress: VaultArchiveProgress?)

internal class OnlyFilesArchiveController(
    private val fileSystem: VaultFileSystem,
    private val state: MutableStateFlow<OnlyFilesUiState>,
    private val scope: CoroutineScope,
    private val reload: () -> Unit,
    private val showError: (Throwable) -> Unit
) {
    private var operation: Job? = null

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
            fileSystem.createZip(vault, parent, name) { output ->
                ZipOutputStream(output, password).use { zip ->
                    val buffer = ByteArray(32 * 1024)
                    try {
                        items.forEachIndexed { index, item ->
                            coroutine.ensureActive()
                            progress(VaultArchiveProgress(index, items.size, item.node.name))
                            val entry = ZipParameters().apply {
                                fileNameInZip = item.path
                                lastModifiedFileTime = item.node.modifiedAtMillis
                                entrySize = item.node.sizeBytes
                                compressionMethod = CompressionMethod.DEFLATE
                                compressionLevel = level.toZipCompressionLevel()
                                if (password != null && !item.node.isDirectory) {
                                    isEncryptFiles = true
                                    encryptionMethod = EncryptionMethod.AES
                                    aesKeyStrength = AesKeyStrength.KEY_STRENGTH_256
                                }
                            }
                            zip.putNextEntry(entry)
                            if (!item.node.isDirectory) {
                                fileSystem.openReader(item.node.ref).getOrThrow().use { reader ->
                                    if (reader.sizeBytes != item.node.sizeBytes) {
                                        throw VaultFailure.Unavailable("An archive source changed. Try again.")
                                    }
                                    var offset = 0L
                                    while (offset < reader.sizeBytes) {
                                        coroutine.ensureActive()
                                        val count = reader.readAt(offset, buffer, 0, buffer.size)
                                        if (count <= 0) throw VaultFailure.IntegrityFailed("An archive source ended early")
                                        zip.write(buffer, 0, count)
                                        offset += count
                                    }
                                }
                            }
                            zip.closeEntry()
                            progress(VaultArchiveProgress(index + 1, items.size, null))
                        }
                    } finally {
                        buffer.fill(0)
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
        successMessage: String,
        keepBoth: Boolean = false,
        password: CharArray? = null
    ) {
        val format = ArchiveFormat.fromPath(node.name)
        if (state.value.selectedVaultId != node.ref.vaultId || !node.ref.capabilities.canRead ||
            (format != ArchiveFormat.ZIP && format != ArchiveFormat.SEVEN_Z)
        ) {
            password?.fill('\u0000')
            return
        }
        runArchive(VaultArchiveAction.EXTRACT, successMessage, password) { progress ->
            withContext(Dispatchers.IO) {
                val coroutine = currentCoroutineContext()
                fileSystem.openReader(node.ref).getOrThrow().use { reader ->
                    if (reader.sizeBytes > VAULT_ARCHIVE_MAX_BYTES) {
                        throw VaultFailure.Unavailable("Archive exceeds the vault archive size limit")
                    }
                    VaultZipDirectoryChannel(reader, VAULT_ARCHIVE_MAX_BYTES * 2) { coroutine.ensureActive() }.use { channel ->
                        suspend fun publish(sources: List<VaultArchiveSource>): VaultNodeMetadata =
                            fileSystem.extractArchive(node.ref.vaultId, parent, name, sources, keepBoth, progress).getOrThrow()
                        if (format == ArchiveFormat.SEVEN_Z) {
                            openVaultSevenZ(channel).use { archive ->
                                publish(archive.vaultArchiveSources(selection) { coroutine.ensureActive() })
                            }
                        } else {
                            ZipFile.builder().setSeekableByteChannel(channel).setIgnoreLocalFileHeader(true).get().use { zip ->
                                publish(zip.vaultSources(channel, selection, password))
                            }
                        }
                    }
                }
            }
        }
    }

    private fun ZipFile.vaultSources(
        channel: SeekableByteChannel,
        selection: VaultZipSelection,
        password: CharArray?
    ): List<VaultArchiveSource> {
        val indexed = entries.asSequence().take(VAULT_ARCHIVE_MAX_ENTRIES + 1).toList()
        if (indexed.size > VAULT_ARCHIVE_MAX_ENTRIES) throw VaultFailure.Unavailable("ZIP has too many entries")
        return indexed.mapNotNull { entry ->
            val relative = selection.safeRelativePath(entry.name) ?: return@mapNotNull null
            val encrypted = entry.generalPurposeBit.usesEncryption()
            if (entry.isUnixSymlink || (!encrypted && !canReadEntryData(entry))) {
                throw VaultFailure.Unavailable("ZIP contains an unsupported entry")
            }
            when {
                entry.isDirectory -> VaultArchiveSource.Folder(relative)
                encrypted -> {
                    val secret = password ?: throw VaultFailure.Unavailable("This ZIP needs a password")
                    VaultArchiveSource.File(relative, entry.size, crc32 = null) {
                        openEncryptedVaultZipEntry(channel, entry, secret)
                    }
                }
                else -> VaultArchiveSource.File(relative, entry.size, entry.crc) { getInputStream(entry) }
            }
        }
    }

    fun cancel() {
        operation?.cancel()
    }

    private fun runArchive(
        action: VaultArchiveAction,
        successMessage: String,
        password: CharArray?,
        execute: suspend ((VaultArchiveProgress) -> Unit) -> VaultNodeMetadata
    ) {
        val snapshot = state.value
        if (snapshot.selectedVaultId == null || snapshot.busy || operation != null) {
            password?.fill('\u0000')
            return
        }
        val mount = snapshot.mountGeneration
        state.update {
            it.copy(busy = true, message = null, completedArchive = null, archiveProgress = OnlyFilesArchiveProgress(action, null))
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
                    state.update {
                        it.copy(selectedNodeIds = emptySet(), message = successMessage, completedArchive = completed)
                    }
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
                state.update { it.copy(busy = false, archiveProgress = null) }
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
                throw VaultFailure.Unavailable("Selection exceeds the vault archive limit")
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
            throw VaultFailure.InvalidPath("Selected items have duplicate archive names")
        }
        nodes.forEach { add(it, "") }
        return sources
    }

    private data class ZipSource(val node: VaultNodeMetadata, val path: String)
}

private fun Int.toZipCompressionLevel(): CompressionLevel = when (this) {
    Deflater.NO_COMPRESSION -> CompressionLevel.NO_COMPRESSION
    Deflater.BEST_SPEED -> CompressionLevel.FASTEST
    Deflater.BEST_COMPRESSION -> CompressionLevel.ULTRA
    else -> CompressionLevel.NORMAL
}
