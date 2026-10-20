package dev.qtremors.arcile.core.vault.data

import dev.qtremors.arcile.core.vault.crypto.VaultCryptography
import dev.qtremors.arcile.core.vault.crypto.VaultDirectoryManifestCodec
import dev.qtremors.arcile.core.vault.crypto.VaultFileCodec
import dev.qtremors.arcile.core.vault.crypto.VaultManifestEntry
import dev.qtremors.arcile.core.vault.domain.DirectoryId
import dev.qtremors.arcile.core.vault.domain.NodeId
import dev.qtremors.arcile.core.vault.domain.VAULT_ARCHIVE_MAX_BYTES
import dev.qtremors.arcile.core.vault.domain.VAULT_ARCHIVE_MAX_ENTRIES
import dev.qtremors.arcile.core.vault.domain.VAULT_ARCHIVE_MAX_FOLDERS
import dev.qtremors.arcile.core.vault.domain.VaultArchiveProgress
import dev.qtremors.arcile.core.vault.domain.VaultArchiveSource
import dev.qtremors.arcile.core.vault.domain.VaultFailure
import dev.qtremors.arcile.core.vault.domain.VaultName
import dev.qtremors.arcile.core.vault.domain.VaultNodeKind
import dev.qtremors.arcile.core.vault.domain.VaultNodeMetadata
import dev.qtremors.arcile.core.vault.domain.VaultObjectId
import java.io.FilterInputStream
import java.io.InputStream
import java.util.zip.CRC32

internal class VaultArchiveWriter(
    private val files: VaultFileCodec,
    private val directories: VaultDirectoryManifestCodec,
    private val transactions: VaultTransactionManager
) {
    fun extract(
        session: VaultSessionRecord,
        parentId: DirectoryId,
        name: String,
        sources: List<VaultArchiveSource>,
        keepBoth: Boolean,
        checkActive: () -> Unit,
        onProgress: (VaultArchiveProgress) -> Unit
    ): VaultNodeMetadata {
        val plan = ArchiveExtractionPlan(sources)
        val parent = session.resolveDirectory(parentId)
        val prepared = mutableListOf<VaultPreparedDirectory>()
        val newObjects = linkedSetOf<String>()
        val privateKeys = mutableListOf<ByteArray>()
        try {
            val snapshot = session.readDirectory(parent)
            try {
                val requested = normalizeMutationName(name)
                val comparisonKey = VaultName.of(requested).comparisonKey
                val exists = snapshot.entries.any { VaultName.of(it.name).comparisonKey == comparisonKey }
                val rootName = if (keepBoth && exists) uniqueName(requested, snapshot.entries) else requested
                ensureNameAvailable(snapshot, rootName)
                val writer = ArchiveTreeWriter(session, prepared, newObjects, privateKeys, plan.fileCount, checkActive, onProgress)
                // Solid archives expose entry streams in archive order. Build the folder manifests afterward.
                val writtenFiles = plan.files.associateWith(writer::writeFile)
                val root = writer.writeFolder(rootName, plan.root, writtenFiles)
                prepared += VaultPreparedDirectory(
                    directories.prepare(
                        session.id, parent.id, parent.key, snapshot.generation + 1L, snapshot.entries + root
                    ),
                    parent.key.copyOf()
                )
                checkActive()
                transactions.commit(
                    session.directory, session.id, session.masterSecret, prepared, newObjects, emptySet()
                )
                return root.toMetadata(session.id, parent.id)
            } finally {
                snapshot.clearProtectedKeys()
            }
        } catch (error: Throwable) {
            if (!transactions.hasPendingCommit(session.directory)) newObjects.forEach(session.directory::delete)
            throw error
        } finally {
            parent.key.fill(0)
            privateKeys.forEach { it.fill(0) }
            prepared.forEach { it.directoryKey.fill(0) }
        }
    }

    private inner class ArchiveTreeWriter(
        private val session: VaultSessionRecord,
        private val prepared: MutableList<VaultPreparedDirectory>,
        private val newObjects: MutableSet<String>,
        private val privateKeys: MutableList<ByteArray>,
        private val fileCount: Int,
        private val checkActive: () -> Unit,
        private val onProgress: (VaultArchiveProgress) -> Unit
    ) {
        private var completed = 0

        fun writeFolder(
            name: String,
            folder: ArchivePlanFolder,
            writtenFiles: Map<ArchivePlanFile, VaultManifestEntry>
        ): VaultManifestEntry {
            checkActive()
            val id = DirectoryId.random()
            val key = newKey()
            val entries = folder.children.values.map { entry ->
                when (entry) {
                    is ArchivePlanFolder -> writeFolder(entry.name, entry, writtenFiles)
                    is ArchivePlanFile -> writtenFiles.getValue(entry)
                }
            }
            prepared += VaultPreparedDirectory(
                directories.prepare(session.id, id, key, 0L, entries),
                key.copyOf()
            )
            return VaultManifestEntry(
                NodeId.random(), name, VaultNodeKind.DIRECTORY, 1L, System.currentTimeMillis(),
                0L, null, null, id, key
            )
        }

        fun writeFile(entry: ArchivePlanFile): VaultManifestEntry {
            checkActive()
            val source = entry.source
            val id = VaultObjectId.fromRandomBytes(VaultCryptography.randomBytes(32))
            val objectPath = id.shardedPath()
            val key = newKey()
            onProgress(VaultArchiveProgress(completed, fileCount, entry.name))
            val result = files.writeObject(
                session.directory, objectPath, session.id, id, 1L, key,
                CheckedArchiveStream(source.open(), source.sizeBytes, source.crc32, checkActive)
            )
            newObjects += objectPath
            completed++
            onProgress(VaultArchiveProgress(completed, fileCount, null))
            return VaultManifestEntry(
                NodeId.random(), entry.name, VaultNodeKind.FILE, 1L, System.currentTimeMillis(),
                result.sizeBytes, null, id, null, key
            )
        }

        private fun newKey(): ByteArray = VaultCryptography.randomBytes(32).also(privateKeys::add)
    }
}

