package dev.qtremors.arcile.core.vault.data

import android.content.Context
import dev.qtremors.arcile.core.runtime.di.ArcileDispatchers
import dev.qtremors.arcile.core.vault.crypto.*
import dev.qtremors.arcile.core.vault.domain.*
import java.io.File
import java.util.ArrayDeque
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

internal abstract class VaultRepositoryFoundation(
    protected val context: Context,
    protected val dispatchers: ArcileDispatchers,
    protected val applicationScope: CoroutineScope,
    protected val portableLocationResolver: VaultPortableLocationResolver
) {
    protected val headerCodec = VaultManifestCodec()
    protected val directoryCodec = VaultDirectoryManifestCodec()
    protected val fileCodec = VaultFileCodec()
    protected val transactionManager = VaultTransactionManager(directoryCodec)
    protected val transferEngine = VaultTransferEngine(directoryCodec, fileCodec, transactionManager)
    protected val transferMutex = Mutex()
    protected val vaultRoot = File(context.noBackupFilesDir, ROOT_DIRECTORY)
    protected val sessions = ConcurrentHashMap<String, VaultSessionRecord>()
    protected val locations = ConcurrentHashMap<String, VaultLocationRecord>()
    protected val importReservations = ConcurrentHashMap<String, VaultSessionRecord>()
    protected val lifecycleMutex = Mutex()
    protected val locationRegistry = VaultLocationRegistry(context)
    protected val importEngine = VaultImportEngine(context, directoryCodec, fileCodec, transactionManager)
    protected val externalManager = VaultExternalManager(locationRegistry)
    protected val appPrivateManager = VaultAppPrivateManager(vaultRoot)
    protected val biometricStore = VaultBiometricStore(context)
    protected val mutableVaults = MutableStateFlow<List<VaultSummary>>(emptyList())
    protected val mutableUnlockedVaultIds = MutableStateFlow<Set<VaultId>>(emptySet())
    protected val transferProgress = MutableSharedFlow<VaultTransferProgress>(extraBufferCapacity = 64)

    protected abstract suspend fun refreshVaults()

    protected fun holdSessionRecord(vaultId: VaultId): Result<VaultSessionLease> {
        val session = sessions[vaultId.value] ?: return Result.failure(VaultFailure.Locked(vaultId))
        synchronized(session) {
            if (sessions[vaultId.value] !== session || session.lockRequested) {
                return Result.failure(VaultFailure.Locked(vaultId))
            }
            session.holdCount++
        }
        return Result.success(VaultSessionLeaseImpl(session, ::releaseHold))
    }

    protected suspend fun <T> withSession(
        vaultId: VaultId,
        block: suspend (VaultSessionRecord) -> T
    ): Result<T> = withContext(dispatchers.io) {
        val lease = holdSessionRecord(vaultId).getOrElse { return@withContext Result.failure(it) }
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

    protected suspend fun <T> mutate(
        vaultId: VaultId,
        block: suspend (VaultSessionRecord) -> T
    ): Result<T> = withSession(vaultId) { session ->
        session.mutationMutex.withLock { block(session) }
    }

    protected fun collectObsoleteSubtree(
        session: VaultSessionRecord,
        root: VaultManifestEntry
    ): Set<String> {
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
                            VaultNodeKind.DIRECTORY -> queue +=
                                requireNotNull(entry.childDirectoryId) to entry.protectedKey.copyOf()
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

    protected fun resolveStableEntry(
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

    protected fun createStableDirectory(
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

    protected fun requestLock(vaultId: VaultId) {
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

    protected fun publishUnlockedIds() {
        mutableUnlockedVaultIds.value = sessions.values.filterNot(VaultSessionRecord::lockRequested)
            .mapTo(mutableSetOf()) { it.id }
        mutableVaults.value = mutableVaults.value.map {
            it.copy(isUnlocked = it.id in mutableUnlockedVaultIds.value)
        }
    }

    protected inline fun <T> catchingCancellation(block: () -> T): Result<T> = try {
        Result.success(block())
    } catch (error: Throwable) {
        if (error is CancellationException) throw error
        Result.failure(error)
    }
}

internal fun normalizeMutationName(name: String): String = try {
    VaultName.of(name).value
} catch (error: IllegalArgumentException) {
    throw VaultFailure.InvalidName(error.message ?: "Invalid vault item name", error)
}

internal fun ensureNameAvailable(
    snapshot: VaultDirectorySnapshot,
    name: String,
    except: NodeId? = null
) {
    val comparison = VaultName.of(name).comparisonKey
    val conflict = snapshot.entries.firstOrNull {
        it.nodeId != except && VaultName.of(it.name).comparisonKey == comparison
    }
    if (conflict != null) throw VaultFailure.NameConflict(conflict.name)
}

internal fun VaultManifestEntry.toLegacyNode(parent: VaultPath): VaultNode = VaultNode(
    id = nodeId.value,
    path = parent.resolve(name),
    sizeBytes = sizeBytes,
    modifiedAtMillis = modifiedAtMillis,
    isDirectory = kind == VaultNodeKind.DIRECTORY,
    mimeType = mimeType
)

internal data class SearchFrame(
    val directoryId: DirectoryId,
    val key: ByteArray,
    val parentNames: List<String>
)

internal fun VaultManifestEntry.toMetadata(vaultId: VaultId, parentId: DirectoryId): VaultNodeMetadata =
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

internal fun VaultListOptions.comparator(): Comparator<VaultNodeMetadata> {
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

internal fun String?.toPageOffset(maximum: Int): Int {
    if (this == null) return 0
    val offset = toIntOrNull() ?: throw VaultFailure.InvalidPath("Invalid page token")
    if (offset !in 0..maximum) throw VaultFailure.InvalidPath("Expired page token")
    return offset
}
