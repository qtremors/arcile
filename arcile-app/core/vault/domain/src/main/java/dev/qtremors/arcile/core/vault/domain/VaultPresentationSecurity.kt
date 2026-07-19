package dev.qtremors.arcile.core.vault.domain

import kotlinx.coroutines.flow.StateFlow

data class VaultSecuritySettings(
    val screenshotProtectionEnabled: Boolean = true
)

interface VaultSecurityPreferences {
    val settings: StateFlow<VaultSecuritySettings>
    suspend fun setScreenshotProtectionEnabled(enabled: Boolean)
}

data class VaultThumbnailCacheStats(val encryptedFileCount: Int, val encryptedBytes: Long)

interface VaultThumbnailCache {
    suspend fun loadOrCreate(
        ref: VaultNodeRef,
        revision: Long,
        requestedSizePx: Int
    ): Result<ByteArray>

    suspend fun clear(): Result<VaultThumbnailCacheStats>
    suspend fun stats(): Result<VaultThumbnailCacheStats>
}
