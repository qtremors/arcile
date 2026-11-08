package dev.qtremors.arcile.feature.onlyfiles

import android.os.SystemClock
import dev.qtremors.arcile.navigation.AppRoutes
import java.util.UUID

/** Keeps source paths out of navigation arguments and saved instance state. */
fun createOnlyFilesImportRoute(paths: List<String>): AppRoutes.OnlyFiles? =
    OnlyFilesBrowserImports.register(paths)?.let { AppRoutes.OnlyFiles(importRequestId = it) }

internal object OnlyFilesBrowserImports {
    private const val MAX_REQUESTS = 8
    private const val REQUEST_TTL_MILLIS = 60_000L
    private val pending = LinkedHashMap<String, Request>()

    @Synchronized
    fun register(paths: List<String>): String? {
        if (paths.isEmpty() || paths.any { !it.startsWith('/') || '\u0000' in it }) return null
        val now = SystemClock.elapsedRealtime()
        pending.entries.removeAll { it.value.expiresAt <= now }
        if (pending.size >= MAX_REQUESTS) pending.remove(pending.keys.first())
        val id = UUID.randomUUID().toString()
        pending[id] = Request(paths.distinct(), now + REQUEST_TTL_MILLIS)
        return id
    }

    @Synchronized
    fun take(id: String): List<String>? {
        val now = SystemClock.elapsedRealtime()
        val request = pending.remove(id)
        pending.entries.removeAll { it.value.expiresAt <= now }
        return request?.paths?.takeIf { request.expiresAt > now }
    }

    private data class Request(val paths: List<String>, val expiresAt: Long)
}
