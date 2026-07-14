package dev.qtremors.arcile.core.vault.domain

data class VaultNode(
    val id: String,
    val path: VaultPath,
    val sizeBytes: Long,
    val modifiedAtMillis: Long,
    val isDirectory: Boolean,
    val mimeType: String? = null
) {
    val name: String get() = path.name
    val extension: String
        get() = if (isDirectory || '.' !in name) "" else name.substringAfterLast('.').lowercase()
}
