package dev.qtremors.arcile.domain

/**
 * Represents a naming conflict between a source file being pasted
 * and an existing file at the destination.
 *
 * @property sourcePath Absolute path of the incoming (clipboard) file.
 * @property sourceFile Metadata for the incoming file.
 * @property existingFile Metadata for the file already present at the destination.
 */
data class FileConflict(
    val sourcePath: String,
    val sourceFile: FileModel,
    val existingFile: FileModel
)

/**
 * User-chosen resolution for a single file conflict during paste.
 */
enum class ConflictResolution {
    /** Auto-rename the incoming file (e.g. `photo - Copy.jpg`). */
    KEEP_BOTH,
    /** Overwrite the existing file with the incoming one. */
    REPLACE,
    /** Skip pasting this file entirely. */
    SKIP
}
