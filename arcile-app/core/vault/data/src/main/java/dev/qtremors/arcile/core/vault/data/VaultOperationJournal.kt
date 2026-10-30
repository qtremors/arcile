package dev.qtremors.arcile.core.vault.data

import dev.qtremors.arcile.core.vault.crypto.FileVaultDirectory
import dev.qtremors.arcile.core.vault.crypto.VaultCryptography
import dev.qtremors.arcile.core.vault.crypto.VaultKeyDomain
import dev.qtremors.arcile.core.vault.crypto.VaultSealedValue
import dev.qtremors.arcile.core.vault.domain.DirectoryId
import dev.qtremors.arcile.core.vault.domain.NodeId
import dev.qtremors.arcile.core.vault.domain.VaultFailure
import dev.qtremors.arcile.core.vault.domain.VaultItemResult
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
import kotlinx.coroutines.sync.Mutex

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
    val localDestinationIdentity: ExportFolderIdentity? = null,
    val publicationReceipts: Boolean = false
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
internal data class VaultQueueCursor(val next: Int, val inFlight: Boolean = false, val retries: List<Int> = emptyList(), val publicationPrepared: Boolean = false, val blockedRetries: List<Int> = emptyList()) {
    val firstSafeIndex: Int get() = next + if (inFlight) 1 else 0

