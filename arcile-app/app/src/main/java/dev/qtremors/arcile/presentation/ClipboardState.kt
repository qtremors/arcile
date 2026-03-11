package dev.qtremors.arcile.presentation

enum class ClipboardOperation { COPY, CUT }

data class ClipboardState(
    val operation: ClipboardOperation,
    val sourcePaths: List<String>
)
