package dev.qtremors.arcile.feature.audio

import dev.qtremors.arcile.core.storage.domain.AudioTrack
import dev.qtremors.arcile.core.storage.domain.FileSortOption
import dev.qtremors.arcile.core.ui.category.matchesCategorySearchFilters
import java.io.File
import java.util.Locale

internal fun buildAudioLibraryState(
    current: AudioLibraryState,
    tracks: List<AudioTrack> = current.tracks
): AudioLibraryState {
    val categoryTracks = tracks.filter {
        it.file.matchesCategorySearchFilters(current.searchFilters)
    }
    val query = current.query.trim()
    val playlistDates = current.playlists.associate { it.id to it.updatedAt }
    fun AudioCollection.sortDate(): Long = when (current.collectionKind) {
        AudioCollectionKind.ALBUMS -> this.tracks.mapNotNull(AudioTrack::year)
            .maxOrNull()?.toLong() ?: 0L
        AudioCollectionKind.PLAYLISTS -> playlistDates[key] ?: 0L
        else -> newestModified
    }
    val collections = buildCollections(current, categoryTracks)
        .filter { collection ->
            query.isBlank() ||
                collection.title.contains(query, ignoreCase = true) ||
                collection.key.contains(query, ignoreCase = true) ||
                collection.tracks.any { track ->
                    track.displayTitle.contains(query, ignoreCase = true) ||
                        track.artist.orEmpty().contains(query, ignoreCase = true) ||
                        track.album.orEmpty().contains(query, ignoreCase = true) ||
                        track.albumArtist.orEmpty().contains(query, ignoreCase = true) ||
                        track.genre.orEmpty().contains(query, ignoreCase = true)
                }
        }
        .let { visibleCollections ->
            val sortedCollections = when (current.presentationFor(current.collectionKind).sortOption) {
                FileSortOption.NAME_ASC -> visibleCollections.sortedBy {
                    it.title.lowercase(Locale.getDefault())
                }
                FileSortOption.NAME_DESC -> visibleCollections.sortedByDescending {
                    it.title.lowercase(Locale.getDefault())
                }
                FileSortOption.DATE_NEWEST -> visibleCollections.sortedByDescending { it.sortDate() }
                FileSortOption.DATE_OLDEST -> visibleCollections.sortedBy { it.sortDate() }
                FileSortOption.SIZE_LARGEST -> visibleCollections.sortedByDescending(AudioCollection::totalSize)
                FileSortOption.SIZE_SMALLEST -> visibleCollections.sortedBy(AudioCollection::totalSize)
                FileSortOption.FILE_COUNT_HIGHEST -> visibleCollections.sortedByDescending { it.tracks.size }
                FileSortOption.FILE_COUNT_LOWEST -> visibleCollections.sortedBy { it.tracks.size }
            }
            sortedCollections.sortedByDescending(AudioCollection::isPinned)
        }
        .let { visibleCollections ->
            val favoriteTracks = categoryTracks.filter {
                it.file.reference in current.favoritePaths
            }
            val favoritesMatch = query.isBlank() ||
                current.favoriteSearchAliases.any { alias ->
                    alias.contains(query, ignoreCase = true)
                } ||
                favoriteTracks.any { track ->
                    track.displayTitle.contains(query, ignoreCase = true) ||
                        track.artist.orEmpty().contains(query, ignoreCase = true)
                }
            if (current.collectionKind == AudioCollectionKind.FOLDERS &&
                favoriteTracks.isNotEmpty() && favoritesMatch
            ) {
                listOf(
                    AudioCollection(
                        key = AUDIO_FAVORITES_FOLDER_KEY,
                        title = "",
                        subtitle = null,
                        tracks = favoriteTracks.sortedBy {
                            it.displayTitle.lowercase(Locale.getDefault())
                        },
                        kind = AudioCollectionType.Favorites
                    )
                ) + visibleCollections
            } else {
                visibleCollections
            }
        }
    val currentCollection = current.collectionFilter?.let { selected ->
        collections.firstOrNull { it.key == selected.key }
    }
    val sorted = presentVisibleAudioTracks(
        current.copy(collectionFilter = currentCollection), categoryTracks
    )
    return current.copy(
        presentedCollectionKind = current.collectionKind,
        tracks = tracks,
        visibleTracks = sorted,
        collections = collections,
        collectionFilter = currentCollection
    )
}

internal fun AudioLibraryState.withPresentedVisibleTracks(): AudioLibraryState {
    val categoryTracks = tracks.filter {
        it.file.matchesCategorySearchFilters(searchFilters)
    }
    return copy(visibleTracks = presentVisibleAudioTracks(this, categoryTracks))
}

