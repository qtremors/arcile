package dev.qtremors.arcile.core.vault.data

import dev.qtremors.arcile.core.storage.domain.TrashRepository
import dev.qtremors.arcile.core.vault.domain.DirectoryId
import dev.qtremors.arcile.core.vault.domain.VaultBatchResult
import dev.qtremors.arcile.core.vault.domain.VaultBoundaryTransferCoordinator
import dev.qtremors.arcile.core.vault.domain.VaultBoundaryTransferReservation
import dev.qtremors.arcile.core.vault.domain.VaultCancellationSignal
import dev.qtremors.arcile.core.vault.domain.VaultConflict
import dev.qtremors.arcile.core.vault.domain.VaultConflictDecision
import dev.qtremors.arcile.core.vault.domain.VaultConflictResolver
import dev.qtremors.arcile.core.vault.domain.VaultFailure
import dev.qtremors.arcile.core.vault.domain.VaultExportRetry
import dev.qtremors.arcile.core.vault.domain.VaultFileSystem
import dev.qtremors.arcile.core.vault.domain.VaultItemOutcome
import dev.qtremors.arcile.core.vault.domain.VaultItemResult
import dev.qtremors.arcile.core.vault.domain.VaultLeasePurpose
import dev.qtremors.arcile.core.vault.domain.VaultListOptions
import dev.qtremors.arcile.core.vault.domain.VaultNodeMetadata
import dev.qtremors.arcile.core.vault.domain.VaultNodeRef
import dev.qtremors.arcile.core.vault.domain.VaultSessionManager
import dev.qtremors.arcile.core.vault.domain.VaultTransferAction
import dev.qtremors.arcile.core.vault.domain.VaultTransferProgress
import java.security.MessageDigest
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

