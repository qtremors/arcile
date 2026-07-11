package dev.qtremors.arcile.core.storage.domain

/**
 * Represents a naming conflict between a source file being pasted and an existing file.
 */
data class FileConflict(
    val sourcePath: String,
    val sourceFile: FileModel,
    val existingFile: FileModel
)
