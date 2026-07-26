package dev.qtremors.arcile.core.storage.domain

@Immutable
data class AudioTrack(
    val file: FileModel,
    val title: String,
    val artist: String? = null,
    val album: String? = null,
    val durationMs: Long = 0L
) {
    val displayTitle: String
        get() = title.takeIf(String::isNotBlank)
            ?: file.name.substringBeforeLast('.', file.name)
}

interface AudioLibraryRepository {
    suspend fun getTracks(scope: StorageScope = StorageScope.AllStorage): Result<List<AudioTrack>>
}