@Singleton
class DefaultVaultBoundaryTransferCoordinator private constructor(
    private val repository: DefaultVaultRepository?,
    private val legacyFileSystem: VaultFileSystem?,
    private val legacySessions: VaultSessionManager?,
    private val trashRepository: TrashRepository?
) : VaultBoundaryTransferCoordinator {
    @Inject internal constructor(
        repository: DefaultVaultRepository,
        trashRepository: TrashRepository
    ) : this(repository, null, null, trashRepository)

    internal constructor(
        fileSystem: VaultFileSystem,
        sessions: VaultSessionManager,
        trashRepository: TrashRepository? = null
    ) : this(null, fileSystem, sessions, trashRepository)
    private val mutex = Mutex()
    private val updates = MutableSharedFlow<VaultTransferProgress>(extraBufferCapacity = 32)
    override val progress: Flow<VaultTransferProgress> = updates

    override fun prepareExport(sources: List<VaultNodeRef>): Result<VaultBoundaryTransferReservation> =
        runCatching {
            val unique = sources.distinctBy { "${it.vaultId.value}:${it.nodeId.value}" }
            val session = if (repository != null) {
                BoundaryAccessSession.Repository(repository.createBoundarySession(unique))
            } else {
                val vaultId = unique.firstOrNull()?.vaultId
                    ?: throw VaultFailure.InvalidPath("Select at least one vault item")
                if (unique.any { it.vaultId != vaultId }) {
                    throw VaultFailure.InvalidPath("Export selections must belong to one vault")
                }
                BoundaryAccessSession.Legacy(
                    requireNotNull(legacySessions).acquireLease(vaultId, VaultLeasePurpose.TRANSFER).getOrThrow()
                )
            }
            BoundaryReservation(unique, session)
        }

    override suspend fun exportToDestination(
        reservation: VaultBoundaryTransferReservation,
        destinationPath: String,
        move: Boolean,
        conflicts: VaultConflictResolver,
        cancellation: VaultCancellationSignal
    ): VaultBatchResult = withExportSession(reservation) { session, sources ->
        exportToLocalDirectory(session, sources, File(destinationPath), move, conflicts, cancellation)
    }

    override suspend fun resumeExport(
        retry: VaultExportRetry,
        conflicts: VaultConflictResolver,
        cancellation: VaultCancellationSignal
    ): VaultItemResult {
        val pending = retry as? PendingVaultExport
            ?: throw VaultFailure.InvalidPath("This export cannot be resumed")
        return prepareExport(listOf(retry.source)).getOrThrow().use { reservation ->
            withExportSession(reservation) { session, _ -> resumeLocalExport(session, pending, conflicts, cancellation) }
        }
    }

    private suspend fun <T> withExportSession(
        reservation: VaultBoundaryTransferReservation,
        operation: suspend (BoundaryAccessSession, List<VaultNodeRef>) -> T
    ): T = withContext(Dispatchers.IO) {
        mutex.withLock {
            val prepared = reservation as? BoundaryReservation
                ?: throw VaultFailure.Unavailable("Export session is invalid")
            val session = prepared.consume()
            val unique = prepared.sources
            try {
                when (session) {
                    is BoundaryAccessSession.Repository -> session.value.mutationMutex.withLock {
                        operation(session, unique)
                    }
                    is BoundaryAccessSession.Legacy -> operation(session, unique)
                }
            } finally {
                prepared.closeSession(session)
            }
        }
    }

    override suspend fun moveToMainTrash(
        reservation: VaultBoundaryTransferReservation,
        cancellation: VaultCancellationSignal
    ): VaultBatchResult = withContext(Dispatchers.IO) {
        mutex.withLock {
            val trash = trashRepository ?: throw VaultFailure.Unavailable("Main Trash is unavailable")
            val prepared = reservation as? BoundaryReservation
                ?: throw VaultFailure.Unavailable("Trash export session is invalid")
            val session = prepared.consume()
            try {
                when (session) {
                    is BoundaryAccessSession.Repository -> session.value.mutationMutex.withLock {
                        moveToTrash(session, prepared.sources, trash, cancellation)
                    }
                    is BoundaryAccessSession.Legacy -> moveToTrash(session, prepared.sources, trash, cancellation)
                }
            } finally {
                prepared.closeSession(session)
            }
        }
    }

    private suspend fun moveToTrash(
        session: BoundaryAccessSession,
        sources: List<VaultNodeRef>,
        trash: TrashRepository,
        cancellation: VaultCancellationSignal
    ): VaultBatchResult {
        val progress = ExportProgress(VaultTransferAction.MOVE, sources.size, null)
        val results = sources.mapIndexed { index, ref ->
            var name = ""
            var accepted = false
            var removed = emptySet<DirectoryId>()
            try {
                cancellation.throwIfCancelled()
                val source = metadata(session, ref)
                name = source.name
                progress.start(index, name)
                trash.acceptExternalItem(
                    displayName = name,
                    writePayload = { path ->
                        val payload = File(path)
                        cancellation.throwIfCancelled()
                        if (source.isDirectory) {
                            check(payload.mkdir()) { "Unable to prepare folder for Trash" }
                            val partial = exportVaultDirectoryLocal(
                                session, source, payload, VaultConflictResolver { VaultConflictDecision.SKIP },
                                cancellation, progress::advance
                            )
                            check(!partial) { "Unable to export the complete folder to Trash" }
                        } else exportLocalFile(session, source, payload, cancellation, progress::advance)
                        cancellation.throwIfCancelled()
                    },
                    afterPublished = {
                        accepted = true
                        cancellation.throwIfCancelled()
                        removed = deletePermanently(session, ref)
                    }
                ).getOrThrow()
                check(accepted) { "Main Trash did not publish the item" }
                item(ref, name, VaultItemOutcome.COMPLETED).copy(removedFolders = removed)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                val failure = failed(ref, name, error)
                when {
                    accepted -> failure.copy(outcome = VaultItemOutcome.PARTIAL, removedFolders = removed)
                    error is VaultFailure.Cancelled -> failure.copy(outcome = VaultItemOutcome.ROLLED_BACK)
                    else -> failure
                }
            } finally {
                progress.finish(index + 1)
            }
        }
        return VaultBatchResult(results)
    }

    private suspend fun exportToLocalDirectory(
        session: BoundaryAccessSession,
        sources: List<VaultNodeRef>,
        destination: File,
        move: Boolean,
        conflicts: VaultConflictResolver,
        cancellation: VaultCancellationSignal
    ): VaultBatchResult {
        if (!destination.inspectDestination().isDirectory || !destination.canWrite()) {
            throw VaultFailure.Unavailable("The Arcile destination is not writable")
        }
        val prepared = sources.map { ref ->
            ref to try {
                if (cancellation.isCancelled()) Result.failure(VaultFailure.Cancelled())
                else Result.success(metadata(session, ref))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Result.failure(error)
            }
        }
        val destinationEntries = destination.readLocalChildren()
        val resolver = conflicts.reviewed(prepared.mapNotNull { (_, result) ->
            val source = result.getOrNull() ?: return@mapNotNull null
            val existing = destinationEntries.findLocalChild(source.name) ?: return@mapNotNull null
            try {
                existing.inspectDestination().conflictWith(source)
            } catch (_: Exception) {
                // A changed or unsupported entry is validated again before that item is copied.
                null
            }
        }, cancellation)
        val results = mutableListOf<VaultItemResult>()
        val totalBytes = if (prepared.all { it.second.getOrNull()?.isDirectory == false }) {
            prepared.sumOf { it.second.getOrThrow().sizeBytes }
        } else null
        val progress = ExportProgress(
            if (move) VaultTransferAction.MOVE else VaultTransferAction.EXPORT, sources.size, totalBytes
        )
        prepared.forEachIndexed { index, (ref, metadata) ->
            try {
                cancellation.throwIfCancelled()
                val source = metadata.getOrThrow()
                progress.start(index, source.name)
                val exported = exportLocalTopLevel(session, source, destination, resolver, cancellation, move, progress::advance)
                val removed = if (exported.outcome == VaultItemOutcome.COMPLETED && move) deletePermanently(session, ref)
                else emptySet()
                val retryUnavailable = if (exported.outcome == VaultItemOutcome.PARTIAL && exported.snapshot == null) {
                    VaultFailure.Unavailable("This export exceeded the retry limit. The vault original was kept.")
                } else null
                results += item(ref, source.name, exported.outcome, retryUnavailable).copy(
                    removedFolders = removed,
                    exportRetry = exported.snapshot?.takeIf { exported.outcome == VaultItemOutcome.PARTIAL }
                )
                progress.finish(index + 1)
            } catch (error: VaultFailure.Cancelled) {
                results += item(ref, metadata.getOrNull()?.name.orEmpty(), VaultItemOutcome.ROLLED_BACK, error)
                sources.drop(index + 1).forEach {
                    results += item(it, it.nodeId.value, VaultItemOutcome.SKIPPED, VaultFailure.Cancelled())
                }
                return VaultBatchResult(results)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                results += failed(ref, metadata.getOrNull()?.name.orEmpty(), error)
            }
        }
        return VaultBatchResult(results)
    }

    private suspend fun resumeLocalExport(
        session: BoundaryAccessSession,
        retry: PendingVaultExport,
        conflicts: VaultConflictResolver,
        cancellation: VaultCancellationSignal
    ): VaultItemResult {
        var checkpoint = retry
        var removed = emptySet<DirectoryId>()
        var failure: VaultFailure? = null
        var canContinue = true
        val progress = ExportProgress(
            if (retry.move) VaultTransferAction.MOVE else VaultTransferAction.EXPORT, retry.remainingCount, null
        )
        try {
            cancellation.throwIfCancelled()
            verifyExportSource(session, retry, cancellation)
            if (retry.move && retry.deferred.isNotEmpty()) verifyExportOutputs(retry, cancellation)
            val known = retry.deferred.mapNotNull { child ->
                cancellation.throwIfCancelled()
                val source = unchangedSource(session, child.node)
                val parent = exportFolder(retry, child.parent)
                parent.readLocalChildren().findLocalChild(source.name)?.inspectDestination()?.conflictWith(source)
            }
            val resolver = conflicts.reviewed(known, cancellation)
            for ((index, child) in retry.deferred.withIndex()) {
                cancellation.throwIfCancelled()
                progress.start(index, child.node.name)
                val source = unchangedSource(session, child.node)
                val parent = exportFolder(retry, child.parent)
                val exported = exportLocalTopLevel(
                    session, source, parent, resolver, cancellation, retry.move, progress::advance
                ) { exportFolder(retry, child.parent) }
                if (exported.outcome != VaultItemOutcome.SKIPPED) {
                    val appended = exported.snapshot
                    if (appended == null) {
                        canContinue = false
                        throw VaultFailure.Unavailable("This export exceeded the retry limit. The vault original was kept.")
                    }
                    checkpoint = checkpoint.withPublishedChild(child, appended)
                    if (checkpoint.deferred.size + checkpoint.files.size + checkpoint.folders.size > MAX_EXPORT_RETRY_RECORDS) {
                        canContinue = false
                        throw VaultFailure.Unavailable("This export exceeded the retry limit. The vault original was kept.")
                    }
                }
                progress.finish(index + 1)
            }
            if (checkpoint.deferred.isEmpty() && checkpoint.move) {
                verifyExportSource(session, checkpoint, cancellation)
                verifyExportOutputs(checkpoint, cancellation)
                cancellation.throwIfCancelled()
                removed = deletePermanently(session, retry.source)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            failure = error as? VaultFailure ?: VaultFailure.Unavailable("The export could not be resumed", error)
        }
        val completed = failure == null && checkpoint.deferred.isEmpty()
        return item(
            retry.source, retry.rootNode.name,
            if (completed) VaultItemOutcome.COMPLETED else VaultItemOutcome.PARTIAL, failure
        ).copy(
            removedFolders = removed,
            exportRetry = checkpoint.takeIf { !completed && canContinue }
        )
    }

    private fun PendingVaultExport.withPublishedChild(
        child: DeferredExportChild,
        appended: PendingVaultExport
    ): PendingVaultExport {
        val prefix = child.parent + appended.outputRoot.name
        return copy(
            deferred = deferred - child + appended.deferred.map { it.copy(parent = prefix + it.parent) },
            generations = generations + appended.generations,
            files = files + appended.files.map { it.copy(relativePath = prefix + it.relativePath) },
            folders = folders + appended.folders.mapKeys { (path, _) -> prefix + path }
        )
    }

    private suspend fun unchangedSource(session: BoundaryAccessSession, expected: VaultNodeMetadata): VaultNodeMetadata {
        val current = metadata(session, expected.ref)
        if (current.revision != expected.revision || current.name != expected.name ||
            current.isDirectory != expected.isDirectory || current.sizeBytes != expected.sizeBytes
        ) throw VaultFailure.SourceChanged()
        return current
    }

    private suspend fun verifyExportSource(
        session: BoundaryAccessSession,
        retry: PendingVaultExport,
        cancellation: VaultCancellationSignal
    ) {
        unchangedSource(session, retry.rootNode)
        for ((directory, generation) in retry.generations) {
            cancellation.throwIfCancelled()
            val current = listDirectory(session, retry.source.vaultId, directory, VaultListOptions(pageSize = 1))
            if (current.generation != generation) throw VaultFailure.SourceChanged()
        }
    }

    private fun verifyExportOutputs(retry: PendingVaultExport, cancellation: VaultCancellationSignal) {
        exportFolder(retry, emptyList())
        for (folder in retry.folders.keys) {
            cancellation.throwIfCancelled()
            exportFolder(retry, folder)
        }
        for (file in retry.files) {
            val parent = exportFolder(retry, file.relativePath.dropLast(1))
            val output = File(parent, file.relativePath.last())
            val before = output.inspectDestination()
            if (before.isDirectory || before.size != file.size || localDigest(output, cancellation) != file.digest ||
                output.inspectDestination() != before
            ) throw VaultFailure.IntegrityFailed("A previously exported file changed. The vault original was kept.")
        }
    }

    private data class LocalExport(val outcome: VaultItemOutcome, val snapshot: PendingVaultExport? = null)

    private suspend fun exportLocalTopLevel(
        session: BoundaryAccessSession,
        source: VaultNodeMetadata,
        parent: File,
        conflicts: VaultConflictResolver,
        cancellation: VaultCancellationSignal,
        move: Boolean,
        onBytes: (Int) -> Unit,
        validateParent: () -> Unit = {}
    ): LocalExport {
        val existing = parent.readLocalChildren().findLocalChild(source.name)?.inspectDestination()
        val decision = existing?.let {
            conflicts.decide(it.conflictWith(source))
        }
        if (decision == VaultConflictDecision.SKIP) return LocalExport(VaultItemOutcome.SKIPPED)
        validateParent()
        if (decision == VaultConflictDecision.MERGE_DIRECTORIES &&
            (!source.isDirectory || !existing.isDirectory)
        ) throw VaultFailure.NameConflict(source.name)
        val finalName = if (decision == VaultConflictDecision.KEEP_BOTH) uniqueLocalName(parent, source.name) else source.name
        val published = File(parent.canonicalFile, finalName)
        val staging = File(parent, ".arcile-${UUID.randomUUID()}.tmp")
        val trace = ExportRetryTrace(staging, trackProofs = move)
        try {
            val partial = if (source.isDirectory) {
                check(staging.mkdir()) { "Unable to stage exported folder" }
                if (decision == VaultConflictDecision.MERGE_DIRECTORIES) {
                    copyLocalDirectory(requireNotNull(existing).file, staging, cancellation)
                }
                exportVaultDirectoryLocal(session, source, staging, conflicts, cancellation, onBytes, trace)
            } else {
                val digest = exportLocalFile(session, source, staging, cancellation, onBytes)
                trace.file(staging, source.sizeBytes, digest)
                false
            }
            cancellation.throwIfCancelled()
            validateParent()
            publishLocal(staging, File(parent, finalName), existing?.takeIf {
                decision == VaultConflictDecision.REPLACE || decision == VaultConflictDecision.MERGE_DIRECTORIES
            })
            return LocalExport(
                if (partial) VaultItemOutcome.PARTIAL else VaultItemOutcome.COMPLETED,
                trace.snapshot(source, published, move)
            )
        } catch (error: Throwable) {
            try {
                staging.deleteLocalTree()
            } catch (cleanup: Exception) {
                error.addSuppressed(cleanup)
            }
            throw error
        }
    }

    private suspend fun exportVaultDirectoryLocal(
        session: BoundaryAccessSession,
        root: VaultNodeMetadata,
        rootDestination: File,
        conflicts: VaultConflictResolver,
        cancellation: VaultCancellationSignal,
        onBytes: (Int) -> Unit,
        trace: ExportRetryTrace? = null
    ): Boolean {
        var skipped = false
        val pending = ArrayDeque<Pair<VaultNodeMetadata, File>>()
        pending += root to rootDestination
        while (pending.isNotEmpty()) {
            val (directory, destination) = pending.removeLast()
            val id = directory.ref.directoryId ?: throw VaultFailure.InvalidPath("Folder identity is missing")
            var token: String? = null
            do {
                val page = listDirectory(session, directory.ref.vaultId, id, VaultListOptions(pageToken = token))
                trace?.directory(id, page.generation, destination)
                page.items.forEach { child ->
                    cancellation.throwIfCancelled()
                    val existing = destination.readLocalChildren().findLocalChild(child.name)?.inspectDestination()
                    val decision = existing?.let {
                        conflicts.decide(it.conflictWith(child))
                    }
                    if (decision == VaultConflictDecision.SKIP) {
                        skipped = true
                        trace?.skipped(child, destination)
                        return@forEach
                    }
                    val name = if (decision == VaultConflictDecision.KEEP_BOTH) uniqueLocalName(destination, child.name) else child.name
                    if (child.isDirectory) {
                        if (decision == VaultConflictDecision.MERGE_DIRECTORIES && !existing.isDirectory) {
                            throw VaultFailure.NameConflict(child.name)
                        }
                        if (decision == VaultConflictDecision.REPLACE) {
                            requireNotNull(existing).file.deleteLocalTree()
                        }
                        val target = if (decision == VaultConflictDecision.MERGE_DIRECTORIES) requireNotNull(existing).file
                        else File(destination, name).also { check(it.mkdir()) { "Unable to create exported folder" } }
                        pending += child to target
                    } else {
                        if (decision == VaultConflictDecision.MERGE_DIRECTORIES) throw VaultFailure.NameConflict(child.name)
                        val temporary = File(destination, ".arcile-${UUID.randomUUID()}.tmp")
                        try {
                            val digest = exportLocalFile(session, child, temporary, cancellation, onBytes)
                            val target = File(destination, name)
                            publishLocal(temporary, target, existing?.takeIf { decision == VaultConflictDecision.REPLACE })
                            trace?.file(target, child.sizeBytes, digest)
                        } finally {
                            temporary.delete()
                        }
                    }
                }
                token = page.nextPageToken
            } while (token != null)
        }
        return skipped
    }

    private fun exportLocalFile(
        session: BoundaryAccessSession,
        source: VaultNodeMetadata,
        destination: File,
        cancellation: VaultCancellationSignal,
        onBytes: (Int) -> Unit
    ): String {
        val expected = MessageDigest.getInstance("SHA-256")
        openReader(session, source.ref).use { reader ->
            FileOutputStream(destination).use { output ->
                val buffer = ByteArray(VAULT_LOCAL_EXPORT_BUFFER_SIZE)
                var position = 0L
                try {
                    while (position < reader.sizeBytes) {
                        cancellation.throwIfCancelled()
                        val count = reader.readAt(position, buffer, 0, minOf(buffer.size.toLong(), reader.sizeBytes - position).toInt())
                        if (count <= 0) throw VaultFailure.SourceChanged()
                        output.write(buffer, 0, count)
                        expected.update(buffer, 0, count)
                        position += count
                        onBytes(count)
                    }
                    output.fd.sync()
                } finally {
                    buffer.fill(0)
                }
            }
        }
        val hash = expected.digest().toHexDigest()
        if (destination.length() != source.sizeBytes || hash != localDigest(destination, cancellation)) {
            throw VaultFailure.IntegrityFailed("Destination verification failed")
        }
        return hash
    }

    private suspend fun metadata(session: BoundaryAccessSession, ref: VaultNodeRef): VaultNodeMetadata =
        when (session) {
            is BoundaryAccessSession.Repository -> requireNotNull(repository).metadata(session.value, ref)
            is BoundaryAccessSession.Legacy -> requireNotNull(legacyFileSystem).metadata(ref).getOrThrow()
        }

    private suspend fun listDirectory(
        session: BoundaryAccessSession,
        vaultId: dev.qtremors.arcile.core.vault.domain.VaultId,
        directoryId: dev.qtremors.arcile.core.vault.domain.DirectoryId,
        options: VaultListOptions
    ) = when (session) {
        is BoundaryAccessSession.Repository ->
            requireNotNull(repository).listDirectory(session.value, directoryId, options)
        is BoundaryAccessSession.Legacy ->
            requireNotNull(legacyFileSystem).listDirectory(vaultId, directoryId, options).getOrThrow()
    }

    private fun openReader(session: BoundaryAccessSession, ref: VaultNodeRef) = when (session) {
        is BoundaryAccessSession.Repository -> requireNotNull(repository).openReader(session.value, ref)
        is BoundaryAccessSession.Legacy -> requireNotNull(legacyFileSystem).openReader(ref).getOrThrow()
    }

    private suspend fun deletePermanently(session: BoundaryAccessSession, ref: VaultNodeRef): Set<DirectoryId> =
        when (session) {
            is BoundaryAccessSession.Repository -> buildSet {
                requireNotNull(repository).deleteWithMutationLockHeld(session.value, ref) { add(it) }
            }
            is BoundaryAccessSession.Legacy -> requireNotNull(legacyFileSystem).deleteAndReportFolders(ref).getOrThrow()
        }

    private inner class ExportProgress(
        private val action: VaultTransferAction,
        private val totalItems: Int,
        private val totalBytes: Long?
    ) {
        private val startedAt = System.currentTimeMillis()
        private var lastUpdate = System.nanoTime()
        private var completed = 0
        private var name: String? = null
        private var bytes = 0L

        fun start(index: Int, sourceName: String) {
            completed = index
            name = sourceName
            emit()
        }

        fun advance(count: Int) {
            bytes += count
            if (System.nanoTime() - lastUpdate >= 100_000_000L) emit()
        }

        fun finish(count: Int) {
            completed = count
            emit()
        }

        private fun emit() {
            lastUpdate = System.nanoTime()
            updates.tryEmit(VaultTransferProgress(action, name, completed, totalItems, bytes, totalBytes, startedAt))
        }
    }

    private class BoundaryReservation(
        override val sources: List<VaultNodeRef>,
        private var session: BoundaryAccessSession?
    ) : VaultBoundaryTransferReservation {
        private val closed = AtomicBoolean(false)
        override val isClosed: Boolean get() = closed.get()

        fun consume(): BoundaryAccessSession = synchronized(this) {
            if (!closed.compareAndSet(false, true)) {
                throw VaultFailure.Unavailable("Export session expired")
            }
            session.also { session = null }
                ?: throw VaultFailure.Unavailable("Export session expired")
        }

        fun closeSession(consumed: BoundaryAccessSession) = consumed.close()

        override fun close() {
            if (closed.compareAndSet(false, true)) synchronized(this) {
                session?.close()
                session = null
            }
        }
    }

    private sealed interface BoundaryAccessSession : java.io.Closeable {
        class Repository(val value: VaultSessionRecord) : BoundaryAccessSession {
            override fun close() = value.destroy()
        }
        class Legacy(val lease: dev.qtremors.arcile.core.vault.domain.VaultKeyLease) : BoundaryAccessSession {
            override fun close() = lease.close()
        }
    }

    private fun item(ref: VaultNodeRef, name: String, outcome: VaultItemOutcome, failure: VaultFailure? = null) =
        VaultItemResult("${ref.vaultId.value}:${ref.nodeId.value}", name, outcome, failure)
    private fun failed(ref: VaultNodeRef, name: String, error: Throwable) = item(
        ref, name, VaultItemOutcome.FAILED, error as? VaultFailure ?: VaultFailure.Unavailable("Boundary transfer failed", error)
    )

}
