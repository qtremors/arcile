package dev.qtremors.arcile.core.presentation

import dev.qtremors.arcile.core.storage.domain.ClipboardOperation
import dev.qtremors.arcile.core.storage.domain.ClipboardRepository
import dev.qtremors.arcile.core.storage.domain.ClipboardState
import dev.qtremors.arcile.core.storage.domain.FileModel

class ClipboardController(
    private val repository: ClipboardRepository
) {
    fun store(operation: ClipboardOperation, files: List<FileModel>): Boolean {
        if (files.isEmpty()) return false
        repository.setClipboardState(ClipboardState(operation, files))
        return true
    }

    fun clear() {
        repository.clearClipboardState()
    }

    fun remove(path: String) {
        val clipboard = repository.clipboardState.value ?: return
        val remaining = clipboard.files.filterNot { it.absolutePath == path }
        if (remaining.isEmpty()) {
            clear()
        } else {
            repository.setClipboardState(clipboard.copy(files = remaining))
        }
    }
}
