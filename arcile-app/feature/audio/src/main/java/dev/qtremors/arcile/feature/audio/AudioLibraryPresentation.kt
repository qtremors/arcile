package dev.qtremors.arcile.feature.audio

import dev.qtremors.arcile.core.storage.domain.AudioTrack
import dev.qtremors.arcile.core.storage.domain.FileSortOption
import java.io.File
import java.util.Locale

internal fun buildAudioLibraryState(
    current: AudioLibraryState,
    tracks: List<AudioTrack> = current.tracks
): AudioLibraryState {
    val filteredByFolder = current.folderFilter?.let { folder ->
        tracks.filter { it.file.parentPath() == folder.key }
    } ?: tracks
    val query = current.query.trim()
    val filtered = if (query.isBlank()) {
        filteredByFolder
    } else {
        filteredByFolder.filter { track ->
            track.displayTitle.contains(query, ignoreCase = true) ||
                track.artist.orEmpty().contains(query, ignoreCase = true) ||
                track.album.orEmpty().contains(query, ignoreCase = true) ||
                track.file.parentPath().contains(query, ignoreCase = true)
        }
    }
    val sorted = when (current.audioPresentation.sortOption) {
        FileSortOption.NAME_ASC -> filtered.sortedBy {
            it.displayTitle.lowercase(Locale.getDefault())
        }
        FileSortOption.NAME_DESC -> filtered.sortedByDescending {
            it.displayTitle.lowercase(Locale.getDefault())
        }
        FileSortOption.DATE_NEWEST -> filtered.sortedByDescending { it.file.lastModified }
        FileSortOption.DATE_OLDEST -> filtered.sortedBy { it.file.lastModified }
        FileSortOption.SIZE_LARGEST -> filtered.sortedByDescending { it.file.size }
        FileSortOption.SIZE_SMALLEST -> filtered.sortedBy { it.file.size }
    }
    val folders = tracks
        .groupBy { it.file.parentPath() }
        .map { (path, members) ->
            AudioFolder(
                key = path,
                title = File(path).name.ifBlank { path },
                subtitle = path,
                tracks = members.sortedBy { it.displayTitle.lowercase(Locale.getDefault()) }
            )
        }
        .filter { folder ->
            query.isBlank() ||
                folder.title.contains(query, ignoreCase = true) ||
                folder.key.contains(query, ignoreCase = true) ||
                folder.tracks.any { track ->
                    track.displayTitle.contains(query, ignoreCase = true) ||
                        track.artist.orEmpty().contains(query, ignoreCase = true) ||
                        track.album.orEmpty().contains(query, ignoreCase = true)
                }
        }
        .let { visibleFolders ->
            when (current.folderPresentation.sortOption) {
                FileSortOption.NAME_ASC -> visibleFolders.sortedBy {
                    it.title.lowercase(Locale.getDefault())
                }
                FileSortOption.NAME_DESC -> visibleFolders.sortedByDescending {
                    it.title.lowercase(Locale.getDefault())
                }
                FileSortOption.DATE_NEWEST -> visibleFolders.sortedByDescending(AudioFolder::newestModified)
                FileSortOption.DATE_OLDEST -> visibleFolders.sortedBy(AudioFolder::newestModified)
                FileSortOption.SIZE_LARGEST -> visibleFolders.sortedByDescending(AudioFolder::totalSize)
                FileSortOption.SIZE_SMALLEST -> visibleFolders.sortedBy(AudioFolder::totalSize)
            }
        }
    return current.copy(
        tracks = tracks,
        visibleTracks = sorted,
        folders = folders
    )
}

internal fun formatAudioDuration(durationMs: Long): String {
    val totalSeconds = (durationMs.coerceAtLeast(0L) / 1_000L)
    val hours = totalSeconds / 3_600L
    val minutes = (totalSeconds % 3_600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0L) {
        "%d:%02d:%02d".format(Locale.ROOT, hours, minutes, seconds)
    } else {
        "%d:%02d".format(Locale.ROOT, minutes, seconds)
    }
}

private fun dev.qtremors.arcile.core.storage.domain.FileModel.parentPath(): String =
    absolutePath.replace('\\', '/').substringBeforeLast('/', "")
