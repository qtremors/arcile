package dev.qtremors.arcile.core.storage.domain

data class ImageCatalogItem(
    val file: FileModel,
    val width: Int?,
    val height: Int?
) {
    val aspectRatio: Float?
        get() = if (width != null && height != null && width > 0 && height > 0) {
            width.toFloat() / height.toFloat()
        } else {
            null
        }
}

data class ImageCatalogSnapshot(
    val items: List<ImageCatalogItem>,
    val isStale: Boolean
)

interface ImageCatalogRepository {
    suspend fun loadImages(volumeId: String?, forceRefresh: Boolean = false): Result<ImageCatalogSnapshot>
    fun invalidate(paths: Collection<String> = emptyList())
}
