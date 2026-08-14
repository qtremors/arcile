package dev.qtremors.arcile.core.storage.data

import android.content.Context
import android.net.Uri
import dev.qtremors.arcile.core.runtime.di.ArcileDispatchers
import dev.qtremors.arcile.core.storage.data.source.PrivilegedFileSystemDataSource
import dev.qtremors.arcile.core.storage.data.source.StorageNodeInput
import dev.qtremors.arcile.core.storage.domain.FileModel
import dev.qtremors.arcile.core.storage.domain.PrivilegedContentAccessManager
import dev.qtremors.arcile.core.storage.domain.PrivilegedContentAccessMode
import dev.qtremors.arcile.core.storage.domain.PrivilegedContentFailure
import dev.qtremors.arcile.core.storage.domain.PrivilegedContentGrant
import dev.qtremors.arcile.core.storage.domain.PrivilegedContentGrantPurpose
import dev.qtremors.arcile.core.storage.domain.PrivilegedContentReader
import dev.qtremors.arcile.core.storage.domain.PrivilegedGrantedContent
import dev.qtremors.arcile.core.storage.domain.StorageNodeRef
import dev.qtremors.arcile.core.storage.domain.isPrivileged
import java.io.FileInputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/**
 * Process-local capability registry for protected files.
 *
 * A record retains backend identity and an authoritative metadata snapshot, but only its random
 * token is placed in the URI. Every open re-resolves the node through the active backend and
 * rejects path replacement or metadata changes before handing bytes to the consumer.
 */
