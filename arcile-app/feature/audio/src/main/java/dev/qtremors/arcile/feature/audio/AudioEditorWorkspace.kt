package dev.qtremors.arcile.feature.audio

import android.content.Context
import android.media.MediaMetadataRetriever
import android.media.MediaScannerConnection
import android.net.Uri
import android.provider.OpenableColumns
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.qtremors.arcile.core.runtime.di.ArcileDispatchers
import dev.qtremors.arcile.core.runtime.di.ApplicationScope
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal class AudioEditorWorkspace @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dispatchers: ArcileDispatchers,
    @ApplicationScope private val applicationScope: CoroutineScope
) {
    private val waveformExtractor = AudioWaveformExtractor()
    private val temporaryInputs = mutableSetOf<String>()
    private val temporaryInputsLock = Any()

    suspend fun readSources(paths: List<String>): List<AudioEditorSource> =
        withContext(dispatchers.io) {
            paths.distinct().mapNotNull(::readSource)
        }

    suspend fun importSources(uris: List<Uri>): List<Pair<Uri, AudioEditorSource?>> =
        withContext(dispatchers.io) {
            uris.map { uri -> uri to importTemporarySource(uri) }
        }

    suspend fun extractWaveform(source: AudioEditorSource): AudioWaveform? =
        withContext(dispatchers.io) {
            runCatching {
                waveformExtractor.extract(source.path, source.durationMs)
            }.getOrNull()
        }

    suspend fun preserveMetadata(sourcePath: String, outputPath: String) =
        withContext(dispatchers.io) {
            AudioMetadataPreserver.copy(sourcePath, outputPath)
        }

    fun previewUri(path: String): Uri = Uri.fromFile(File(path))

    fun removeTemporary(path: String) {
        synchronized(temporaryInputsLock) {
            temporaryInputs -= path
        }
        delete(path)
    }

    fun delete(path: String) {
        applicationScope.launch(dispatchers.io) {
            File(path).delete()
        }
    }

    fun scan(path: String) {
        MediaScannerConnection.scanFile(context, arrayOf(path), null, null)
    }

    fun clearTemporaryInputs() {
        val paths = synchronized(temporaryInputsLock) {
            temporaryInputs.toList().also { temporaryInputs.clear() }
        }
        applicationScope.launch(dispatchers.io) {
            paths.forEach { path -> File(path).delete() }
        }
    }

    private fun readSource(path: String): AudioEditorSource? {
        val file = File(path)
        if (!file.isFile) return null
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull()
                ?: return null
            AudioEditorSource(
                path = file.absolutePath,
                name = file.name,
                durationMs = duration,
                sizeBytes = file.length(),
                metadata = AudioEditorMetadata(
                    title = retriever.metadata(MediaMetadataRetriever.METADATA_KEY_TITLE),
                    artist = retriever.metadata(MediaMetadataRetriever.METADATA_KEY_ARTIST),
                    album = retriever.metadata(MediaMetadataRetriever.METADATA_KEY_ALBUM),
                    albumArtist = retriever.metadata(
                        MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST
                    ),
                    genre = retriever.metadata(MediaMetadataRetriever.METADATA_KEY_GENRE),
                    year = retriever.metadata(MediaMetadataRetriever.METADATA_KEY_YEAR),
                    bitrate = retriever.metadata(MediaMetadataRetriever.METADATA_KEY_BITRATE),
                    mimeType = retriever.metadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE),
                    artwork = retriever.embeddedPicture
                )
            )
        } catch (_: RuntimeException) {
            null
        } finally {
            retriever.release()
        }
    }

    private fun importTemporarySource(uri: Uri): AudioEditorSource? {
        val resolver = context.contentResolver
        val displayName = resolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }?.takeIf(String::isNotBlank) ?: "audio_${System.currentTimeMillis()}"
        val safeName = displayName.replace(Regex("[^A-Za-z0-9._ -]"), "_")
        val directory = File(context.cacheDir, "audio_editor_inputs").apply(File::mkdirs)
        val destination = generateSequence(1) { it + 1 }
            .map { suffix ->
                if (suffix == 1) {
                    File(directory, safeName)
                } else {
                    File(
                        directory,
                        "${safeName.substringBeforeLast('.', safeName)}_$suffix." +
                            safeName.substringAfterLast('.', "audio")
                    )
                }
            }
            .first { !it.exists() }
        return runCatching {
            resolver.openInputStream(uri)?.use { input ->
                destination.outputStream().use(input::copyTo)
            } ?: return null
            synchronized(temporaryInputsLock) {
                temporaryInputs += destination.absolutePath
            }
            readSource(destination.absolutePath)?.copy(temporary = true)
        }.getOrElse {
            destination.delete()
            null
        }
    }

    private fun MediaMetadataRetriever.metadata(key: Int): String? =
        extractMetadata(key)?.trim()?.takeIf(String::isNotBlank)
}
