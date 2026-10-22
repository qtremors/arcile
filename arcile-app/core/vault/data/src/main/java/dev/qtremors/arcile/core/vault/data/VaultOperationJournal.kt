package dev.qtremors.arcile.core.vault.data

import dev.qtremors.arcile.core.vault.crypto.FileVaultDirectory
import dev.qtremors.arcile.core.vault.crypto.VaultCryptography
import dev.qtremors.arcile.core.vault.crypto.VaultKeyDomain
import dev.qtremors.arcile.core.vault.crypto.VaultSealedValue
import dev.qtremors.arcile.core.vault.domain.DirectoryId
import dev.qtremors.arcile.core.vault.domain.NodeId
import dev.qtremors.arcile.core.vault.domain.VaultFailure
import dev.qtremors.arcile.core.vault.domain.VaultId
import dev.qtremors.arcile.core.vault.domain.VaultNodeCapabilities
import dev.qtremors.arcile.core.vault.domain.VaultNodeRef
import dev.qtremors.arcile.core.vault.domain.VaultRecoveryKind
import dev.qtremors.arcile.core.vault.domain.VaultRecoveryQueue
import java.io.Closeable
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
internal data class QueuedVaultNode(
    val vault: String,
    val node: String,
    val parent: String,
    val directory: String?
) {
    fun ref() = VaultNodeRef(
        VaultId.of(vault), NodeId.of(node), DirectoryId.of(parent),
        VaultNodeCapabilities(), directory?.let(DirectoryId::of)
    )

    companion object {
        fun from(ref: VaultNodeRef) = QueuedVaultNode(
            ref.vaultId.value, ref.nodeId.value, ref.parentId.value, ref.directoryId?.value
        )
    }
}

@Serializable
internal data class VaultQueuePlan(
    val kind: VaultRecoveryKind,
    val destination: String,
    val move: Boolean,
    val nodes: List<QueuedVaultNode> = emptyList(),
    val localSources: List<String> = emptyList(),
    val localDestinationIdentity: ExportFolderIdentity? = null
) {
    val size: Int get() = if (kind == VaultRecoveryKind.IMPORT) localSources.size else nodes.size

    fun validate(owner: VaultId) {
        require(size in 1..5_000)
        require(destination.isNotEmpty())
        when (kind) {
            VaultRecoveryKind.IMPORT -> {
                DirectoryId.of(destination)
                require(nodes.isEmpty() && localDestinationIdentity == null)
                require(localSources.none { it.isEmpty() } && localSources.distinct().size == size)
            }
            VaultRecoveryKind.PASTE -> {
                DirectoryId.of(destination)
                require(localSources.isEmpty() && localDestinationIdentity == null)
            }
            VaultRecoveryKind.EXPORT -> {
                require(File(destination).isAbsolute && localDestinationIdentity != null && localSources.isEmpty())
                require(nodes.all { it.vault == owner.value })
            }
        }
        nodes.forEach { it.ref() }
    }
}

@Serializable
private data class VaultQueueDocument(
    val plan: VaultQueuePlan,
    val ownerLocation: String,
    val createdAtMillis: Long
)

@Serializable
internal data class VaultQueueCursor(val next: Int, val inFlight: Boolean = false) {
    val firstSafeIndex: Int get() = next + if (inFlight) 1 else 0

    fun validate(plan: VaultQueuePlan) {
        require(next in 0..plan.size && firstSafeIndex <= plan.size)
    }
}

/** The immutable plan and small cursor are written separately so large queues need one small sync per step. */
internal class VaultOperationJournal(root: File) {
    private val directory = FileVaultDirectory(root)
    private val active = mutableSetOf<String>()

    @Synchronized
    fun start(session: VaultSessionRecord, plan: VaultQueuePlan): VaultQueueRun {
        plan.validate(session.id)
        val id = UUID.randomUUID().toString()
        val path = path(session.id, id)
        val document = VaultQueueDocument(plan, session.directory.stableId, System.currentTimeMillis())
        val cursor = VaultQueueCursor(0)
        write(session, "$path.cursor", Json.encodeToString(cursor))
        try {
            write(session, "$path.plan", Json.encodeToString(document))
        } catch (error: Exception) {
            directory.delete("$path.cursor")
            throw error
        }
        return claim(session, path, plan, cursor)
    }

    @Synchronized
    fun resume(session: VaultSessionRecord, id: String, kind: VaultRecoveryKind): VaultQueueRun {
        val path = path(session.id, id)
        if (path in active) throw VaultFailure.OperationInProgress()
        val (document, cursor) = readQueue(session, path)
        require(document.plan.kind == kind)
        val next = VaultQueueCursor(cursor.firstSafeIndex)
        if (next.next == document.plan.size) throw VaultFailure.Unavailable("There are no unstarted items to resume")
        if (cursor.inFlight) write(session, "$path.cursor", Json.encodeToString(next))
        return claim(session, path, document.plan, next)
    }

    @Synchronized
    fun plan(session: VaultSessionRecord, id: String): VaultQueuePlan {
        val path = path(session.id, id)
        if (path in active) throw VaultFailure.OperationInProgress()
        return readQueue(session, path).first.plan
    }

