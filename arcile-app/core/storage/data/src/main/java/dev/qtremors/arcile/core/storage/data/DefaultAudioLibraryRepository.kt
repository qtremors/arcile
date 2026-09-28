package dev.qtremors.arcile.core.storage.data

import android.content.Context
import android.provider.MediaStore
import dev.qtremors.arcile.core.runtime.di.ArcileDispatchers
import dev.qtremors.arcile.core.storage.data.provider.VolumeProvider
import dev.qtremors.arcile.core.storage.data.source.MediaStoreFileRow
import dev.qtremors.arcile.core.storage.data.source.mediaProjection
import dev.qtremors.arcile.core.storage.data.source.readMediaStoreFileRow
import dev.qtremors.arcile.core.storage.data.source.rowMatchesScope
import dev.qtremors.arcile.core.storage.data.util.indexedVolumesForScope
import dev.qtremors.arcile.core.storage.domain.AudioLibraryRepository
import dev.qtremors.arcile.core.storage.domain.AudioTrack
import dev.qtremors.arcile.core.storage.domain.FileOperationException
import dev.qtremors.arcile.core.storage.domain.StorageScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

internal class DefaultAudioLibraryRepository(
    private val context: Context,
    private val volumeProvider: VolumeProvider,
    private val dispatchers: ArcileDispatchers
) : AudioLibraryRepository {

    override suspend fun getTracks(scope: StorageScope): Result<List<AudioTrack>> =
        withContext(dispatchers.io) {
            try {
                val volumes = indexedVolumesForScope(scope, volumeProvider.currentVolumes())
                if (scope !is StorageScope.AllStorage && volumes.isEmpty()) {
                    return@withContext Result.success(emptyList())
                }
                val projection = mediaProjection() + arrayOf(
                    MediaStore.Audio.Media.TITLE,
                    MediaStore.Audio.Media.ARTIST,
                    MediaStore.Audio.Media.ALBUM,
                    MediaStore.Audio.Media.DURATION,
                    MediaStore.Audio.Media.ALBUM_ARTIST,
                    MediaStore.Audio.Media.GENRE,
                    MediaStore.Audio.Media.TRACK,
                    MediaStore.Audio.Media.YEAR
                )
                val tracks = buildList {
                    context.contentResolver.query(
                        MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL),
                        projection,
                        null,
                        null,
                        "${MediaStore.Audio.Media.DATE_MODIFIED} DESC"
                    )?.use { cursor ->
                        val titleIndex = cursor.getColumnIndex(MediaStore.Audio.Media.TITLE)
                        val artistIndex = cursor.getColumnIndex(MediaStore.Audio.Media.ARTIST)
                        val albumIndex = cursor.getColumnIndex(MediaStore.Audio.Media.ALBUM)
                        val durationIndex = cursor.getColumnIndex(MediaStore.Audio.Media.DURATION)
                        val albumArtistIndex = cursor.getColumnIndex(MediaStore.Audio.Media.ALBUM_ARTIST)
                        val genreIndex = cursor.getColumnIndex(MediaStore.Audio.Media.GENRE)
                        val trackIndex = cursor.getColumnIndex(MediaStore.Audio.Media.TRACK)
                        val yearIndex = cursor.getColumnIndex(MediaStore.Audio.Media.YEAR)
                        while (cursor.moveToNext()) {
                            currentCoroutineContext().ensureActive()
                            val row = cursor.readMediaStoreFileRow()
                            if (!rowMatchesScope(row, scope, volumes)) continue
                            val encodedTrack = cursor.optionalAudioLong(trackIndex).toInt()
                            add(
                                row.toAudioTrack(
                                    volumes = volumes,
                                    title = cursor.optionalAudioString(titleIndex),
                                    artist = cursor.optionalAudioString(artistIndex),
                                    album = cursor.optionalAudioString(albumIndex),
                                    durationMs = cursor.optionalAudioLong(durationIndex),
                                    albumArtist = cursor.optionalAudioString(albumArtistIndex),
                                    genre = cursor.optionalAudioString(genreIndex),
                                    trackNumber = (encodedTrack % 1000).takeIf { it > 0 },
                                    discNumber = (encodedTrack / 1000).takeIf { it > 0 },
                                    year = cursor.optionalAudioLong(yearIndex).toInt()
                                        .takeIf { it > 0 }
                                )
                            )
                        }
                    }
                }
                Result.success(tracks.distinctBy { it.file.absolutePath })
            } catch (error: SecurityException) {
                Result.failure(FileOperationException.AccessDenied(cause = error))
            } catch (error: java.io.IOException) {
                Result.failure(FileOperationException.IOError(cause = error))
            } catch (error: Exception) {
                error.rethrowIfCancellation()
                Result.failure(FileOperationException.Unknown(cause = error))
            }
        }
}

private fun MediaStoreFileRow.toAudioTrack(
    volumes: List<dev.qtremors.arcile.core.storage.domain.StorageVolume>,
    title: String?,
    artist: String?,
    album: String?,
    durationMs: Long,
    albumArtist: String?,
    genre: String?,
    trackNumber: Int?,
    discNumber: Int?,
    year: Int?
): AudioTrack {
    val file = toFileModel(volumes)
    return AudioTrack(
        file = file,
        title = title.orEmpty(),
        artist = artist.cleanMediaStoreLabel(),
        album = album.cleanMediaStoreLabel(),
        durationMs = durationMs.coerceAtLeast(0L),
        albumArtist = albumArtist.cleanMediaStoreLabel(),
        genre = genre.cleanMediaStoreLabel(),
        trackNumber = trackNumber,
        discNumber = discNumber,
        year = year
    )
}

private fun String?.cleanMediaStoreLabel(): String? =
    this?.takeIf { it.isNotBlank() && it != MediaStore.UNKNOWN_STRING }

private fun android.database.Cursor.optionalAudioString(index: Int): String? =
    if (index >= 0 && !isNull(index)) getString(index) else null

private fun android.database.Cursor.optionalAudioLong(index: Int): Long =
    if (index >= 0 && !isNull(index)) getLong(index) else 0L
