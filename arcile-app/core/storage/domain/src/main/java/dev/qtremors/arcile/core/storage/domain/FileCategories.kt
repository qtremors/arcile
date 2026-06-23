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
        name = "Images",
        mimePrefix = "image/",
        extensions = setOf("jpg", "jpeg", "png", "gif", "bmp", "webp", "svg", "heic", "heif", "ico", "raw")
    )

    val Videos = CategoryDef(
        name = "Videos",
        mimePrefix = "video/",
        extensions = setOf(
            "mp4", "mkv", "avi", "mov", "wmv", "flv", "webm", "m4v", "3gp", "3g2",
            "ts", "mts", "m2ts", "mpeg", "mpg", "vob", "ogv"
        )
    )

    val Audio = CategoryDef(
        name = "Audio",
        mimePrefix = "audio/",
        extensions = setOf("mp3", "wav", "flac", "aac", "ogg", "wma", "m4a", "opus", "amr", "mid", "midi")
    )

    val Documents = CategoryDef(
        name = "Docs",
        mimePrefix = null,
        extensions = setOf("pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "rtf", "odt", "ods", "odp", "csv", "epub")
    )

    val Archives = CategoryDef(
        name = "Archives",
        mimePrefix = "application/zip",
        extensions = setOf("zip", "rar", "7z", "tar", "gz", "bz2", "xz", "zst")
    )

    val APKs = CategoryDef(
        name = "APKs",
        mimePrefix = "application/vnd.android.package-archive",
        extensions = setOf("apk", "xapk", "apks", "apkm")
    )

    val Models = CategoryDef(
        name = "Models",
        mimePrefix = "model/",
        extensions = setOf("glb")
    )

    val all = listOf(Images, Videos, Audio, Documents, Archives, APKs, Models)

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
                "apk" -> "application/vnd.android.package-archive"
                "glb" -> "model/gltf-binary"
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
    val name: String,
    val extensions: Set<String>,
    val mimePrefix: String? = null
)
