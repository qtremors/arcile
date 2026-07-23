package dev.qtremors.arcile.core.vault.data

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
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

@Singleton
class DefaultVaultBoundaryTransferCoordinator private constructor(
    private val repository: DefaultVaultRepository?,
    private val legacyFileSystem: VaultFileSystem?,
    private val legacySessions: VaultSessionManager?
) : VaultBoundaryTransferCoordinator {
    @Inject internal constructor(
        repository: DefaultVaultRepository
    ) : this(repository, null, null)

    internal constructor(
        fileSystem: VaultFileSystem,
        sessions: VaultSessionManager
    ) : this(null, fileSystem, sessions)
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
                exportToLocalDirectory(session, unique, destination, move, conflicts, cancellation)
            } finally {
                prepared.closeSession(session)
            }
        }
    }

    private suspend fun exportToLocalDirectory(
        session: BoundaryAccessSession,
        sources: List<VaultNodeRef>,
        destination: File,
        move: Boolean,
        conflicts: VaultConflictResolver,
        cancellation: VaultCancellationSignal
    ): VaultBatchResult {
        if (!destination.isDirectory || !destination.canWrite()) {
            throw VaultFailure.Unavailable("The Arcile destination is not writable")
        }
        val results = mutableListOf<VaultItemResult>()
        sources.forEachIndexed { index, ref ->
            val source = runCatching { metadata(session, ref) }.getOrElse {
                results += failed(ref, ref.nodeId.value, it); return@forEachIndexed
            }
            updates.tryEmit(VaultTransferProgress(
                if (move) VaultTransferAction.MOVE else VaultTransferAction.EXPORT,
                source.name, index, sources.size, 0L, source.sizeBytes.takeIf { !source.isDirectory }
            ))
            try {
                cancellation.throwIfCancelled()
                val outcome = exportLocalTopLevel(session, source, destination, conflicts, cancellation)
                if (outcome == VaultItemOutcome.COMPLETED && move) deletePermanently(session, ref)
                results += item(ref, source.name, outcome)
            } catch (error: VaultFailure.Cancelled) {
                results += item(ref, source.name, VaultItemOutcome.ROLLED_BACK, error)
                sources.drop(index + 1).forEach {
                    results += item(it, it.nodeId.value, VaultItemOutcome.SKIPPED, VaultFailure.Cancelled())
                }
                return VaultBatchResult(results)
            } catch (error: Throwable) {
                results += failed(ref, source.name, error)
            }
        }
        return VaultBatchResult(results)
    }

    private suspend fun exportLocalTopLevel(
        session: BoundaryAccessSession,
        source: VaultNodeMetadata,
        parent: File,
        conflicts: VaultConflictResolver,
        cancellation: VaultCancellationSignal
    ): VaultItemOutcome {
        val existing = parent.listFiles()?.firstOrNull { it.name.equals(source.name, true) }
        val decision = existing?.let {
            conflicts.decide(VaultConflict(source.name, it.name, source.isDirectory, it.isDirectory))
        }
        if (decision == VaultConflictDecision.SKIP) return VaultItemOutcome.SKIPPED
        if (decision == VaultConflictDecision.MERGE_DIRECTORIES &&
            (!source.isDirectory || existing?.isDirectory != true)
        ) throw VaultFailure.NameConflict(source.name)
        val finalName = if (decision == VaultConflictDecision.KEEP_BOTH) uniqueLocalName(parent, source.name) else source.name
        val staging = File(parent, ".arcile-${UUID.randomUUID()}.tmp")
        try {
            if (source.isDirectory) {
                check(staging.mkdir()) { "Unable to stage exported folder" }
                if (decision == VaultConflictDecision.MERGE_DIRECTORIES) {
                    copyLocalDirectory(requireNotNull(existing), staging, cancellation)
                }
                exportVaultDirectoryLocal(session, source, staging, conflicts, cancellation)
            } else {
                exportLocalFile(session, source, staging, cancellation)
            }
            publishLocal(staging, File(parent, finalName), existing?.takeIf {
                decision == VaultConflictDecision.REPLACE || decision == VaultConflictDecision.MERGE_DIRECTORIES
            })
            return VaultItemOutcome.COMPLETED
        } catch (error: Throwable) {
            staging.deleteRecursively()
            throw error
        }
    }

    private suspend fun exportVaultDirectoryLocal(
        session: BoundaryAccessSession,
        root: VaultNodeMetadata,
        rootDestination: File,
        conflicts: VaultConflictResolver,
        cancellation: VaultCancellationSignal
    ) {
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
                    val existing = destination.listFiles()?.firstOrNull { it.name.equals(child.name, true) }
                    val decision = existing?.let {
                        conflicts.decide(VaultConflict(child.name, it.name, child.isDirectory, it.isDirectory))
                    }
                    if (decision == VaultConflictDecision.SKIP) return@forEach
                    val name = if (decision == VaultConflictDecision.KEEP_BOTH) uniqueLocalName(destination, child.name) else child.name
                    if (child.isDirectory) {
                        if (decision == VaultConflictDecision.MERGE_DIRECTORIES && existing?.isDirectory != true) {
                            throw VaultFailure.NameConflict(child.name)
                        }
                        if (decision == VaultConflictDecision.REPLACE) existing?.deleteRecursively()
                        val target = if (decision == VaultConflictDecision.MERGE_DIRECTORIES) requireNotNull(existing)
                        else File(destination, name).also { check(it.mkdir()) { "Unable to create exported folder" } }
                        pending += child to target
                    } else {
                        if (decision == VaultConflictDecision.MERGE_DIRECTORIES) throw VaultFailure.NameConflict(child.name)
                        val temporary = File(destination, ".arcile-${UUID.randomUUID()}.tmp")
                        try {
                            exportLocalFile(session, child, temporary, cancellation)
                            publishLocal(temporary, File(destination, name), existing?.takeIf { decision == VaultConflictDecision.REPLACE })
                        } finally {
                            temporary.delete()
                        }
                    }
                }
                token = page.nextPageToken
            } while (token != null)
        }
    }

    private fun exportLocalFile(
        session: BoundaryAccessSession,
        source: VaultNodeMetadata,
        destination: File,
        cancellation: VaultCancellationSignal
    ) {
        val expected = MessageDigest.getInstance("SHA-256")
        openReader(session, source.ref).use { reader ->
            FileOutputStream(destination).use { output ->
                val buffer = ByteArray(BUFFER_SIZE)
                var position = 0L
                while (position < reader.sizeBytes) {
                    cancellation.throwIfCancelled()
                    val count = reader.readAt(position, buffer, 0, minOf(buffer.size.toLong(), reader.sizeBytes - position).toInt())
                    if (count <= 0) throw VaultFailure.SourceChanged()
                    output.write(buffer, 0, count); expected.update(buffer, 0, count); position += count
                }
                buffer.fill(0); output.fd.sync()
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
            from.listFiles()?.forEach { child ->
                cancellation.throwIfCancelled()
                val target = File(to, child.name)
                if (child.isDirectory) { check(target.mkdir()); pending += child to target }
                else child.inputStream().use { input -> target.outputStream().use(input::copyTo) }
            } ?: throw VaultFailure.Unavailable("Unable to read existing destination")
        }
    }

    private fun publishLocal(staging: File, destination: File, replacing: File?) {
        if (replacing == null) {
            if (!staging.renameTo(destination)) throw VaultFailure.Unavailable("Unable to publish exported item")
            return
        }
        val backup = File(replacing.parentFile, ".arcile-backup-${UUID.randomUUID()}.tmp")
        if (!replacing.renameTo(backup)) throw VaultFailure.Unavailable("Unable to stage existing destination")
        if (!staging.renameTo(destination)) {
            backup.renameTo(replacing)
            throw VaultFailure.Unavailable("Unable to publish replacement")
        }
        if (!backup.deleteRecursively()) throw VaultFailure.Unavailable("Unable to remove replaced destination")
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

    private suspend fun deletePermanently(session: BoundaryAccessSession, ref: VaultNodeRef) {
        when (session) {
            is BoundaryAccessSession.Repository -> requireNotNull(repository).deletePermanently(session.value, ref)
            is BoundaryAccessSession.Legacy -> requireNotNull(legacyFileSystem).deletePermanently(ref).getOrThrow()
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
