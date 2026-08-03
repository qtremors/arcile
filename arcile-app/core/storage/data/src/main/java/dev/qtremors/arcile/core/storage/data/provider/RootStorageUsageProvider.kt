package dev.qtremors.arcile.core.storage.data.provider

import android.os.StatFs
import dev.qtremors.arcile.core.storage.data.runCatchingPreservingCancellation
import dev.qtremors.arcile.core.storage.domain.RootStorageUsage

fun interface RootStorageUsageProvider {
    fun getRootStorageUsage(): RootStorageUsage?
}

class DefaultRootStorageUsageProvider : RootStorageUsageProvider {
    override fun getRootStorageUsage(): RootStorageUsage? =
        runCatchingPreservingCancellation {
            val stats = StatFs(ROOT_PATH)
            val totalBytes = stats.totalBytes
            if (totalBytes <= 0L) return@runCatchingPreservingCancellation null
            RootStorageUsage(
                totalBytes = totalBytes,
                freeBytes = stats.availableBytes.coerceIn(0L, totalBytes)
            )
        }.getOrNull()
}

private const val ROOT_PATH = "/"
