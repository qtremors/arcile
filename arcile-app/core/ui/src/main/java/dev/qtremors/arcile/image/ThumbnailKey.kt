package dev.qtremors.arcile.image

import dev.qtremors.arcile.core.storage.domain.FileCategories
import dev.qtremors.arcile.core.storage.domain.FileModel
import java.io.File

data class ThumbnailKey(
    val path: String,
    val extension: String,
    val sizeBytes: Long,
    val lastModifiedMillis: Long,
    val contentUri: String? = null
) {
    val file: File get() = File(path)

    val identityKey: ThumbnailIdentityKey
        get() = ThumbnailIdentityKey(
            source = contentUri ?: path,
            extension = extension,
            sizeBytes = sizeBytes,
            lastModifiedMillis = lastModifiedMillis
        )

    val cacheKey: String
        get() = identityKey.cacheKey

    fun variantKey(sizePx: Int): ThumbnailVariantKey =
        ThumbnailVariantKey(identityKey, ThumbnailTargetSize.bucket(sizePx))


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
                lastModifiedMillis = file.lastModified,
                contentUri = file.nodeRef.contentUri
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

data class ThumbnailIdentityKey(
    val source: String,
    val extension: String,
    val sizeBytes: Long,
    val lastModifiedMillis: Long
) {
    val cacheKey: String
        get() = "thumbnail:$source:$extension:$sizeBytes:$lastModifiedMillis"
}

data class ThumbnailVariantKey(
    val identity: ThumbnailIdentityKey,
    val sizeBucketPx: Int
) {
    val cacheKey: String
        get() = "${identity.cacheKey}:$sizeBucketPx"
}

enum class ThumbnailType {
    Image,
    Video,
    Audio,
    Pdf,
    Apk,
    Unsupported
}
