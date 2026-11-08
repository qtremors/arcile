package dev.qtremors.arcile.core.vault.data

import dev.qtremors.arcile.core.runtime.runCatchingCancellable
import android.content.Context
import dev.qtremors.arcile.core.runtime.di.ArcileDispatchers
import dev.qtremors.arcile.core.vault.crypto.VaultFileCodec
import dev.qtremors.arcile.core.vault.crypto.VaultHeaderCodec
import dev.qtremors.arcile.core.vault.domain.*
import java.io.File
import java.nio.file.Files
import java.nio.file.LinkOption
import java.util.ArrayDeque
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

internal abstract class VaultHealthLayer(
    context: Context,
    dispatchers: ArcileDispatchers,
    applicationScope: CoroutineScope,
    portableLocationResolver: VaultPortableLocationResolver
) : VaultLegacyLayer(context, dispatchers, applicationScope, portableLocationResolver),
    VaultHealthService, VaultBackupService {
    override suspend fun createBackup(
        vaultId: VaultId,
        destinationPath: String,
        label: String,
        password: CharArray,
        weakPasswordConfirmed: Boolean
    ): Result<String> = try {
        mutate(vaultId) { source ->
            VaultPasswordPolicy.requireAccepted(password, weakPasswordConfirmed)
            val destination = portableLocationResolver.backupParent(destinationPath)
            requireOutsideVault(destination)
            val outputName = "${validateVaultName(label)}.onlyfiles-backup"
            if (Files.exists(File(destination, outputName).toPath(), LinkOption.NOFOLLOW_LINKS)) {
                throw VaultFailure.Unavailable("A folder with that backup name already exists")
            }
            val operation = currentCoroutineContext()
            val cancellation = VaultCancellationSignal {
                !operation.isActive || synchronized(source) { source.lockRequested }
            }
            val backup = VaultAppPrivateManager(destination).create(label, password, outputName) { staged ->
                copyVerifiedSnapshot(source, staged, cancellation)
            }
            try {
                backup.access.directory.absolutePath
            } finally {
                backup.masterSecret.fill(0)
            }
        }
    } finally {
        password.fill('\u0000')
    }

    override suspend fun restoreBackup(sourcePath: String, label: String, password: CharArray): Result<VaultId> = try {
        withContext(dispatchers.io) {
            lifecycleMutex.withLock {
                catchingCancellation {
                    val access = portableLocationResolver.identify(sourcePath).access
                    val opened = headerCodec.open(access, password).getOrThrow()
                    val source = VaultSessionRecord(opened.id, access, opened.masterKey)
                    try {
                        if (locations.containsKey(source.id.value)) {
                            throw VaultFailure.Unavailable("Remove the backup's registration before restoring it")
                        }
                        val operation = currentCoroutineContext()
                        val cancellation = VaultCancellationSignal { !operation.isActive }
                        val restored = appPrivateManager.create(label, password) { staged ->
                            copyVerifiedSnapshot(source, staged, cancellation)
                        }
                        restored.masterSecret.fill(0)
                        refreshVaults()
                        restored.id
                    } finally {
                        source.destroy()
                    }
                }
            }
        }
    } finally {
        password.fill('\u0000')
    }

    private fun requireOutsideVault(destination: File) {
        val insideVault = generateSequence(destination) { it.parentFile }.any { folder ->
            folder == vaultRoot.canonicalFile ||
                File(folder, VaultHeaderCodec.PRIMARY_FILE).isFile ||
                File(folder, VaultHeaderCodec.BACKUP_FILE).isFile
        }
        if (insideVault) throw VaultFailure.InvalidPath("Choose a backup folder outside an existing vault")
    }

    private suspend fun copyVerifiedSnapshot(
        source: VaultSessionRecord,
        staged: OpenAppPrivateVault,
        cancellation: VaultCancellationSignal
    ) {
        val sourceReport = verifyHealth(source, VaultHealthMode.FULL, cancellation::throwIfCancelled)
        if (!sourceReport.isHealthy) throw VaultFailure.IntegrityFailed("The source vault failed verification")
        val destination = VaultSessionRecord(staged.id, staged.access, staged.masterSecret.copyOf())
        try {
            transferEngine.copyToUnpublishedVault(source, destination, cancellation)
            val copied = verifyHealth(destination, VaultHealthMode.FULL, cancellation::throwIfCancelled)
            if (!copied.isHealthy || copied.checkedChunks != sourceReport.checkedChunks) {
                throw VaultFailure.IntegrityFailed("The copied vault failed verification")
            }
            cancellation.throwIfCancelled()
        } finally {
            destination.destroy()
        }
    }

    override suspend fun verify(vaultId: VaultId, mode: VaultHealthMode): Result<VaultHealthReport> =
        mutate(vaultId) { session ->
            val operation = currentCoroutineContext()
            verifyHealth(session, mode, operation::ensureActive)
        }

    override suspend fun cleanupOrphans(
        vaultId: VaultId,
        confirmedObjectIds: Set<VaultObjectId>
    ): Result<Int> = mutate(vaultId) { session ->
        if (confirmedObjectIds.isEmpty()) return@mutate 0
        val report = verifyHealth(session, VaultHealthMode.QUICK)
        if (report.issues.any { it.code == "pending_transaction" }) {
            throw VaultFailure.OperationInProgress()
        }
        if (!report.orphanObjectIds.containsAll(confirmedObjectIds)) {
            throw VaultFailure.ConcurrentMutation()
        }
        confirmedObjectIds.count { objectId -> session.directory.delete(objectId.shardedPath()) }
    }

    override suspend fun recoverTransactions(vaultId: VaultId): Result<Unit> = mutate(vaultId) { session ->
        transactionManager.recover(session.directory, session.id, session.masterSecret)
    }

    private fun verifyHealth(
        session: VaultSessionRecord,
        mode: VaultHealthMode,
        checkActive: () -> Unit = {}
    ): VaultHealthReport {
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
                if (header.id != session.id) {
                    issues += VaultHealthIssue(
                        VaultHealthSeverity.ERROR,
                        "header_identity",
                        message = "Public headers identify a different vault"
                    )
                }
            },
            onFailure = { error ->
                issues += VaultHealthIssue(
                    VaultHealthSeverity.ERROR,
                    "header_damage",
                    message = error.message ?: "Public headers are damaged"
                )
            }
        )
        if (transactionManager.hasPendingCommit(session.directory)) {
            issues += VaultHealthIssue(
                VaultHealthSeverity.ERROR,
                "pending_transaction",
                message = "A committed transaction still requires recovery"
            )
        }

        try {
            while (queue.isNotEmpty()) {
                checkActive()
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
                        if (directoryId == VaultSessionRecord.ROOT_DIRECTORY_ID) {
                            rootGeneration = snapshot.generation
                        }
                        val referencedPages = directoryCodec.referencedPageObjectIds(
                            session.directory,
                            session.id,
                            directoryId,
                            key
                        )
                        referencedObjects += referencedPages
                        checkedObjects += referencedPages.size
                        snapshot.entries.forEach { entry ->
                            checkActive()
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
                                                throw VaultFailure.IntegrityFailed(
                                                    "Declared file size does not match its object"
                                                )
                                            }
                                            if (mode == VaultHealthMode.FULL) {
                                                val buffer = ByteArray(VaultFileCodec.DEFAULT_CHUNK_SIZE_BYTES)
                                                var position = 0L
                                                try {
                                                    while (position < reader.sizeBytes) {
                                                        checkActive()
                                                        val count = reader.readAt(
                                                            position,
                                                            buffer,
                                                            0,
                                                            buffer.size
                                                        )
                                                        if (count <= 0) {
                                                            throw VaultFailure.IntegrityFailed(
                                                                "Encrypted file ended early"
                                                            )
                                                        }
                                                        position += count
                                                        checkedChunks++
                                                    }
                                                } finally {
                                                    buffer.fill(0)
                                                }
                                            }
                                        }
                                    } catch (error: Throwable) {
                                        if (error is CancellationException || error is VaultFailure.Cancelled) throw error
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
        } finally {
            queue.forEach { (_, key) -> key.fill(0) }
        }

        val physicalObjectIds = session.directory.listFiles(OBJECTS_DIRECTORY).asSequence()
            .filterNot { it.isDirectory }
            .mapNotNull { physical ->
                physical.relativePath.substringAfterLast('/').removeSuffix(".obj")
                    .let { runCatchingCancellable { VaultObjectId.of(it) }.getOrNull() }
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
}
