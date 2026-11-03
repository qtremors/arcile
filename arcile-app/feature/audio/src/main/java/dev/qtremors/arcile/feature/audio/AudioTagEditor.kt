package dev.qtremors.arcile.feature.audio

import android.content.Context
import android.media.MediaScannerConnection
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.webkit.MimeTypeMap
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.qtremors.arcile.core.storage.domain.AudioTrack
import java.io.File
import java.io.IOException
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.jaudiotagger.audio.AudioFileIO
import dev.qtremors.arcile.core.ui.audio.AudioLyrics
import dev.qtremors.arcile.core.ui.audio.AudioMetadataEdit
import dev.qtremors.arcile.core.ui.audio.AudioMetadataEditor
import dev.qtremors.arcile.core.ui.audio.AudioMetadataEditException
import dev.qtremors.arcile.core.ui.audio.parseLrc
import org.jaudiotagger.tag.FieldKey
import org.jaudiotagger.tag.TagOptionSingleton
import org.jaudiotagger.tag.images.ArtworkFactory


internal typealias AudioFileDetails = dev.qtremors.arcile.core.ui.audio.AudioFileDetails


/** Writes tags to the actual audio file and keeps a recovery copy until verification finishes. */
@Singleton
internal class AudioTagEditor @Inject constructor(
    @ApplicationContext private val context: Context
) : AudioMetadataEditor {
    private val writeMutex = Mutex()

    suspend fun fileDetails(track: AudioTrack): AudioFileDetails? = withContext(Dispatchers.IO) {
        val extractor = MediaExtractor()
        try {
            val uri = track.file.nodeRef.contentUri
            if (uri != null) extractor.setDataSource(context, Uri.parse(uri), null)
            else extractor.setDataSource(track.file.reference)
            (0 until extractor.trackCount).asSequence().map(extractor::getTrackFormat)
                .firstOrNull { it.getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true }
                ?.let { format ->
                    fun number(key: String) = if (format.containsKey(key))
                        format.getInteger(key).takeIf { it > 0 } else null
                    AudioFileDetails(number(MediaFormat.KEY_BIT_RATE),
                        number(MediaFormat.KEY_SAMPLE_RATE), number(MediaFormat.KEY_CHANNEL_COUNT))
                }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        } finally {
            extractor.release()
        }
    }

    override suspend fun lyrics(track: AudioTrack): AudioLyrics = withContext(Dispatchers.IO) {
        val file = File(track.file.reference)
        val sidecar = lrcFile(file)
        if (sidecar.isFile && sidecar.length() > MAX_LRC_BYTES) {
            throw AudioMetadataEditException(AudioMetadataEditException.Reason.LYRICS_TOO_LARGE)
        }
        val synced = sidecar.takeIf(File::isFile)?.readText(Charsets.UTF_8)
        TagOptionSingleton.getInstance().setAndroid(true)
        val embedded = AudioFileIO.read(file).tag?.getFirst(FieldKey.LYRICS)?.trim().orEmpty()
        val embeddedTimed = embedded.takeIf { parseLrc(it).isNotEmpty() }
        AudioLyrics(
            plain = embedded.takeIf { it.isNotBlank() && embeddedTimed == null },
            syncedLrc = synced ?: embeddedTimed
        )
    }

    override suspend fun editMetadata(track: AudioTrack, edit: AudioMetadataEdit): Result<AudioTrack> =
        withContext(Dispatchers.IO) {
            try {
                Result.success(writeMutex.withLock { writeAndVerify(track, edit) })
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Result.failure(error)
            }
        }

    private fun writeAndVerify(track: AudioTrack, edit: AudioMetadataEdit): AudioTrack {
        val file = File(track.file.reference)
        if (!file.isFile || !file.canRead() || !file.canWrite()) {
            throw AudioMetadataEditException(AudioMetadataEditException.Reason.READ_ONLY)
        }
        val sidecar = lrcFile(file)
        val recoveryDir = File(context.filesDir, "audio-tag-recovery").apply { mkdirs() }
        val recovery = File(recoveryDir, "${UUID.randomUUID()}.audio")
        val sidecarRecovery = File(recoveryDir, "${UUID.randomUUID()}.lrc")
        val hadSidecar = sidecar.isFile
        var commitStarted = false
        var keepRecovery = false
        try {
            file.copyTo(recovery)
            if (hadSidecar) sidecar.copyTo(sidecarRecovery)
            TagOptionSingleton.getInstance().setAndroid(true)
            val audio = AudioFileIO.read(file)
            val tag = audio.tagOrCreateAndSetDefault
            fun put(key: FieldKey, value: String) {
                if (value.isBlank()) tag.deleteField(key) else tag.setField(key, value)
            }
            put(FieldKey.TITLE, edit.title)
            put(FieldKey.ARTIST, edit.artist)
            put(FieldKey.ALBUM, edit.album)
            put(FieldKey.ALBUM_ARTIST, edit.albumArtist)
            put(FieldKey.GENRE, edit.genre)
            put(FieldKey.TRACK, edit.trackNumber?.toString().orEmpty())
            put(FieldKey.DISC_NO, edit.discNumber?.toString().orEmpty())
            put(FieldKey.YEAR, edit.year?.toString().orEmpty())
            put(FieldKey.LYRICS, edit.lyrics.plain.orEmpty())
            if (edit.removeArtwork) tag.deleteArtworkField()
            edit.artworkUri?.let { uri ->
                val mime = context.contentResolver.getType(Uri.parse(uri))
                val extension = mime?.let(MimeTypeMap.getSingleton()::getExtensionFromMimeType)
                    ?.takeIf { it.matches(Regex("[a-zA-Z0-9]{1,5}")) } ?: "jpg"
                val artwork = File(recoveryDir, "${UUID.randomUUID()}.$extension")
                try {
                    context.contentResolver.openInputStream(Uri.parse(uri))?.use { input ->
                        artwork.outputStream().use { output -> input.copyTo(output) }
                    } ?: throw AudioMetadataEditException(AudioMetadataEditException.Reason.ARTWORK_UNREADABLE)
                    if (artwork.length() !in 1..MAX_ARTWORK_BYTES) {
                        throw AudioMetadataEditException(AudioMetadataEditException.Reason.ARTWORK_TOO_LARGE)
                    }
                    tag.deleteArtworkField()
                    tag.setField(ArtworkFactory.createArtworkFromFile(artwork))
                } finally {
                    artwork.delete()
                }
            }
            commitStarted = true
            audio.commit()
            val lrc = edit.lyrics.syncedLrc
            if (lrc == null) {
                if (sidecar.isFile && !sidecar.delete()) throw AudioMetadataEditException(AudioMetadataEditException.Reason.LYRICS_NOT_SAVED)
            } else {
                if (lrc.toByteArray(Charsets.UTF_8).size > MAX_LRC_BYTES) {
                    throw AudioMetadataEditException(AudioMetadataEditException.Reason.LYRICS_TOO_LARGE)
                }
                sidecar.writeText(lrc, Charsets.UTF_8)
            }
            val verified = AudioFileIO.read(file).tag
                ?: throw AudioMetadataEditException(AudioMetadataEditException.Reason.VERIFICATION_FAILED)
            fun matches(key: FieldKey, expected: String): Boolean =
                verified.getFirst(key).orEmpty().trim() == expected.trim()
            if (!matches(FieldKey.TITLE, edit.title) ||
                !matches(FieldKey.ARTIST, edit.artist) ||
                !matches(FieldKey.ALBUM, edit.album) ||
                !matches(FieldKey.ALBUM_ARTIST, edit.albumArtist) ||
                !matches(FieldKey.GENRE, edit.genre) ||
                !matches(FieldKey.TRACK, edit.trackNumber?.toString().orEmpty()) ||
                !matches(FieldKey.DISC_NO, edit.discNumber?.toString().orEmpty()) ||
                !matches(FieldKey.YEAR, edit.year?.toString().orEmpty()) ||
                !matches(FieldKey.LYRICS, edit.lyrics.plain.orEmpty()) ||
                (edit.artworkUri != null && verified.artworkList.isEmpty()) ||
                (edit.removeArtwork && verified.artworkList.isNotEmpty()) ||
                (lrc != null && sidecar.readText(Charsets.UTF_8) != lrc)
            ) throw AudioMetadataEditException(AudioMetadataEditException.Reason.VERIFICATION_FAILED)
            runCatching {
                MediaScannerConnection.scanFile(context, arrayOf(file.absolutePath), null, null)
            }
            return track.copy(
                file = track.file.copy(size = file.length(), lastModified = file.lastModified()),
                title = edit.title,
                artist = edit.artist.takeIf(String::isNotBlank),
                album = edit.album.takeIf(String::isNotBlank),
                albumArtist = edit.albumArtist.takeIf(String::isNotBlank),
                genre = edit.genre.takeIf(String::isNotBlank),
                trackNumber = edit.trackNumber,
                discNumber = edit.discNumber,
                year = edit.year,
                artworkUri = if (edit.removeArtwork) null
                    else edit.artworkUri ?: track.artworkUri
            )
        } catch (error: Exception) {
            if (commitStarted) {
                try {
                    recovery.copyTo(file, overwrite = true)
                    if (hadSidecar) sidecarRecovery.copyTo(sidecar, overwrite = true)
                    else if (sidecar.isFile && !sidecar.delete()) {
                        throw IOException("Timed lyrics could not be restored")
                    }
                } catch (restoreError: Exception) {
                    keepRecovery = true
                    error.addSuppressed(restoreError)
                }
            }
            if (keepRecovery) {
                throw IOException(
                    "The edit failed and the original could not be restored. Recovery copy: ${recovery.absolutePath}",
                    error
                )
            }
            throw error
        } finally {
            if (!keepRecovery) {
                recovery.delete()
                sidecarRecovery.delete()
            }
        }
    }

    private fun lrcFile(audio: File): File = File(audio.parentFile,
        "${audio.nameWithoutExtension}.lrc")

    private companion object {
        const val MAX_LRC_BYTES = 1024 * 1024
        const val MAX_ARTWORK_BYTES = 12L * 1024L * 1024L
    }
}
