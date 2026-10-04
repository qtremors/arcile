package dev.qtremors.arcile.core.storage.domain

@Immutable
data class AudioTrack(
    val file: FileModel,
    val title: String,
    val artist: String? = null,
    val album: String? = null,
    val durationMs: Long = 0L,
    val albumArtist: String? = null,
    val genre: String? = null,
    val trackNumber: Int? = null,
    val year: Int? = null,
    val discNumber: Int? = null,
    val artworkUri: String? = null
) {
    val displayTitle: String
        get() = title.takeIf(String::isNotBlank)
            ?: file.name.substringBeforeLast('.', file.name)
}

interface AudioLibraryRepository {
    suspend fun getTracks(scope: StorageScope = StorageScope.AllStorage): Result<List<AudioTrack>>

    suspend fun getMusicTracks(scope: StorageScope = StorageScope.AllStorage): Result<List<AudioTrack>> =
        getTracks(scope)
}
