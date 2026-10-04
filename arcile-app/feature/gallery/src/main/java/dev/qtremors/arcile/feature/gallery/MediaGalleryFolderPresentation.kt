package dev.qtremors.arcile.feature.gallery

import dev.qtremors.arcile.core.storage.domain.FileModel
import dev.qtremors.arcile.core.storage.domain.storageParentPath

internal const val FAVORITES_FOLDER_PATH = "__favorites__"

internal fun isPasteDestinationFolderPath(folderPath: String?): Boolean =
    !folderPath.isNullOrBlank() && folderPath != FAVORITES_FOLDER_PATH

internal fun buildVisibleFolderTiles(
    sortedFolders: List<MediaGalleryFolder>,
    files: List<FileModel>,
    favoriteFiles: Set<String>,
    favoritesLabel: String
): List<MediaGalleryFolder> {
    val currentFavoriteCount = files.count { it.reference in favoriteFiles }
    if (currentFavoriteCount == 0) return sortedFolders

    val favoritesFolder = MediaGalleryFolder(
        path = FAVORITES_FOLDER_PATH,
        label = favoritesLabel,
        count = currentFavoriteCount,
        lastModified = 0L
    )
    return listOf(favoritesFolder) + sortedFolders
}

internal fun buildFolderCoverLookup(
    files: List<FileModel>,
    favoriteFiles: Set<String>,
    folderCovers: Map<String, String>
): Map<String?, FileModel> {
    if (files.isEmpty()) return emptyMap()

    val filesByPath = files.associateBy { it.reference }
    val firstFileByFolder = LinkedHashMap<String?, FileModel>()
    files.forEach { file ->
        firstFileByFolder.putIfAbsent(storageParentPath(file.reference), file)
    }

    val lookup = LinkedHashMap<String?, FileModel>()
    firstFileByFolder.forEach { (folderPath, fallback) ->
        val custom = folderPath?.let(folderCovers::get)?.let(filesByPath::get)
        lookup[folderPath] = custom ?: fallback
    }

    favoriteFiles.asSequence().mapNotNull(filesByPath::get).lastOrNull()?.let { favoriteCover ->
        lookup[FAVORITES_FOLDER_PATH] = favoriteCover
    }

    return lookup
}

internal fun resolveFolderCoverFile(
    folderPath: String?,
    files: List<FileModel>,
    favoriteFiles: Set<String>,
    folderCovers: Map<String, String>
): FileModel? {
    return buildFolderCoverLookup(files, favoriteFiles, folderCovers)[folderPath]
}
