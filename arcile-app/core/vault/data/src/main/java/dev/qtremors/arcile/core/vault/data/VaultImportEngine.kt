package dev.qtremors.arcile.core.vault.data

import android.content.Context
import dev.qtremors.arcile.core.vault.crypto.VaultCryptography
import dev.qtremors.arcile.core.vault.crypto.VaultDirectoryManifestCodec
import dev.qtremors.arcile.core.vault.crypto.VaultFileCodec
import dev.qtremors.arcile.core.vault.crypto.VaultManifestEntry
import dev.qtremors.arcile.core.vault.domain.DirectoryId
import dev.qtremors.arcile.core.vault.domain.NodeId
import dev.qtremors.arcile.core.vault.domain.VaultBatchResult
import dev.qtremors.arcile.core.vault.domain.VaultFailure
import dev.qtremors.arcile.core.vault.domain.VaultItemOutcome
import dev.qtremors.arcile.core.vault.domain.VaultItemResult
import dev.qtremors.arcile.core.vault.domain.VaultName
import dev.qtremors.arcile.core.vault.domain.VaultNodeKind
import dev.qtremors.arcile.core.vault.domain.VaultObjectId
import dev.qtremors.arcile.core.vault.domain.VaultPath
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.withLock
import java.util.Locale
import kotlin.coroutines.coroutineContext

