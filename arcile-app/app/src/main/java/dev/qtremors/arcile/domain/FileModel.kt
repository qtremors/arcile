package dev.qtremors.arcile.domain

/**
 * Core domain model representing a single file or directory entry.
 *
 * All properties must be supplied explicitly by the caller. This keeps the domain
 * layer free of `java.io.File` references and avoids implicit I/O during construction.
 *
 * @property name Display name of the file or directory.
 * @property absolutePath Absolute filesystem path (e.g. `/storage/emulated/0/DCIM`).
 * @property size File size in bytes. Always `0` for directories.
 * @property lastModified Last-modified timestamp as a Unix epoch millisecond value.
 * @property isDirectory `true` when this entry represents a directory.
 * @property extension Lowercase file extension without the leading dot (e.g. `"jpg"`).
 *   Empty string for directories or files without an extension.
 * @property isHidden `true` when the filename starts with a dot (Unix hidden-file convention).
 */
data class FileModel(
    val name: String,
    val absolutePath: String,
    val size: Long = 0L,
    val lastModified: Long = 0L,
    val isDirectory: Boolean = false,
    val extension: String = "",
    val isHidden: Boolean = false
)
