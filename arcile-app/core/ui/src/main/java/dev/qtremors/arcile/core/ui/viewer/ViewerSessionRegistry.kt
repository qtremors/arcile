package dev.qtremors.arcile.core.ui.viewer

import dev.qtremors.arcile.core.storage.domain.FileModel
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

data class ViewerSessionData(
    val sessionToken: String,
    val sourceScope: ViewerSourceScope,
    val initialFile: FileModel,
    val queue: List<FileModel> = listOf(initialFile),
    val createdAt: Long = System.currentTimeMillis()
)

@Singleton
class ViewerSessionRegistry @Inject constructor() {
    private val sessions = ConcurrentHashMap<String, ViewerSessionData>()

    fun registerSession(
        sourceScope: ViewerSourceScope,
        initialFile: FileModel,
        queue: List<FileModel> = listOf(initialFile)
    ): String {
        val token = UUID.randomUUID().toString()
        sessions[token] = ViewerSessionData(
            sessionToken = token,
            sourceScope = sourceScope,
            initialFile = initialFile,
            queue = queue
        )
        return token
    }

    fun getSession(token: String?): ViewerSessionData? {
        if (token.isNullOrBlank()) return null
        return sessions[token]
    }

    fun updateQueue(token: String, newQueue: List<FileModel>) {
        sessions.computeIfPresent(token) { _, data ->
            data.copy(queue = newQueue)
        }
    }

    fun removeSession(token: String?) {
        if (!token.isNullOrBlank()) {
            sessions.remove(token)
        }
    }
}