    @Synchronized
    fun list(session: VaultSessionRecord, unlocked: Set<VaultId>): List<VaultRecoveryQueue> {
        val prefix = prefix(session.id)
        val files = directory.listFiles(prefix)
        val plans = files.filter { !it.isDirectory && it.relativePath.endsWith(".plan") }
        val paths = plans.mapTo(hashSetOf()) { it.relativePath.removeSuffix(".plan") }
        for (file in files) {
            val path = file.relativePath
            if (path.endsWith(".cursor") && path.removeSuffix(".cursor") !in paths) directory.delete(path)
            if (path.endsWith(".tmp")) directory.delete(path)
        }
        return plans.mapNotNull { file ->
            val path = file.relativePath.removeSuffix(".plan")
            val id = path.substringAfterLast('/')
            if (path in active || !isQueueId(id)) return@mapNotNull null
            try {
                val (document, cursor) = readQueue(session, path)
                if (cursor.next == document.plan.size) {
                    remove(path)
                    return@mapNotNull null
                }
                val locked = document.plan.nodes.mapTo(linkedSetOf()) { VaultId.of(it.vault) } - unlocked
                VaultRecoveryQueue(
                    id, session.id, document.plan.kind, document.createdAtMillis, cursor.next,
                    document.plan.size - cursor.firstSafeIndex, cursor.inFlight, document.plan.move, locked
                )
            } catch (_: Exception) {
                VaultRecoveryQueue(id, session.id, null, file.modifiedAtMillis, 0, 0, false, false, emptySet())
            }
        }.sortedByDescending { it.createdAtMillis }
    }

    @Synchronized
    fun discard(session: VaultSessionRecord, id: String) {
        val path = path(session.id, id)
        if (path in active) throw VaultFailure.OperationInProgress()
        remove(path)
    }

    private fun claim(
        session: VaultSessionRecord,
        path: String,
        plan: VaultQueuePlan,
        cursor: VaultQueueCursor
    ): VaultQueueRun {
        check(active.add(path))
        return VaultQueueRun(
            plan, cursor,
            save = { next -> synchronized(this) { write(session, "$path.cursor", Json.encodeToString(next)) } },
            finish = { synchronized(this) { remove(path) } },
            release = { synchronized(this) { active.remove(path) } }
        )
    }

    private fun readQueue(session: VaultSessionRecord, path: String): Pair<VaultQueueDocument, VaultQueueCursor> {
        val document = read<VaultQueueDocument>(session, "$path.plan")
        if (document.ownerLocation != session.directory.stableId) throw VaultFailure.StaleRegistration()
        document.plan.validate(session.id)
        val cursor = read<VaultQueueCursor>(session, "$path.cursor")
        cursor.validate(document.plan)
        return document to cursor
    }

    private fun remove(path: String) {
        if (!directory.delete("$path.plan") && directory.exists("$path.plan")) {
            throw VaultFailure.Unavailable("Unable to clear the recovery queue")
        }
        directory.delete("$path.cursor")
    }

    private fun write(session: VaultSessionRecord, path: String, document: String) {
        val plaintext = document.encodeToByteArray()
        try {
            require(plaintext.size <= MAX_PLAINTEXT) { "The recovery queue is too large" }
            val sealed = withKey(session) { key -> VaultCryptography.seal(key, plaintext, associatedData(path)) }
            directory.writeAtomic(path, sealed.nonce + sealed.ciphertext)
        } finally {
            plaintext.fill(0)
        }
    }

    private inline fun <reified T> read(session: VaultSessionRecord, path: String): T {
        val encoded = directory.openRandom(path, writable = false).use { file ->
            require(file.length in (OVERHEAD + 1).toLong()..(MAX_PLAINTEXT + OVERHEAD).toLong())
            ByteArray(file.length.toInt()).also(file::readFully)
        }
        val plaintext = withKey(session) { key ->
            VaultCryptography.open(
                key, VaultSealedValue(encoded.copyOfRange(0, NONCE_BYTES), encoded.copyOfRange(NONCE_BYTES, encoded.size)),
                associatedData(path)
            )
        }
        return try {
            Json.decodeFromString<T>(plaintext.decodeToString())
        } finally {
            plaintext.fill(0)
        }
    }

    private inline fun <T> withKey(session: VaultSessionRecord, block: (ByteArray) -> T): T {
        val key = VaultCryptography.deriveDomainKey(session.masterSecret, session.id, VaultKeyDomain.OPERATION_QUEUE)
        return try {
            block(key)
        } finally {
            key.fill(0)
        }
    }

    private fun associatedData(path: String) = "arcile-operation-queue-v1:$path".encodeToByteArray()

    private fun path(vault: VaultId, id: String): String {
        require(isQueueId(id))
        return "${prefix(vault)}/$id"
    }

    private fun prefix(vault: VaultId) =
        MessageDigest.getInstance("SHA-256").digest(vault.value.encodeToByteArray()).toHexDigest()

    private fun isQueueId(id: String): Boolean = runCatching { UUID.fromString(id).toString() == id }.getOrDefault(false)

    private companion object {
        const val MAX_PLAINTEXT = 4 * 1024 * 1024
        const val NONCE_BYTES = VaultCryptography.NONCE_SIZE_BYTES
        const val OVERHEAD = NONCE_BYTES + VaultCryptography.GCM_TAG_SIZE_BYTES
    }
}

internal class VaultQueueRun(
    val plan: VaultQueuePlan,
    private var cursor: VaultQueueCursor,
    private val save: (VaultQueueCursor) -> Unit,
    private val finish: () -> Unit,
    private val release: () -> Unit
) : Closeable {
    private var closed = false
    val startIndex: Int get() = cursor.next

    fun beginItem(index: Int) {
        check(!closed && cursor.next == index && !cursor.inFlight)
        persist(cursor.copy(inFlight = true))
    }

    fun finishItem(index: Int) {
        check(!closed && cursor.next == index && cursor.inFlight)
        persist(VaultQueueCursor(index + 1))
    }

    fun finishQueue() {
        check(!closed && !cursor.inFlight)
        finish()
    }

    fun cancelQueue() {
        check(!closed)
        finish()
    }

    private fun persist(next: VaultQueueCursor) {
        next.validate(plan)
        save(next)
        cursor = next
    }

    override fun close() {
        if (closed) return
        closed = true
        release()
    }
}
