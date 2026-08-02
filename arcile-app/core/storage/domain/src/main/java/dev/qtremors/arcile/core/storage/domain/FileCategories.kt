package dev.qtremors.arcile.core.storage.domain

// storage breakdown by category
data class CategoryStorage(
    val name: String,
    val sizeBytes: Long,
    val extensions: Set<String>
)

// predefined file categories with their associated extensions and colors
object FileCategories {

    val Images = CategoryDef(
        id = CategoryId.of("Images"),
        displayName = "Images",
        storageName = "Images",
        mimePrefix = "image/",
        extensions = setOf("jpg", "jpeg", "png", "gif", "bmp", "webp", "svg", "heic", "heif", "ico", "raw")
    )

    val Videos = CategoryDef(
        id = CategoryId.of("Videos"),
        displayName = "Videos",
        storageName = "Videos",
        mimePrefix = "video/",
        extensions = setOf(
            "mp4", "mkv", "avi", "mov", "wmv", "flv", "webm", "m4v", "3gp", "3g2",
            "ts", "mts", "m2ts", "mpeg", "mpg", "vob", "ogv"
        )
    )

    val Audio = CategoryDef(
        id = CategoryId.of("Audio"),
        displayName = "Audio",
        storageName = "Audio",
        mimePrefix = "audio/",
        extensions = setOf("mp3", "wav", "flac", "aac", "ogg", "wma", "m4a", "opus", "amr", "mid", "midi")
    )

    val Documents = CategoryDef(
        id = CategoryId.of("Docs"),
        displayName = "Docs",
        storageName = "Docs",
        mimePrefix = null,
        extensions = setOf("pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "md", "markdown", "rtf", "odt", "ods", "odp", "csv", "epub")
    )

    val Archives = CategoryDef(
        id = CategoryId.of("Archives"),
        displayName = "Archives",
        storageName = "Archives",
        mimePrefix = "application/zip",
        extensions = setOf("zip", "rar", "7z", "tar", "gz", "bz2", "xz", "zst")
    )

    val APKs = CategoryDef(
        id = CategoryId.of("APKs"),
        displayName = "APKs",
        storageName = "APKs",
        mimePrefix = "application/vnd.android.package-archive",
        extensions = setOf("apk", "xapk", "apks", "apkm")
    )

    val all = listOf(Images, Videos, Audio, Documents, Archives, APKs)

    fun find(value: String): CategoryDef? = all.firstOrNull { it.matches(value) }

    fun getCategoryForFile(extension: String, mimeType: String?): CategoryDef? {
        var normalizedMime = mimeType?.lowercase()
        val normalizedExt = extension.lowercase()

        if (normalizedMime == null && normalizedExt.isNotEmpty()) {
            normalizedMime = when (normalizedExt) {
                "jpg", "jpeg", "png", "gif", "bmp", "webp", "svg", "heic", "heif", "ico" -> "image/$normalizedExt"
                "mp4", "mkv", "avi", "mov", "wmv", "flv", "webm", "m4v", "3gp", "3g2",
                "ts", "mts", "m2ts", "mpeg", "mpg", "vob", "ogv" -> "video/$normalizedExt"
                "mp3", "wav", "flac", "aac", "ogg", "wma", "m4a", "opus", "amr" -> "audio/$normalizedExt"
                "pdf" -> "application/pdf"
                "zip" -> "application/zip"
                "apk", "apks", "xapk", "apkm" -> "application/vnd.android.package-archive"
                else -> java.net.URLConnection.guessContentTypeFromName("file.$normalizedExt")?.lowercase()
            }
        }


        // 1. Try matching by MIME type prefix or full MIME type
        all.forEach { category ->
            val prefix = category.mimePrefix
            if (normalizedMime != null && prefix != null) {
                if (prefix.endsWith("/") && normalizedMime.startsWith(prefix)) return category
                if (normalizedMime == prefix) return category
            }
        }

        // 2. Try matching by extension (fallback)
        all.forEach { category ->
            if (category.extensions.contains(normalizedExt)) return category
        }

        return null
    }
}

data class CategoryDef(
    val id: CategoryId,
    val displayName: String,
    val storageName: String,
    val extensions: Set<String>,
    val mimePrefix: String? = null
) {
    fun matches(value: String): Boolean =
        id.value.equals(value, ignoreCase = true) ||
            displayName.equals(value, ignoreCase = true) ||
            storageName.equals(value, ignoreCase = true)
}
