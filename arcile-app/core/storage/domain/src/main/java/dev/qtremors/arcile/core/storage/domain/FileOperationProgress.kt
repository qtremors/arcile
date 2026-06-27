package dev.qtremors.arcile.core.storage.domain

import kotlinx.serialization.Serializable

@Serializable
data class FileOperationProgress(
    val completedItems: Int,
    val totalItems: Int,
    val currentPath: String? = null,
    val bytesCopied: Long? = null,
    val totalBytes: Long? = null
)