private fun presentVisibleAudioTracks(
    current: AudioLibraryState,
    categoryTracks: List<AudioTrack>
): List<AudioTrack> {
    val filteredByCollection = current.collectionFilter?.let { collection ->
        if (collection.isFavorites) {
            categoryTracks.filter { it.file.reference in current.favoritePaths }
        } else {
            val paths = collection.tracks.mapTo(hashSetOf()) { it.file.reference }
            categoryTracks.filter { it.file.reference in paths }
        }
    } ?: categoryTracks
    val query = current.query.trim()
    val filtered = if (query.isBlank()) {
        filteredByCollection
    } else {
        filteredByCollection.filter { track ->
            track.displayTitle.contains(query, ignoreCase = true) ||
                track.artist.orEmpty().contains(query, ignoreCase = true) ||
                track.album.orEmpty().contains(query, ignoreCase = true) ||
                track.albumArtist.orEmpty().contains(query, ignoreCase = true) ||
                track.genre.orEmpty().contains(query, ignoreCase = true) ||
                track.year?.toString()?.contains(query) == true ||
                track.file.parentPath().contains(query, ignoreCase = true)
        }
    }
    val sorted = if (current.collectionFilter?.kind == AudioCollectionType.Playlist) {
        val position = current.collectionFilter?.tracks.orEmpty().mapIndexed { index, track ->
            track.file.reference to index
        }
            .toMap()
        filtered.sortedBy { position[it.file.reference] ?: Int.MAX_VALUE }
    } else if (current.collectionFilter?.kind == AudioCollectionType.Album) {
        filtered.sortedWith(compareBy<AudioTrack> { it.discNumber ?: 0 }
            .thenBy { it.trackNumber ?: Int.MAX_VALUE }
            .thenBy { it.displayTitle.lowercase(Locale.getDefault()) })
    } else when (current.audioPresentation.sortOption) {
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
        FileSortOption.FILE_COUNT_HIGHEST,
        FileSortOption.FILE_COUNT_LOWEST -> filtered.sortedBy {
            it.displayTitle.lowercase(Locale.getDefault())
        }
    }
    if (current.collectionKind != AudioCollectionKind.SONGS || current.collectionFilter != null) {
        return sorted
    }
    return when (current.songFilter) {
        AudioSongFilter.ALL -> sorted
        AudioSongFilter.FAVORITES -> sorted.filter { it.file.reference in current.favoritePaths }
        AudioSongFilter.RECENTLY_PLAYED -> sorted
            .filter { (current.lastPlayedAt[it.file.reference] ?: 0L) > 0L }
            .sortedByDescending { current.lastPlayedAt[it.file.reference] ?: 0L }
        AudioSongFilter.MOST_PLAYED -> sorted
            .filter { (current.playCounts[it.file.reference] ?: 0) > 0 }
            .sortedWith(compareByDescending<AudioTrack> {
                current.playCounts[it.file.reference] ?: 0
            }.thenBy { it.displayTitle.lowercase(Locale.getDefault()) })
    }
}

internal fun relatedAudioAlbums(album: AudioCollection, tracks: List<AudioTrack>): List<AudioCollection> {
    if (album.kind != AudioCollectionType.Album) return emptyList()
    val artist = album.subtitle?.trim()?.takeIf(String::isNotEmpty)
        ?: album.tracks.firstNotNullOfOrNull { (it.albumArtist ?: it.artist)?.trim()?.takeIf(String::isNotEmpty) }
        ?: return emptyList()
    return buildCollections(AudioLibraryState(collectionKind = AudioCollectionKind.ALBUMS), tracks)
        .filter { it.key != album.key && it.subtitle?.trim()?.equals(artist, ignoreCase = true) == true }
        .sortedBy { it.title.lowercase(Locale.getDefault()) }
}

private fun buildCollections(
    state: AudioLibraryState,
    tracks: List<AudioTrack>
): List<AudioCollection> {
    fun grouped(
        kind: AudioCollectionType,
        key: (AudioTrack) -> String?
    ): List<AudioCollection> = tracks.groupBy { key(it)?.trim().orEmpty().ifBlank { "Unknown" } }
        .map { (label, members) ->
            AudioCollection(
                key = "${kind.name}:$label",
                title = label,
                subtitle = null,
                tracks = members.sortedBy { it.displayTitle.lowercase(Locale.getDefault()) },
                kind = kind
            )
        }
    return when (state.collectionKind) {
        AudioCollectionKind.SONGS -> emptyList()
        AudioCollectionKind.FOLDERS -> tracks.groupBy { it.file.parentPath() }
            .map { (path, members) ->
                AudioCollection(
                    key = path,
                    title = File(path).name.ifBlank { path },
                    subtitle = path,
                    tracks = members.sortedBy { it.displayTitle.lowercase(Locale.getDefault()) },
                    customCoverPath = state.folderCoverPaths[path],
                    isPinned = path in state.pinnedFolderPaths
                )
            }
        AudioCollectionKind.ARTISTS -> grouped(AudioCollectionType.Artist, AudioTrack::artist)
        AudioCollectionKind.ALBUMS -> tracks.groupBy { track ->
            (track.albumArtist ?: track.artist).orEmpty().trim() to
                track.album.orEmpty().trim().ifBlank { "Unknown album" }
        }.map { (identity, members) ->
            AudioCollection(
                key = "Album:${identity.first}:${identity.second}",
                title = identity.second,
                subtitle = identity.first.takeIf(String::isNotBlank),
                tracks = members.sortedWith(compareBy<AudioTrack> { it.discNumber ?: 0 }
                    .thenBy { it.trackNumber ?: 0 }.thenBy { it.displayTitle }),
                kind = AudioCollectionType.Album
            )
        }
        AudioCollectionKind.GENRES -> grouped(AudioCollectionType.Genre, AudioTrack::genre)
        AudioCollectionKind.PLAYLISTS -> {
            val byPath = tracks.associateBy { it.file.reference }
            state.playlists.map { playlist ->
                AudioCollection(
                    key = playlist.id,
                    title = playlist.name,
                    subtitle = null,
                    tracks = playlist.trackPaths.mapNotNull(byPath::get),
                    kind = AudioCollectionType.Playlist
                )
            }
        }
    }
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
    reference.replace('\\', '/').substringBeforeLast('/', "")

internal const val AUDIO_FAVORITES_FOLDER_KEY = "__arcile_audio_favorites__"
