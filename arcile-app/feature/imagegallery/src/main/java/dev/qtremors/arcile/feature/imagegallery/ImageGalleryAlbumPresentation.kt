package dev.qtremors.arcile.feature.imagegallery

import dev.qtremors.arcile.core.storage.domain.FileModel

internal const val FAVORITES_ALBUM_PATH = "__favorites__"

internal fun isPasteDestinationAlbumPath(albumPath: String?): Boolean =
    !albumPath.isNullOrBlank() && albumPath != FAVORITES_ALBUM_PATH

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

internal fun buildAlbumCoverLookup(
    files: List<FileModel>,
    favoriteFiles: Set<String>,
    albumCovers: Map<String, String>
): Map<String?, FileModel> {
    if (files.isEmpty()) return emptyMap()

    val filesByPath = files.associateBy { it.absolutePath }
    val firstFileByAlbum = LinkedHashMap<String?, FileModel>()
    files.forEach { file ->
        firstFileByAlbum.putIfAbsent(galleryParentPath(file.absolutePath), file)
    }

    val lookup = LinkedHashMap<String?, FileModel>()
    firstFileByAlbum.forEach { (albumPath, fallback) ->
        val custom = albumPath?.let(albumCovers::get)?.let(filesByPath::get)
        lookup[albumPath] = custom ?: fallback
    }

    favoriteFiles.asSequence().mapNotNull(filesByPath::get).lastOrNull()?.let { favoriteCover ->
        lookup[FAVORITES_ALBUM_PATH] = favoriteCover
    }

    return lookup
}

internal fun resolveAlbumCoverFile(
    albumPath: String?,
    files: List<FileModel>,
    favoriteFiles: Set<String>,
    albumCovers: Map<String, String>
): FileModel? {
    return buildAlbumCoverLookup(files, favoriteFiles, albumCovers)[albumPath]
}
