package dev.qtremors.arcile.core.storage.domain

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class FileOperationProgress(
    val completedItems: Int,
    val totalItems: Int,
    val currentPath: String? = null,
    @SerialName("bytesCopied")
    val bytesProcessed: Long? = null,
    val totalBytes: Long? = null
)
