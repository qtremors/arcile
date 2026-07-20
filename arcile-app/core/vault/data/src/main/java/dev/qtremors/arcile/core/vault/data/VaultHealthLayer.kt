package dev.qtremors.arcile.core.vault.data

import android.content.Context
import dev.qtremors.arcile.core.runtime.di.ArcileDispatchers
import dev.qtremors.arcile.core.vault.crypto.VaultFileCodec
import dev.qtremors.arcile.core.vault.domain.*
import java.util.ArrayDeque
import kotlinx.coroutines.CoroutineScope

internal abstract class VaultHealthLayer(
    context: Context,
    dispatchers: ArcileDispatchers,
    applicationScope: CoroutineScope,
    portableLocationResolver: VaultPortableLocationResolver
) : VaultLegacyLayer(context, dispatchers, applicationScope, portableLocationResolver),
    VaultHealthService {
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
}