internal class VaultImportEngine(
    private val context: Context,
    private val directoryCodec: VaultDirectoryManifestCodec = VaultDirectoryManifestCodec(),
    private val fileCodec: VaultFileCodec = VaultFileCodec(),
    private val transactionManager: VaultTransactionManager = VaultTransactionManager(directoryCodec)
) {
    suspend fun import(
        session: VaultSessionRecord,
        destination: VaultPath,
        sourceUris: List<String>,
        onProgress: (Int, Int, Long, Long?, String?) -> Unit
    ): VaultBatchResult = session.mutationMutex.withLock {
        val reader = VaultUriTreeReader(context.contentResolver)
        val batches = sourceUris.map { sourceUri -> sourceUri to reader.collect(listOf(sourceUri)) }
        val totalBytes = batches.flatMap { it.second }.filterNot(VaultImportSource::isDirectory)
            .map(VaultImportSource::sizeBytes).let { sizes ->
                if (sizes.all { it != null }) sizes.sumOf { requireNotNull(it) } else null
            }
        val results = mutableListOf<VaultItemResult>()
        var completed = 0
        var copiedBytes = 0L

        batches.forEach { (sourceIdentity, sources) ->
            coroutineContext.ensureActive()
            val displayName = sources.firstOrNull()?.topLevelName() ?: sourceIdentity
            val before = copiedBytes
            try {
                val importedBytes = importOne(
                    session,
                    destination,
                    sources
                ) { itemBytes, currentName ->
                    onProgress(completed, batches.size, before + itemBytes, totalBytes, currentName)
                }
                copiedBytes += importedBytes
                completed++
                results += VaultItemResult(sourceIdentity, displayName, VaultItemOutcome.COMPLETED)
                onProgress(completed, batches.size, copiedBytes, totalBytes, displayName)
            } catch (error: CancellationException) {
                results += VaultItemResult(sourceIdentity, displayName, VaultItemOutcome.ROLLED_BACK, VaultFailure.Cancelled())
                throw error
            } catch (error: Throwable) {
                results += VaultItemResult(
                    sourceIdentity,
                    displayName,
                    VaultItemOutcome.ROLLED_BACK,
                    error.asVaultFailure()
                )
            }
        }
        VaultBatchResult(results)
    }

    private suspend fun importOne(
        session: VaultSessionRecord,
        destination: VaultPath,
        sources: List<VaultImportSource>,
        onProgress: (Long, String?) -> Unit
    ): Long {
        require(sources.isNotEmpty())
        val destinationDirectory = session.resolveDirectory(destination)
        val destinationSnapshot = session.readDirectory(destinationDirectory)
        val createdObjectPaths = mutableSetOf<String>()
        val temporaryDirectories = linkedMapOf<List<String>, ImportDirectory>()
        var copied = 0L
        try {
            val isTree = sources.any(VaultImportSource::isDirectory)
            if (isTree) {
                sources.filter(VaultImportSource::isDirectory)
                    .sortedBy { it.relativeParent.size }
                    .forEach { source ->
                        val relativePath = source.relativeParent.map(::normalizeImportName)
                        require(relativePath.isNotEmpty())
                        val temp = temporaryDirectories.getOrPut(relativePath) {
                            ImportDirectory(DirectoryId.random(), VaultCryptography.randomBytes(32), source.modifiedAtMillis)
                        }
                        val parentPath = relativePath.dropLast(1)
                        if (parentPath.isNotEmpty()) {
                            val parent = temporaryDirectories[parentPath]
                                ?: throw VaultFailure.ImportUnavailable("Folder hierarchy is incomplete")
                            parent.addDirectory(relativePath.last(), temp)
                        }
                    }
            }

            sources.filterNot(VaultImportSource::isDirectory).forEach { source ->
                coroutineContext.ensureActive()
                val contentKey = VaultCryptography.randomBytes(32)
                val objectId = VaultObjectId.fromRandomBytes(VaultCryptography.randomBytes(32))
                val objectPath = objectId.shardedPath()
                try {
                    val input = context.contentResolver.openInputStream(source.uri)
                        ?: throw VaultFailure.ImportUnavailable("Unable to read ${source.name}")
                    val itemStart = copied
                    val result = input.use { stream ->
                        fileCodec.writeObject(
                            session.directory,
                            objectPath,
                            session.id,
                            objectId,
                            1L,
                            contentKey,
                            stream
                        ) { bytes -> onProgress(itemStart + bytes, source.name) }
                    }
                    createdObjectPaths += objectPath
                    copied += result.sizeBytes
                    val entry = VaultManifestEntry(
                        nodeId = NodeId.random(),
                        name = normalizeImportName(source.name),
                        kind = VaultNodeKind.FILE,
                        revision = 1L,
                        modifiedAtMillis = source.modifiedAtMillis ?: System.currentTimeMillis(),
                        sizeBytes = result.sizeBytes,
                        mimeType = source.mimeType,
                        objectId = objectId,
                        childDirectoryId = null,
                        protectedKey = contentKey.copyOf()
                    )
                    if (source.relativeParent.isEmpty()) {
                        require(!isTree)
                        val uniqueName = keepBothName(destinationSnapshot.entries.map { it.name }, entry.name)
                        temporaryDirectories.getOrPut(emptyList()) {
                            ImportDirectory(destinationDirectory.id, destinationDirectory.key.copyOf(), null)
                        }.entries += entry.copy(name = uniqueName, protectedKey = entry.protectedKey.copyOf())
                        entry.protectedKey.fill(0)
                    } else {
                        val relativeParent = source.relativeParent.map(::normalizeImportName)
                        val parent = temporaryDirectories[relativeParent]
                            ?: throw VaultFailure.ImportUnavailable("Folder hierarchy is incomplete")
                        parent.addEntry(entry)
                    }
                } finally {
                    contentKey.fill(0)
                }
            }

            val topLevelEntries = if (isTree) {
                temporaryDirectories.filterKeys { it.size == 1 }.map { (path, temp) ->
                    VaultManifestEntry(
                        nodeId = NodeId.random(),
                        name = keepBothName(destinationSnapshot.entries.map { it.name }, path.single()),
                        kind = VaultNodeKind.DIRECTORY,
                        revision = 1L,
                        modifiedAtMillis = temp.modifiedAtMillis ?: System.currentTimeMillis(),
                        sizeBytes = 0L,
                        mimeType = null,
                        objectId = null,
                        childDirectoryId = temp.id,
                        protectedKey = temp.key.copyOf()
                    )
                }
            } else {
                temporaryDirectories[emptyList()]?.entries.orEmpty()
            }
            require(topLevelEntries.isNotEmpty()) { "Import produced no top-level item" }
            topLevelEntries.fold(destinationSnapshot.entries.map { it.name }) { names, entry ->
                val key = VaultName.of(entry.name).comparisonKey
                require(names.none { VaultName.of(it).comparisonKey == key })
                names + entry.name
            }

            val prepared = mutableListOf<VaultPreparedDirectory>()
            val destinationEntries = destinationSnapshot.entries + topLevelEntries
            val destinationManifest = directoryCodec.prepare(
                session.id,
                destinationDirectory.id,
                destinationDirectory.key,
                destinationSnapshot.generation + 1L,
                destinationEntries
            )
            prepared += VaultPreparedDirectory(destinationManifest, destinationDirectory.key.copyOf())
            if (isTree) {
                temporaryDirectories.values.forEach { temp ->
                    val manifest = directoryCodec.prepare(session.id, temp.id, temp.key, 0L, temp.entries)
                    prepared += VaultPreparedDirectory(manifest, temp.key.copyOf())
                    session.cacheDirectoryKey(temp.id, temp.key)
                }
            }
            transactionManager.commit(
                session.directory,
                session.id,
                session.masterSecret,
                prepared,
                createdObjectPaths,
                emptySet()
            )
            return copied
        } catch (error: Throwable) {
            if (!transactionManager.hasPendingCommit(session.directory)) {
                createdObjectPaths.forEach(session.directory::delete)
            }
            throw error
        } finally {
            temporaryDirectories.values.forEach(ImportDirectory::clear)
            destinationSnapshot.clearProtectedKeys()
            destinationDirectory.key.fill(0)
        }
    }

    private class ImportDirectory(
        val id: DirectoryId,
        val key: ByteArray,
        val modifiedAtMillis: Long?
    ) {
        val entries = mutableListOf<VaultManifestEntry>()

        fun addDirectory(name: String, child: ImportDirectory) {
            addEntry(
                VaultManifestEntry(
                    nodeId = NodeId.random(),
                    name = name,
                    kind = VaultNodeKind.DIRECTORY,
                    revision = 1L,
                    modifiedAtMillis = child.modifiedAtMillis ?: System.currentTimeMillis(),
                    sizeBytes = 0L,
                    mimeType = null,
                    objectId = null,
                    childDirectoryId = child.id,
                    protectedKey = child.key.copyOf()
                )
            )
        }

        fun addEntry(entry: VaultManifestEntry) {
            val comparison = VaultName.of(entry.name).comparisonKey
            if (entries.any { VaultName.of(it.name).comparisonKey == comparison }) {
                entry.protectedKey.fill(0)
                throw VaultFailure.NameConflict(entry.name)
            }
            entries += entry
        }

        fun clear() {
            key.fill(0)
            entries.forEach { it.protectedKey.fill(0) }
        }
    }
}

private fun VaultImportSource.topLevelName(): String =
    relativeParent.firstOrNull()?.let(::normalizeImportName) ?: normalizeImportName(name)

private fun normalizeImportName(value: String): String = try {
    VaultName.of(value).value
} catch (error: IllegalArgumentException) {
    throw VaultFailure.InvalidName(error.message ?: "Invalid imported name", error)
}

private fun keepBothName(existing: List<String>, requested: String): String {
    val keys = existing.mapTo(mutableSetOf()) { VaultName.of(it).comparisonKey }
    if (VaultName.of(requested).comparisonKey !in keys) return requested
    val extension = requested.substringAfterLast('.', "").takeIf { '.' in requested }
    val base = if (extension == null) requested else requested.dropLast(extension.length + 1)
    var number = 1
    while (true) {
        val candidate = if (extension == null) "$base ($number)" else "$base ($number).$extension"
        val normalized = VaultName.of(candidate).value
        if (VaultName.of(normalized).comparisonKey !in keys) return normalized
        number++
    }
}

private fun Throwable.asVaultFailure(): VaultFailure = when (this) {
    is VaultFailure -> this
    else -> VaultFailure.ImportUnavailable(message ?: "Import failed", this)
}
