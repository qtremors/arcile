package dev.qtremors.arcile.feature.onlyfiles

import android.content.ContentResolver
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.system.Os
import dev.qtremors.arcile.core.storage.domain.AudioTrack
import dev.qtremors.arcile.core.ui.audio.AudioLyrics
import dev.qtremors.arcile.core.ui.audio.AudioMetadataEdit
import dev.qtremors.arcile.core.ui.audio.AudioMetadataEditor
import dev.qtremors.arcile.core.ui.audio.AudioMetadataEditException
import dev.qtremors.arcile.core.ui.audio.parseLrc
import dev.qtremors.arcile.core.vault.domain.VaultFileSystem
import dev.qtremors.arcile.core.vault.domain.VaultListOptions
import dev.qtremors.arcile.core.vault.domain.VaultNodeKind
import dev.qtremors.arcile.core.vault.domain.VaultNodeMetadata
import java.io.Closeable
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.RandomAccessFile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import org.jaudiotagger.tag.Tag
import org.jaudiotagger.tag.TagOptionSingleton
import org.jaudiotagger.tag.images.ArtworkFactory
import org.jaudiotagger.tag.id3.valuepair.ImageFormats
import org.jaudiotagger.tag.reference.PictureTypes

/** A RAM-backed working file. Tag libraries need a path, so they open the anonymous memory file through /proc. */
internal interface VaultAudioScratch : Closeable {
    val file: File
}

internal fun interface VaultAudioScratchFactory {
    fun create(): VaultAudioScratch
}

/** An anonymous memory file: never linked into storage, released when closed or when the process dies. */
internal object MemoryAudioScratchFactory : VaultAudioScratchFactory {
    override fun create(): VaultAudioScratch {
        val anonymous = Os.memfd_create("arcile-vault-audio", 0)
        val descriptor = try { ParcelFileDescriptor.dup(anonymous) } finally { Os.close(anonymous) }
        return object : VaultAudioScratch {
            override val file = File("/proc/self/fd/${descriptor.fd}")
            override fun close() {
                try { Os.ftruncate(descriptor.fileDescriptor, 0L) } catch (_: Exception) { }
                descriptor.close()
            }
        }
    }
}

/**
 * Edits one vault track's music details without plaintext on storage. The decrypted audio lives in memory,
 * tags are verified by reading them back, and the result is published as a new encrypted revision. Timed
 * lyrics are stored in an encrypted `.lrc` file beside the track, like Browser's sidecar.
 */
