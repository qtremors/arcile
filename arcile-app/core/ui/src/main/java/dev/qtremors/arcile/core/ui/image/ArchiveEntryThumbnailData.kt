package dev.qtremors.arcile.core.ui.image

data class ArchiveEntryThumbnailData(
    val archivePath: String,
    val entryPath: String,
    val sizeBytes: Long,
    val lastModifiedMillis: Long
) {
    val cacheKey: String
        get() = "archive-thumbnail:$archivePath:$entryPath:$sizeBytes:$lastModifiedMillis"

    companion object {
        const val VIRTUAL_PREFIX = "arcile-archive-entry://"
        private const val SEPARATOR = "::"

        fun virtualPath(archivePath: String, entryPath: String): String =
            "$VIRTUAL_PREFIX$archivePath$SEPARATOR$entryPath"

        fun fromVirtualPath(path: String, sizeBytes: Long, lastModifiedMillis: Long): ArchiveEntryThumbnailData? {
            if (!path.startsWith(VIRTUAL_PREFIX)) return null
            val payload = path.removePrefix(VIRTUAL_PREFIX)
            val separatorIndex = payload.indexOf(SEPARATOR)
            if (separatorIndex <= 0) return null
            val archivePath = payload.substring(0, separatorIndex).takeIf { it.isNotBlank() } ?: return null
            val entryPath = payload.substring(separatorIndex + SEPARATOR.length).takeIf { it.isNotBlank() } ?: return null
            return ArchiveEntryThumbnailData(archivePath, entryPath, sizeBytes, lastModifiedMillis)
        }

        fun entryPathFromVirtualPath(path: String): String? {
            if (!path.startsWith(VIRTUAL_PREFIX)) return null
            val payload = path.removePrefix(VIRTUAL_PREFIX)
            val separatorIndex = payload.indexOf(SEPARATOR)
            return if (separatorIndex >= 0) {
                payload.substring(separatorIndex + SEPARATOR.length).takeIf { it.isNotBlank() }
            } else {
                payload.takeIf { it.isNotBlank() }
            }
        }
    }
}
