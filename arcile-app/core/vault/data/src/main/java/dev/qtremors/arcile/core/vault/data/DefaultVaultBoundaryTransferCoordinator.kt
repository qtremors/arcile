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
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.attribute.BasicFileAttributes
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
    ): VaultBatchResult = withContext(Dispatchers.IO) {
        mutex.withLock {
            val prepared = reservation as? BoundaryReservation
                ?: throw VaultFailure.Unavailable("Export session is invalid")
            val session = prepared.consume()
            val unique = prepared.sources
            try {
                val destination = File(destinationPath)
                when (session) {
                    is BoundaryAccessSession.Repository -> session.value.mutationMutex.withLock {
                        exportToLocalDirectory(session, unique, destination, move, conflicts, cancellation)
                    }
                    is BoundaryAccessSession.Legacy ->
                        exportToLocalDirectory(session, unique, destination, move, conflicts, cancellation)
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
                val outcome = exportLocalTopLevel(session, source, destination, resolver, cancellation, progress::advance)
                val removed = if (outcome == VaultItemOutcome.COMPLETED && move) deletePermanently(session, ref)
                else emptySet()
                results += item(ref, source.name, outcome).copy(removedFolders = removed)
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

    private suspend fun exportLocalTopLevel(
        session: BoundaryAccessSession,
        source: VaultNodeMetadata,
        parent: File,
        conflicts: VaultConflictResolver,
        cancellation: VaultCancellationSignal,
        onBytes: (Int) -> Unit
    ): VaultItemOutcome {
        val existing = parent.readLocalChildren().findLocalChild(source.name)?.inspectDestination()
        val decision = existing?.let {
            conflicts.decide(it.conflictWith(source))
        }
        if (decision == VaultConflictDecision.SKIP) return VaultItemOutcome.SKIPPED
        if (decision == VaultConflictDecision.MERGE_DIRECTORIES &&
            (!source.isDirectory || !existing.isDirectory)
        ) throw VaultFailure.NameConflict(source.name)
        val finalName = if (decision == VaultConflictDecision.KEEP_BOTH) uniqueLocalName(parent, source.name) else source.name
        val staging = File(parent, ".arcile-${UUID.randomUUID()}.tmp")
        try {
            val partial = if (source.isDirectory) {
                check(staging.mkdir()) { "Unable to stage exported folder" }
                if (decision == VaultConflictDecision.MERGE_DIRECTORIES) {
                    copyLocalDirectory(requireNotNull(existing).file, staging, cancellation)
                }
                exportVaultDirectoryLocal(session, source, staging, conflicts, cancellation, onBytes)
            } else {
                exportLocalFile(session, source, staging, cancellation, onBytes)
                false
            }
            cancellation.throwIfCancelled()
            publishLocal(staging, File(parent, finalName), existing?.takeIf {
                decision == VaultConflictDecision.REPLACE || decision == VaultConflictDecision.MERGE_DIRECTORIES
            })
            return if (partial) VaultItemOutcome.PARTIAL else VaultItemOutcome.COMPLETED
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
        onBytes: (Int) -> Unit
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
                page.items.forEach { child ->
                    cancellation.throwIfCancelled()
                    val existing = destination.readLocalChildren().findLocalChild(child.name)?.inspectDestination()
                    val decision = existing?.let {
                        conflicts.decide(it.conflictWith(child))
                    }
                    if (decision == VaultConflictDecision.SKIP) {
                        skipped = true
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
                            exportLocalFile(session, child, temporary, cancellation, onBytes)
                            publishLocal(temporary, File(destination, name), existing?.takeIf { decision == VaultConflictDecision.REPLACE })
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
    ) {
        val expected = MessageDigest.getInstance("SHA-256")
        openReader(session, source.ref).use { reader ->
            FileOutputStream(destination).use { output ->
                val buffer = ByteArray(BUFFER_SIZE)
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
        val actual = MessageDigest.getInstance("SHA-256")
        FileInputStream(destination).use { input ->
            val buffer = ByteArray(BUFFER_SIZE)
            while (true) { val count = input.read(buffer); if (count < 0) break; actual.update(buffer, 0, count) }
            buffer.fill(0)
        }
        val hash = expected.digest()
        if (destination.length() != source.sizeBytes || !MessageDigest.isEqual(hash, actual.digest())) {
            throw VaultFailure.IntegrityFailed("Destination verification failed")
        }
        hash.fill(0)
    }

    private fun copyLocalDirectory(source: File, destination: File, cancellation: VaultCancellationSignal) {
        val pending = ArrayDeque<Pair<File, File>>()
        pending += source to destination
        while (pending.isNotEmpty()) {
            val (from, to) = pending.removeLast()
            if (!from.inspectDestination().isDirectory) throw VaultFailure.ConcurrentMutation()
            from.listFiles()?.forEach { child ->
                cancellation.throwIfCancelled()
                val target = File(to, child.name)
                if (child.inspectDestination().isDirectory) {
                    check(target.mkdir())
                    pending += child to target
                } else {
                    Files.newInputStream(child.toPath(), LinkOption.NOFOLLOW_LINKS).use { input ->
                        target.outputStream().use(input::copyTo)
                    }
                }
            } ?: throw VaultFailure.Unavailable("Unable to read existing destination")
        }
    }

    private fun publishLocal(staging: File, destination: File, replacing: LocalDestinationEntry?) {
        val parent = requireNotNull(destination.parentFile)
        if (!parent.inspectDestination().isDirectory) throw VaultFailure.ConcurrentMutation()
        val current = parent.readLocalChildren().findLocalChild(destination.name)
        if (replacing == null) {
            if (current != null) throw VaultFailure.ConcurrentMutation()
            Files.move(staging.toPath(), destination.toPath())
            return
        }
        if (current?.inspectDestination() != replacing) throw VaultFailure.ConcurrentMutation()
        val backup = File(parent, ".arcile-backup-${UUID.randomUUID()}.tmp")
        Files.move(replacing.file.toPath(), backup.toPath())
        try {
            Files.move(staging.toPath(), destination.toPath())
        } catch (error: Exception) {
            try {
                Files.move(backup.toPath(), replacing.file.toPath())
            } catch (restore: Exception) {
                error.addSuppressed(restore)
            }
            throw error
        }
        backup.deleteLocalTree()
    }

    private fun File.deleteLocalTree() {
        val root = toPath()
        if (!Files.exists(root, LinkOption.NOFOLLOW_LINKS)) return
        Files.walk(root).use { paths -> paths.sorted(Comparator.reverseOrder()).forEach(Files::delete) }
    }

    private fun uniqueLocalName(parent: File, name: String): String {
        val dot = name.lastIndexOf('.').takeIf { it > 0 }
        val stem = dot?.let { name.substring(0, it) } ?: name
        val suffix = dot?.let(name::substring).orEmpty()
        for (index in 1..10_000) {
            val candidate = "$stem ($index)$suffix"
            if (parent.listFiles()?.none { it.name.equals(candidate, true) } == true) return candidate
        }
        throw VaultFailure.NameConflict(name)
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

    private fun File.readLocalChildren(): Array<File> =
        listFiles() ?: throw VaultFailure.Unavailable("Unable to read export destination")

    private fun Array<File>.findLocalChild(name: String): File? =
        firstOrNull { it.name == name } ?: firstOrNull { it.name.equals(name, ignoreCase = true) }

    private fun File.inspectDestination(): LocalDestinationEntry {
        val attributes = Files.readAttributes(toPath(), BasicFileAttributes::class.java, LinkOption.NOFOLLOW_LINKS)
        if (attributes.isSymbolicLink) throw VaultFailure.SymlinkRejected(name)
        if (!attributes.isRegularFile && !attributes.isDirectory) {
            throw VaultFailure.Unavailable("The export destination contains an unsupported item")
        }
        return LocalDestinationEntry(
            this, attributes.isDirectory, attributes.size(), attributes.lastModifiedTime().toMillis(),
            attributes.creationTime().toMillis(), attributes.fileKey()?.toString()
        )
    }

    private data class LocalDestinationEntry(
        val file: File,
        val isDirectory: Boolean,
        val size: Long,
        val modifiedAt: Long,
        val createdAt: Long,
        val fileKey: String?
    ) {
        fun conflictWith(source: VaultNodeMetadata) = VaultConflict(
            sourceName = source.name,
            destinationName = file.name,
            sourceIsDirectory = source.isDirectory,
            destinationIsDirectory = isDirectory,
            sourceIdentity = "${source.ref.vaultId.value}:${source.ref.nodeId.value}:${source.revision}",
            destinationIdentity = "$file:$fileKey:$createdAt",
            sourceSize = source.sizeBytes,
            destinationSize = if (isDirectory) 0L else size,
            sourceModifiedAt = source.modifiedAtMillis,
            destinationModifiedAt = modifiedAt
        )
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

    private companion object { const val BUFFER_SIZE = 256 * 1024 }
}