private sealed interface ArchivePlanEntry {
    val name: String
}

private class ArchivePlanFolder(override val name: String) : ArchivePlanEntry {
    val children = linkedMapOf<String, ArchivePlanEntry>()
}

private data class ArchivePlanFile(
    override val name: String,
    val source: VaultArchiveSource.File
) : ArchivePlanEntry

private class ArchiveExtractionPlan(sources: List<VaultArchiveSource>) {
    val root = ArchivePlanFolder("")
    val files = mutableListOf<ArchivePlanFile>()
    val fileCount: Int get() = files.size
    private var folderCount = 1
    private var totalBytes = 0L

    init {
        if (sources.isEmpty() || sources.size > VAULT_ARCHIVE_MAX_ENTRIES) {
            throw VaultFailure.Unavailable("Archive entry count is outside the vault extraction limit")
        }
        for (source in sources) add(source)
    }

    private fun add(source: VaultArchiveSource) {
        if (source.path.isEmpty() || source.path.size > VAULT_ARCHIVE_MAX_FOLDERS) {
            throw VaultFailure.InvalidPath("Invalid archive entry path")
        }
        var parent = root
        for ((index, segment) in source.path.withIndex()) {
            val name = VaultName.of(normalizeMutationName(segment))
            val existing = parent.children[name.comparisonKey]
            if (existing != null && existing.name != name.value) {
                throw VaultFailure.InvalidPath("Archive entries collide after vault name normalization")
            }
            if (index == source.path.lastIndex && source is VaultArchiveSource.File) {
                if (existing != null) throw VaultFailure.InvalidPath("Duplicate archive entry")
                if (source.sizeBytes < 0 || source.sizeBytes > VAULT_ARCHIVE_MAX_BYTES - totalBytes) {
                    throw VaultFailure.Unavailable("Archive exceeds the vault extraction size limit")
                }
                val crc = source.crc32
                if (crc != null && crc !in 0..0xFFFFFFFFL) {
                    throw VaultFailure.InvalidPath("Archive checksum is invalid")
                }
                totalBytes += source.sizeBytes
                val file = ArchivePlanFile(name.value, source)
                parent.children[name.comparisonKey] = file
                files += file
            } else {
                parent = when (existing) {
                    is ArchivePlanFolder -> existing
                    is ArchivePlanFile -> throw VaultFailure.InvalidPath("Archive file and folder names collide")
                    null -> {
                        if (++folderCount > VAULT_ARCHIVE_MAX_FOLDERS) {
                            throw VaultFailure.Unavailable("Archive exceeds the vault extraction folder limit")
                        }
                        ArchivePlanFolder(name.value).also { parent.children[name.comparisonKey] = it }
                    }
                }
            }
        }
    }
}

private class CheckedArchiveStream(
    input: InputStream,
    private val expectedBytes: Long,
    private val expectedCrc: Long?,
    private val checkActive: () -> Unit
) : FilterInputStream(input) {
    private val checksum = CRC32()
    private var total = 0L

    override fun read(): Int {
        checkActive()
        val value = super.read()
        if (value < 0) verifyEnd() else {
            addSize(1)
            if (expectedCrc != null) checksum.update(value)
        }
        return value
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        checkActive()
        val count = super.read(buffer, offset, length)
        if (count < 0) verifyEnd() else if (count > 0) {
            addSize(count)
            if (expectedCrc != null) checksum.update(buffer, offset, count)
        }
        return count
    }

    private fun addSize(count: Int) {
        total += count
        if (total > expectedBytes) throw VaultFailure.IntegrityFailed("Archive entry expanded past its declared size")
    }

    private fun verifyEnd() {
        if (total != expectedBytes || (expectedCrc != null && checksum.value != expectedCrc)) {
            throw VaultFailure.IntegrityFailed("Archive entry failed its size or checksum check")
        }
    }
}
