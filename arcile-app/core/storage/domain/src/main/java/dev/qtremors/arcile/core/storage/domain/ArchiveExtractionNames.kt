package dev.qtremors.arcile.core.storage.domain

/** Shared destination folder name for full archives and selections inside them. */
fun archiveExtractionFolderName(name: String): String {
    val format = ArchiveFormat.fromPath(name)
    val stem = if (format != null) name.dropLast(format.extension.length + 1) else name.substringBeforeLast('.', name)
    return stem.ifBlank { "Archive" }
}
