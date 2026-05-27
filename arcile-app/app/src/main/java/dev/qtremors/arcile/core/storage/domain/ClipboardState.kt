package dev.qtremors.arcile.core.storage.domain

enum class ClipboardOperation { COPY, CUT }

data class ClipboardState(
    val operation: ClipboardOperation,
    val files: List<FileModel>
) {
    val totalSize: Long get() = files.sumOf { it.size }
}
