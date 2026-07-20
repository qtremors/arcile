package dev.qtremors.arcile.core.vault.data

import android.content.Context
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import androidx.core.app.NotificationCompat
import android.net.Uri
import android.os.Process
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.qtremors.arcile.core.runtime.di.ApplicationScope
import dev.qtremors.arcile.core.ui.externalfile.ExternalFileAccessHelper
import dev.qtremors.arcile.core.ui.externalfile.ExternalFileAccessProvider
import dev.qtremors.arcile.core.vault.crypto.VaultFileCodec
import dev.qtremors.arcile.core.vault.domain.VaultExternalAccessManager
import dev.qtremors.arcile.core.vault.domain.VaultExternalGrant
import dev.qtremors.arcile.core.vault.domain.VaultFailure
import dev.qtremors.arcile.core.vault.domain.VaultGrantedContent
import dev.qtremors.arcile.core.vault.domain.VaultNodeKind
import dev.qtremors.arcile.core.vault.domain.VaultNodeMetadata
import dev.qtremors.arcile.core.vault.domain.VaultNodeRef
import dev.qtremors.arcile.core.vault.domain.VaultSeekableReader
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class DefaultVaultExternalAccessManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val repository: DefaultVaultRepository,
    @param:ApplicationScope private val applicationScope: CoroutineScope
) : VaultExternalAccessManager {
    private val grants = ConcurrentHashMap<String, GrantRecord>()
    private val fallbacks = ConcurrentHashMap<String, FallbackRecord>()
    private val random = SecureRandom()
    private val fileCodec = VaultFileCodec()

    override fun issue(ref: VaultNodeRef, lifetimeMillis: Long): Result<VaultExternalGrant> = runCatching {
        require(lifetimeMillis in MIN_LIFETIME_MILLIS..MAX_LIFETIME_MILLIS)
        cleanupExpired()
        val (session, metadata) = repository.createExternalAccessSession(ref)
        if (metadata.kind != VaultNodeKind.FILE) {
            session.destroy()
            throw VaultFailure.InvalidPath("Only files can be shared or opened externally")
        }
        var token: String
        do token = randomToken() while (grants.containsKey(token))
        val now = System.currentTimeMillis()
        val expires = if (now > Long.MAX_VALUE - lifetimeMillis) Long.MAX_VALUE else now + lifetimeMillis
        val public = VaultExternalGrant(
            token,
            Uri.Builder().scheme("content").authority(authority(context)).appendPath(token).build().toString(),
            metadata.name,
            metadata.mimeType ?: "application/octet-stream",
            metadata.sizeBytes,
            expires
        )
        grants[token] = GrantRecord(public, ref, session)
        updateNotification()
        public
    }

    override fun activeGrants(): List<VaultExternalGrant> {
        cleanupExpired()
        return (grants.values.map(GrantRecord::public) + fallbacks.values.map(FallbackRecord::public))
            .sortedBy(VaultExternalGrant::expiresAtMillis)
    }

    override suspend fun issuePlaintextFallback(
        ref: VaultNodeRef,
        lifetimeMillis: Long
    ): Result<VaultExternalGrant> = runCatching {
        require(lifetimeMillis in MIN_LIFETIME_MILLIS..MAX_LIFETIME_MILLIS)
        cleanupExpired()
        val streamGrant = issue(ref, lifetimeMillis).getOrThrow()
        var fallback: ExternalFileAccessHelper.PrivatePlaintextFallback? = null
        var fallbackToken: String? = null
        try {
            val content = openGrantedContent(streamGrant.token, Process.myUid()).getOrThrow()
            val job = currentCoroutineContext()[Job]
            content.reader.use { reader ->
                fallback = ExternalFileAccessHelper.createPrivatePlaintextFallback(
                    context,
                    content.displayName,
                    content.mimeType,
                    content.sizeBytes
                ) { output ->
                    val buffer = ByteArray(PLAINTEXT_COPY_BUFFER_SIZE)
                    try {
                        var position = 0L
                        while (position < reader.sizeBytes) {
                            job?.ensureActive()
                            val count = reader.readAt(
                                position,
                                buffer,
                                0,
                                minOf(buffer.size.toLong(), reader.sizeBytes - position).toInt()
                            )
                            if (count <= 0) throw VaultFailure.SourceChanged()
                            output.write(buffer, 0, count)
                            position += count
                        }
                    } finally {
                        buffer.fill(0)
                    }
                }
            }
            revoke(streamGrant.token)
            val staged = requireNotNull(fallback)
            var token: String
            do token = randomToken() while (grants.containsKey(token) || fallbacks.containsKey(token))
            fallbackToken = token
            val public = VaultExternalGrant(
                token = token,
                contentUri = staged.target.uri.toString(),
                displayName = staged.target.displayName,
                mimeType = staged.target.mimeType,
                sizeBytes = staged.target.sizeBytes,
                expiresAtMillis = streamGrant.expiresAtMillis
            )
            val record = FallbackRecord(public, staged.id)
            fallbacks[token] = record
            ExternalFileAccessProvider.registerAccessObserver(
                context,
                staged.target.uri,
                ExternalFileAccessProvider.Companion.AccessObserver(
                    onOpen = { uid -> fallbackOpened(record, uid) },
                    onClose = { fallbackClosed(record) }
                )
            )
            updateNotification()
            public
        } catch (error: Throwable) {
            revoke(streamGrant.token)
            fallbackToken?.let(::revoke)
                ?: fallback?.let { ExternalFileAccessHelper.deletePrivatePlaintextFallback(context, it.id) }
            throw error
        }
    }

    override fun revoke(token: String): Boolean = grants.remove(token)?.let {
        synchronized(it) { it.autoRevoke?.cancel() }
        it.session.destroy()
        updateNotification()
        true
    } ?: fallbacks.remove(token)?.let { record ->
        synchronized(record) { record.autoRevoke?.cancel() }
        val uri = Uri.parse(record.public.contentUri)
        runCatching { ExternalFileAccessProvider.unregisterAccessObserver(context, uri) }
        ExternalFileAccessHelper.deletePrivatePlaintextFallback(context, record.fallbackId)
        updateNotification()
        true
    } ?: false

    override fun revokeAll() {
        (grants.keys + fallbacks.keys).toList().forEach(::revoke)
    }

    override fun describe(token: String): Result<VaultExternalGrant> {
        cleanupExpired()
        return (grants[token]?.public ?: fallbacks[token]?.public)?.let(Result.Companion::success)
            ?: Result.failure(VaultFailure.ExternalGrantExpired())
    }

    override fun openGrantedContent(token: String, consumerUid: Int): Result<VaultGrantedContent> {
        require(consumerUid >= 0)
        cleanupExpired()
        val record = grants[token] ?: return Result.failure(VaultFailure.ExternalGrantExpired())
        val reservation = AtomicBoolean(false)
        synchronized(record) {
            if (grants[token] !== record || record.public.expiresAtMillis <= System.currentTimeMillis()) {
                return Result.failure(VaultFailure.ExternalGrantExpired())
            }
            val claimedUid = record.consumerUid.get()
            if (claimedUid != UNCLAIMED_UID && claimedUid != consumerUid) {
                return Result.failure(VaultFailure.ExternalGrantConsumerMismatch())
            }
            record.consumerUid.compareAndSet(UNCLAIMED_UID, consumerUid)
            record.autoRevoke?.cancel()
            record.autoRevoke = null
            record.openReaders.incrementAndGet()
        }
        return runCatching {
            val resolved = record.session.resolveDirectory(record.ref.parentId)
            val snapshot = record.session.readDirectory(resolved)
            try {
                val entry = snapshot.entries.firstOrNull { it.nodeId == record.ref.nodeId }
                    ?: throw VaultFailure.NodeNotFound(record.ref.nodeId)
                if (entry.kind != VaultNodeKind.FILE) throw VaultFailure.InvalidPath("Granted file is unavailable")
                val objectId = requireNotNull(entry.objectId)
                val delegate = fileCodec.openObject(
                    record.session.directory,
                    objectId.shardedPath(),
                    record.session.id,
                    objectId,
                    entry.revision,
                    entry.protectedKey
                )
                val reader = SessionTrackedReader(delegate, record.session) { readerClosed(record, reservation) }
                if (!record.session.registerReader(reader)) {
                    reader.close()
                    throw VaultFailure.ExternalGrantExpired()
                }
                VaultGrantedContent(
                    record.public.displayName,
                    record.public.mimeType,
                    record.public.sizeBytes,
                    reader
                )
            } finally {
                snapshot.clearProtectedKeys()
                resolved.key.fill(0)
            }
        }.onFailure {
            readerClosed(record, reservation)
        }
    }

    private fun cleanupExpired() {
        val now = System.currentTimeMillis()
        grants.values.filter { it.public.expiresAtMillis <= now }.forEach { revoke(it.public.token) }
        fallbacks.values.filter { it.public.expiresAtMillis <= now }.forEach { revoke(it.public.token) }
    }

    private fun randomToken(): String {
        val bytes = ByteArray(32).also(random::nextBytes)
        return bytes.joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }

    private class GrantRecord(
        val public: VaultExternalGrant,
        val ref: VaultNodeRef,
        val session: VaultSessionRecord
    ) {
        val openReaders = AtomicInteger(0)
        val consumerUid = AtomicInteger(UNCLAIMED_UID)
        var autoRevoke: Job? = null
    }

    private class FallbackRecord(
        val public: VaultExternalGrant,
        val fallbackId: String
    ) {
        val openReaders = AtomicInteger(0)
        val consumerUid = AtomicInteger(UNCLAIMED_UID)
        var autoRevoke: Job? = null
    }

    private class SessionTrackedReader(
        private val delegate: VaultSeekableReader,
        private val session: VaultSessionRecord,
        private val onClose: () -> Unit
    ) : VaultSeekableReader {
        private val closed = AtomicBoolean(false)
        override val sizeBytes: Long get() = delegate.sizeBytes
        override fun readAt(position: Long, target: ByteArray, offset: Int, length: Int): Int =
            delegate.readAt(position, target, offset, length)
        override fun close() {
            if (closed.compareAndSet(false, true)) try {
                delegate.close()
            } finally {
                session.unregisterReader(this)
                onClose()
            }
        }
    }

    private fun readerClosed(record: GrantRecord, released: AtomicBoolean) {
        if (!released.compareAndSet(false, true)) return
        synchronized(record) {
            val remaining = record.openReaders.updateAndGet { maxOf(0, it - 1) }
            if (remaining != 0 || grants[record.public.token] !== record) return
            record.autoRevoke?.cancel()
            record.autoRevoke = applicationScope.launch {
                delay(REVOKE_AFTER_CLOSE_MILLIS)
                synchronized(record) {
                    if (record.openReaders.get() != 0) return@launch
                }
                revoke(record.public.token)
            }
        }
    }

    private fun fallbackOpened(record: FallbackRecord, consumerUid: Int): Boolean = synchronized(record) {
        if (fallbacks[record.public.token] !== record ||
            record.public.expiresAtMillis <= System.currentTimeMillis()
        ) {
            applicationScope.launch { revoke(record.public.token) }
            return@synchronized false
        }
        val claimed = record.consumerUid.get()
        if (claimed != UNCLAIMED_UID && claimed != consumerUid) return@synchronized false
        record.consumerUid.compareAndSet(UNCLAIMED_UID, consumerUid)
        record.autoRevoke?.cancel()
        record.autoRevoke = null
        record.openReaders.incrementAndGet()
        true
    }

    private fun fallbackClosed(record: FallbackRecord) {
        synchronized(record) {
            val remaining = record.openReaders.updateAndGet { maxOf(0, it - 1) }
            if (remaining != 0 || fallbacks[record.public.token] !== record) return
            record.autoRevoke?.cancel()
            record.autoRevoke = applicationScope.launch {
                delay(REVOKE_AFTER_CLOSE_MILLIS)
                synchronized(record) {
                    if (record.openReaders.get() != 0) return@launch
                }
                revoke(record.public.token)
            }
        }
    }

    private fun updateNotification() {
        val manager = context.getSystemService(NotificationManager::class.java)
        val activeCount = grants.size + fallbacks.size
        if (activeCount == 0) {
            manager.cancel(NOTIFICATION_ID)
            return
        }
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, context.getString(R.string.onlyfiles_external_channel), NotificationManager.IMPORTANCE_LOW)
        )
        val revokeIntent = Intent(context, VaultExternalGrantReceiver::class.java).setAction(VaultExternalGrantReceiver.ACTION_REVOKE_ALL)
        val revoke = PendingIntent.getBroadcast(
            context,
            0,
            revokeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_onlyfiles_notification)
            .setContentTitle(context.getString(R.string.onlyfiles_external_active))
            .setContentText(context.resources.getQuantityString(R.plurals.onlyfiles_external_count, activeCount, activeCount))
            .setOngoing(true)
            .setSilent(true)
            .addAction(0, context.getString(R.string.onlyfiles_external_revoke), revoke)
            .build()
        manager.notify(NOTIFICATION_ID, notification)
    }

    companion object {
        const val MIN_LIFETIME_MILLIS = 1_000L
        const val MAX_LIFETIME_MILLIS = 12 * 60 * 60 * 1000L
        const val REVOKE_AFTER_CLOSE_MILLIS = 30_000L
        const val CHANNEL_ID = "onlyfiles_external_access"
        const val NOTIFICATION_ID = 0x0F11E5
        private const val UNCLAIMED_UID = -1
        private const val PLAINTEXT_COPY_BUFFER_SIZE = 256 * 1024
        fun authority(context: Context): String = "${context.packageName}.onlyfiles.external"
    }
}
