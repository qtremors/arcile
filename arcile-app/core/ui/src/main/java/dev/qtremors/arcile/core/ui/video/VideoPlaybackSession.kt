package dev.qtremors.arcile.core.ui.video

import androidx.media3.common.MediaItem
import androidx.media3.datasource.DataSource
import java.util.UUID

data class VideoPlaybackItem(
    val mediaItem: MediaItem,
    val title: String,
    val onShare: (() -> Unit)? = null,
    val onOpenWith: (() -> Unit)? = null
)

data class VideoPlaybackSession(
    val items: List<VideoPlaybackItem>,
    val startIndex: Int = 0,
    val dataSourceFactory: DataSource.Factory? = null,
    val securityScopeId: String? = null
) {
    init {
        require(items.isNotEmpty())
        require(startIndex in items.indices)
    }
}

/**
 * Bounded process-memory storage for playback sources. Navigation persists only an opaque token,
 * so file paths, content URIs, decrypted vault names, keys, and callbacks never enter saved state.
 */
class VideoPlaybackSessionStore(
    private val clockMillis: () -> Long = System::currentTimeMillis,
    private val tokenFactory: () -> String = { UUID.randomUUID().toString() },
    private val maxSessions: Int = 16,
    private val lifetimeMillis: Long = 12L * 60L * 60L * 1_000L
) {
    private data class Entry(val session: VideoPlaybackSession, val expiresAtMillis: Long)

    private val entries = LinkedHashMap<String, Entry>()

    init {
        require(maxSessions > 0)
        require(lifetimeMillis > 0L)
    }

    @Synchronized
    fun register(session: VideoPlaybackSession): String {
        pruneExpired()
        while (entries.size >= maxSessions) {
            entries.remove(entries.keys.first())
        }
        var token: String
        do {
            token = tokenFactory()
            require(token.isNotBlank())
        } while (token in entries)
        entries[token] = Entry(session, saturatedAdd(clockMillis(), lifetimeMillis))
        return token
    }

    @Synchronized
    fun resolve(token: String): VideoPlaybackSession? {
        if (token.isBlank()) return null
        pruneExpired()
        return entries[token]?.session
    }

    @Synchronized
    fun remove(token: String) {
        entries.remove(token)
    }

    @Synchronized
    fun clear() {
        entries.clear()
    }

    @Synchronized
    fun removeSecurityScope(scopeId: String) {
        entries.entries.removeAll { it.value.session.securityScopeId == scopeId }
    }

    private fun pruneExpired() {
        val now = clockMillis()
        entries.entries.removeAll { it.value.expiresAtMillis <= now }
    }

    private fun saturatedAdd(left: Long, right: Long): Long =
        if (left > Long.MAX_VALUE - right) Long.MAX_VALUE else left + right
}

object GlobalVideoPlaybackSessions {
    private val store = VideoPlaybackSessionStore()

    fun register(session: VideoPlaybackSession): String = store.register(session)
    fun resolve(token: String): VideoPlaybackSession? = store.resolve(token)
    fun remove(token: String) = store.remove(token)
    fun clear() = store.clear()
    fun removeSecurityScope(scopeId: String) = store.removeSecurityScope(scopeId)
}
