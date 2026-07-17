package dev.qtremors.arcile.core.vault.data

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
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
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DefaultVaultExternalAccessManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val repository: DefaultVaultRepository
) : VaultExternalAccessManager {
    private val grants = ConcurrentHashMap<String, GrantRecord>()
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
        val token = randomToken()
        val expires = System.currentTimeMillis() + lifetimeMillis
        val public = VaultExternalGrant(
            token,
            Uri.Builder().scheme("content").authority(authority(context)).appendPath(token).build().toString(),
            metadata.name,
            metadata.mimeType ?: "application/octet-stream",
            metadata.sizeBytes,
            expires
        )
        grants[token] = GrantRecord(public, ref, session)
        public
    }

    override fun activeGrants(): List<VaultExternalGrant> {
        cleanupExpired()
        return grants.values.map(GrantRecord::public).sortedBy(VaultExternalGrant::expiresAtMillis)
    }

    override fun revoke(token: String): Boolean = grants.remove(token)?.let {
        it.session.destroy()
        true
    } ?: false

    override fun revokeAll() {
        grants.keys.toList().forEach(::revoke)
    }

    override fun openGrantedContent(token: String): Result<VaultGrantedContent> {
        cleanupExpired()
        val record = grants[token] ?: return Result.failure(VaultFailure.ExternalGrantExpired())
        if (record.public.expiresAtMillis <= System.currentTimeMillis()) {
            revoke(token)
            return Result.failure(VaultFailure.ExternalGrantExpired())
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
                val reader = SessionTrackedReader(delegate, record.session)
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
        }
    }

    private fun cleanupExpired() {
        val now = System.currentTimeMillis()
        grants.values.filter { it.public.expiresAtMillis <= now }.forEach { revoke(it.public.token) }
    }

    private fun randomToken(): String {
        val bytes = ByteArray(32).also(random::nextBytes)
        return bytes.joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }

    private data class GrantRecord(
        val public: VaultExternalGrant,
        val ref: VaultNodeRef,
        val session: VaultSessionRecord
    )

    private class SessionTrackedReader(
        private val delegate: VaultSeekableReader,
        private val session: VaultSessionRecord
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
            }
        }
    }

    companion object {
        const val MIN_LIFETIME_MILLIS = 1_000L
        const val MAX_LIFETIME_MILLIS = 60 * 60 * 1000L
        fun authority(context: Context): String = "${context.packageName}.onlyfiles.external"
    }
}
