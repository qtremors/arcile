package dev.qtremors.arcile.domain

import java.io.File

/**
 * Core domain model representing a single file or directory entry.
 *
 * Most properties are derived from [file] at construction time. Prefer supplying explicit
 * values when constructing without a [File] reference (e.g. from MediaStore results) to
 * avoid unexpected I/O side effects.
 *
 * > **Technical debt:** The [file] property leaks a `java.io.File` reference into the domain
 * > layer and triggers synchronous disk reads during construction. See TASKS.md C5 / D.
 *
 * @property file Underlying [java.io.File] reference. May be `null` when the model is built
 *   from non-filesystem sources such as MediaStore.
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
    val file: File? = null,
    val name: String = file?.name ?: "",
    val absolutePath: String = file?.absolutePath ?: "",
    val size: Long = if (file != null && file.isFile) file.length() else 0L,
    val lastModified: Long = file?.lastModified() ?: 0L,
    val isDirectory: Boolean = file?.isDirectory ?: false,
    val extension: String = file?.extension ?: "",
    val isHidden: Boolean = file?.isHidden ?: false
)
