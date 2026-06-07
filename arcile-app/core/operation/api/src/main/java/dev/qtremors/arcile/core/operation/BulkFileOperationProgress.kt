package dev.qtremors.arcile.core.operation

import kotlinx.serialization.Serializable

@Serializable
data class BulkFileOperationProgress(
    val completedItems: Int,
    val totalItems: Int,
    val currentPath: String? = null,
    val bytesCopied: Long? = null,
    val totalBytes: Long? = null
)
