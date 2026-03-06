package dev.qtremors.arcile.domain

// storage breakdown by category
data class CategoryStorage(
    val name: String,
    val color: Long,
    val sizeBytes: Long,
    val extensions: Set<String>
)

// predefined file categories with their associated extensions and colors
object FileCategories {

    val Images = CategoryDef(
        name = "Images",
        color = 0xFF4CAF50, // green
        extensions = setOf("jpg", "jpeg", "png", "gif", "bmp", "webp", "svg", "heic", "heif", "ico", "tiff", "tif", "raw")
    )

    val Videos = CategoryDef(
        name = "Videos",
        color = 0xFFE91E63, // pink
        extensions = setOf("mp4", "mkv", "avi", "mov", "wmv", "flv", "webm", "m4v", "3gp", "ts")
    )

    val Audio = CategoryDef(
        name = "Audio",
        color = 0xFFFF9800, // orange
        extensions = setOf("mp3", "wav", "flac", "aac", "ogg", "wma", "m4a", "opus", "amr", "mid", "midi")
    )

    val Documents = CategoryDef(
        name = "Docs",
        color = 0xFF2196F3, // blue
        extensions = setOf("pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "rtf", "odt", "ods", "odp", "csv", "epub")
    )

    val Archives = CategoryDef(
        name = "Archives",
        color = 0xFF9C27B0, // purple
        extensions = setOf("zip", "rar", "7z", "tar", "gz", "bz2", "xz", "zst")
    )

    val APKs = CategoryDef(
        name = "APKs",
        color = 0xFF00BCD4, // cyan
        extensions = setOf("apk", "xapk", "apks", "apkm")
    )

    val all = listOf(Images, Videos, Audio, Documents, Archives, APKs)
}

data class CategoryDef(
    val name: String,
    val color: Long,
    val extensions: Set<String>
)
