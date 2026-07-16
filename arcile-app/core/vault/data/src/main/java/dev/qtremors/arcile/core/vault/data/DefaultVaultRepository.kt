package dev.qtremors.arcile.core.vault.data

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.qtremors.arcile.core.runtime.di.ApplicationScope
import dev.qtremors.arcile.core.runtime.di.ArcileDispatchers
import dev.qtremors.arcile.core.vault.crypto.FileVaultDirectory
import dev.qtremors.arcile.core.vault.crypto.VaultCryptography
import dev.qtremors.arcile.core.vault.crypto.VaultDirectoryManifestCodec
import dev.qtremors.arcile.core.vault.crypto.VaultDirectorySnapshot
import dev.qtremors.arcile.core.vault.crypto.VaultFileCodec
import dev.qtremors.arcile.core.vault.crypto.VaultManifestCodec
import dev.qtremors.arcile.core.vault.crypto.VaultManifestEntry
import dev.qtremors.arcile.core.vault.domain.DirectoryId
import dev.qtremors.arcile.core.vault.domain.NodeId
import dev.qtremors.arcile.core.vault.domain.VaultFailure
import dev.qtremors.arcile.core.vault.domain.VaultBatchResult
import dev.qtremors.arcile.core.vault.domain.VaultId
import dev.qtremors.arcile.core.vault.domain.VaultLocationKind
import dev.qtremors.arcile.core.vault.domain.VaultName
import dev.qtremors.arcile.core.vault.domain.VaultNode
import dev.qtremors.arcile.core.vault.domain.VaultNodeKind
import dev.qtremors.arcile.core.vault.domain.VaultObjectId
import dev.qtremors.arcile.core.vault.domain.VaultPath
import dev.qtremors.arcile.core.vault.domain.VaultRepository
import dev.qtremors.arcile.core.vault.domain.VaultSeekableReader
import dev.qtremors.arcile.core.vault.domain.VaultSessionLease
import dev.qtremors.arcile.core.vault.domain.VaultSummary
import dev.qtremors.arcile.core.vault.domain.VaultHealthIssue
import dev.qtremors.arcile.core.vault.domain.VaultHealthMode
import dev.qtremors.arcile.core.vault.domain.VaultHealthReport
import dev.qtremors.arcile.core.vault.domain.VaultHealthService
import dev.qtremors.arcile.core.vault.domain.VaultHealthSeverity
import dev.qtremors.arcile.core.vault.domain.VaultFileSystem
import dev.qtremors.arcile.core.vault.domain.VaultListOptions
import dev.qtremors.arcile.core.vault.domain.VaultNodeCapabilities
import dev.qtremors.arcile.core.vault.domain.VaultNodeMetadata
import dev.qtremors.arcile.core.vault.domain.VaultNodeRef
import dev.qtremors.arcile.core.vault.domain.VaultPage
import dev.qtremors.arcile.core.vault.domain.VaultSearchHit
import dev.qtremors.arcile.core.vault.domain.VaultSearchQuery
import dev.qtremors.arcile.core.vault.domain.VaultSortDirection
import dev.qtremors.arcile.core.vault.domain.VaultSortField
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.ByteArrayInputStream
import java.util.ArrayDeque
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DefaultVaultRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val dispatchers: ArcileDispatchers,
    @param:ApplicationScope private val applicationScope: CoroutineScope
) : VaultRepository, VaultHealthService, VaultFileSystem {
    private val headerCodec = VaultManifestCodec()
    private val directoryCodec = VaultDirectoryManifestCodec()
    private val fileCodec = VaultFileCodec()
    private val transactionManager = VaultTransactionManager(directoryCodec)
    private val vaultRoot = File(context.noBackupFilesDir, ROOT_DIRECTORY)
    private val sessions = ConcurrentHashMap<String, VaultSessionRecord>()
    private val locations = ConcurrentHashMap<String, VaultLocationRecord>()
    private val importReservations = ConcurrentHashMap<String, VaultSessionRecord>()
    private val lifecycleMutex = Mutex()
    private val locationRegistry = VaultLocationRegistry(context)
    private val importEngine = VaultImportEngine(context, directoryCodec, fileCodec, transactionManager)
    private val externalManager = VaultExternalManager(locationRegistry)
    private val appPrivateManager = VaultAppPrivateManager(vaultRoot)

    private val _vaults = MutableStateFlow<List<VaultSummary>>(emptyList())
    override val vaults: StateFlow<List<VaultSummary>> = _vaults.asStateFlow()
    private val _unlockedVaultIds = MutableStateFlow<Set<VaultId>>(emptySet())
    override val unlockedVaultIds: StateFlow<Set<VaultId>> = _unlockedVaultIds.asStateFlow()

    init {
        applicationScope.launch(dispatchers.io) {
            cleanupInterruptedArtifacts(vaultRoot)
            refreshVaults()
        }
    }

    override suspend fun refreshVaults() = withContext(dispatchers.io) {
        vaultRoot.mkdirs()
        val appPrivate = vaultRoot.listFiles().orEmpty().asSequence()
            .filter { it.isDirectory && !it.name.startsWith(STAGING_PREFIX) }
            .mapNotNull { directory ->
                headerCodec.readPublic(directory).getOrNull()?.let { header ->
                    locations[header.id.value] = VaultLocationRecord(FileVaultDirectory(directory), VaultLocationKind.APP_PRIVATE)
                    VaultSummary(
                        id = header.id,
                        name = header.publicName,
                        locationKind = VaultLocationKind.APP_PRIVATE,
                        createdAtMillis = header.createdAtMillis,
                        isUnlocked = sessions.containsKey(header.id.value),
                        headerFingerprint = header.headerFingerprint
                    )
                }
            }.toList()
        val privateIds = appPrivate.mapTo(mutableSetOf()) { it.id.value }
        val unavailable = mutableSetOf<VaultId>()
        val portable = locationRegistry.load().mapNotNull { pointer ->
            val pointerId = runCatching { VaultId.of(pointer.vaultId) }.getOrNull() ?: return@mapNotNull null
            if (pointerId.value in privateIds) return@mapNotNull null
            val access = FileVaultDirectory(File(pointer.path))
            runCatching {
                val header = headerCodec.readPublic(access).getOrThrow()
                if (header.id != pointerId) throw VaultFailure.StaleRegistration()
                locations[header.id.value] = VaultLocationRecord(access, VaultLocationKind.PORTABLE)
                locationRegistry.put(
                    pointer.copy(
                        cachedName = header.publicName,
                        cachedCreatedAtMillis = header.createdAtMillis,
                        headerFingerprint = header.headerFingerprint
                    )
                )
                VaultSummary(
                    id = header.id,
                    name = header.publicName,
                    locationKind = VaultLocationKind.PORTABLE,
                    createdAtMillis = header.createdAtMillis,
                    isUnlocked = sessions.containsKey(header.id.value),
                    headerFingerprint = header.headerFingerprint
                )
            }.getOrElse { failure ->
                unavailable += pointerId
                VaultSummary(
                    id = pointerId,
                    name = pointer.cachedName,
                    locationKind = VaultLocationKind.PORTABLE,
                    createdAtMillis = pointer.cachedCreatedAtMillis,
                    isUnlocked = false,
                    isAvailable = false,
                    availability = if (failure is VaultFailure.StaleRegistration) {
                        dev.qtremors.arcile.core.vault.domain.VaultAvailability.STALE_REGISTRATION
                    } else {
                        dev.qtremors.arcile.core.vault.domain.VaultAvailability.VOLUME_MISSING
                    },
                    headerFingerprint = pointer.headerFingerprint
                )
            }
        }
        val summaries = (appPrivate + portable).distinctBy { it.id }.sortedBy { it.name.lowercase() }
        val liveIds = summaries.filter(VaultSummary::isAvailable).mapTo(mutableSetOf()) { it.id.value }
        locations.keys.removeIf { it !in liveIds }
        unavailable.forEach(::requestLock)
        _vaults.value = summaries
        publishUnlockedIds()
    }

    override suspend fun createAppPrivateVault(name: String, password: CharArray): Result<VaultId> =
        withContext(dispatchers.io) {
            lifecycleMutex.withLock {
                catchingCancellation {
                    val created = appPrivateManager.create(name, password)
                    locations[created.id.value] = VaultLocationRecord(created.access, VaultLocationKind.APP_PRIVATE)
                    sessions[created.id.value] = VaultSessionRecord(created.id, created.access, created.masterSecret)
                    refreshVaults()
                    created.id
                }.also { password.fill('\u0000') }
            }
        }

    override suspend fun createUserFolderVault(path: String, name: String, password: CharArray): Result<VaultId> =
        withContext(dispatchers.io) {
            lifecycleMutex.withLock {
                catchingCancellation {
                    val created = externalManager.create(path, name, password)
                    val secret = requireNotNull(created.masterSecret)
                    locations[created.id.value] = VaultLocationRecord(created.access, VaultLocationKind.PORTABLE)
                    sessions[created.id.value] = VaultSessionRecord(created.id, created.access, secret)
                    refreshVaults()
                    created.id
                }.also { password.fill('\u0000') }
            }
        }

    override suspend fun attachExistingVault(path: String): Result<VaultId> = withContext(dispatchers.io) {
        lifecycleMutex.withLock {
            catchingCancellation {
                val attached = externalManager.attach(path)
                if (locations.containsKey(attached.id.value) || locationRegistry.find(attached.id) != null) {
                    throw VaultFailure.DuplicateVault(attached.id)
                }
                externalManager.register(attached)
                locations[attached.id.value] = VaultLocationRecord(attached.access, VaultLocationKind.PORTABLE)
                refreshVaults()
                attached.id
            }
        }
    }

    override suspend fun unlock(vaultId: VaultId, password: CharArray): Result<Unit> = withContext(dispatchers.io) {
        lifecycleMutex.withLock {
            if (sessions.containsKey(vaultId.value)) {
                password.fill('\u0000')
                return@withLock Result.success(Unit)
            }
            val location = locations[vaultId.value] ?: run {
                refreshVaults()
                locations[vaultId.value]
            }
            if (location == null) {
                password.fill('\u0000')
                return@withLock Result.failure(VaultFailure.NotFound(vaultId))
            }
            var secret: ByteArray? = null
            try {
                val opened = headerCodec.open(location.access, password).getOrThrow()
                if (opened.id != vaultId) throw VaultFailure.StaleRegistration()
                secret = opened.masterKey
                transactionManager.recover(location.access, vaultId, secret)
                val candidate = VaultSessionRecord(vaultId, location.access, secret)
                candidate.root().let { root ->
                    try {
                        candidate.readDirectory(root).clearProtectedKeys()
                    } finally {
                        root.key.fill(0)
                    }
                }
                sessions[vaultId.value] = candidate
                secret = null
                refreshVaults()
                Result.success(Unit)
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                Result.failure(error)
            } finally {
                secret?.fill(0)
                password.fill('\u0000')
            }
        }
    }

    override suspend fun lock(vaultId: VaultId) = withContext(dispatchers.io) {
        lifecycleMutex.withLock {
            requestLock(vaultId)
            refreshVaults()
        }
    }

    override suspend fun lockAll() = withContext(dispatchers.io) {
        lifecycleMutex.withLock {
            sessions.values.toList().forEach { requestLock(it.id) }
            refreshVaults()
        }
    }

    override fun holdSession(vaultId: VaultId): Result<VaultSessionLease> {
        val session = sessions[vaultId.value] ?: return Result.failure(VaultFailure.Locked(vaultId))
        synchronized(session) {
            if (sessions[vaultId.value] !== session || session.lockRequested) {
                return Result.failure(VaultFailure.Locked(vaultId))
            }
            session.holdCount++
        }
        return Result.success(VaultSessionLeaseImpl(session, ::releaseHold))
    }

    override suspend fun list(vaultId: VaultId, directory: VaultPath): Result<List<VaultNode>> =
        withSession(vaultId) { session ->
            val resolved = session.resolveDirectory(directory)
            try {
                val snapshot = session.readDirectory(resolved)
                try {
                    snapshot.entries.map { it.toLegacyNode(directory) }
                        .sortedWith(compareByDescending<VaultNode> { it.isDirectory }.thenBy { it.name.lowercase() })
                } finally {
                    snapshot.clearProtectedKeys()
                }
            } finally {
                resolved.key.fill(0)
            }
        }

    override suspend fun createDirectory(vaultId: VaultId, parent: VaultPath, name: String): Result<VaultNode> =
        mutate(vaultId) { session ->
            val normalizedName = normalizeMutationName(name)
            val resolved = session.resolveDirectory(parent)
            val snapshot = session.readDirectory(resolved)
            val childKey = VaultCryptography.randomBytes(VaultCryptography.KEY_SIZE_BYTES)
            val childId = DirectoryId.random()
            try {
                ensureNameAvailable(snapshot, normalizedName)
                val entry = VaultManifestEntry(
                    nodeId = NodeId.random(),
                    name = normalizedName,
                    kind = VaultNodeKind.DIRECTORY,
                    revision = 1L,
                    modifiedAtMillis = System.currentTimeMillis(),
                    sizeBytes = 0L,
                    mimeType = null,
                    objectId = null,
                    childDirectoryId = childId,
                    protectedKey = childKey.copyOf()
                )
                val parentPrepared = directoryCodec.prepare(
                    session.id,
                    resolved.id,
                    resolved.key,
                    snapshot.generation + 1L,
                    snapshot.entries + entry
                )
                val childPrepared = directoryCodec.prepare(session.id, childId, childKey, 0L, emptyList())
                session.cacheDirectoryKey(childId, childKey)
                transactionManager.commit(
                    session.directory,
                    session.id,
                    session.masterSecret,
                    listOf(
                        VaultPreparedDirectory(parentPrepared, resolved.key.copyOf()),
                        VaultPreparedDirectory(childPrepared, childKey.copyOf())
                    ),
                    emptySet(),
                    emptySet()
                )
                entry.toLegacyNode(parent)
            } finally {
                childKey.fill(0)
                snapshot.clearProtectedKeys()
                resolved.key.fill(0)
            }
        }

    override suspend fun rename(vaultId: VaultId, path: VaultPath, newName: String): Result<VaultNode> =
        mutate(vaultId) { session ->
            val resolved = session.resolveEntry(path)
            val snapshot = session.readDirectory(resolved.parent)
            try {
                val normalizedName = normalizeMutationName(newName)
                ensureNameAvailable(snapshot, normalizedName, except = resolved.entry.nodeId)
                val now = System.currentTimeMillis()
                val entries = snapshot.entries.map { entry ->
                    if (entry.nodeId == resolved.entry.nodeId) {
                        entry.copy(name = normalizedName, modifiedAtMillis = now, protectedKey = entry.protectedKey.copyOf())
                    } else entry
                }
                val prepared = directoryCodec.prepare(
                    session.id,
                    resolved.parent.id,
                    resolved.parent.key,
                    snapshot.generation + 1L,
                    entries
                )
                transactionManager.commit(
                    session.directory,
                    session.id,
                    session.masterSecret,
                    listOf(VaultPreparedDirectory(prepared, resolved.parent.key.copyOf())),
                    emptySet(),
                    emptySet()
                )
                entries.first { it.nodeId == resolved.entry.nodeId }.toLegacyNode(requireNotNull(path.parent))
            } finally {
                resolved.entry.protectedKey.fill(0)
                snapshot.clearProtectedKeys()
                resolved.parent.key.fill(0)
            }
        }

    override suspend fun delete(vaultId: VaultId, path: VaultPath): Result<Unit> = mutate(vaultId) { session ->
        val resolved = session.resolveEntry(path)
        val parentSnapshot = session.readDirectory(resolved.parent)
        try {
            val obsolete = collectObsoleteSubtree(session, resolved.entry)
            val remaining = parentSnapshot.entries.filterNot { it.nodeId == resolved.entry.nodeId }
            val prepared = directoryCodec.prepare(
                session.id,
                resolved.parent.id,
                resolved.parent.key,
                parentSnapshot.generation + 1L,
                remaining
            )
            transactionManager.commit(
                session.directory,
                session.id,
                session.masterSecret,
                listOf(VaultPreparedDirectory(prepared, resolved.parent.key.copyOf())),
                emptySet(),
                obsolete
            )
        } finally {
            resolved.entry.protectedKey.fill(0)
            parentSnapshot.clearProtectedKeys()
            resolved.parent.key.fill(0)
        }
    }

    override suspend fun readBytes(vaultId: VaultId, path: VaultPath, maximumBytes: Long): Result<ByteArray> =
        withContext(dispatchers.io) {
            val reader = openReader(vaultId, path).getOrElse { return@withContext Result.failure(it) }
            reader.use {
                if (it.sizeBytes > maximumBytes || it.sizeBytes > Int.MAX_VALUE) {
                    return@withContext Result.failure(VaultFailure.FileTooLarge(it.sizeBytes, maximumBytes))
                }
                val output = ByteArray(it.sizeBytes.toInt())
                var offset = 0
                while (offset < output.size) {
                    val count = it.readAt(offset.toLong(), output, offset, output.size - offset)
                    if (count <= 0) return@withContext Result.failure(
                        VaultFailure.IntegrityFailed("Encrypted file ended before its authenticated size")
                    )
                    offset += count
                }
                Result.success(output)
            }
        }

    override fun openReader(vaultId: VaultId, path: VaultPath): Result<VaultSeekableReader> {
        val lease = holdSession(vaultId).getOrElse { return Result.failure(it) }
        val session = sessions[vaultId.value]
        if (session == null) {
            lease.close()
            return Result.failure(VaultFailure.Locked(vaultId))
        }
        return try {
            val resolved = session.resolveEntry(path)
            try {
                if (resolved.entry.kind != VaultNodeKind.FILE) throw VaultFailure.InvalidPath("File is unavailable")
                val objectId = requireNotNull(resolved.entry.objectId)
                val reader = fileCodec.openObject(
                    session.directory,
                    objectId.shardedPath(),
                    session.id,
                    objectId,
                    resolved.entry.revision,
                    resolved.entry.protectedKey
                )
                val leased = VaultLeasedReader(reader, lease, session::unregisterReader)
                if (!session.registerReader(leased)) {
                    leased.close()
                    throw VaultFailure.Locked(vaultId)
                }
                Result.success(leased)
            } finally {
                resolved.entry.protectedKey.fill(0)
                resolved.parent.key.fill(0)
            }
        } catch (error: Throwable) {
            lease.close()
            Result.failure(error)
        }
    }

    override suspend fun listDirectory(
        vaultId: VaultId,
        directoryId: DirectoryId,
        options: VaultListOptions
    ): Result<VaultPage<VaultNodeMetadata>> = withSession(vaultId) { session ->
        val directory = session.resolveDirectory(directoryId)
        try {
            val snapshot = session.readDirectory(directory)
            try {
                snapshot.entries.filter { entry ->
                    if (entry.kind == VaultNodeKind.DIRECTORY) {
                        session.cacheDirectoryKey(requireNotNull(entry.childDirectoryId), entry.protectedKey)
                    }
                    true
                }.map { it.toMetadata(session.id, directoryId) }
                    .sortedWith(options.comparator())
                    .let { sorted ->
                        val offset = options.pageToken.toPageOffset(sorted.size)
                        val items = sorted.drop(offset).take(options.pageSize)
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

    override suspend fun metadata(ref: VaultNodeRef): Result<VaultNodeMetadata> = withSession(ref.vaultId) { session ->
        val (parent, snapshot, entry) = resolveStableEntry(session, ref)
        try {
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
    ): Result<VaultPage<VaultSearchHit>> = withSession(vaultId) { session ->
        val start = session.resolveDirectory(directoryId)
        val frames = ArrayDeque<SearchFrame>()
        frames += SearchFrame(start.id, start.key, emptyList())
        val requestedOffset = query.pageToken.toPageOffset(Int.MAX_VALUE)
        val matches = mutableListOf<VaultSearchHit>()
        var skipped = 0
        var generation = 0L
        try {
            searchLoop@ while (frames.isNotEmpty()) {
                val frame = frames.removeLast()
                try {
                    val snapshot = session.readDirectory(frame.directoryId, frame.key)
                    generation = maxOf(generation, snapshot.generation)
                    try {
                        for (entry in snapshot.entries) {
                            if (query.recursive && entry.kind == VaultNodeKind.DIRECTORY) {
                                val childId = requireNotNull(entry.childDirectoryId)
                                session.cacheDirectoryKey(childId, entry.protectedKey)
                                frames += SearchFrame(childId, entry.protectedKey.copyOf(), frame.parentNames + entry.name)
                            }
                            if (entry.name.contains(query.text, ignoreCase = true)) {
                                if (skipped < requestedOffset) skipped++ else {
                                    matches += VaultSearchHit(
                                        entry.toMetadata(session.id, frame.directoryId),
                                        frame.parentNames
                                    )
                                    if (matches.size == query.pageSize) break@searchLoop
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
        VaultPage(
            matches,
            if (matches.size == query.pageSize) (requestedOffset + matches.size).toString() else null,
            generation
        )
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
    ): Result<VaultNodeMetadata> = mutate(vaultId) { session ->
        val parent = session.resolveDirectory(parentId)
        val snapshot = session.readDirectory(parent)
        val contentKey = VaultCryptography.randomBytes(32)
        val objectId = VaultObjectId.fromRandomBytes(VaultCryptography.randomBytes(32))
        val objectPath = objectId.shardedPath()
        try {
            val normalized = normalizeMutationName(name)
            ensureNameAvailable(snapshot, normalized)
            fileCodec.writeObject(
                session.directory,
                objectPath,
                session.id,
                objectId,
                1L,
                contentKey,
                ByteArrayInputStream(ByteArray(0))
            )
            val entry = VaultManifestEntry(
                NodeId.random(), normalized, VaultNodeKind.FILE, 1L, System.currentTimeMillis(),
                0L, mimeType, objectId, null, contentKey.copyOf()
            )
            val prepared = directoryCodec.prepare(
                session.id, parent.id, parent.key, snapshot.generation + 1L, snapshot.entries + entry
            )
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
            if (!transactionManager.hasPendingCommit(session.directory)) session.directory.delete(objectPath)
            throw error
        } finally {
            contentKey.fill(0)
            snapshot.clearProtectedKeys()
            parent.key.fill(0)
        }
    }

    override suspend fun rename(ref: VaultNodeRef, newName: String): Result<VaultNodeMetadata> = mutate(ref.vaultId) { session ->
        val (parent, snapshot, existing) = resolveStableEntry(session, ref)
        try {
            val normalized = normalizeMutationName(newName)
            ensureNameAvailable(snapshot, normalized, existing.nodeId)
            val replacement = existing.copy(
                name = normalized,
                modifiedAtMillis = System.currentTimeMillis(),
                protectedKey = existing.protectedKey.copyOf()
            )
            val entries = snapshot.entries.map { if (it.nodeId == existing.nodeId) replacement else it }
            val prepared = directoryCodec.prepare(
                session.id, parent.id, parent.key, snapshot.generation + 1L, entries
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

    override suspend fun deletePermanently(ref: VaultNodeRef): Result<Unit> = mutate(ref.vaultId) { session ->
        val (parent, snapshot, existing) = resolveStableEntry(session, ref)
        try {
            val obsolete = collectObsoleteSubtree(session, existing)
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
                if (entry.kind != VaultNodeKind.FILE) throw VaultFailure.InvalidPath("File is unavailable")
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

    override suspend fun verify(vaultId: VaultId, mode: VaultHealthMode): Result<VaultHealthReport> =
        withSession(vaultId) { session -> verifyHealth(session, mode) }

    override suspend fun cleanupOrphans(
        vaultId: VaultId,
        confirmedObjectIds: Set<VaultObjectId>
    ): Result<Int> = mutate(vaultId) { session ->
        if (confirmedObjectIds.isEmpty()) return@mutate 0
        val report = verifyHealth(session, VaultHealthMode.QUICK)
        if (!report.orphanObjectIds.containsAll(confirmedObjectIds)) {
            throw VaultFailure.ConcurrentMutation()
        }
        confirmedObjectIds.count { objectId -> session.directory.delete(objectId.shardedPath()) }
    }

    override suspend fun recoverTransactions(vaultId: VaultId): Result<Unit> = mutate(vaultId) { session ->
        transactionManager.recover(session.directory, session.id, session.masterSecret)
        Unit
    }

    internal fun reserveImport(vaultId: VaultId, selectionLease: VaultSessionLease? = null): String? {
        if (selectionLease != null && (selectionLease !is VaultSessionLeaseImpl || !selectionLease.belongsTo(vaultId))) {
            selectionLease.close()
            return null
        }
        val interactive = sessions[vaultId.value] ?: run {
            selectionLease?.close()
            return null
        }
        val operation = runCatching { interactive.copyForOperation() }.getOrNull() ?: run {
            selectionLease?.close()
            return null
        }
        val token = java.util.UUID.randomUUID().toString()
        importReservations[token] = operation
        selectionLease?.close()
        return token
    }

    internal fun releaseImportReservation(token: String) {
        importReservations.remove(token)?.destroy()
    }

    internal suspend fun importUris(
        token: String,
        destination: VaultPath,
        sourceUris: List<String>,
        onProgress: (Int, Int, Long, Long?, String?) -> Unit
    ): Result<VaultBatchResult> = withContext(dispatchers.io) {
        val operationSession = importReservations[token]
            ?: return@withContext Result.failure(VaultFailure.ImportUnavailable("Import session expired"))
        try {
            Result.success(importEngine.import(operationSession, destination, sourceUris, onProgress))
        } catch (error: Throwable) {
            if (error is CancellationException) throw error
            Result.failure(error)
        } finally {
            releaseImportReservation(token)
        }
    }

    private suspend fun <T> withSession(vaultId: VaultId, block: suspend (VaultSessionRecord) -> T): Result<T> =
        withContext(dispatchers.io) {
            val lease = holdSession(vaultId).getOrElse { return@withContext Result.failure(it) }
            val session = sessions[vaultId.value]
            if (session == null) {
                lease.close()
                return@withContext Result.failure(VaultFailure.Locked(vaultId))
            }
            try {
                Result.success(block(session))
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                Result.failure(error)
            } finally {
                lease.close()
            }
        }

    private suspend fun <T> mutate(vaultId: VaultId, block: suspend (VaultSessionRecord) -> T): Result<T> =
        withSession(vaultId) { session -> session.mutationMutex.withLock { block(session) } }

    private fun collectObsoleteSubtree(session: VaultSessionRecord, root: VaultManifestEntry): Set<String> {
        if (root.kind == VaultNodeKind.FILE) return setOf(requireNotNull(root.objectId).shardedPath())
        val obsolete = mutableSetOf<String>()
        val queue = ArrayDeque<Pair<DirectoryId, ByteArray>>()
        queue += requireNotNull(root.childDirectoryId) to root.protectedKey.copyOf()
        while (queue.isNotEmpty()) {
            val (directoryId, key) = queue.removeFirst()
            try {
                val snapshot = session.readDirectory(directoryId, key)
                try {
                    obsolete += snapshot.pageObjectIds.map(VaultObjectId::shardedPath)
                    obsolete += VaultDirectoryManifestCodec.rootSlot(directoryId, 0L)
                    obsolete += VaultDirectoryManifestCodec.rootSlot(directoryId, 1L)
                    snapshot.entries.forEach { entry ->
                        when (entry.kind) {
                            VaultNodeKind.FILE -> obsolete += requireNotNull(entry.objectId).shardedPath()
                            VaultNodeKind.DIRECTORY -> queue += requireNotNull(entry.childDirectoryId) to entry.protectedKey.copyOf()
                        }
                    }
                } finally {
                    snapshot.clearProtectedKeys()
                }
            } finally {
                key.fill(0)
            }
        }
        return obsolete
    }

    private fun verifyHealth(session: VaultSessionRecord, mode: VaultHealthMode): VaultHealthReport {
        val issues = mutableListOf<VaultHealthIssue>()
        val referencedObjects = mutableSetOf<VaultObjectId>()
        val visitedDirectories = mutableSetOf<DirectoryId>()
        val queue = ArrayDeque<Pair<DirectoryId, ByteArray>>()
        val root = session.root()
        queue += root.id to root.key
        var rootGeneration = 0L
        var checkedObjects = 0L
        var checkedChunks = 0L

        headerCodec.readPublic(session.directory).fold(
            onSuccess = { header ->
                if (header.id != session.id) issues += VaultHealthIssue(
                    VaultHealthSeverity.ERROR,
                    "header_identity",
                    message = "Public headers identify a different vault"
                )
            },
            onFailure = { error -> issues += VaultHealthIssue(
                VaultHealthSeverity.ERROR,
                "header_damage",
                message = error.message ?: "Public headers are damaged"
            ) }
        )
        if (transactionManager.hasPendingCommit(session.directory)) {
            issues += VaultHealthIssue(
                VaultHealthSeverity.ERROR,
                "pending_transaction",
                message = "A committed transaction still requires recovery"
            )
        }

        while (queue.isNotEmpty()) {
            val (directoryId, key) = queue.removeFirst()
            if (!visitedDirectories.add(directoryId)) {
                key.fill(0)
                issues += VaultHealthIssue(
                    VaultHealthSeverity.ERROR,
                    "directory_cycle",
                    directoryId.value,
                    "A directory is referenced more than once"
                )
                continue
            }
            try {
                val snapshot = try {
                    session.readDirectory(directoryId, key)
                } catch (error: Throwable) {
                    issues += VaultHealthIssue(
                        VaultHealthSeverity.ERROR,
                        "manifest_damage",
                        directoryId.value,
                        error.message ?: "Directory metadata is damaged"
                    )
                    continue
                }
                try {
                    if (directoryId == VaultSessionRecord.ROOT_DIRECTORY_ID) rootGeneration = snapshot.generation
                    val allReferencedPages = directoryCodec.referencedPageObjectIds(
                        session.directory,
                        session.id,
                        directoryId,
                        key
                    )
                    referencedObjects += allReferencedPages
                    checkedObjects += allReferencedPages.size
                    snapshot.entries.forEach { entry ->
                        when (entry.kind) {
                            VaultNodeKind.DIRECTORY -> queue +=
                                requireNotNull(entry.childDirectoryId) to entry.protectedKey.copyOf()
                            VaultNodeKind.FILE -> {
                                val objectId = requireNotNull(entry.objectId)
                                referencedObjects += objectId
                                checkedObjects++
                                try {
                                    fileCodec.openObject(
                                        session.directory,
                                        objectId.shardedPath(),
                                        session.id,
                                        objectId,
                                        entry.revision,
                                        entry.protectedKey
                                    ).use { reader ->
                                        if (reader.sizeBytes != entry.sizeBytes) {
                                            throw VaultFailure.IntegrityFailed("Declared file size does not match its object")
                                        }
                                        if (mode == VaultHealthMode.FULL) {
                                            val buffer = ByteArray(VaultFileCodec.DEFAULT_CHUNK_SIZE_BYTES)
                                            var position = 0L
                                            try {
                                                while (position < reader.sizeBytes) {
                                                    val count = reader.readAt(position, buffer, 0, buffer.size)
                                                    if (count <= 0) throw VaultFailure.IntegrityFailed("Encrypted file ended early")
                                                    position += count
                                                    checkedChunks++
                                                }
                                            } finally {
                                                buffer.fill(0)
                                            }
                                        }
                                    }
                                } catch (error: Throwable) {
                                    issues += VaultHealthIssue(
                                        VaultHealthSeverity.ERROR,
                                        "object_damage",
                                        objectId.value,
                                        error.message ?: "Encrypted file object is damaged"
                                    )
                                }
                            }
                        }
                    }
                } finally {
                    snapshot.clearProtectedKeys()
                }
            } finally {
                key.fill(0)
            }
        }

        val physicalObjectIds = session.directory.listFiles(OBJECTS_DIRECTORY).asSequence()
            .filterNot { it.isDirectory }
            .mapNotNull { physical ->
                physical.relativePath.substringAfterLast('/').removeSuffix(".obj")
                    .let { runCatching { VaultObjectId.of(it) }.getOrNull() }
            }.toSet()
        val orphans = physicalObjectIds - referencedObjects
        if (orphans.isNotEmpty()) {
            issues += VaultHealthIssue(
                VaultHealthSeverity.WARNING,
                "orphan_objects",
                message = "${orphans.size} unreachable encrypted objects can be cleaned up"
            )
        }
        referencedObjects.filterNot(physicalObjectIds::contains).forEach { missing ->
            issues += VaultHealthIssue(
                VaultHealthSeverity.ERROR,
                "missing_object",
                missing.value,
                "Referenced encrypted object is missing"
            )
        }
        return VaultHealthReport(
            vaultId = session.id,
            mode = mode,
            generation = rootGeneration,
            checkedObjects = checkedObjects,
            checkedChunks = checkedChunks,
            orphanObjectIds = orphans,
            issues = issues
        )
    }

    private fun resolveStableEntry(
        session: VaultSessionRecord,
        ref: VaultNodeRef
    ): Triple<ResolvedVaultDirectory, VaultDirectorySnapshot, VaultManifestEntry> {
        if (ref.vaultId != session.id) throw VaultFailure.StaleRegistration()
        val parent = session.resolveDirectory(ref.parentId)
        try {
            val snapshot = session.readDirectory(parent)
            val entry = snapshot.entries.firstOrNull { it.nodeId == ref.nodeId }?.copyDefensively()
                ?: run {
                    snapshot.clearProtectedKeys()
                    throw VaultFailure.NodeNotFound(ref.nodeId)
                }
            return Triple(parent, snapshot, entry)
        } catch (error: Throwable) {
            parent.key.fill(0)
            throw error
        }
    }

    private fun createStableDirectory(
        session: VaultSessionRecord,
        parentId: DirectoryId,
        name: String
    ): VaultNodeMetadata {
        val parent = session.resolveDirectory(parentId)
        val snapshot = session.readDirectory(parent)
        val childKey = VaultCryptography.randomBytes(32)
        val childId = DirectoryId.random()
        try {
            ensureNameAvailable(snapshot, name)
            val entry = VaultManifestEntry(
                NodeId.random(), name, VaultNodeKind.DIRECTORY, 1L, System.currentTimeMillis(),
                0L, null, null, childId, childKey.copyOf()
            )
            val parentPrepared = directoryCodec.prepare(
                session.id, parent.id, parent.key, snapshot.generation + 1L, snapshot.entries + entry
            )
            val childPrepared = directoryCodec.prepare(session.id, childId, childKey, 0L, emptyList())
            session.cacheDirectoryKey(childId, childKey)
            transactionManager.commit(
                session.directory,
                session.id,
                session.masterSecret,
                listOf(
                    VaultPreparedDirectory(parentPrepared, parent.key.copyOf()),
                    VaultPreparedDirectory(childPrepared, childKey.copyOf())
                ),
                emptySet(),
                emptySet()
            )
            return entry.toMetadata(session.id, parent.id)
        } finally {
            childKey.fill(0)
            snapshot.clearProtectedKeys()
            parent.key.fill(0)
        }
    }

    private fun requestLock(vaultId: VaultId) {
        val session = sessions[vaultId.value] ?: return
        val removed = synchronized(session) {
            session.lockRequested = true
            sessions.remove(vaultId.value, session)
        }
        if (!removed) return
        session.destroy()
        publishUnlockedIds()
    }

    private fun releaseHold(session: VaultSessionRecord) {
        synchronized(session) {
            if (session.holdCount > 0) session.holdCount--
        }
        publishUnlockedIds()
        applicationScope.launch(dispatchers.io) { refreshVaults() }
    }

    private fun publishUnlockedIds() {
        _unlockedVaultIds.value = sessions.values.filterNot(VaultSessionRecord::lockRequested)
            .mapTo(mutableSetOf()) { it.id }
        _vaults.value = _vaults.value.map { it.copy(isUnlocked = it.id in _unlockedVaultIds.value) }
    }

    private inline fun <T> catchingCancellation(block: () -> T): Result<T> = try {
        Result.success(block())
    } catch (error: Throwable) {
        if (error is CancellationException) throw error
        Result.failure(error)
    }
}

private fun normalizeMutationName(name: String): String = try {
    VaultName.of(name).value
} catch (error: IllegalArgumentException) {
    throw VaultFailure.InvalidName(error.message ?: "Invalid vault item name", error)
}

private fun ensureNameAvailable(snapshot: VaultDirectorySnapshot, name: String, except: NodeId? = null) {
    val comparison = VaultName.of(name).comparisonKey
    val conflict = snapshot.entries.firstOrNull {
        it.nodeId != except && VaultName.of(it.name).comparisonKey == comparison
    }
    if (conflict != null) throw VaultFailure.NameConflict(conflict.name)
}

private fun VaultManifestEntry.toLegacyNode(parent: VaultPath): VaultNode = VaultNode(
    id = nodeId.value,
    path = parent.resolve(name),
    sizeBytes = sizeBytes,
    modifiedAtMillis = modifiedAtMillis,
    isDirectory = kind == VaultNodeKind.DIRECTORY,
    mimeType = mimeType
)

private data class SearchFrame(
    val directoryId: DirectoryId,
    val key: ByteArray,
    val parentNames: List<String>
)

private fun VaultManifestEntry.toMetadata(vaultId: VaultId, parentId: DirectoryId): VaultNodeMetadata =
    VaultNodeMetadata(
        ref = VaultNodeRef(
            vaultId = vaultId,
            nodeId = nodeId,
            parentId = parentId,
            capabilities = VaultNodeCapabilities(),
            directoryId = childDirectoryId
        ),
        name = name,
        kind = kind,
        sizeBytes = sizeBytes,
        modifiedAtMillis = modifiedAtMillis,
        revision = revision,
        mimeType = mimeType
    )

private fun VaultListOptions.comparator(): Comparator<VaultNodeMetadata> {
    val fieldComparator = when (sortField) {
        VaultSortField.NAME -> compareBy<VaultNodeMetadata> { VaultName.of(it.name).comparisonKey }
        VaultSortField.MODIFIED -> compareBy { it.modifiedAtMillis }
        VaultSortField.SIZE -> compareBy { it.sizeBytes }
        VaultSortField.TYPE -> compareBy<VaultNodeMetadata> { it.mimeType.orEmpty() }
            .thenBy { VaultName.of(it.name).comparisonKey }
    }
    val directed = if (direction == VaultSortDirection.ASCENDING) fieldComparator else fieldComparator.reversed()
    return compareByDescending<VaultNodeMetadata> { it.isDirectory }.then(directed)
}

private fun String?.toPageOffset(maximum: Int): Int {
    if (this == null) return 0
    val offset = toIntOrNull() ?: throw VaultFailure.InvalidPath("Invalid page token")
    if (offset !in 0..maximum) throw VaultFailure.InvalidPath("Expired page token")
    return offset
}
