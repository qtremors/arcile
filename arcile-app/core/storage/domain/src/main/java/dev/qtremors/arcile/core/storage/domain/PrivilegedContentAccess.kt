package dev.qtremors.arcile.core.storage.domain

import java.io.Closeable

/** Access supported by the protected-content bridge. External grants are intentionally read-only. */
enum class PrivilegedContentAccessMode {
    READ
}

/**
 * Describes why a grant was issued so its lifetime can match the consumer.
 *
 * Internal preview grants are reusable by Arcile while a browser listing is alive. External handoff
 * grants are single-purpose capabilities and are revoked shortly after their final reader closes.
 */
enum class PrivilegedContentGrantPurpose {
    INTERNAL_PREVIEW,
    EXTERNAL_HANDOFF
}

/** Public, path-free description of a protected-content capability. */
data class PrivilegedContentGrant(
    val token: String,
    val contentUri: String,
    val backendId: String,
    val canonicalStorageIdentity: String,
    val accessMode: PrivilegedContentAccessMode,
    val displayName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val expiresAtMillis: Long,
    val expectedConsumerUid: Int?,
    val purpose: PrivilegedContentGrantPurpose
)

/** A random-access reader used by Android's proxy file-descriptor API. */
interface PrivilegedContentReader : Closeable {
    val sizeBytes: Long

    /** Reads at [position] without changing the observable position of other readers. */
    fun readAt(
        position: Long,
        target: ByteArray,
        offset: Int = 0,
        length: Int = target.size - offset
    ): Int
}

data class PrivilegedGrantedContent(
    val displayName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val reader: PrivilegedContentReader
)

sealed class PrivilegedContentFailure(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class InvalidGrant : PrivilegedContentFailure("Protected-content grant is invalid or expired")
    class ConsumerMismatch : PrivilegedContentFailure("Protected-content grant belongs to another consumer")
    class SourceChanged : PrivilegedContentFailure("Protected file changed after access was granted")
    class UnsupportedNode : PrivilegedContentFailure("Only readable protected files can be granted")
    class BackendUnavailable(cause: Throwable? = null) :
        PrivilegedContentFailure("Protected storage backend is unavailable", cause)
}

interface PrivilegedContentAccessManager {
    /**
     * Issues an opaque, time-limited content capability. The URI never contains a storage path.
     * [expectedConsumerUid] should be supplied only when the eventual reader is known safely.
     */
    suspend fun issue(
        node: StorageNodeRef,
        displayName: String,
        mimeType: String? = null,
        sizeBytes: Long? = null,
        modifiedAtMillis: Long? = null,
        accessMode: PrivilegedContentAccessMode = PrivilegedContentAccessMode.READ,
        purpose: PrivilegedContentGrantPurpose = PrivilegedContentGrantPurpose.INTERNAL_PREVIEW,
        lifetimeMillis: Long = DEFAULT_INTERNAL_GRANT_LIFETIME_MILLIS,
        expectedConsumerUid: Int? = null
    ): Result<PrivilegedContentGrant>

    fun describe(token: String): Result<PrivilegedContentGrant>

    suspend fun openGrantedContent(
        token: String,
        consumerUid: Int
    ): Result<PrivilegedGrantedContent>

    fun activeGrants(): List<PrivilegedContentGrant>

    fun revoke(token: String): Boolean

    fun revokeAll()

    companion object {
        const val DEFAULT_INTERNAL_GRANT_LIFETIME_MILLIS = 2 * 60 * 60 * 1000L
        const val DEFAULT_EXTERNAL_GRANT_LIFETIME_MILLIS = 15 * 60 * 1000L
    }
}
