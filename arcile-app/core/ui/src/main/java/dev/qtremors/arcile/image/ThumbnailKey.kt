package dev.qtremors.arcile.image

import dev.qtremors.arcile.core.storage.domain.FileCategories
import dev.qtremors.arcile.core.storage.domain.FileModel
import java.io.File

data class ThumbnailKey(
    val path: String,
    val extension: String,
    val sizeBytes: Long,
    val lastModifiedMillis: Long
) {
    val file: File get() = File(path)

    val cacheKey: String
        get() = "thumbnail:$path:$extension:$sizeBytes:$lastModifiedMillis"

    val type: ThumbnailType
        get() = when (extension.lowercase()) {
            in FileCategories.Images.extensions -> ThumbnailType.Image
            in FileCategories.Videos.extensions -> ThumbnailType.Video
            in FileCategories.Audio.extensions -> ThumbnailType.Audio
            in FileCategories.APKs.extensions -> ThumbnailType.Apk
            "pdf" -> ThumbnailType.Pdf
            else -> ThumbnailType.Unsupported
        }

    companion object {
        fun from(file: FileModel): ThumbnailKey =
            ThumbnailKey(
                path = file.absolutePath,
                extension = file.extension.lowercase(),
                sizeBytes = file.size,
                lastModifiedMillis = file.lastModified
            )

        fun from(file: File): ThumbnailKey =
            ThumbnailKey(
                path = file.absolutePath,
                extension = file.extension.lowercase(),
                sizeBytes = file.length(),
                lastModifiedMillis = file.lastModified()
            )
    }
}

enum class ThumbnailType {
    Image,
    Video,
    Audio,
    Pdf,
    Apk,
    Unsupported
}
