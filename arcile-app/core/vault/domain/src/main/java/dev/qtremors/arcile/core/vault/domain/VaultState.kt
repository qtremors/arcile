package dev.qtremors.arcile.core.vault.domain

enum class VaultLocationKind {
    APP_PRIVATE,
    USER_FOLDER
}

data class VaultSummary(
    val id: VaultId,
    val name: String,
    val locationKind: VaultLocationKind,
    val createdAtMillis: Long,
    val isUnlocked: Boolean
)

data class VaultImportProgress(
    val completedItems: Int,
    val totalItems: Int,
    val bytesCopied: Long,
    val totalBytes: Long?,
    val currentName: String?
)

data class VaultImportState(
    val vaultId: VaultId,
    val progress: VaultImportProgress? = null,
    val isCancelling: Boolean = false
)

sealed interface VaultImportEvent {
    data class Started(val vaultId: VaultId) : VaultImportEvent
    data class Progress(val vaultId: VaultId, val value: VaultImportProgress) : VaultImportEvent
    data class Completed(val vaultId: VaultId) : VaultImportEvent
    data class Failed(val vaultId: VaultId, val message: String) : VaultImportEvent
    data class Cancelled(val vaultId: VaultId) : VaultImportEvent
}