class DefaultPrivilegedContentAccessManager(
    private val context: Context,
    private val dataSource: PrivilegedFileSystemDataSource,
    private val applicationScope: CoroutineScope,
    private val dispatchers: ArcileDispatchers,
    private val clock: () -> Long = System::currentTimeMillis,
    private val tokenGenerator: () -> String = SecureTokenGenerator()::next,
    private val revokeAfterCloseMillis: Long = REVOKE_AFTER_CLOSE_MILLIS,
    private val maximumActiveGrants: Int = MAXIMUM_ACTIVE_GRANTS
) : PrivilegedContentAccessManager {
    private val grants = ConcurrentHashMap<String, GrantRecord>()
    private val reusableGrants = ConcurrentHashMap<ReuseKey, String>()

    override suspend fun issue(
        node: StorageNodeRef,
        displayName: String,
        mimeType: String?,
        sizeBytes: Long?,
        modifiedAtMillis: Long?,
        accessMode: PrivilegedContentAccessMode,
        purpose: PrivilegedContentGrantPurpose,
        lifetimeMillis: Long,
        expectedConsumerUid: Int?
    ): Result<PrivilegedContentGrant> = runCatchingPreservingCancellation {
        require(lifetimeMillis in MIN_LIFETIME_MILLIS..MAX_LIFETIME_MILLIS) {
            "Protected-content lifetime is outside the supported range"
        }
        require(expectedConsumerUid == null || expectedConsumerUid >= 0) {
            "Expected consumer UID cannot be negative"
        }
        require(accessMode == PrivilegedContentAccessMode.READ) {
            "Protected-content grants are read-only"
        }
        cleanupExpired()

        val inspected = inspectGrantableNode(node)
        sizeBytes?.takeIf { it >= 0L }?.let { expected ->
            if (expected != inspected.size) throw PrivilegedContentFailure.SourceChanged()
        }
        modifiedAtMillis?.takeIf { it > 0L }?.let { expected ->
            if (inspected.lastModified > 0L && expected != inspected.lastModified) {
                throw PrivilegedContentFailure.SourceChanged()
            }
        }

        val authoritativeNode = inspected.nodeRef
        val resolvedName = inspected.name.takeIf(String::isNotBlank)
            ?: displayName.takeIf(String::isNotBlank)
            ?: "protected-file"
        val resolvedMimeType = inspected.mimeType?.takeIf(String::isNotBlank)
            ?: mimeType?.takeIf(String::isNotBlank)
            ?: "application/octet-stream"
        val reuseKey = ReuseKey(
            backendId = authoritativeNode.backendId,
            canonicalIdentity = authoritativeNode.canonicalIdentity.value,
            accessMode = accessMode,
            expectedConsumerUid = expectedConsumerUid,
            purpose = purpose
        )
        if (purpose == PrivilegedContentGrantPurpose.INTERNAL_PREVIEW) {
            findReusable(reuseKey, inspected)?.let { return@runCatchingPreservingCancellation it }
        }

        ensureCapacity()
        val now = clock()
        val expiresAt = saturatedAdd(now, lifetimeMillis)
        var token: String
        do {
            token = tokenGenerator()
            require(isValidToken(token)) { "Token generator returned an invalid capability" }
        } while (grants.containsKey(token))

        val public = PrivilegedContentGrant(
            token = token,
            contentUri = Uri.Builder()
                .scheme("content")
                .authority(authority(context))
                .appendPath(token)
                .build()
                .toString(),
            backendId = authoritativeNode.backendId,
            canonicalStorageIdentity = authoritativeNode.canonicalIdentity.value,
            accessMode = accessMode,
            displayName = resolvedName,
            mimeType = resolvedMimeType,
            sizeBytes = inspected.size.coerceAtLeast(0L),
            expiresAtMillis = expiresAt,
            expectedConsumerUid = expectedConsumerUid,
            purpose = purpose
        )
        val record = GrantRecord(
            public = public,
            node = authoritativeNode,
            modifiedAtMillis = inspected.lastModified.coerceAtLeast(0L),
            createdAtMillis = now,
            reuseKey = reuseKey.takeIf {
                purpose == PrivilegedContentGrantPurpose.INTERNAL_PREVIEW
            }
        )
        grants[token] = record
        record.reuseKey?.let { key ->
            reusableGrants.put(key, token)?.takeIf { it != token }?.let(::revoke)
        }
        public
    }

    override fun describe(token: String): Result<PrivilegedContentGrant> {
        cleanupExpired()
        if (!isValidToken(token)) return Result.failure(PrivilegedContentFailure.InvalidGrant())
        return grants[token]?.public?.let(Result.Companion::success)
            ?: Result.failure(PrivilegedContentFailure.InvalidGrant())
    }

    override suspend fun openGrantedContent(
        token: String,
        consumerUid: Int
    ): Result<PrivilegedGrantedContent> = runCatchingPreservingCancellation {
        require(consumerUid >= 0) { "Consumer UID cannot be negative" }
        cleanupExpired()
        val record = grants[token] ?: throw PrivilegedContentFailure.InvalidGrant()
        reserveReader(record, consumerUid)
        val released = AtomicBoolean(false)
        try {
            val inspected = inspectGrantableNode(record.node)
            verifyUnchanged(record, inspected)
            val initial = dataSource.openNodeInput(inspected.nodeRef).getOrElse { error ->
                throw PrivilegedContentFailure.BackendUnavailable(error)
            }
            val reader = ReopenablePrivilegedContentReader(
                sizeBytes = record.public.sizeBytes,
                initialInput = initial,
                reopen = {
                    val current = inspectGrantableNode(record.node)
                    verifyUnchanged(record, current)
                    dataSource.openNodeInput(current.nodeRef).getOrElse { error ->
                        throw PrivilegedContentFailure.BackendUnavailable(error)
                    }
                },
                dispatchers = dispatchers,
                onClose = { releaseReader(record, released) }
            )
            PrivilegedGrantedContent(
                displayName = record.public.displayName,
                mimeType = record.public.mimeType,
                sizeBytes = record.public.sizeBytes,
                reader = reader
            )
        } catch (error: Throwable) {
            releaseReader(record, released)
            throw error
        }
    }

    override fun activeGrants(): List<PrivilegedContentGrant> {
        cleanupExpired()
        return grants.values.map(GrantRecord::public).sortedBy(PrivilegedContentGrant::expiresAtMillis)
    }

    override fun revoke(token: String): Boolean {
        val record = grants.remove(token) ?: return false
        synchronized(record) {
            record.autoRevoke?.cancel()
            record.autoRevoke = null
        }
        record.reuseKey?.let { reusableGrants.remove(it, token) }
        return true
    }

    override fun revokeAll() {
        grants.keys.toList().forEach(::revoke)
    }

    private suspend fun inspectGrantableNode(node: StorageNodeRef): FileModel {
        if (!node.isPrivileged || !node.capabilities.canRead) {
            throw PrivilegedContentFailure.UnsupportedNode()
        }
        val inspected = dataSource.inspectNode(node).getOrElse { error ->
            throw PrivilegedContentFailure.BackendUnavailable(error)
        }
        if (
            inspected.isDirectory ||
            !inspected.nodeRef.isPrivileged ||
            !inspected.nodeRef.capabilities.canRead ||
            inspected.nodeRef.backendId != node.backendId
        ) {
            throw PrivilegedContentFailure.UnsupportedNode()
        }
        if (inspected.nodeRef.canonicalIdentity != node.canonicalIdentity) {
            throw PrivilegedContentFailure.SourceChanged()
        }
        return inspected
    }

    private fun verifyUnchanged(record: GrantRecord, inspected: FileModel) {
        if (
            inspected.nodeRef.backendId != record.public.backendId ||
            inspected.nodeRef.canonicalIdentity.value != record.public.canonicalStorageIdentity ||
            inspected.size.coerceAtLeast(0L) != record.public.sizeBytes ||
            metadataChanged(record.modifiedAtMillis, inspected.lastModified)
        ) {
            revoke(record.public.token)
            throw PrivilegedContentFailure.SourceChanged()
        }
    }

    private fun findReusable(reuseKey: ReuseKey, inspected: FileModel): PrivilegedContentGrant? {
        val token = reusableGrants[reuseKey] ?: return null
        val record = grants[token] ?: run {
            reusableGrants.remove(reuseKey, token)
            return null
        }
        if (
            record.public.expiresAtMillis <= clock() ||
            record.public.sizeBytes != inspected.size.coerceAtLeast(0L) ||
            metadataChanged(record.modifiedAtMillis, inspected.lastModified)
        ) {
            revoke(token)
            return null
        }
        return record.public
    }

    private fun reserveReader(record: GrantRecord, consumerUid: Int) {
        synchronized(record) {
            if (grants[record.public.token] !== record || record.public.expiresAtMillis <= clock()) {
                throw PrivilegedContentFailure.InvalidGrant()
            }
            record.public.expectedConsumerUid?.let { expected ->
                if (consumerUid != expected) throw PrivilegedContentFailure.ConsumerMismatch()
            }
            record.autoRevoke?.cancel()
            record.autoRevoke = null
            record.openReaders.incrementAndGet()
        }
    }

    private fun releaseReader(record: GrantRecord, released: AtomicBoolean) {
        if (!released.compareAndSet(false, true)) return
        synchronized(record) {
            val remaining = record.openReaders.updateAndGet { count -> maxOf(0, count - 1) }
            if (
                remaining != 0 ||
                grants[record.public.token] !== record ||
                record.public.purpose != PrivilegedContentGrantPurpose.EXTERNAL_HANDOFF
            ) {
                return
            }
            record.autoRevoke?.cancel()
            record.autoRevoke = applicationScope.launch {
                delay(revokeAfterCloseMillis)
                synchronized(record) {
                    if (record.openReaders.get() != 0) return@launch
                }
                revoke(record.public.token)
            }
        }
    }

    private fun cleanupExpired() {
        val now = clock()
        grants.values
            .asSequence()
            .filter { it.public.expiresAtMillis <= now && it.openReaders.get() == 0 }
            .map { it.public.token }
            .toList()
            .forEach(::revoke)
    }

    private fun ensureCapacity() {
        if (grants.size < maximumActiveGrants) return
        grants.values
            .asSequence()
            .filter { it.openReaders.get() == 0 }
            .sortedWith(
                compareBy<GrantRecord> { it.public.purpose != PrivilegedContentGrantPurpose.EXTERNAL_HANDOFF }
                    .thenBy(GrantRecord::createdAtMillis)
            )
            .take((grants.size - maximumActiveGrants + 1).coerceAtLeast(1))
            .map { it.public.token }
            .toList()
            .forEach(::revoke)
        check(grants.size < maximumActiveGrants) { "Too many protected-content readers are active" }
    }

    private data class ReuseKey(
        val backendId: String,
        val canonicalIdentity: String,
        val accessMode: PrivilegedContentAccessMode,
        val expectedConsumerUid: Int?,
        val purpose: PrivilegedContentGrantPurpose
    )

    private class GrantRecord(
        val public: PrivilegedContentGrant,
        val node: StorageNodeRef,
        val modifiedAtMillis: Long,
        val createdAtMillis: Long,
        val reuseKey: ReuseKey?
    ) {
        val openReaders = AtomicInteger(0)
        var autoRevoke: Job? = null
    }

    companion object {
        const val MIN_LIFETIME_MILLIS = 1_000L
        const val MAX_LIFETIME_MILLIS = 12 * 60 * 60 * 1000L
        const val REVOKE_AFTER_CLOSE_MILLIS = 30_000L
        const val MAXIMUM_ACTIVE_GRANTS = 2_048
        const val TOKEN_HEX_LENGTH = 64

        fun authority(context: Context): String = "${context.packageName}.privileged.content"

        fun isValidToken(token: String): Boolean =
            token.length == TOKEN_HEX_LENGTH && token.all { it in '0'..'9' || it in 'a'..'f' }

        private fun metadataChanged(expected: Long, actual: Long): Boolean =
            expected > 0L && actual > 0L && expected != actual

        private fun saturatedAdd(left: Long, right: Long): Long =
            if (right > Long.MAX_VALUE - left) Long.MAX_VALUE else left + right
    }
}

