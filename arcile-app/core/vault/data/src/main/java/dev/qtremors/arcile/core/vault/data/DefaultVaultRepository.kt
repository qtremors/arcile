package dev.qtremors.arcile.core.vault.data

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.qtremors.arcile.core.runtime.di.ApplicationScope
import dev.qtremors.arcile.core.runtime.di.ArcileDispatchers
import dev.qtremors.arcile.core.vault.crypto.VaultFileCodec
import dev.qtremors.arcile.core.vault.crypto.FileVaultDirectory
import dev.qtremors.arcile.core.vault.crypto.VaultIndex
import dev.qtremors.arcile.core.vault.crypto.VaultIndexCodec
import dev.qtremors.arcile.core.vault.crypto.VaultIndexEntry
import dev.qtremors.arcile.core.vault.crypto.VaultManifestCodec
import dev.qtremors.arcile.core.vault.domain.VaultFailure
import dev.qtremors.arcile.core.vault.domain.VaultId
import dev.qtremors.arcile.core.vault.domain.VaultLocationKind
import dev.qtremors.arcile.core.vault.domain.VaultNode
import dev.qtremors.arcile.core.vault.domain.VaultPath
import dev.qtremors.arcile.core.vault.domain.VaultRepository
import dev.qtremors.arcile.core.vault.domain.VaultSeekableReader
import dev.qtremors.arcile.core.vault.domain.VaultSessionLease
import dev.qtremors.arcile.core.vault.domain.VaultSummary
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DefaultVaultRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val dispatchers: ArcileDispatchers,
    @param:ApplicationScope private val applicationScope: CoroutineScope
) : VaultRepository {
    private val manifestCodec = VaultManifestCodec()
    private val indexCodec = VaultIndexCodec()
    private val fileCodec = VaultFileCodec()
    private val vaultRoot = File(context.noBackupFilesDir, ROOT_DIRECTORY)
    private val sessions = ConcurrentHashMap<String, VaultSessionRecord>()
    private val locations = ConcurrentHashMap<String, VaultLocationRecord>()
    private val importReservations = ConcurrentHashMap<String, VaultId>()
    private val lifecycleMutex = Mutex()
    private val locationRegistry = VaultLocationRegistry(context)
    private val importEngine = VaultImportEngine(context)
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
        val appPrivate = vaultRoot.listFiles()
            .orEmpty()
            .asSequence()
            .filter { it.isDirectory && !it.name.startsWith(STAGING_PREFIX) }
            .mapNotNull { directory ->
                manifestCodec.readPublic(directory).getOrNull()?.let { manifest ->
                    locations[manifest.id.value] = VaultLocationRecord(
                        FileVaultDirectory(directory),
                        VaultLocationKind.APP_PRIVATE
                    )
                    VaultSummary(
                        id = manifest.id,
                        name = manifest.publicName,
                        locationKind = VaultLocationKind.APP_PRIVATE,
                        createdAtMillis = manifest.createdAtMillis,
                        isUnlocked = sessions.containsKey(manifest.id.value)
                    )
                }
            }
            .toList()
        val appPrivateIds = appPrivate.mapTo(mutableSetOf()) { it.id.value }
        val unavailableIds = mutableSetOf<VaultId>()
        val external = locationRegistry.load().mapNotNull { pointer ->
            val access = FileVaultDirectory(File(pointer.path))
            val pointerId = runCatching { VaultId.of(pointer.vaultId) }.getOrNull() ?: return@mapNotNull null
            if (pointerId.value in appPrivateIds) return@mapNotNull null
            runCatching {
                val manifest = manifestCodec.readPublic(access).getOrThrow()
                require(manifest.id == pointerId) { "Vault identity changed" }
                    locations[manifest.id.value] = VaultLocationRecord(
                        access,
                        VaultLocationKind.USER_FOLDER
                    )
                    locationRegistry.put(
                        pointer.copy(cachedName = manifest.publicName, cachedCreatedAtMillis = manifest.createdAtMillis)
                    )
                    VaultSummary(
                        id = manifest.id,
                        name = manifest.publicName,
                        locationKind = VaultLocationKind.USER_FOLDER,
                        createdAtMillis = manifest.createdAtMillis,
                        isUnlocked = sessions.containsKey(manifest.id.value)
                    )
                }.getOrElse {
                    unavailableIds += pointerId
                    VaultSummary(
                        id = pointerId,
                        name = pointer.cachedName,
                        locationKind = VaultLocationKind.USER_FOLDER,
                        createdAtMillis = pointer.cachedCreatedAtMillis,
                        isUnlocked = false,
                        isAvailable = false
                    )
                }
        }
        val summaries = (appPrivate + external).distinctBy { it.id }.sortedBy { it.name.lowercase() }
        val liveIds = summaries.filter(VaultSummary::isAvailable).mapTo(mutableSetOf()) { it.id.value }
        locations.keys.removeIf { it !in liveIds }
        unavailableIds.forEach(::requestLock)
        _vaults.value = summaries
        publishUnlockedIds()
    }

    override suspend fun createAppPrivateVault(name: String, password: CharArray): Result<VaultId> =
        withContext(dispatchers.io) {
            lifecycleMutex.withLock {
                try {
                    val created = appPrivateManager.create(name, password)
                    locations[created.id.value] = VaultLocationRecord(created.access, VaultLocationKind.APP_PRIVATE)
                    sessions[created.id.value] = VaultSessionRecord(
                        created.id,
                        created.access,
                        created.masterKey,
                        created.index
                    )
                    refreshVaults()
                    Result.success(created.id)
                } catch (error: Throwable) {
                    password.fill('\u0000')
                    if (error is CancellationException) throw error
                    Result.failure(error)
                }
            }
        }

    override suspend fun createUserFolderVault(
        path: String,
        name: String,
        password: CharArray
    ): Result<VaultId> = withContext(dispatchers.io) {
        lifecycleMutex.withLock {
            try {
                val created = externalManager.create(path, name, password)
                val key = requireNotNull(created.masterKey)
                val index = requireNotNull(created.index)
                locations[created.id.value] = VaultLocationRecord(
                    created.access,
                    VaultLocationKind.USER_FOLDER
                )
                sessions[created.id.value] = VaultSessionRecord(created.id, created.access, key, index)
                refreshVaults()
                Result.success(created.id)
            } catch (error: Throwable) {
                password.fill('\u0000')
                if (error is CancellationException) throw error
                Result.failure(error)
            }
        }
    }

    override suspend fun attachExistingVault(path: String): Result<VaultId> = withContext(dispatchers.io) {
        lifecycleMutex.withLock {
            try {
                val attached = externalManager.attach(path)
                val current = locations[attached.id.value]
                if (current?.kind == VaultLocationKind.APP_PRIVATE) {
                    throw VaultFailure.Unavailable("A different vault with this identity is already registered")
                }
                if (sessions.containsKey(attached.id.value) && current?.access?.stableId != attached.access.stableId) {
                    throw VaultFailure.Unavailable("Lock this vault before reconnecting a different folder")
                }
                externalManager.register(attached)
                locations[attached.id.value] = VaultLocationRecord(
                    attached.access,
                    VaultLocationKind.USER_FOLDER
                )
                refreshVaults()
                Result.success(attached.id)
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                Result.failure(error)
            }
        }
    }

    override suspend fun unlock(vaultId: VaultId, password: CharArray): Result<Unit> =
        withContext(dispatchers.io) {
            lifecycleMutex.withLock {
            if (sessions.containsKey(vaultId.value)) {
                password.fill('\u0000')
                return@withLock Result.success(Unit)
            }
            val directory = locations[vaultId.value]
                ?: run {
                    refreshVaults()
                    locations[vaultId.value]
                }
            if (directory == null) {
                password.fill('\u0000')
                return@withLock Result.failure<Unit>(VaultFailure.NotFound(vaultId))
            }

            var masterKey: ByteArray? = null
            try {
                val opened = manifestCodec.open(directory.access, password).getOrThrow()
                require(opened.id == vaultId) { "Vault identity does not match its registration" }
                masterKey = opened.masterKey
                val index = indexCodec.read(directory.access, vaultId, masterKey)
                sessions[vaultId.value] = VaultSessionRecord(vaultId, directory.access, masterKey, index)
                masterKey = null
                refreshVaults()
                Result.success(Unit)
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                Result.failure(error)
            } finally {
                masterKey?.fill(0)
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
            if (!directory.isRoot) {
                val directoryEntry = session.index.entries.firstOrNull { it.path == directory.value }
                if (directoryEntry?.isDirectory != true) throw VaultFailure.InvalidPath("Folder is unavailable")
            }
            session.index.entries.asSequence()
                .filter { VaultPath.of(it.path).parent == directory }
                .map(VaultIndexEntry::toNode)
                .sortedWith(compareByDescending<VaultNode> { it.isDirectory }.thenBy { it.name.lowercase() })
                .toList()
        }

    override suspend fun createDirectory(
        vaultId: VaultId,
        parent: VaultPath,
        name: String
    ): Result<VaultNode> = mutate(vaultId) { session ->
        requireDirectory(session.index, parent)
        val target = parent.resolve(name)
        ensureAvailable(session.index, target)
        val entry = VaultIndexEntry(
            id = UUID.randomUUID().toString(),
            path = target.value,
            objectName = null,
            sizeBytes = 0L,
            modifiedAtMillis = System.currentTimeMillis(),
            isDirectory = true,
            mimeType = null
        )
        persist(session, session.index.entries + entry)
        entry.toNode()
    }

    override suspend fun rename(vaultId: VaultId, path: VaultPath, newName: String): Result<VaultNode> =
        mutate(vaultId) { session ->
            if (path.isRoot) throw VaultFailure.InvalidPath("The vault root cannot be renamed")
            val existing = session.index.entries.firstOrNull { it.path == path.value }
                ?: throw VaultFailure.InvalidPath("Item is unavailable")
            val target = requireNotNull(path.parent).resolve(newName)
            if (!target.value.equals(path.value, ignoreCase = true)) ensureAvailable(session.index, target)
            val now = System.currentTimeMillis()
            val updated = session.index.entries.map { entry ->
                when {
                    entry.path == path.value -> entry.copy(path = target.value, modifiedAtMillis = now)
                    existing.isDirectory && entry.path.startsWith("${path.value}/") ->
                        entry.copy(path = target.value + entry.path.removePrefix(path.value))
                    else -> entry
                }
            }
            persist(session, updated)
            updated.first { it.id == existing.id }.toNode()
        }

    override suspend fun delete(vaultId: VaultId, path: VaultPath): Result<Unit> = mutate(vaultId) { session ->
        if (path.isRoot) throw VaultFailure.InvalidPath("The vault root cannot be deleted")
        val existing = session.index.entries.firstOrNull { it.path == path.value }
            ?: throw VaultFailure.InvalidPath("Item is unavailable")
        val removed = session.index.entries.filter { entry ->
            entry.path == path.value || (existing.isDirectory && entry.path.startsWith("${path.value}/"))
        }
        persist(session, session.index.entries - removed.toSet())
        removed.mapNotNull(VaultIndexEntry::objectName).forEach { objectName ->
                session.directory.delete("$OBJECTS_DIRECTORY/$objectName")
        }
    }

    override suspend fun readBytes(
        vaultId: VaultId,
        path: VaultPath,
        maximumBytes: Long
    ): Result<ByteArray> = withContext(dispatchers.io) {
        val reader = openReader(vaultId, path).getOrElse { return@withContext Result.failure(it) }
        reader.use {
            if (it.sizeBytes > maximumBytes || it.sizeBytes > Int.MAX_VALUE) {
                return@withContext Result.failure(VaultFailure.FileTooLarge(it.sizeBytes, maximumBytes))
            }
            val output = ByteArray(it.sizeBytes.toInt())
            var offset = 0
            while (offset < output.size) {
                val read = it.readAt(offset.toLong(), output, offset, output.size - offset)
                if (read <= 0) return@withContext Result.failure(
                    VaultFailure.IntegrityFailed("Encrypted file ended before its authenticated size")
                )
                offset += read
            }
            Result.success(output)
        }
    }

    override fun openReader(vaultId: VaultId, path: VaultPath): Result<VaultSeekableReader> {
        val lease = holdSession(vaultId).getOrElse { return Result.failure(it) }
        val session = sessions[vaultId.value]
        if (session == null) {
            lease.close()
            return Result.failure<VaultSeekableReader>(VaultFailure.Locked(vaultId))
        }
        return try {
            val entry = session.index.entries.firstOrNull { it.path == path.value && !it.isDirectory }
                ?: throw VaultFailure.InvalidPath("File is unavailable")
            val objectName = requireNotNull(entry.objectName)
            val delegate = fileCodec.open(
                session.directory,
                "$OBJECTS_DIRECTORY/$objectName",
                vaultId,
                session.masterKey
            )
            Result.success(VaultLeasedReader(delegate, lease))
        } catch (error: Throwable) {
            lease.close()
            Result.failure(error)
        }
    }

    internal fun reserveImport(vaultId: VaultId, selectionLease: VaultSessionLease? = null): String? {
        val lease = when (selectionLease) {
            null -> holdSession(vaultId).getOrNull() as? VaultSessionLeaseImpl
            is VaultSessionLeaseImpl -> selectionLease.takeIf { it.belongsTo(vaultId) }
            else -> null
        } ?: return null
        val token = UUID.randomUUID().toString()
        if (!lease.detachToReservation()) return null
        importReservations[token] = vaultId
        return token
    }

    internal fun releaseImportReservation(token: String) {
        val vaultId = importReservations.remove(token) ?: return
        sessions[vaultId.value]?.let(::releaseHold)
    }

    internal suspend fun importUris(
        token: String,
        destination: VaultPath,
        sourceUris: List<String>,
        onProgress: (completedItems: Int, totalItems: Int, copiedBytes: Long, totalBytes: Long?, currentName: String?) -> Unit
    ): Result<Unit> = withContext(dispatchers.io) {
        val vaultId = importReservations[token]
            ?: return@withContext Result.failure(VaultFailure.ImportUnavailable("Import session expired"))
        val session = sessions[vaultId.value]
            ?: return@withContext Result.failure(VaultFailure.Locked(vaultId))
        try {
            importEngine.import(session, destination, sourceUris, onProgress)
            Result.success(Unit)
        } catch (error: Throwable) {
            if (error is CancellationException) throw error
            Result.failure(error)
        } finally {
            releaseImportReservation(token)
        }
    }

    private suspend fun <T> withSession(
        vaultId: VaultId,
        block: suspend (VaultSessionRecord) -> T
    ): Result<T> = withContext(dispatchers.io) {
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

    private suspend fun <T> mutate(
        vaultId: VaultId,
        block: suspend (VaultSessionRecord) -> T
    ): Result<T> = withSession(vaultId) { session ->
        session.mutationMutex.withLock { block(session) }
    }

    private fun persist(session: VaultSessionRecord, entries: List<VaultIndexEntry>) {
        val next = session.index.copy(
            generation = session.index.generation + 1L,
            entries = entries
        )
        indexCodec.write(session.directory, session.id, session.masterKey, next)
        session.index = next
    }

    private fun requestLock(vaultId: VaultId) {
        val session = sessions[vaultId.value] ?: return
        val destroyNow = synchronized(session) {
            if (session.holdCount > 0) {
                session.lockRequested = true
                false
            } else {
                sessions.remove(vaultId.value, session)
            }
        }
        if (destroyNow) session.destroy()
        publishUnlockedIds()
    }

    private fun releaseHold(session: VaultSessionRecord) {
        val destroyNow = synchronized(session) {
            if (session.holdCount > 0) session.holdCount--
            if (session.holdCount == 0 && session.lockRequested) {
                sessions.remove(session.id.value, session)
            } else {
                false
            }
        }
        if (destroyNow) session.destroy()
        publishUnlockedIds()
        applicationScope.launch(dispatchers.io) { refreshVaults() }
    }

    private fun publishUnlockedIds() {
        _unlockedVaultIds.value = sessions.values
            .filterNot(VaultSessionRecord::lockRequested)
            .mapTo(mutableSetOf()) { it.id }
        _vaults.value = _vaults.value.map { it.copy(isUnlocked = it.id in _unlockedVaultIds.value) }
    }

}
