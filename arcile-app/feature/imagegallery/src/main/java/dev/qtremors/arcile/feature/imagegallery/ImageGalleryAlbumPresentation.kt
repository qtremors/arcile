package dev.qtremors.arcile.feature.imagegallery

import dev.qtremors.arcile.core.storage.domain.FileModel
import java.io.File

internal const val FAVORITES_ALBUM_PATH = "__favorites__"

internal fun buildVisibleAlbumTiles(
    sortedAlbums: List<ImageGalleryAlbum>,
    files: List<FileModel>,
    favoriteFiles: Set<String>,
    favoritesLabel: String
): List<ImageGalleryAlbum> {
    val currentFavoriteCount = files.count { it.absolutePath in favoriteFiles }
    if (currentFavoriteCount == 0) return sortedAlbums

    val favoritesAlbum = ImageGalleryAlbum(
        path = FAVORITES_ALBUM_PATH,
        label = favoritesLabel,
        count = currentFavoriteCount,
        lastModified = 0L
    )
    return listOf(favoritesAlbum) + sortedAlbums
}

internal fun resolveAlbumCoverFile(
    albumPath: String?,
    files: List<FileModel>,
    favoriteFiles: Set<String>,
    albumCovers: Map<String, String>
): FileModel? {
    if (albumPath == FAVORITES_ALBUM_PATH) {
        val filesByPath = files.associateBy { it.absolutePath }
        return favoriteFiles.asSequence().mapNotNull(filesByPath::get).lastOrNull()
    }

    val fallback = files.firstOrNull { File(it.absolutePath).parent == albumPath }
    val customCoverPath = albumCovers[albumPath]
    return if (!customCoverPath.isNullOrEmpty()) {
        files.firstOrNull { it.absolutePath == customCoverPath } ?: fallback
    } else {
        fallback
    }
}
