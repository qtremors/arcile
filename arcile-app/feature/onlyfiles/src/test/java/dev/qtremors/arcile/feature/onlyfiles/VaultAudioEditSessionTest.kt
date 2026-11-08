package dev.qtremors.arcile.feature.onlyfiles

import dev.qtremors.arcile.core.vault.domain.*
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import java.io.File
import java.io.InputStream
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class VaultAudioEditSessionTest {
    @get:Rule val temporary = TemporaryFolder()
    private val vault = VaultId.of("vault")
    private val unlocked = MutableStateFlow(setOf(vault))
    private val stored = linkedMapOf<String, ByteArray>()
    private val names = mutableSetOf("Song.mp3")
    private var open = 0
    private var failReplace = false
    private val files = mockk<VaultFileSystem>()
    private val song = VaultNodeMetadata(VaultNodeRef(vault, NodeId.of("song"), DirectoryId.Root, VaultNodeCapabilities()),
        "Song.mp3", VaultNodeKind.FILE, 5, 0, 1)

    init {
        stored["song"] = "audio".encodeToByteArray()
        every { files.openReader(any()) } answers { Result.success(Reader(stored.getValue(firstArg<VaultNodeRef>().nodeId.value))) }
        coEvery { files.createEmptyFile(vault, DirectoryId.Root, any()) } answers {
            val name = thirdArg<String>()
            if (!names.add(name)) Result.failure(VaultFailure.NameConflict(name))
            else Result.success(song.copy(ref = song.ref.copy(nodeId = NodeId.of("n${names.size}")), name = name, sizeBytes = 0))
        }
        coEvery { files.replaceFileContents(any(), any(), any<Long>(), any()) } answers {
            if (failReplace) return@answers Result.failure(VaultFailure.Unavailable())
            val bytes = arg<() -> InputStream>(3)().use { it.readBytes() }
            stored[firstArg<VaultNodeRef>().nodeId.value] = bytes
            Result.success(song.copy(ref = firstArg(), sizeBytes = bytes.size.toLong()))
        }
        coEvery { files.deletePermanently(any()) } answers { stored.remove(firstArg<VaultNodeRef>().nodeId.value); Result.success(Unit) }
    }

    private fun session() = runBlocking {
        VaultAudioEditSession.create(listOf(song), files, temporary.root.path, unlocked, scratch = {
            open++
            val file = temporary.newFile()
            object : VaultAudioScratch {
                override val file: File = file
                override fun close() { open--; file.delete() }
            }
        }, link = { _, _ -> })
    }

    @Test fun `sources are decrypted to memory and named by their vault display names`() {
        val session = session()
        val path = session.sourcePaths.single()
        assertTrue(path.endsWith(".mp3"))
        assertFalse(path.contains("Song"))
        assertEquals("Song.mp3", session.displayName(path))
        assertEquals(1, open)
        session.close()
        assertEquals(0, open)
        assertFalse(session.active.value)
    }

    @Test fun `published edits are saved beside the source without replacing existing names`() = runBlocking {
        val session = session()
        names += "Song (edited).mp3"
        val output = session.createOutput("mp3")
        writeOutput(session, output, "edited audio")

        val saved = session.publish(output).getOrThrow()

        assertEquals("Song (edited 2).mp3", saved)
        assertEquals("edited audio", stored.values.last().decodeToString())
        session.close()
    }

    @Test fun `a failed encrypted write removes the placeholder file`() = runBlocking {
        val session = session()
        val output = session.createOutput("m4a")
        writeOutput(session, output, "edited")
        failReplace = true

        assertTrue(session.publish(output).isFailure)

        coVerify(exactly = 1) { files.deletePermanently(any()) }
        session.close()
    }

    @Test fun `locking the vault ends the session and releases memory`() = runBlocking {
        val session = session()
        session.createOutput("mp3")
        unlocked.value = emptySet()
        withTimeout(5_000) { session.active.first { !it } }
        assertEquals(0, open)
        assertTrue(session.publish("anything").exceptionOrNull() is VaultFailure.Locked)
    }

    @Test fun `files beyond the in-memory limit are refused before decrypting`() {
        val large = song.copy(sizeBytes = VaultAudioMetadataEditor.MAX_EDIT_BYTES + 1)
        val error = runCatching {
            runBlocking { VaultAudioEditSession.create(listOf(large), files, temporary.root.path, unlocked, link = { _, _ -> }) }
        }.exceptionOrNull()
        assertTrue(error is VaultFailure.FileTooLarge)
        assertEquals(0, open)
    }

    @Test fun `several vault tracks open together for combining in selection order`() {
        val second = song.copy(ref = song.ref.copy(nodeId = NodeId.of("second")), name = "Second.mp3")
        stored["second"] = "more audio".encodeToByteArray()
        val session = runBlocking {
            VaultAudioEditSession.create(listOf(song, second.copy(sizeBytes = 10)), files, temporary.root.path, unlocked, scratch = {
                open++
                val file = temporary.newFile()
                object : VaultAudioScratch {
                    override val file: File = file
                    override fun close() { open--; file.delete() }
                }
            }, link = { _, _ -> })
        }
        assertEquals(listOf("Song.mp3", "Second.mp3"), session.sourcePaths.map { session.displayName(it) })
        assertEquals(2, open)
        session.close()
        assertEquals(0, open)
    }

    @Test fun `only writable audio from one vault within the memory limits can be edited`() {
        val other = song.copy(ref = song.ref.copy(vaultId = VaultId.of("other"), nodeId = NodeId.of("x")))
        val text = song.copy(ref = song.ref.copy(nodeId = NodeId.of("t")), name = "Notes.txt")
        val readOnly = song.copy(ref = song.ref.copy(capabilities = VaultNodeCapabilities(canRename = false)))
        val huge = song.copy(sizeBytes = VaultAudioMetadataEditor.MAX_EDIT_BYTES + 1)
        assertTrue(canEditVaultAudio(listOf(song)))
        assertFalse(canEditVaultAudio(emptyList()))
        assertFalse(canEditVaultAudio(listOf(song, other)))
        assertFalse(canEditVaultAudio(listOf(song, text)))
        assertFalse(canEditVaultAudio(listOf(readOnly)))
        assertFalse(canEditVaultAudio(listOf(huge)))
    }

    /** The test scratch stands in for the memory file behind the link path. */
    private fun writeOutput(session: VaultAudioEditSession, output: String, text: String) {
        val field = VaultAudioEditSession::class.java.getDeclaredField("entries").apply { isAccessible = true }
        @Suppress("UNCHECKED_CAST")
        val entries = field.get(session) as Map<String, Pair<VaultAudioScratch, String?>>
        entries.getValue(output).first.file.writeText(text)
    }

    private class Reader(private val data: ByteArray) : VaultSeekableReader {
        override val sizeBytes: Long get() = data.size.toLong()
        override fun readAt(position: Long, target: ByteArray, offset: Int, length: Int): Int {
            if (position >= data.size) return -1
            val count = minOf(length, data.size - position.toInt())
            data.copyInto(target, offset, position.toInt(), position.toInt() + count)
            return count
        }
        override fun close() = Unit
    }
}
