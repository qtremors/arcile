package dev.qtremors.arcile.core.ui.audio

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.flow.StateFlow

/**
 * Lets the shared audio editor work on private sources without plaintext on storage. Source and output
 * paths resolve to anonymous memory files; the owner publishes the result and ends the session when its
 * source locks. Held only in this process, never persisted.
 */
interface PrivateAudioEditSession {
    val sourcePaths: List<String>
    /** False once the owner locks or the editor closes; the editor must stop using every path. */
    val active: StateFlow<Boolean>
    fun displayName(path: String): String?
    /** A fresh in-memory output with the container's file extension. */
    fun createOutput(extension: String): String
    /** Publishes a finished output to the private source and returns the name it was saved as. */
    suspend fun publish(outputPath: String): Result<String>
    fun discard(outputPath: String)
    fun close()
}

/** Process-local registry, so an Intent carries only an opaque session ID. */
object PrivateAudioEditSessions {
    const val ACTION_EDIT = "dev.qtremors.arcile.action.EDIT_PRIVATE_AUDIO"
    const val EXTRA_SESSION = "dev.qtremors.arcile.extra.PRIVATE_AUDIO_SESSION"

    private val sessions = ConcurrentHashMap<String, PrivateAudioEditSession>()

    fun register(session: PrivateAudioEditSession): String =
        UUID.randomUUID().toString().also { sessions[it] = session }

    fun get(id: String?): PrivateAudioEditSession? = id?.let(sessions::get)?.takeIf { it.active.value }

    /** Removes and closes the session; safe to call more than once. */
    fun end(id: String?) {
        id?.let(sessions::remove)?.close()
    }
}
