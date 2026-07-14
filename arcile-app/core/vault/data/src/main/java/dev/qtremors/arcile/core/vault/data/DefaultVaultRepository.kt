package dev.qtremors.arcile.core.vault.data

import android.content.ContentResolver
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.qtremors.arcile.core.runtime.di.ApplicationScope
import dev.qtremors.arcile.core.runtime.di.ArcileDispatchers
import dev.qtremors.arcile.core.vault.crypto.VaultCryptography
import dev.qtremors.arcile.core.vault.crypto.VaultFileCodec
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
import java.io.ByteArrayOutputStream
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
    private val locations = ConcurrentHashMap<String, File>()
    private val importReservations = ConcurrentHashMap<String, VaultId>()
    private val lifecycleMutex = Mutex()

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
        val summaries = vaultRoot.listFiles()
            .orEmpty()
            .asSequence()
            .filter { it.isDirectory && !it.name.startsWith(STAGING_PREFIX) }
            .mapNotNull { directory ->
                manifestCodec.readPublic(directory).getOrNull()?.let { manifest ->
                    locations[manifest.id.value] = directory
                    VaultSummary(
                        id = manifest.id,
                        name = manifest.publicName,
                        locationKind = VaultLocationKind.APP_PRIVATE,
                        createdAtMillis = manifest.createdAtMillis,
                        isUnlocked = sessions.containsKey(manifest.id.value)
                    )
                }
            }
            .sortedBy { it.name.lowercase() }
            .toList()
        val liveIds = summaries.mapTo(mutableSetOf()) { it.id.value }
        locations.keys.removeIf { it !in liveIds }
        _vaults.value = summaries
        publishUnlockedIds()
    }

    override suspend fun createAppPrivateVault(name: String, password: CharArray): Result<VaultId> =
        withContext(dispatchers.io) {
            lifecycleMutex.withLock {
            val cleanName = try {
                validateVaultName(name)
            } catch (error: Throwable) {
                password.fill('\u0000')
                return@withLock Result.failure(error)
            }
            vaultRoot.mkdirs()
            val target = File(vaultRoot, cleanName)
            if (target.exists() || vaultRoot.listFiles().orEmpty().any { it.name.equals(cleanName, ignoreCase = true) }) {
                password.fill('\u0000')
                return@withLock Result.failure(VaultFailure.NameConflict(cleanName))
            }

            val staging = File(vaultRoot, "$STAGING_PREFIX${UUID.randomUUID()}")
            val id = VaultId.of(UUID.randomUUID().toString())
            val masterKey = VaultCryptography.randomBytes(VaultCryptography.KEY_SIZE_BYTES)
            var openedMasterKey: ByteArray? = null
            try {
                check(staging.mkdir()) { "Unable to create vault staging directory" }
                check(File(staging, OBJECTS_DIRECTORY).mkdir()) { "Unable to create vault object directory" }
                manifestCodec.create(
                    vaultDirectory = staging,
                    id = id,
                    publicName = cleanName,
                    createdAtMillis = System.currentTimeMillis(),
                    password = password,
                    masterKey = masterKey
                )
                indexCodec.create(staging, id, cleanName, masterKey)

                val verifiedManifest = manifestCodec.open(staging, password).getOrThrow()
                openedMasterKey = verifiedManifest.masterKey
                val verifiedIndex = indexCodec.read(staging, id, openedMasterKey)
                require(verifiedIndex.vaultName == cleanName)
                moveDirectory(staging, target)

                locations[id.value] = target
                sessions[id.value] = VaultSessionRecord(id, target, openedMasterKey, verifiedIndex)
                openedMasterKey = null
                refreshVaults()
                Result.success(id)
            } catch (error: Throwable) {
                staging.deleteRecursively()
                if (error is CancellationException) throw error
                Result.failure(error)
            } finally {
                masterKey.fill(0)
                openedMasterKey?.fill(0)
                password.fill('\u0000')
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
                val opened = manifestCodec.open(directory, password).getOrThrow()
                require(opened.id == vaultId) { "Vault identity does not match its registration" }
                masterKey = opened.masterKey
                val index = indexCodec.read(directory, vaultId, masterKey)
                sessions[vaultId.value] = VaultSessionRecord(vaultId, directory, masterKey, index)
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
            File(session.directory, "$OBJECTS_DIRECTORY/$objectName").delete()
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
                File(session.directory, "$OBJECTS_DIRECTORY/$objectName"),
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
            session.mutationMutex.withLock {
                requireDirectory(session.index, destination)
                val sources = VaultUriTreeReader(context.contentResolver).collect(sourceUris)
                val files = sources.filterNot(VaultImportSource::isDirectory)
                val totalBytes = files.mapNotNull { it.sizeBytes }.takeIf { it.size == files.size }?.sum()
                var completed = 0
                var copied = 0L
                sources.forEach { source ->
                    val desiredParent = source.relativeParent.fold(destination) { path, segment ->
                        val desired = path.resolve(segment)
                        val existing = session.index.entries.firstOrNull {
                            it.path.equals(desired.value, ignoreCase = true)
                        }
                        if (existing == null) {
                            val directoryEntry = VaultIndexEntry(
                                id = UUID.randomUUID().toString(),
                                path = desired.value,
                                objectName = null,
                                sizeBytes = 0L,
                                modifiedAtMillis = source.modifiedAtMillis ?: System.currentTimeMillis(),
                                isDirectory = true,
                                mimeType = null
                            )
                            persist(session, session.index.entries + directoryEntry)
                        } else if (!existing.isDirectory) {
                            throw VaultFailure.PathConflict(desired)
                        }
                        desired
                    }
                    if (source.isDirectory) return@forEach
                    val target = uniqueImportPath(session.index, desiredParent, source.name)
                    val objectName = "${UUID.randomUUID()}.off"
                    val part = File(session.directory, "$OBJECTS_DIRECTORY/.$objectName.part")
                    val finalObject = File(session.directory, "$OBJECTS_DIRECTORY/$objectName")
                    val input = context.contentResolver.openInputStream(source.uri)
                        ?: throw VaultFailure.ImportUnavailable("Unable to read ${source.name}")
                    val startCopied = copied
                    var indexed = false
                    try {
                        val writeResult = input.use { stream ->
                            fileCodec.write(part, vaultId, session.masterKey, stream) { itemBytes ->
                                onProgress(completed, files.size, startCopied + itemBytes, totalBytes, source.name)
                            }
                        }
                        moveFile(part, finalObject)
                        val entry = VaultIndexEntry(
                            id = writeResult.fileId,
                            path = target.value,
                            objectName = objectName,
                            sizeBytes = writeResult.sizeBytes,
                            modifiedAtMillis = source.modifiedAtMillis ?: System.currentTimeMillis(),
                            isDirectory = false,
                            mimeType = source.mimeType
                        )
                        persist(session, session.index.entries + entry)
                        indexed = true
                        completed++
                        copied += writeResult.sizeBytes
                        onProgress(completed, files.size, copied, totalBytes, source.name)
                    } finally {
                        part.delete()
                        if (!indexed) finalObject.delete()
                    }
                }
            }
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
