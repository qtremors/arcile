package dev.qtremors.arcile.feature.onlyfiles

import android.content.ContentResolver
import dev.qtremors.arcile.core.storage.domain.AudioTrack
import dev.qtremors.arcile.core.ui.audio.AudioLyrics
import dev.qtremors.arcile.core.ui.audio.AudioMetadataEdit
import dev.qtremors.arcile.core.vault.domain.*
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import java.io.File
import java.io.InputStream
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class VaultAudioMetadataEditorTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun `mp3 tags are written in memory and published as one new encrypted revision`() = runTest {
        val vault = InMemoryVault(temporary)
        val song = vault.add("Song.mp3", mp3Bytes())
        val saved = mutableListOf<VaultNodeMetadata>()
        val editor = vault.editor(song, StandardTestDispatcher(testScheduler)) { saved += it }

        val result = editor.editMetadata(track(song), edit(title = "Private title", artist = "Private artist", lrc = "[00:01.00]Hello"))
        testScheduler.advanceUntilIdle()

        assertTrue(result.exceptionOrNull()?.stackTraceToString(), result.isSuccess)
        assertEquals("Private title", result.getOrThrow().title)
        val published = vault.node(song.ref.nodeId)
        assertEquals(song.revision + 1, published.revision)
        assertEquals(listOf(published), saved)
        val tag = vault.readTag(published)
        assertEquals("Private title", tag.getFirst(FieldKey.TITLE))
        assertEquals("Private artist", tag.getFirst(FieldKey.ARTIST))
        assertEquals("[00:01.00]Hello", vault.text(vault.named("Song.lrc")))
        assertEquals(0, vault.openScratches)
    }

    @Test fun `timed lyrics come from the encrypted sidecar and clearing them removes it`() = runTest {
        val vault = InMemoryVault(temporary)
        val song = vault.add("Song.mp3", mp3Bytes())
        vault.add("song.LRC", "[00:02.00]Stored".encodeToByteArray())
        val editor = vault.editor(song, StandardTestDispatcher(testScheduler))

        assertEquals("[00:02.00]Stored", editor.lyrics(track(song)).syncedLrc)

        assertTrue(editor.editMetadata(track(song), edit(lrc = null)).isSuccess)
        assertNull(vault.items.values.firstOrNull { it.first.name.equals("song.lrc", ignoreCase = true) })
    }

    @Test fun `formats whose writers need a temporary file are refused before decrypting`() = runTest {
        val vault = InMemoryVault(temporary)
        val song = vault.add("Song.m4a", ByteArray(4096))
        val editor = vault.editor(song, StandardTestDispatcher(testScheduler))

        assertFalse(editor.canEditTags)
        assertTrue(editor.editMetadata(track(song), edit()).isFailure)
        assertEquals(song, vault.node(song.ref.nodeId))
        assertEquals(0, vault.reads)
    }

    @Test fun `a changed revision rejects the save and leaves lyrics untouched`() = runTest {
        val vault = InMemoryVault(temporary)
        val song = vault.add("Song.mp3", mp3Bytes())
        val editor = vault.editor(song, StandardTestDispatcher(testScheduler))
        vault.bump(song.ref.nodeId)

        val result = editor.editMetadata(track(song), edit(lrc = "[00:01.00]New"))

        assertTrue(result.isFailure)
        assertNull(vault.items.values.firstOrNull { it.first.name == "Song.lrc" })
        assertEquals(0, vault.openScratches)
    }

    private fun track(node: VaultNodeMetadata) = AudioTrack(node.toSharedFileModel(), node.name.substringBeforeLast('.'))

    private fun edit(title: String = "Title", artist: String = "", lrc: String? = null) = AudioMetadataEdit(
        title = title, artist = artist, album = "", albumArtist = "", genre = "", trackNumber = null, discNumber = null,
        year = null, artworkUri = null, lyrics = AudioLyrics(plain = null, syncedLrc = lrc)
    )

    /** Forty MPEG-1 Layer III frames at 128 kbps and 44.1 kHz with silent payloads. */
    private fun mp3Bytes(): ByteArray {
        val frame = ByteArray(417).also { it[0] = 0xFF.toByte(); it[1] = 0xFB.toByte(); it[2] = 0x90.toByte(); it[3] = 0x00 }
        return ByteArray(frame.size * 40).also { out -> repeat(40) { frame.copyInto(out, it * frame.size) } }
    }

    private class InMemoryVault(private val temporary: TemporaryFolder) {
        val vault = VaultId.of("vault")
        val items = linkedMapOf<NodeId, Pair<VaultNodeMetadata, ByteArray>>()
        var reads = 0
        var openScratches = 0
        private var nextId = 0
        val files = mockk<VaultFileSystem>()

        init {
            coEvery { files.listDirectory(vault, DirectoryId.Root, any()) } answers {
                Result.success(VaultPage(items.values.map { it.first }, null, 0L))
            }
            every { files.openReader(any()) } answers {
                reads++
                Result.success(MemoryReader(items.getValue(firstArg<VaultNodeRef>().nodeId).second))
            }
            coEvery { files.replaceFileContents(any(), any(), any<Long>(), any()) } answers {
                val ref = firstArg<VaultNodeRef>()
                val current = items.getValue(ref.nodeId)
                if (current.first.revision != secondArg<Long>()) return@answers Result.failure(VaultFailure.ConcurrentMutation())
                val bytes = arg<() -> InputStream>(3)().use { it.readBytes() }
                if (bytes.size.toLong() != thirdArg<Long>()) return@answers Result.failure(VaultFailure.SourceChanged())
                val updated = current.first.copy(revision = current.first.revision + 1, sizeBytes = bytes.size.toLong())
                items[ref.nodeId] = updated to bytes
                Result.success(updated)
            }
            coEvery { files.replaceFileContents(any(), any(), any<ByteArray>()) } answers {
                val ref = firstArg<VaultNodeRef>()
                val bytes = thirdArg<ByteArray>().copyOf()
                val updated = items.getValue(ref.nodeId).first.let { it.copy(revision = it.revision + 1, sizeBytes = bytes.size.toLong()) }
                items[ref.nodeId] = updated to bytes
                Result.success(updated)
            }
            coEvery { files.createEmptyFile(vault, DirectoryId.Root, any()) } answers { Result.success(add(thirdArg(), ByteArray(0))) }
            coEvery { files.deletePermanently(any()) } answers { items.remove(firstArg<VaultNodeRef>().nodeId); Result.success(Unit) }
        }

        fun add(name: String, bytes: ByteArray): VaultNodeMetadata {
            val node = VaultNodeMetadata(VaultNodeRef(vault, NodeId.of("node-${nextId++}"), DirectoryId.Root,
                VaultNodeCapabilities()), name, VaultNodeKind.FILE, bytes.size.toLong(), 0L, 1L)
            items[node.ref.nodeId] = node to bytes
            return node
        }
        fun node(id: NodeId) = items.getValue(id).first
        fun named(name: String) = items.values.single { it.first.name == name }.first
        fun bump(id: NodeId) { items[id] = items.getValue(id).let { it.first.copy(revision = it.first.revision + 1) to it.second } }
        fun text(node: VaultNodeMetadata) = items.getValue(node.ref.nodeId).second.decodeToString()
        fun readTag(node: VaultNodeMetadata) = temporary.newFile("${node.ref.nodeId.value}.mp3").let { file ->
            file.writeBytes(items.getValue(node.ref.nodeId).second)
            AudioFileIO.read(file).tag
        }
        fun editor(node: VaultNodeMetadata, dispatcher: kotlinx.coroutines.CoroutineDispatcher, onSaved: (VaultNodeMetadata) -> Unit = {}) =
            VaultAudioMetadataEditor(node, files, mockk<ContentResolver>(relaxed = true), onSaved, scratch = {
                openScratches++
                val file = temporary.newFile()
                object : VaultAudioScratch {
                    override val file: File = file
                    override fun close() { openScratches--; file.delete() }
                }
            }, io = dispatcher)
    }

    private class MemoryReader(private val data: ByteArray) : VaultSeekableReader {
        override val sizeBytes: Long get() = data.size.toLong()
        override fun readAt(position: Long, buffer: ByteArray, offset: Int, length: Int): Int {
            if (position >= data.size) return -1
            val count = minOf(length, data.size - position.toInt())
            data.copyInto(buffer, offset, position.toInt(), position.toInt() + count)
            return count
        }
        override fun close() = Unit
    }
}
