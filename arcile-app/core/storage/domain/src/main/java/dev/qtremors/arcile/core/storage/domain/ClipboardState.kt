package dev.qtremors.arcile.core.storage.domain

import java.util.UUID

enum class ClipboardOperation { COPY, CUT }

data class ClipboardState(
    val operation: ClipboardOperation,
    val files: List<FileModel>,
    val sessionId: String = UUID.randomUUID().toString()
) {
    val totalSize: Long get() = files.sumOf { it.size }
}
