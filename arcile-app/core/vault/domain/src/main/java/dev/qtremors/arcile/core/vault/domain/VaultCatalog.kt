package dev.qtremors.arcile.core.vault.domain

enum class VaultLocationKind { APP_PRIVATE, PORTABLE, USER_FOLDER }

sealed interface VaultLocation {
    data class AppPrivate(val directoryToken: String) : VaultLocation
    data class Portable(val volumeId: String, val relativePath: String) : VaultLocation {
        init {
            require(volumeId.isNotBlank())
            require(relativePath.isNotBlank())
            require(relativePath.indexOf('\u0000') < 0)
        }
    }
}

enum class VaultAvailability {
    AVAILABLE,
    VOLUME_MISSING,
    FOLDER_MISSING,
    STALE_REGISTRATION,
    DAMAGED_HEADER
}

data class VaultSummary(
    val id: VaultId,
    val name: String,
    val locationKind: VaultLocationKind,
    val createdAtMillis: Long,
    val isUnlocked: Boolean,
    val isAvailable: Boolean = true,
    val availability: VaultAvailability = if (isAvailable) VaultAvailability.AVAILABLE else VaultAvailability.FOLDER_MISSING,
    val headerFingerprint: String? = null
)

data class VaultRegistration(
    val vaultId: VaultId,
    val location: VaultLocation,
    val cachedPublicLabel: String,
    val headerFingerprint: String
)

data class VaultCreationRequest(
    val label: String,
    val password: CharArray,
    val location: VaultLocation,
    val weakPasswordConfirmed: Boolean
)

data class VaultAttachmentRequest(
    val volumeId: String,
    val relativePath: String,
    val password: CharArray
)

interface VaultCatalog {
    suspend fun list(): List<VaultSummary>
    suspend fun refresh()
    suspend fun create(request: VaultCreationRequest): Result<VaultId>
    suspend fun attach(request: VaultAttachmentRequest): Result<VaultId>
    suspend fun removeRegistration(vaultId: VaultId): Result<Unit>
    suspend fun deletePermanently(vaultId: VaultId, confirmation: String): Result<Unit>
}
