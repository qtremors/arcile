package dev.qtremors.arcile.core.storage.domain

/** Extension choices take precedence over the older category choices. */
object FileOpenPreferences {
    private const val EXTENSION_PREFIX = "ext:"

    fun extensionKey(extension: String): String =
        EXTENSION_PREFIX + extension.trim().removePrefix(".").lowercase()

    fun extensionFromKey(key: String): String? =
        key.takeIf { it.startsWith(EXTENSION_PREFIX) }
            ?.removePrefix(EXTENSION_PREFIX)
            ?.takeIf { it.isNotBlank() }

    fun effectiveBehavior(
        behaviors: Map<String, FileOpenBehavior>,
        extension: String,
        category: CategoryDef? = null
    ): FileOpenBehavior = behaviors[extensionKey(extension)]
        ?: category?.let { behaviors[it.id.value] }
        ?: FileOpenBehavior.ARCILE

    fun extensionForPath(path: String, extension: String): String =
        ArchiveFormat.fromPath(path)?.extension ?: extension.lowercase()
}

/** Built-in file types that Arcile resolves to its own viewer, editor, archive browser, or installer. */
object ArcileOpenExtensions {
    val text = setOf(
        "txt", "md", "markdown", "log", "json", "xml", "yaml", "yml",
        "csv", "ini", "conf", "properties", "kt", "java", "py", "js", "html", "css"
    )

    val documents = text + "pdf"
    val archives = ArchiveFormat.entries.filter(ArchiveFormat::canBrowse).mapTo(linkedSetOf()) { it.extension }
}