private class SecureTokenGenerator {
    private val random = SecureRandom()

    fun next(): String {
        val bytes = ByteArray(32).also(random::nextBytes)
        return bytes.joinToString(separator = "") { byte ->
            "%02x".format(byte.toInt() and 0xff)
        }
    }
}

private class ReopenablePrivilegedContentReader(
    override val sizeBytes: Long,
    initialInput: StorageNodeInput,
    private val reopen: suspend () -> StorageNodeInput,
    private val dispatchers: ArcileDispatchers,
    private val onClose: () -> Unit
) : PrivilegedContentReader {
    private val closed = AtomicBoolean(false)
    private var source: StorageNodeInput? = initialInput
    private var sequentialPosition = 0L

    override fun readAt(position: Long, target: ByteArray, offset: Int, length: Int): Int = synchronized(this) {
        check(!closed.get()) { "Protected-content reader is closed" }
        require(position >= 0L) { "Read position cannot be negative" }
        require(offset >= 0 && length >= 0 && offset <= target.size - length) {
            "Read buffer range is invalid"
        }
        if (length == 0 || position >= sizeBytes) return@synchronized 0
        val requested = minOf(length.toLong(), sizeBytes - position).toInt()
        val current = requireNotNull(source)
        val stream = current.stream
        if (stream is FileInputStream) {
            return@synchronized stream.channel.read(ByteBuffer.wrap(target, offset, requested), position)
                .coerceAtLeast(0)
        }

        if (position < sequentialPosition) {
            current.close()
            source = runBlocking(dispatchers.io) { reopen() }
            sequentialPosition = 0L
        }
        val active = requireNotNull(source).stream
        skipFully(active, position - sequentialPosition)
        sequentialPosition = position
        val count = active.read(target, offset, requested)
        if (count > 0) sequentialPosition += count
        count.coerceAtLeast(0)
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        try {
            synchronized(this) {
                source?.close()
                source = null
            }
        } finally {
            onClose()
        }
    }

    private fun skipFully(stream: InputStream, count: Long) {
        var remaining = count
        val discard = ByteArray(SKIP_BUFFER_SIZE)
        while (remaining > 0L) {
            val skipped = stream.skip(remaining)
            if (skipped > 0L) {
                remaining -= skipped
                continue
            }
            val read = stream.read(discard, 0, minOf(discard.size.toLong(), remaining).toInt())
            if (read < 0) throw PrivilegedContentFailure.SourceChanged()
            remaining -= read
        }
    }

    private companion object {
        const val SKIP_BUFFER_SIZE = 16 * 1024
    }
}
