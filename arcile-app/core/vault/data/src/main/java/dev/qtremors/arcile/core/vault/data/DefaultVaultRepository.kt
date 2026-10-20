package dev.qtremors.arcile.core.vault.data

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.qtremors.arcile.core.runtime.di.ApplicationScope
import dev.qtremors.arcile.core.runtime.di.ArcileDispatchers
import dev.qtremors.arcile.core.vault.crypto.VaultCryptography
import dev.qtremors.arcile.core.vault.crypto.FileVaultDirectory
import dev.qtremors.arcile.core.vault.crypto.VaultManifestEntry
import dev.qtremors.arcile.core.vault.domain.*
import java.io.ByteArrayInputStream
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.attribute.BasicFileAttributes
import java.util.ArrayDeque
import java.util.PriorityQueue
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.random.Random

@Singleton
internal class DefaultVaultRepository @Inject constructor(
    @ApplicationContext context: Context,
    dispatchers: ArcileDispatchers,
    @ApplicationScope applicationScope: CoroutineScope,
    portableLocationResolver: VaultPortableLocationResolver
) : VaultHealthLayer(context, dispatchers, applicationScope, portableLocationResolver),
    VaultFileSystem {
    init {
        applicationScope.launch(dispatchers.io) {
            lifecycleMutex.withLock {
                cleanupInterruptedArtifacts(vaultRoot)
                refreshVaults()
            }
        }
    }

    override suspend fun listDirectory(
        vaultId: VaultId,
        directoryId: DirectoryId,
        options: VaultListOptions
    ): Result<VaultPage<VaultNodeMetadata>> = withSession(vaultId) { session ->
        listDirectory(session, directoryId, options)
    }

    internal suspend fun listDirectory(
        session: VaultSessionRecord,
        directoryId: DirectoryId,
        options: VaultListOptions,
        pageSize: Int = options.pageSize
    ): VaultPage<VaultNodeMetadata> {
        val directory = session.resolveDirectory(directoryId)
        return try {
            val snapshot = session.readDirectory(directory)
            try {
                val fileCounts = mutableMapOf<NodeId, Long>()
                val budget = FolderScanBudget()
                snapshot.entries.filter { entry ->
                    if (!options.showHiddenFiles && entry.name.startsWith('.') && entry.nodeId.value != options.revealHiddenNodeId) {
                        return@filter false
                    }
                    if (options.foldersOnly && entry.kind != VaultNodeKind.DIRECTORY) return@filter false
                    if (entry.kind == VaultNodeKind.DIRECTORY) {
                        session.cacheDirectoryKey(
                            requireNotNull(entry.childDirectoryId),
                            entry.protectedKey
                        )
                    }
                    true
                }.map { entry ->
                    val node = entry.toMetadata(session.id, directoryId)
                    if (options.sortField == VaultSortField.FILE_COUNT) {
                        readFileCount(session, entry, budget)?.let { fileCounts[node.ref.nodeId] = it }
                    }
                    node
                }
                    .sortedWith(vaultListingComparator(options.sortField, options.direction, options.foldersFirst, fileCounts))
                    .let { sorted ->
                        val offset = options.pageToken.toPageOffset(sorted.size)
                        val items = sorted.drop(offset).take(pageSize)
                        VaultPage(
                            items,
                            (offset + items.size).takeIf { it < sorted.size }?.toString(),
                            snapshot.generation
                        )
                    }
            } finally {
                snapshot.clearProtectedKeys()
            }
        } finally {
            directory.key.fill(0)
        }
    }

    override suspend fun metadata(ref: VaultNodeRef): Result<VaultNodeMetadata> =
        withSession(ref.vaultId) { session ->
            metadata(session, ref)
        }

    override suspend fun directoryTrail(
        vaultId: VaultId,
        directoryId: DirectoryId
    ): Result<List<VaultDirectoryLocation>> = withSession(vaultId) { session ->
        val root = session.resolveDirectory(DirectoryId.Root)
        val rootLocation = VaultDirectoryLocation(root.id, "")
        val pending = ArrayDeque<Pair<List<VaultDirectoryLocation>, ByteArray>>()
        val visited = mutableSetOf(root.id)
        pending.add(listOf(rootLocation) to root.key)
        try {
            while (pending.isNotEmpty()) {
                currentCoroutineContext().ensureActive()
                val (trail, key) = pending.removeLast()
                try {
                    val current = trail.last().id
                    if (current == directoryId) {
                        session.cacheDirectoryKey(current, key)
                        return@withSession trail
                    }
                    val snapshot = session.readDirectory(current, key)
                    try {
                        for (entry in snapshot.entries.asReversed()) {
                            val child = entry.childDirectoryId ?: continue
                            if (!visited.add(child)) {
                                throw VaultFailure.IntegrityFailed("A vault folder was encountered twice")
                            }
                            pending.add(trail + VaultDirectoryLocation(child, entry.name) to entry.protectedKey.copyOf())
                        }
                    } finally {
                        snapshot.clearProtectedKeys()
                    }
                } finally {
                    key.fill(0)
                }
            }
            throw VaultFailure.InvalidPath("Folder is unavailable")
        } finally {
            while (pending.isNotEmpty()) pending.removeLast().second.fill(0)
        }
    }

    override suspend fun folderTotals(ref: VaultNodeRef): Result<VaultFolderTotals> = withSession(ref.vaultId) { session ->
        val (parent, snapshot, entry) = resolveStableEntry(session, ref)
        try {
            val root = entry.childDirectoryId ?: throw VaultFailure.Unavailable("The item is not a folder")
            scanFolder(session, root, entry.protectedKey, FolderScanBudget())
        } finally {
            entry.protectedKey.fill(0)
            snapshot.clearProtectedKeys()
            parent.key.fill(0)
        }
    }

    private suspend fun readFileCount(
        session: VaultSessionRecord,
        entry: VaultManifestEntry,
        budget: FolderScanBudget
    ): Long? {
        val child = entry.childDirectoryId ?: return null
        if (budget.directories <= 0 || budget.entries <= 0) return null
        return try {
            scanFolder(session, child, entry.protectedKey, budget).fileCount
        } catch (_: VaultFailure) {
            null
        }
    }

    private class FolderScanBudget(var directories: Int = 128, var entries: Int = 8192)

    override suspend fun storageUsage(vaultId: VaultId): Result<VaultStorageUsage> = mutate(vaultId) { session ->
        val coroutine = currentCoroutineContext()
        val physical = (session.directory as? FileVaultDirectory)?.directory?.toPath()?.let { root ->
            Files.walk(root).use { stream ->
                val paths = stream.iterator()
                var bytes = 0L
                var visited = 0
                while (paths.hasNext() && visited < 100_000) {
                    coroutine.ensureActive()
                    val attributes = Files.readAttributes(
                        paths.next(), BasicFileAttributes::class.java, LinkOption.NOFOLLOW_LINKS
                    )
                    if (attributes.isRegularFile) bytes = Math.addExact(bytes, attributes.size())
                    visited++
                }
                bytes to paths.hasNext()
            }
        }
        val root = session.root()
        try {
            val snapshot = session.readDirectory(root)
            try {
                val folders = snapshot.entries.filter { it.childDirectoryId != null }
                val budget = FolderScanBudget()
                val measured = folders.mapNotNull { entry ->
                    coroutine.ensureActive()
                    val id = requireNotNull(entry.childDirectoryId)
                    val bytes = try {
                        if (budget.directories <= 0 || budget.entries <= 0) null
                        else scanFolder(session, id, entry.protectedKey, budget).totalBytes
                    } catch (_: VaultFailure.Unavailable) {
                        null
                    }
                    bytes?.let { VaultFolderUsage(id, entry.name, it) }
                }
                VaultStorageUsage(
                    storedBytes = physical?.first,
                    storedBytesPartial = physical?.second == true,
                    largestFolders = measured.sortedWith(
                        compareByDescending<VaultFolderUsage> { it.totalBytes }.thenBy { it.name }
                    ).take(10),
                    folderCount = folders.size,
                    foldersMeasured = measured.size
                )
            } finally {
                snapshot.clearProtectedKeys()
            }
        } finally {
            root.key.fill(0)
        }
    }

    override suspend fun selectionTotals(refs: List<VaultNodeRef>): Result<VaultFolderTotals> {
        val vault = refs.firstOrNull()?.vaultId ?: return Result.success(VaultFolderTotals(0, 0))
        return mutate(vault) { session ->
            if (refs.any { it.vaultId != vault }) throw VaultFailure.StaleRegistration()
            val budget = FolderScanBudget()
            val folders = mutableSetOf<DirectoryId>()
            val files = mutableSetOf<NodeId>()
            var totalBytes = 0L
            for ((parentId, siblings) in refs.groupBy(VaultNodeRef::parentId)) {
                currentCoroutineContext().ensureActive()
                val parent = session.resolveDirectory(parentId)
                try {
                    val snapshot = session.readDirectory(parent)
                    try {
                        val entries = snapshot.entries.associateBy(VaultManifestEntry::nodeId)
                        for (ref in siblings) {
                            currentCoroutineContext().ensureActive()
                            val entry = entries[ref.nodeId] ?: throw VaultFailure.NodeNotFound(ref.nodeId)
                            val child = entry.childDirectoryId
                            if (child == null) {
                                if (files.add(entry.nodeId)) totalBytes = Math.addExact(totalBytes, entry.sizeBytes)
                            } else if (child !in folders) {
                                val totals = scanFolder(session, child, entry.protectedKey, budget, folders, files)
                                totalBytes = Math.addExact(totalBytes, totals.totalBytes)
                            }
                        }
                    } finally {
                        snapshot.clearProtectedKeys()
                    }
                } finally {
                    parent.key.fill(0)
                }
            }
            VaultFolderTotals(files.size.toLong(), totalBytes, folders.size)
        }
    }

    private suspend fun scanFolder(
        session: VaultSessionRecord,
        root: DirectoryId,
        rootKey: ByteArray,
        budget: FolderScanBudget,
        seenFolders: MutableSet<DirectoryId>? = null,
        seenFiles: MutableSet<NodeId>? = null
    ): VaultFolderTotals {
        val pending = ArrayDeque<Pair<DirectoryId, ByteArray>>()
        pending.add(root to rootKey.copyOf())
        val coroutine = currentCoroutineContext()
        val visited = mutableSetOf<DirectoryId>()
        var fileCount = 0L
        var totalBytes = 0L
        return try {
            while (pending.isNotEmpty()) {
                coroutine.ensureActive()
                val (id, key) = pending.removeFirst()
                try {
                    if (!visited.add(id)) throw VaultFailure.IntegrityFailed("A vault folder was encountered twice")
                    if (seenFolders?.add(id) == false) continue
                    if (budget.directories-- <= 0) throw VaultFailure.Unavailable("Folder exceeds the size calculation limit")
                    val directory = session.readDirectory(id, key)
                    try {
                        budget.entries -= directory.entries.size
                        if (budget.entries < 0) throw VaultFailure.Unavailable("Folder exceeds the size calculation limit")
                        for (child in directory.entries) {
                            val childId = child.childDirectoryId
                            if (childId != null) {
                                if (childId in visited) throw VaultFailure.IntegrityFailed("A vault folder was encountered twice")
                                if (seenFolders?.contains(childId) == true) continue
                                if (pending.size >= budget.directories) {
                                    budget.directories = 0
                                    throw VaultFailure.Unavailable("Folder exceeds the size calculation limit")
                                }
                                pending.add(childId to child.protectedKey.copyOf())
                            } else if (seenFiles?.add(child.nodeId) != false) {
                                fileCount++
                                totalBytes = Math.addExact(totalBytes, child.sizeBytes)
                            }
                        }
                    } finally {
                        directory.clearProtectedKeys()
                    }
                } finally {
                    key.fill(0)
                }
            }
            VaultFolderTotals(fileCount, totalBytes, folderCount = visited.size - 1)
        } finally {
            while (pending.isNotEmpty()) pending.removeFirst().second.fill(0)
        }
    }

    internal fun metadata(session: VaultSessionRecord, ref: VaultNodeRef): VaultNodeMetadata {
        val (parent, snapshot, entry) = resolveStableEntry(session, ref)
        return try {
            entry.toMetadata(session.id, parent.id)
        } finally {
            entry.protectedKey.fill(0)
            snapshot.clearProtectedKeys()
            parent.key.fill(0)
        }
    }

    override suspend fun search(
        vaultId: VaultId,
        directoryId: DirectoryId,
        query: VaultSearchQuery
    ): Result<VaultPage<VaultSearchHit>> = mutate(vaultId) { session ->
        if (!query.reusePageWindow || locations[vaultId.value]?.kind != VaultLocationKind.APP_PRIVATE ||
            transactionManager.hasPendingCommit(session.directory)
        ) {
            session.updateSearchWindow(null)
            search(session, directoryId, query)
        } else {
            searchWindowed(session, directoryId, query)
        }
    }

    private suspend fun searchWindowed(
        session: VaultSessionRecord,
        directoryId: DirectoryId,
        query: VaultSearchQuery
    ): VaultPage<VaultSearchHit> {
        val offset = query.pageToken.toPageOffset(Int.MAX_VALUE)
        val key = query.copy(pageToken = null)
        val publication = transactionManager.publicationGeneration
        val cached = session.currentSearchWindow()
        if (query.pageToken != null && cached?.directoryId == directoryId && cached.query == key &&
            cached.publicationGeneration == publication
        ) {
            cached.page(offset)?.let { return it }
        }

        val ranked = search(session, directoryId, query, pageSize = query.pageSize * 4)
        val window = VaultSearchWindow(directoryId, key, publication, offset, ranked)
        session.updateSearchWindow(window.takeIf { ranked.items.size > query.pageSize })
        return window.page(offset) ?: ranked
    }

    override suspend fun selectDirectoryRows(
        vaultId: VaultId,
        directoryId: DirectoryId,
        options: VaultListOptions
    ): Result<VaultPage<VaultNodeMetadata>> = mutate(vaultId) { session ->
        listDirectory(session, directoryId, options.copy(pageToken = null), VAULT_SELECT_ROWS_LIMIT)
            .requireCompleteSelection()
    }

    override suspend fun selectSearchRows(
        vaultId: VaultId,
        directoryId: DirectoryId,
        query: VaultSearchQuery
    ): Result<VaultPage<VaultSearchHit>> = mutate(vaultId) { session ->
        search(session, directoryId, query.copy(pageToken = null), VAULT_SELECT_ROWS_LIMIT)
            .requireCompleteSelection()
    }

    private fun <T> VaultPage<T>.requireCompleteSelection(): VaultPage<T> {
        if (nextPageToken != null) {
            throw VaultFailure.Unavailable("Narrow the listing to select up to $VAULT_SELECT_ROWS_LIMIT items at once")
        }
        return this
    }

    private suspend fun search(
        session: VaultSessionRecord,
        directoryId: DirectoryId,
        query: VaultSearchQuery,
        pageSize: Int = query.pageSize
    ): VaultPage<VaultSearchHit> {
        val requestedOffset = query.pageToken.toPageOffset(Int.MAX_VALUE)
        val start = session.resolveDirectory(directoryId)
        val frames = ArrayDeque<SearchFrame>()
        frames += SearchFrame(start.id, start.key, emptyList())
        val visited = mutableSetOf(start.id)
        val limit = (requestedOffset.toLong() + pageSize + 1L).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        val fileCounts = mutableMapOf<NodeId, Long>()
        val budget = FolderScanBudget()
        val nodeComparator = vaultListingComparator(query.sortField, query.direction, query.foldersFirst, fileCounts)
        val comparator = Comparator<VaultSearchHit> { left, right -> nodeComparator.compare(left.metadata, right.metadata) }
        // Keep only the prefix needed for this page while ranking matches across the whole tree.
        val matches = PriorityQueue(comparator.reversed())
        var generation = 1L
        try {
            while (frames.isNotEmpty()) {
                val frame = frames.removeLast()
                try {
                    val snapshot = session.readDirectory(frame.directoryId, frame.key)
                    generation = 31L * generation + frame.directoryId.hashCode()
                    generation = 31L * generation + snapshot.generation
                    try {
                        for (entry in snapshot.entries) {
                            currentCoroutineContext().ensureActive()
                            if (!query.includeHidden && entry.name.startsWith('.')) continue
                            if (query.recursive && entry.kind == VaultNodeKind.DIRECTORY) {
                                val childId = requireNotNull(entry.childDirectoryId)
                                if (!visited.add(childId)) throw VaultFailure.IntegrityFailed("A vault folder was encountered twice")
                                session.cacheDirectoryKey(childId, entry.protectedKey)
                                frames += SearchFrame(
                                    childId,
                                    entry.protectedKey.copyOf(),
                                    frame.parentNames + entry.name,
                                    frame.parentDirectoryIds + childId
                                )
                            }
                            if (entry.name.contains(query.text, ignoreCase = true)) {
                                val metadata = entry.toMetadata(session.id, frame.directoryId)
                                if (query.matches?.invoke(metadata) == false) continue
                                if (query.sortField == VaultSortField.FILE_COUNT) {
                                    readFileCount(session, entry, budget)?.let {
                                        fileCounts[metadata.ref.nodeId] = it
                                    }
                                }
                                val hit = VaultSearchHit(metadata, frame.parentNames, frame.parentDirectoryIds)
                                if (matches.size < limit) {
                                    matches.add(hit)
                                } else if (comparator.compare(hit, matches.peek()) < 0) {
                                    matches.remove()
                                    matches.add(hit)
                                }
                            }
                        }
                    } finally {
                        snapshot.clearProtectedKeys()
                    }
                } finally {
                    frame.key.fill(0)
                }
                if (!query.recursive) break
            }
        } finally {
            while (frames.isNotEmpty()) frames.removeLast().key.fill(0)
        }
        val page = matches.sortedWith(comparator).drop(requestedOffset)
        val items = page.take(pageSize)
        return VaultPage(items, (requestedOffset + items.size).takeIf { page.size > items.size }?.toString(), generation)
    }

    override suspend fun createDirectory(
        vaultId: VaultId,
        parentId: DirectoryId,
        name: String
    ): Result<VaultNodeMetadata> = mutate(vaultId) { session ->
        createStableDirectory(session, parentId, normalizeMutationName(name))
    }

    override suspend fun createEmptyFile(
        vaultId: VaultId,
        parentId: DirectoryId,
        name: String,
        mimeType: String?
    ): Result<VaultNodeMetadata> = createFile(vaultId, parentId, name, mimeType) {}

    override suspend fun createSyntheticFile(
        vaultId: VaultId, parentId: DirectoryId, name: String, size: Long
    ): Result<VaultNodeMetadata> {
        if (size < 0L) return Result.failure(IllegalArgumentException("Synthetic file size must be non-negative"))
        val coroutine = currentCoroutineContext()
        return createFile(vaultId, parentId, name, "application/octet-stream") { output ->
            val buffer = ByteArray(16 * 1024)
            var remaining = size
            try {
                while (remaining > 0L) {
                    coroutine.ensureActive()
                    val count = minOf(buffer.size.toLong(), remaining).toInt()
                    Random.nextBytes(buffer, 0, count)
                    output.write(buffer, 0, count)
                    remaining -= count
                }
            } finally {
                buffer.fill(0)
            }
        }
    }

    override suspend fun createZip(
        vaultId: VaultId, parentId: DirectoryId, name: String, writeZip: (OutputStream) -> Unit
    ): Result<VaultNodeMetadata> =
        createFile(vaultId, parentId, name, "application/zip", VAULT_ARCHIVE_MAX_BYTES, writeZip)

    override suspend fun extractArchive(
        vaultId: VaultId,
        parentId: DirectoryId,
        name: String,
        sources: List<VaultArchiveSource>,
        keepBoth: Boolean,
        onProgress: (VaultArchiveProgress) -> Unit
    ): Result<VaultNodeMetadata> = mutate(vaultId) { session ->
        val coroutine = currentCoroutineContext()
        VaultArchiveWriter(fileCodec, directoryCodec, transactionManager).extract(
            session, parentId, name, sources, keepBoth, { coroutine.ensureActive() }, onProgress
        )
    }

    private suspend fun createFile(
        vaultId: VaultId,
        parentId: DirectoryId,
        name: String,
        mimeType: String?,
        byteLimit: Long = Long.MAX_VALUE,
        writeContents: (OutputStream) -> Unit
    ): Result<VaultNodeMetadata> = mutate(vaultId) { session ->
        val coroutine = currentCoroutineContext()
        val parent = session.resolveDirectory(parentId)
        val snapshot = session.readDirectory(parent)
        val contentKey = VaultCryptography.randomBytes(32)
        val objectId = VaultObjectId.fromRandomBytes(VaultCryptography.randomBytes(32))
        val objectPath = objectId.shardedPath()
        try {
            val normalized = normalizeMutationName(name)
            ensureNameAvailable(snapshot, normalized)
            val result = fileCodec.writeGeneratedObject(
                session.directory,
                objectPath,
                session.id,
                objectId,
                1L,
                contentKey,
                onBytesWritten = { size ->
                    coroutine.ensureActive()
                    if (size > byteLimit) throw VaultFailure.Unavailable("Archive exceeds the vault size limit")
                },
                writeContent = writeContents
            )
            val entry = VaultManifestEntry(
                NodeId.random(),
                normalized,
                VaultNodeKind.FILE,
                1L,
                System.currentTimeMillis(),
                result.sizeBytes,
                mimeType,
                objectId,
                null,
                contentKey
            )
            val prepared = directoryCodec.prepare(
                session.id,
                parent.id,
                parent.key,
                snapshot.generation + 1L,
                snapshot.entries + entry
            )
            coroutine.ensureActive()
            transactionManager.commit(
                session.directory,
                session.id,
                session.masterSecret,
                listOf(VaultPreparedDirectory(prepared, parent.key.copyOf())),
                setOf(objectPath),
                emptySet()
            )
            entry.toMetadata(session.id, parent.id)
        } catch (error: Throwable) {
            if (!transactionManager.hasPendingCommit(session.directory)) {
                session.directory.delete(objectPath)
            }
            throw error
        } finally {
            contentKey.fill(0)
            snapshot.clearProtectedKeys()
            parent.key.fill(0)
        }
    }

    override suspend fun rename(ref: VaultNodeRef, newName: String): Result<VaultNodeMetadata> =
        mutate(ref.vaultId) { session ->
            val (parent, snapshot, existing) = resolveStableEntry(session, ref)
            try {
                val normalized = normalizeMutationName(newName)
                ensureNameAvailable(snapshot, normalized, existing.nodeId)
                val replacement = existing.copy(
                    name = normalized,
                    modifiedAtMillis = System.currentTimeMillis(),
                    protectedKey = existing.protectedKey.copyOf()
                )
                val entries = snapshot.entries.map {
                    if (it.nodeId == existing.nodeId) replacement else it
                }
                val prepared = directoryCodec.prepare(
                    session.id,
                    parent.id,
                    parent.key,
                    snapshot.generation + 1L,
                    entries
                )
                transactionManager.commit(
                    session.directory,
                    session.id,
                    session.masterSecret,
                    listOf(VaultPreparedDirectory(prepared, parent.key.copyOf())),
                    emptySet(),
                    emptySet()
                )
                replacement.toMetadata(session.id, parent.id)
            } finally {
                existing.protectedKey.fill(0)
                snapshot.clearProtectedKeys()
                parent.key.fill(0)
            }
        }

    override suspend fun batchRenameSiblingNames(items: List<VaultRenameItem>): Result<Map<DirectoryId, Set<String>>> =
        if (items.isEmpty()) Result.success(emptyMap())
        else mutate(items.first().ref.vaultId) { session -> VaultBatchRenamer(session).siblingNames(items) }

    override suspend fun batchRename(items: List<VaultRenameItem>): Result<List<VaultNodeMetadata>> =
        if (items.isEmpty()) Result.success(emptyList())
        else mutate(items.first().ref.vaultId) { session ->
            VaultBatchRenamer(session).rename(items, directoryCodec, transactionManager)
        }

    override suspend fun deletePermanently(ref: VaultNodeRef): Result<Unit> =
        withSession(ref.vaultId) { session ->
            deletePermanently(session, ref)
        }

    override suspend fun replaceFileContents(
        ref: VaultNodeRef,
        expectedRevision: Long,
        content: ByteArray
    ): Result<VaultNodeMetadata> = mutate(ref.vaultId) { session ->
        val (parent, snapshot, existing) = resolveStableEntry(session, ref)
        val objectId = VaultObjectId.fromRandomBytes(VaultCryptography.randomBytes(32))
        val objectPath = objectId.shardedPath()
        val contentKey = VaultCryptography.randomBytes(32)
        try {
            if (existing.kind != VaultNodeKind.FILE || !ref.capabilities.canRename) {
                throw VaultFailure.Unavailable("This vault item cannot be edited")
            }
            if (existing.revision != expectedRevision) {
                throw VaultFailure.Unavailable("The file changed. Reopen it before saving.")
            }
            val revision = existing.revision + 1L
            fileCodec.writeObject(
                session.directory, objectPath, session.id, objectId, revision,
                contentKey, ByteArrayInputStream(content)
            )
            val replacement = existing.copy(
                revision = revision,
                modifiedAtMillis = System.currentTimeMillis(),
                sizeBytes = content.size.toLong(),
                objectId = objectId,
                protectedKey = contentKey
            )
            val prepared = directoryCodec.prepare(
                session.id, parent.id, parent.key, snapshot.generation + 1L,
                snapshot.entries.map { if (it.nodeId == existing.nodeId) replacement else it }
            )
            transactionManager.commit(
                session.directory, session.id, session.masterSecret,
                listOf(VaultPreparedDirectory(prepared, parent.key.copyOf())),
                setOf(objectPath), setOf(requireNotNull(existing.objectId).shardedPath())
            )
            replacement.toMetadata(session.id, parent.id)
        } catch (error: Throwable) {
            if (!transactionManager.hasPendingCommit(session.directory)) session.directory.delete(objectPath)
            throw error
        } finally {
            contentKey.fill(0)
            existing.protectedKey.fill(0)
            snapshot.clearProtectedKeys()
            parent.key.fill(0)
        }
    }

    internal suspend fun deletePermanently(session: VaultSessionRecord, ref: VaultNodeRef) =
        session.mutationMutex.withLock { deleteWithMutationLockHeld(session, ref) }

    override suspend fun deleteAndReportFolders(ref: VaultNodeRef): Result<Set<DirectoryId>> =
        mutate(ref.vaultId) { session ->
            buildSet { deleteWithMutationLockHeld(session, ref) { add(it) } }
        }

    internal fun deleteWithMutationLockHeld(
        session: VaultSessionRecord,
        ref: VaultNodeRef,
        onDirectory: (DirectoryId) -> Unit = {}
    ) {
        val (parent, snapshot, existing) = resolveStableEntry(session, ref)
        try {
            val obsolete = collectObsoleteSubtree(session, existing, onDirectory)
            val prepared = directoryCodec.prepare(
                session.id,
                parent.id,
                parent.key,
                snapshot.generation + 1L,
                snapshot.entries.filterNot { it.nodeId == existing.nodeId }
            )
            transactionManager.commit(
                session.directory,
                session.id,
                session.masterSecret,
                listOf(VaultPreparedDirectory(prepared, parent.key.copyOf())),
                emptySet(),
                obsolete
            )
        } finally {
            existing.protectedKey.fill(0)
            snapshot.clearProtectedKeys()
            parent.key.fill(0)
        }
    }

    override fun openReader(ref: VaultNodeRef): Result<VaultSeekableReader> {
        val lease = holdSession(ref.vaultId).getOrElse { return Result.failure(it) }
        val session = sessions[ref.vaultId.value] ?: run {
            lease.close()
            return Result.failure(VaultFailure.Locked(ref.vaultId))
        }
        return try {
            val (parent, snapshot, entry) = resolveStableEntry(session, ref)
            try {
                if (entry.kind != VaultNodeKind.FILE) {
                    throw VaultFailure.InvalidPath("File is unavailable")
                }
                val objectId = requireNotNull(entry.objectId)
                val delegate = fileCodec.openObject(
                    session.directory,
                    objectId.shardedPath(),
                    session.id,
                    objectId,
                    entry.revision,
                    entry.protectedKey
                )
                val leased = VaultLeasedReader(delegate, lease, session::unregisterReader)
                if (!session.registerReader(leased)) {
                    leased.close()
                    throw VaultFailure.Locked(ref.vaultId)
                }
                Result.success(leased)
            } finally {
                entry.protectedKey.fill(0)
                snapshot.clearProtectedKeys()
                parent.key.fill(0)
            }
        } catch (error: Throwable) {
            lease.close()
            Result.failure(error)
        }
    }

    internal fun openReader(session: VaultSessionRecord, ref: VaultNodeRef): VaultSeekableReader {
        val (parent, snapshot, entry) = resolveStableEntry(session, ref)
        return try {
            if (entry.kind != VaultNodeKind.FILE) throw VaultFailure.InvalidPath("File is unavailable")
            val objectId = requireNotNull(entry.objectId)
            fileCodec.openObject(
                session.directory,
                objectId.shardedPath(),
                session.id,
                objectId,
                entry.revision,
                entry.protectedKey
            )
        } finally {
            entry.protectedKey.fill(0)
            snapshot.clearProtectedKeys()
            parent.key.fill(0)
        }
    }

    internal fun createBoundarySession(sources: List<VaultNodeRef>): VaultSessionRecord {
        val vaultId = sources.firstOrNull()?.vaultId
            ?: throw VaultFailure.InvalidPath("Select at least one vault item")
        if (sources.any { it.vaultId != vaultId }) {
            throw VaultFailure.InvalidPath("Export selections must belong to one vault")
        }
        val interactive = sessions[vaultId.value] ?: throw VaultFailure.Locked(vaultId)
        return interactive.copyForOperation()
    }

    internal fun reserveImport(vaultId: VaultId): String? {
        val interactive = sessions[vaultId.value] ?: return null
        val operation = runCatching { interactive.copyForOperation() }.getOrNull() ?: return null
        val token = UUID.randomUUID().toString()
        importReservations[token] = operation
        return token
    }

    internal fun createExternalAccessSession(
        ref: VaultNodeRef
    ): Pair<VaultSessionRecord, VaultNodeMetadata> {
        val interactive = sessions[ref.vaultId.value] ?: throw VaultFailure.Locked(ref.vaultId)
        val operation = interactive.copyForOperation()
        return try {
            val (parent, snapshot, entry) = resolveStableEntry(operation, ref)
            try {
                operation to entry.toMetadata(operation.id, parent.id)
            } finally {
                entry.protectedKey.fill(0)
                snapshot.clearProtectedKeys()
                parent.key.fill(0)
            }
        } catch (error: Throwable) {
            operation.destroy()
            throw error
        }
    }

    internal fun releaseImportReservation(token: String) {
        importReservations.remove(token)?.destroy()
    }

    internal suspend fun importUris(
        token: String,
        destination: VaultPath,
        sourceUris: List<String>,
        move: Boolean = false,
        onProgress: (Int, Int, Long, Long?, String?) -> Unit
    ): Result<VaultBatchResult> = withContext(dispatchers.io) {
        val operationSession = importReservations[token]
            ?: return@withContext Result.failure(
                VaultFailure.ImportUnavailable("Import session expired")
            )
        try {
            val protectedRoots = if (move) {
                (locations.values.mapNotNull { (it.access as? FileVaultDirectory)?.directory } +
                    listOfNotNull((operationSession.directory as? FileVaultDirectory)?.directory))
                    .map { it.canonicalFile.toPath() }
            } else emptyList()
            Result.success(importEngine.import(operationSession, destination, sourceUris, move, protectedRoots, onProgress))
        } catch (error: Throwable) {
            if (error is CancellationException) throw error
            Result.failure(error)
        } finally {
            releaseImportReservation(token)
        }
    }
}
