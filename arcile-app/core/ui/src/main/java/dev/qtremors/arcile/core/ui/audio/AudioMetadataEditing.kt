package dev.qtremors.arcile.core.ui.audio

import dev.qtremors.arcile.core.storage.domain.AudioTrack

data class AudioLyrics(val plain: String?, val syncedLrc: String?)

data class AudioMetadataEdit(
    val title: String,
    val artist: String,
    val album: String,
    val albumArtist: String,
    val genre: String,
    val trackNumber: Int?,
    val discNumber: Int?,
    val year: Int?,
    val artworkUri: String?,
    val removeArtwork: Boolean = false,
    val lyrics: AudioLyrics
)

/** A music-details failure the shared editor explains with a localized message. */
class AudioMetadataEditException(val reason: Reason, cause: Throwable? = null) : java.io.IOException(reason.name, cause) {
    enum class Reason(@androidx.annotation.StringRes val message: Int) {
        UNSUPPORTED_FORMAT(dev.qtremors.arcile.core.ui.R.string.audio_edit_error_format),
        TOO_LARGE(dev.qtremors.arcile.core.ui.R.string.audio_edit_error_too_large),
        READ_ONLY(dev.qtremors.arcile.core.ui.R.string.audio_edit_error_read_only),
        CHANGED(dev.qtremors.arcile.core.ui.R.string.audio_edit_error_changed),
        LYRICS_TOO_LARGE(dev.qtremors.arcile.core.ui.R.string.audio_edit_error_lyrics_size),
        ARTWORK_UNREADABLE(dev.qtremors.arcile.core.ui.R.string.audio_edit_error_artwork),
        ARTWORK_TOO_LARGE(dev.qtremors.arcile.core.ui.R.string.audio_edit_error_artwork_size),
        VERIFICATION_FAILED(dev.qtremors.arcile.core.ui.R.string.audio_edit_error_verification),
        LYRICS_NOT_SAVED(dev.qtremors.arcile.core.ui.R.string.audio_edit_error_lyrics_not_saved)
    }
}

/**
 * Reads and writes music details for the shared editor. Browser files write in place with a recovery copy;
 * vault files publish a new encrypted revision and an encrypted lyrics file instead.
 */
interface AudioMetadataEditor {
    suspend fun lyrics(track: AudioTrack): AudioLyrics
    suspend fun editMetadata(track: AudioTrack, edit: AudioMetadataEdit): Result<AudioTrack>
}
