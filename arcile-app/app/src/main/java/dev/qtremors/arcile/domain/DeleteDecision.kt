package dev.qtremors.arcile.domain

enum class DeleteDestination {
    Trash,
    Permanent,
    AndroidSystemConfirmation,
    MixedBlocked
}

data class DeleteDecision(
    val destination: DeleteDestination,
    val selectedCount: Int,
    val totalBytes: Long,
    val fileCount: Int,
    val folderCount: Int,
    val irreversible: Boolean
)

