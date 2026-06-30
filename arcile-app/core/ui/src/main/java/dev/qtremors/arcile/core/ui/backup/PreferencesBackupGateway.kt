package dev.qtremors.arcile.core.ui.backup

import android.net.Uri

interface PreferencesBackupGateway {
    suspend fun exportTo(uri: Uri): Result<PreferencesBackupOperationResult>
    suspend fun preview(uri: Uri): Result<PreferencesBackupPreview>
    suspend fun restoreFrom(uri: Uri): Result<PreferencesBackupOperationResult>
}

data class PreferencesBackupPreview(
    val createdAtMillis: Long,
    val items: List<PreferencesBackupItem>
)

data class PreferencesBackupOperationResult(
    val items: List<PreferencesBackupItem>,
    val failures: List<PreferencesBackupFailure> = emptyList()
) {
    val successCount: Int
        get() = items.count {
            it.status == PreferencesBackupItemStatus.Exported ||
                it.status == PreferencesBackupItemStatus.Restored ||
                it.status == PreferencesBackupItemStatus.Reset
        }

    val hasFailures: Boolean
        get() = failures.isNotEmpty()
}

data class PreferencesBackupItem(
    val id: String,
    val label: String,
    val status: PreferencesBackupItemStatus
)

enum class PreferencesBackupItemStatus {
    Exported,
    WillRestore,
    WillReset,
    Restored,
    Reset
}

data class PreferencesBackupFailure(
    val label: String,
    val message: String
)