    fun validate(plan: VaultQueuePlan) {
        require(next in 0..plan.size && firstSafeIndex <= plan.size)
        require(!publicationPrepared || inFlight)
        require(retries.size <= plan.size && retries.distinct().size == retries.size)
        require(retries.all { it in 0 until next })
        require(blockedRetries.distinct().size == blockedRetries.size && blockedRetries.all { it in retries })
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
        val next = VaultQueueCursor(cursor.firstSafeIndex, retries = cursor.retries, blockedRetries = cursor.blockedRetries)
        if (next.next == document.plan.size && next.retries.all { it in next.blockedRetries }) throw VaultFailure.Unavailable("There are no verified items to resume")
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
            if (".retry-" in path && path.substringBefore(".retry-") !in paths) directory.delete(path)
            if (".receipt-" in path && path.substringBefore(".receipt-") !in paths) directory.delete(path)
            if (".export-" in path && path.substringBefore(".export-") !in paths) directory.delete(path)
            if (".progress-" in path && path.substringBefore(".progress-") !in paths) directory.delete(path)
            if (".step-" in path && path.substringBefore(".step-") !in paths) directory.delete(path)
        }
        return plans.mapNotNull { file ->
            val path = file.relativePath.removeSuffix(".plan")
            val id = path.substringAfterLast('/')
            if (path in active || !isQueueId(id)) return@mapNotNull null
            try {
                val (document, cursor) = readQueue(session, path)
                if (cursor.next == document.plan.size && cursor.retries.isEmpty()) {
                    remove(path)
                    return@mapNotNull null
                }
                val locked = document.plan.nodes.mapTo(linkedSetOf()) { VaultId.of(it.vault) } - unlocked
                VaultRecoveryQueue(
                    id, session.id, document.plan.kind, document.createdAtMillis, cursor.next,
                    document.plan.size - cursor.firstSafeIndex + cursor.retries.size - cursor.blockedRetries.size,
                    cursor.inFlight || cursor.blockedRetries.isNotEmpty(), document.plan.move, locked,
                    cursor.blockedRetries + listOfNotNull(cursor.next.takeIf { cursor.inFlight })
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
        val checkpointSizes = checkpointSizes(path, cursor.retries)
        var totalBytes = checkpointSizes.values.sum()
        val progressBudget = progressBudget(path)
        check(active.add(path))
        return VaultQueueRun(
            plan, cursor,
            save = { next -> synchronized(this) { write(session, "$path.cursor", Json.encodeToString(next)) } },
            finish = { synchronized(this) { remove(path) } },
            release = { synchronized(this) { active.remove(path) } },
            saveCheckpoint = { index, checkpoint -> synchronized(this) {
                checkpoint.validate(plan, index, session.id)
                val document = Json.encodeToString(checkpoint)
                val size = document.encodeToByteArray().let { bytes ->
                    try { bytes.size.toLong() + OVERHEAD } finally { bytes.fill(0) }
                }
                val total = totalBytes - (checkpointSizes[index] ?: 0L) + size
                require(total <= MAX_PLAINTEXT) { "The recovery checkpoints are too large" }
                write(session, "$path.retry-$index", document)
                totalBytes = total
                checkpointSizes[index] = size
            } },
            readCheckpoint = { index -> synchronized(this) { readCheckpoint(session, path, plan, index) } },
            removeCheckpoint = { index -> synchronized(this) {
                directory.delete("$path.retry-$index")
                totalBytes -= checkpointSizes.remove(index) ?: 0L
                val obsolete = progressBudget.keys.filter { it == "$path.progress-$index" || it.startsWith("$path.step-$index-") }
                for (entry in obsolete) if (directory.delete(entry)) progressBudget.remove(entry)
            } },
            savePublication = { index, receipt -> synchronized(this) {
                receipt.validate(plan, index, session.id)
                receipt.checkpoint?.let { checkpoint ->
                    val encoded = Json.encodeToString(checkpoint).encodeToByteArray()
                    try { require(totalBytes + encoded.size + OVERHEAD <= MAX_PLAINTEXT) }
                    finally { encoded.fill(0) }
                }
                write(session, "$path.receipt-$index", Json.encodeToString(receipt))
            } },
            removePublication = { index -> synchronized(this) {
                directory.delete("$path.receipt-$index")
                directory.delete("$path.export-$index")
                Unit
            } },
            saveExportPublication = { index, receipt -> synchronized(this) {
                receipt.validate(plan, index, session.id)
                receipt.checkpoint?.let { checkpoint ->
                    val encoded = Json.encodeToString(checkpoint).encodeToByteArray()
                    try { require(totalBytes + encoded.size + OVERHEAD <= MAX_PLAINTEXT) }
                    finally { encoded.fill(0) }
                }
                write(session, "$path.export-$index", Json.encodeToString(receipt))
            } },
            canFinishPublication = { index -> synchronized(this) {
                if (!directory.exists("$path.export-$index")) true
                else read<VaultExportPublicationReceipt>(session, "$path.export-$index").also {
                    it.validate(plan, index, session.id)
                }.reconcile() != VaultPublicationState.UNCERTAIN
            } },
            queueId = path.substringAfterLast('/'),
            ownerMount = session.mutationMutex,
            progress = { index -> synchronized(this) { retryProgress(session, path, plan, index, progressBudget) { size ->
                totalBytes += size - (checkpointSizes.put(index, size) ?: 0L)
            } } }

        )
    }

    private fun readQueue(session: VaultSessionRecord, path: String): Pair<VaultQueueDocument, VaultQueueCursor> {
        val document = read<VaultQueueDocument>(session, "$path.plan")
        if (document.ownerLocation != session.directory.stableId) throw VaultFailure.StaleRegistration()
        document.plan.validate(session.id)
        var cursor = read<VaultQueueCursor>(session, "$path.cursor")
        cursor.validate(document.plan)
        // A checkpoint written after publication but before cursor advancement is authoritative.
        if (cursor.inFlight && directory.exists("$path.retry-${cursor.next}")) {
            readCheckpoint(session, path, document.plan, cursor.next)
            cursor = VaultQueueCursor(cursor.next + 1, retries = cursor.retries + cursor.next)
            write(session, "$path.cursor", Json.encodeToString(cursor))
        }
        if (cursor.inFlight && directory.exists("$path.receipt-${cursor.next}")) {
            val receipt = read<VaultPublicationReceipt>(session, "$path.receipt-${cursor.next}")
            receipt.validate(document.plan, cursor.next, session.id)
            val next = reconcilePublication(session, path, document.plan, cursor, receipt.inspect(session), receipt.checkpoint)
            if (next != cursor) {
                cursor = next
                write(session, "$path.cursor", Json.encodeToString(cursor))
            }
        }
        if (cursor.inFlight && directory.exists("$path.export-${cursor.next}")) {
            val receipt = read<VaultExportPublicationReceipt>(session, "$path.export-${cursor.next}")
            receipt.validate(document.plan, cursor.next, session.id)
            val next = reconcilePublication(session, path, document.plan, cursor, receipt.reconcile(), receipt.checkpoint)
            if (next != cursor) {
                cursor = next
                write(session, "$path.cursor", Json.encodeToString(cursor))
            }
        }
        // Only new producers promise to record a receipt before any publication/plaintext write.
        // Older queues without that promise retain their conservative manual-review behavior.
        if (cursor.inFlight && document.plan.publicationReceipts && !cursor.publicationPrepared &&
            !directory.exists("$path.receipt-${cursor.next}") && !directory.exists("$path.export-${cursor.next}")
        ) {
            cursor = cursor.copy(inFlight = false, publicationPrepared = false)
            write(session, "$path.cursor", Json.encodeToString(cursor))
        }
        require(checkpointSizes(path, cursor.retries).values.sum() <= MAX_PLAINTEXT)
        cursor.retries.forEach { readCheckpoint(session, path, document.plan, it) }
        val progressBudget = progressBudget(path)
        val blocked = cursor.retries.filter { index ->
            !retryProgress(session, path, document.plan, index, progressBudget).recover()
        }
        if (blocked != cursor.blockedRetries) {
            cursor = cursor.copy(blockedRetries = blocked)
            write(session, "$path.cursor", Json.encodeToString(cursor))
        }
        return document to cursor
    }

    private fun progressBudget(path: String) = directory.listFiles(path.substringBeforeLast('/')).filter {
        it.relativePath.startsWith("$path.step-") || it.relativePath.startsWith("$path.progress-")
    }.associate { it.relativePath to it.length }.toMutableMap()

    private fun retryProgress(
        session: VaultSessionRecord, path: String, plan: VaultQueuePlan, index: Int,
        sizes: MutableMap<String, Long>, onCheckpointSaved: (Long) -> Unit = {}
    ): VaultRetryProgress {
        fun progressWrite(target: String, document: String) {
            val encoded = document.encodeToByteArray()
            val size = try { encoded.size.toLong() + OVERHEAD } finally { encoded.fill(0) }
            require(sizes.values.sum() - (sizes[target] ?: 0L) + size <= MAX_PLAINTEXT) { "The folder retry journal is too large" }
            write(session, target, document)
            sizes[target] = size
        }
        return VaultRetryProgress(object : VaultRetryProgress.Storage {
            override fun checkpoint() = readCheckpoint(session, path, plan, index)
            override fun saveCheckpoint(value: VaultRetryCheckpoint) {
                value.validate(plan, index, session.id)
                val document = Json.encodeToString(value)
                val encoded = document.encodeToByteArray()
                val size = encoded.size.toLong() + OVERHEAD
                try {
                    val checkpoints = checkpointSizes(path, read<VaultQueueCursor>(session, "$path.cursor").retries)
                    require(checkpoints.values.sum() - checkpoints.getValue(index) + size <= MAX_PLAINTEXT)
                } finally { encoded.fill(0) }
                write(session, "$path.retry-$index", document)
                onCheckpointSaved(size)
            }
            override fun count() = if (directory.exists("$path.progress-$index")) read<Int>(session, "$path.progress-$index") else null
            override fun saveCount(value: Int) = progressWrite("$path.progress-$index", Json.encodeToString(value))
            override fun readStep(sequence: Int) = read<VaultRetryPublication>(session, "$path.step-$index-$sequence")
            override fun writeStep(sequence: Int, value: VaultRetryPublication) =
                progressWrite("$path.step-$index-$sequence", Json.encodeToString(value))
            override fun removeSteps(through: Int) {
                val prefix = "$path.step-$index-"
                val obsolete = sizes.keys.filter { it.startsWith(prefix) && (it.removePrefix(prefix).toIntOrNull() ?: Int.MAX_VALUE) <= through }
                for (entry in obsolete) if (directory.delete(entry)) sizes.remove(entry)
            }
        }, validate = { step, checkpoint -> step.validate(checkpoint, plan, index, session.id) },
            inspect = { step, checkpoint -> step.inspect(checkpoint, session) })
    }

    private fun reconcilePublication(
        session: VaultSessionRecord,
        path: String,
        plan: VaultQueuePlan,
        cursor: VaultQueueCursor,
        state: VaultPublicationState,
        checkpoint: VaultRetryCheckpoint?
    ): VaultQueueCursor = when (state) {
        VaultPublicationState.PUBLISHED -> {
            if (checkpoint != null) {
                checkpoint.validate(plan, cursor.next, session.id)
                val encoded = Json.encodeToString(checkpoint)
                val bytes = encoded.encodeToByteArray()
                try { require(checkpointSizes(path, cursor.retries).values.sum() + bytes.size + OVERHEAD <= MAX_PLAINTEXT) }
                finally { bytes.fill(0) }
                write(session, "$path.retry-${cursor.next}", encoded)
            }
            VaultQueueCursor(cursor.next + 1, retries = cursor.retries + listOfNotNull(cursor.next.takeIf { checkpoint != null }))
        }
        VaultPublicationState.UNPUBLISHED -> cursor.copy(inFlight = false, publicationPrepared = false)
        VaultPublicationState.UNCERTAIN -> cursor
    }

    private fun checkpointSizes(path: String, indices: List<Int>): MutableMap<Int, Long> {
        val files = directory.listFiles(path.substringBeforeLast('/')).associateBy { it.relativePath }
        return indices.associateWith { requireNotNull(files["$path.retry-$it"]).length }.toMutableMap()
    }

    private fun readCheckpoint(session: VaultSessionRecord, path: String, plan: VaultQueuePlan, index: Int): VaultRetryCheckpoint =
        read<VaultRetryCheckpoint>(session, "$path.retry-$index").also { it.validate(plan, index, session.id) }

    private fun remove(path: String) {
        if (!directory.delete("$path.plan") && directory.exists("$path.plan")) {
            throw VaultFailure.Unavailable("Unable to clear the recovery queue")
        }
        directory.delete("$path.cursor")
        directory.listFiles(path.substringBeforeLast('/')).filter {
            !it.isDirectory && (it.relativePath.startsWith("$path.retry-") || it.relativePath.startsWith("$path.receipt-") || it.relativePath.startsWith("$path.export-") ||
                it.relativePath.startsWith("$path.progress-") || it.relativePath.startsWith("$path.step-"))
        }.forEach { directory.delete(it.relativePath) }
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
    private val release: () -> Unit,
    private val saveCheckpoint: (Int, VaultRetryCheckpoint) -> Unit = { _, _ -> },
    private val readCheckpoint: (Int) -> VaultRetryCheckpoint = { error("No retry checkpoint") },
    private val removeCheckpoint: (Int) -> Unit = {},
    private val savePublication: (Int, VaultPublicationReceipt) -> Unit = { _, _ -> },
    private val removePublication: (Int) -> Unit = {},
    private val saveExportPublication: (Int, VaultExportPublicationReceipt) -> Unit = { _, _ -> },
    private val canFinishPublication: (Int) -> Boolean = { true },
    private val progress: (Int) -> VaultRetryProgress = { error("No folder retry journal") },
    private val queueId: String? = null,
    private val ownerMount: Mutex? = null
) : Closeable {
    private var closed = false
    private val retryProgress = mutableMapOf<Int, VaultRetryProgress>()
    val startIndex: Int get() = cursor.next

    fun beginItem(index: Int) {
        check(!closed && cursor.next == index && !cursor.inFlight)
        persist(cursor.copy(inFlight = true))
    }

    fun finishItem(index: Int, result: VaultItemResult? = null): Boolean {
        check(!closed && cursor.next == index && cursor.inFlight)
        if (!canFinishPublication(index)) return false
        val checkpoint = result?.let(VaultRetryCheckpoint::from)
        if (checkpoint != null) saveCheckpoint(index, checkpoint)
        persist(VaultQueueCursor(index + 1, retries = cursor.retries + listOfNotNull(index.takeIf { checkpoint != null }), blockedRetries = cursor.blockedRetries))
        removePublication(index)
        return true
    }

    fun prepareExportPublication(index: Int, receipt: VaultExportPublicationReceipt) {
        check(!closed && cursor.next == index && cursor.inFlight)
        saveExportPublication(index, receipt)
        if (!cursor.publicationPrepared) persist(cursor.copy(publicationPrepared = true))
    }

    fun preparePublication(index: Int, receipt: VaultPublicationReceipt) {
        check(!closed && cursor.next == index && cursor.inFlight)
        savePublication(index, receipt)
        if (!cursor.publicationPrepared) persist(cursor.copy(publicationPrepared = true))
    }

    fun pendingRetries(): Sequence<Pair<Int, VaultItemResult>> {
        check(!closed)
        return cursor.retries.asSequence().filterNot { it in cursor.blockedRetries }.map { index ->
            check(!closed)
            index to bindRetry(index, readCheckpoint(index).restore())
        }
    }

    fun finishRetry(index: Int, result: VaultItemResult): VaultItemResult {
        check(!closed && index in cursor.retries)
        val recorded = retryProgress[index]?.finish()
        val checkpoint = if (recorded != null) recorded.takeIf {
            it.merge?.children?.isNotEmpty() == true || it.export?.deferred?.isNotEmpty() == true
        } else VaultRetryCheckpoint.from(result)
        if (checkpoint != null) {
            if (recorded == null) saveCheckpoint(index, checkpoint)
        }
        else {
            persist(cursor.copy(retries = cursor.retries - index))
            removeCheckpoint(index)
        }
        val actual = if (recorded == null) result else {
            val returned = VaultRetryCheckpoint.from(result)
            if (returned?.merge == recorded.merge && returned?.export == recorded.export) result
            else if (checkpoint != null) recorded.restore().copy(failure = result.failure)
            else result.copy(outcome = dev.qtremors.arcile.core.vault.domain.VaultItemOutcome.COMPLETED,
                failure = null, mergeRetry = null, exportRetry = null)
        }
        return bindRetry(index, actual)
    }

    fun bindRetry(index: Int, result: VaultItemResult, sourceMount: Mutex? = null): VaultItemResult {
        val origin = queueId?.let { id -> VaultRetryOrigin(id, index, ownerMount, sourceMount ?: ownerMount).takeIf { index in cursor.retries } }
        return result.copy(
            mergeRetry = (result.mergeRetry as? PendingVaultMerge)?.copy(origin = origin) ?: result.mergeRetry,
            exportRetry = (result.exportRetry as? PendingVaultExport)?.copy(origin = origin) ?: result.exportRetry
        )
    }

    fun retryProgress(index: Int): VaultRetryProgress {
        check(!closed && index in cursor.retries && index !in cursor.blockedRetries)
        return retryProgress.getOrPut(index) {
            progress(index).also { check(it.recover()) { "The folder publication requires review" } }
        }
    }

    fun finishQueue() {
        check(!closed)
        if (cursor.next == plan.size && !cursor.inFlight && cursor.retries.isEmpty()) finish()
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