internal class VaultAudioMetadataEditor(
    node: VaultNodeMetadata,
    private val files: VaultFileSystem,
    private val contentResolver: ContentResolver,
    private val onSaved: (VaultNodeMetadata) -> Unit = {},
    private val scratch: VaultAudioScratchFactory = MemoryAudioScratchFactory,
    private val io: CoroutineDispatcher = Dispatchers.IO
) : AudioMetadataEditor {
    private val mutex = Mutex()
    @Volatile private var node: VaultNodeMetadata = node
    @Volatile private var cachedLyrics: Pair<Long, AudioLyrics>? = null

    val canEditTags: Boolean
        get() = node.extension.lowercase() in WRITABLE_FORMATS && node.sizeBytes in 1..MAX_EDIT_BYTES &&
            node.ref.capabilities.canRename

    override suspend fun lyrics(track: AudioTrack): AudioLyrics = withContext(io) {
        mutex.withLock {
            val current = node
            cachedLyrics?.takeIf { it.first == current.revision }?.second?.let { return@withLock it }
            val synced = findSidecar(current)?.let { readSmallText(it) }
            val embedded = if (current.sizeBytes in 1..MAX_EDIT_BYTES) readEmbeddedLyrics(current) else ""
            val embeddedTimed = embedded.takeIf { parseLrc(it).isNotEmpty() }
            AudioLyrics(
                plain = embedded.takeIf { it.isNotBlank() && embeddedTimed == null },
                syncedLrc = synced ?: embeddedTimed
            ).also { cachedLyrics = current.revision to it }
        }
    }

    override suspend fun editMetadata(track: AudioTrack, edit: AudioMetadataEdit): Result<AudioTrack> = withContext(io) {
        try {
            Result.success(mutex.withLock { write(track, edit) })
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Result.failure(error)
        }
    }

    private suspend fun write(track: AudioTrack, edit: AudioMetadataEdit): AudioTrack {
        val current = node
        val format = current.extension.lowercase()
        if (format !in WRITABLE_FORMATS) throw AudioMetadataEditException(AudioMetadataEditException.Reason.UNSUPPORTED_FORMAT)
        if (current.sizeBytes !in 1..MAX_EDIT_BYTES) throw AudioMetadataEditException(AudioMetadataEditException.Reason.TOO_LARGE)
        if (!current.ref.capabilities.canRename) throw AudioMetadataEditException(AudioMetadataEditException.Reason.READ_ONLY)
        val lrc = edit.lyrics.syncedLrc
        if (lrc != null && lrc.toByteArray(Charsets.UTF_8).size > MAX_LRC_BYTES) throw AudioMetadataEditException(AudioMetadataEditException.Reason.LYRICS_TOO_LARGE)
        val artwork = edit.artworkUri?.let(::readArtwork)
        val saved = try {
            scratch.create().use { work ->
                decryptVaultAudio(files, current, work.file)
                TagOptionSingleton.getInstance().isAndroid = true
                val audio = AudioFileIO.readAs(work.file, format)
                applyEdit(audio.tagOrCreateAndSetDefault, edit, artwork)
                audio.commit()
                val verified = AudioFileIO.readAs(work.file, format).tag ?: throw AudioMetadataEditException(AudioMetadataEditException.Reason.VERIFICATION_FAILED)
                verifyEdit(verified, edit, artwork != null)
                val size = work.file.length()
                // The expected revision rejects a save if the track changed after it was decrypted.
                files.replaceFileContents(current.ref, current.revision, size) { FileInputStream(work.file) }.getOrThrow()
            }
        } finally {
            artwork?.fill(0)
        }
        node = saved
        cachedLyrics = null
        onSaved(saved)
        // The audio revision is already published; a sidecar failure is reported without undoing it.
        try {
            writeSidecar(saved, lrc)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            throw AudioMetadataEditException(AudioMetadataEditException.Reason.LYRICS_NOT_SAVED, error)
        }
        return track.copy(
            file = track.file.copy(size = saved.sizeBytes, lastModified = saved.modifiedAtMillis),
            title = edit.title,
            artist = edit.artist.takeIf(String::isNotBlank),
            album = edit.album.takeIf(String::isNotBlank),
            albumArtist = edit.albumArtist.takeIf(String::isNotBlank),
            genre = edit.genre.takeIf(String::isNotBlank),
            trackNumber = edit.trackNumber,
            discNumber = edit.discNumber,
            year = edit.year,
            artworkUri = if (edit.removeArtwork) null else track.artworkUri
        )
    }

    private suspend fun readEmbeddedLyrics(current: VaultNodeMetadata): String = try {
        scratch.create().use { work ->
            decryptVaultAudio(files, current, work.file)
            TagOptionSingleton.getInstance().isAndroid = true
            AudioFileIO.readAs(work.file, current.extension.lowercase()).tag?.getFirst(FieldKey.LYRICS)?.trim().orEmpty()
        }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        // Unsupported containers simply have no embedded lyrics to show.
        ""
    }

    private suspend fun findSidecar(current: VaultNodeMetadata): VaultNodeMetadata? {
        val expected = "${current.name.substringBeforeLast('.', current.name)}.lrc"
        var token: String? = null
        do {
            val page = files.listDirectory(current.ref.vaultId, current.ref.parentId, VaultListOptions(pageToken = token)).getOrThrow()
            page.items.firstOrNull { it.kind == VaultNodeKind.FILE && it.name.equals(expected, ignoreCase = true) }?.let { return it }
            token = page.nextPageToken
        } while (token != null)
        return null
    }

    private fun readSmallText(sidecar: VaultNodeMetadata): String {
        if (sidecar.sizeBytes > MAX_LRC_BYTES) throw AudioMetadataEditException(AudioMetadataEditException.Reason.LYRICS_TOO_LARGE)
        return files.openReader(sidecar.ref).getOrThrow().use { reader ->
            val bytes = ByteArray(reader.sizeBytes.toInt())
            var offset = 0
            while (offset < bytes.size) {
                val count = reader.readAt(offset.toLong(), bytes, offset, bytes.size - offset)
                if (count <= 0) throw AudioMetadataEditException(AudioMetadataEditException.Reason.CHANGED)
                offset += count
            }
            try { bytes.decodeToString() } finally { bytes.fill(0) }
        }
    }

    private suspend fun writeSidecar(saved: VaultNodeMetadata, lrc: String?) {
        val existing = findSidecar(saved)
        if (lrc == null) {
            existing?.let { files.deletePermanently(it.ref).getOrThrow() }
            return
        }
        val target = existing ?: files.createEmptyFile(
            saved.ref.vaultId, saved.ref.parentId, "${saved.name.substringBeforeLast('.', saved.name)}.lrc"
        ).getOrThrow()
        val bytes = lrc.toByteArray(Charsets.UTF_8)
        try {
            files.replaceFileContents(target.ref, target.revision, bytes).getOrThrow()
        } finally {
            bytes.fill(0)
        }
    }

    /** Artwork stays in memory; Browser's editor stages it as a file, which a private source must not do. */
    private fun readArtwork(uri: String): ByteArray {
        val output = java.io.ByteArrayOutputStream()
        contentResolver.openInputStream(Uri.parse(uri))?.use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                output.write(buffer, 0, count)
                if (output.size() > MAX_ARTWORK_BYTES) throw AudioMetadataEditException(AudioMetadataEditException.Reason.ARTWORK_TOO_LARGE)
            }
        } ?: throw AudioMetadataEditException(AudioMetadataEditException.Reason.ARTWORK_UNREADABLE)
        val bytes = output.toByteArray()
        if (bytes.isEmpty()) throw AudioMetadataEditException(AudioMetadataEditException.Reason.ARTWORK_UNREADABLE)
        return bytes
    }

    private fun applyEdit(tag: Tag, edit: AudioMetadataEdit, artwork: ByteArray?) {
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
        if (artwork != null) {
            tag.deleteArtworkField()
            tag.setField(ArtworkFactory.getNew().apply {
                binaryData = artwork
                mimeType = ImageFormats.getMimeTypeForBinarySignature(artwork)
                pictureType = PictureTypes.DEFAULT_ID
            })
        }
    }

    private fun verifyEdit(verified: Tag, edit: AudioMetadataEdit, artworkAdded: Boolean) {
        fun matches(key: FieldKey, expected: String) = verified.getFirst(key).orEmpty().trim() == expected.trim()
        val valid = matches(FieldKey.TITLE, edit.title) && matches(FieldKey.ARTIST, edit.artist) &&
            matches(FieldKey.ALBUM, edit.album) && matches(FieldKey.ALBUM_ARTIST, edit.albumArtist) &&
            matches(FieldKey.GENRE, edit.genre) && matches(FieldKey.TRACK, edit.trackNumber?.toString().orEmpty()) &&
            matches(FieldKey.DISC_NO, edit.discNumber?.toString().orEmpty()) &&
            matches(FieldKey.YEAR, edit.year?.toString().orEmpty()) && matches(FieldKey.LYRICS, edit.lyrics.plain.orEmpty()) &&
            (!artworkAdded || verified.artworkList.isNotEmpty()) && (!edit.removeArtwork || verified.artworkList.isEmpty())
        if (!valid) throw AudioMetadataEditException(AudioMetadataEditException.Reason.VERIFICATION_FAILED)
    }

    companion object {
        /** Formats whose tag writers rewrite the same channel in place, without a temporary file beside the audio. */
        val WRITABLE_FORMATS = setOf("mp3", "flac")
        const val MAX_EDIT_BYTES = 256L * 1024L * 1024L
        private const val MAX_LRC_BYTES = 1024 * 1024
        private const val MAX_ARTWORK_BYTES = 12L * 1024L * 1024L
    }
}

/** Decrypts a whole vault file into [target], which callers keep in memory. Fails if the size no longer matches. */
internal suspend fun decryptVaultAudio(files: VaultFileSystem, node: VaultNodeMetadata, target: File) {
    val coroutine = currentCoroutineContext()
    files.openReader(node.ref).getOrThrow().use { reader ->
        if (reader.sizeBytes != node.sizeBytes) throw AudioMetadataEditException(AudioMetadataEditException.Reason.CHANGED)
        RandomAccessFile(target, "rw").use { output ->
            output.setLength(0L)
            val buffer = ByteArray(128 * 1024)
            try {
                var position = 0L
                while (position < reader.sizeBytes) {
                    coroutine.ensureActive()
                    val count = reader.readAt(position, buffer, 0, minOf(buffer.size.toLong(), reader.sizeBytes - position).toInt())
                    if (count <= 0) throw AudioMetadataEditException(AudioMetadataEditException.Reason.CHANGED)
                    output.write(buffer, 0, count)
                    position += count
                }
            } finally {
                buffer.fill(0)
            }
        }
    }
}
