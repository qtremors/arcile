package dev.qtremors.arcile.feature.onlyfiles

internal data class VaultZipEntry(
    val name: String,
    val isDirectory: Boolean,
    val size: Long = 0L
)

internal data class VaultZipItem(
    val name: String,
    val path: List<String>,
    val isDirectory: Boolean,
    val size: Long
)

internal class VaultZipTree(entries: List<VaultZipEntry>) {
    private val children = mutableMapOf<List<String>, MutableMap<String, VaultZipItem>>()

    init {
        for (entry in entries) {
            val segments = entry.safeSegments() ?: continue
            var parent = emptyList<String>()
            for ((index, name) in segments.withIndex()) {
                val directory = index < segments.lastIndex || entry.isDirectory
                val path = parent + name
                val folder = children.getOrPut(parent, ::linkedMapOf)
                val previous = folder[name]
                if (previous == null || directory && !previous.isDirectory) {
                    folder[name] = VaultZipItem(name, path, directory, if (directory) 0L else entry.size.coerceAtLeast(0L))
                }
                parent = path
            }
        }
    }

    fun list(path: List<String>): List<VaultZipItem> =
        children[path]?.values.orEmpty().sortedWith(
            compareByDescending<VaultZipItem> { it.isDirectory }.thenBy { it.name.lowercase() }.thenBy { it.name }
        )
}

private fun VaultZipEntry.safeSegments(): List<String>? {
    if (name.startsWith('/') || '\\' in name || '\u0000' in name) return null
    val segments = name.trimEnd('/').split('/')
    if (segments.size > 64 || segments.first().contains(':')) return null
    return segments.takeIf { parts -> parts.none { it.isBlank() || it == "." || it == ".." } }
}
