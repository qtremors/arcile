package dev.qtremors.arcile.domain

data class TrashMetadata(
    val id: String,
    val originalPath: String,
    val deletionTime: Long,
    val fileModel: FileModel
)
