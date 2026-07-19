package dev.qtremors.arcile.core.ui.security

object SensitiveMemory {
    @Volatile
    var clearDelegate: (() -> Unit)? = null

    fun clear() = clearDelegate?.invoke() ?: Unit
}
