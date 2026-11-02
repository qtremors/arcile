package dev.qtremors.arcile.feature.onlyfiles

import dev.qtremors.arcile.core.vault.domain.DirectoryId
import dev.qtremors.arcile.core.vault.domain.NodeId
import dev.qtremors.arcile.core.vault.domain.VaultArchiveSource
import dev.qtremors.arcile.core.vault.domain.VaultArchiveDestinationResult
import dev.qtremors.arcile.core.vault.domain.VaultBatchResult
import dev.qtremors.arcile.core.vault.domain.VaultConflict
import dev.qtremors.arcile.core.vault.domain.VaultConflictDecision
import dev.qtremors.arcile.core.vault.domain.VaultConflictResolver
import dev.qtremors.arcile.core.vault.domain.VaultFailure
import dev.qtremors.arcile.core.vault.domain.VaultItemResult
import dev.qtremors.arcile.core.vault.domain.VaultItemOutcome
import dev.qtremors.arcile.core.vault.domain.VaultFileSystem
import dev.qtremors.arcile.core.vault.domain.VaultId
import dev.qtremors.arcile.core.vault.domain.VaultNodeCapabilities
import dev.qtremors.arcile.core.vault.domain.VaultNodeKind
import dev.qtremors.arcile.core.vault.domain.VaultNodeMetadata
import dev.qtremors.arcile.core.vault.domain.VaultNodeRef
import dev.qtremors.arcile.core.vault.domain.VaultPage
import dev.qtremors.arcile.core.vault.domain.VaultPath
import dev.qtremors.arcile.core.vault.domain.VaultSeekableReader
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.nio.channels.SeekableByteChannel
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import java.util.zip.GZIPOutputStream
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.apache.commons.compress.archivers.sevenz.SevenZArchiveEntry
import org.apache.commons.compress.archivers.sevenz.SevenZOutputFile
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import org.apache.commons.compress.utils.SeekableInMemoryByteChannel

@OptIn(ExperimentalCoroutinesApi::class)
class OnlyFilesArchiveControllerTest {
    private val vault = VaultId.of("vault")
    private val root = DirectoryId.Root

    @Test
    fun `extracting a selection reads only selected entries and preserves Browser folder paths`() = runTest {
        val payload = zipBytes(
            "docs/one.txt" to "one",
            "docs/chapter/two.txt" to "two",
            "outside.txt" to "outside"
        )
        val source = file("source.zip", payload.size.toLong())
        val fs = mockk<VaultFileSystem>()
        val reader = MemoryReader(payload)
        every { fs.openReader(source.ref) } returns Result.success(reader)
        val extracted = mutableMapOf<List<String>, String>()
        coEvery { fs.extractArchiveToNamedFolder(vault, root, "Result", any(), any(), any()) } coAnswers {
            val entries = arg<List<VaultArchiveSource>>(3)
            entries.filterIsInstance<VaultArchiveSource.File>().forEach {
                extracted[it.path] = it.open().use { input -> input.readBytes().decodeToString() }
            }
            Result.success(namedExtraction())
        }
        val state = mounted()
        var reloads = 0
        val controller = OnlyFilesArchiveController(fs, state, this, { reloads++ }, { throw it })

        controller.extract(
            source, root, VaultZipSelection(listOf("docs"), setOf(listOf("docs", "chapter"))),
            "Result"
        )
        state.first { !it.busy }

        assertEquals(mapOf(listOf("docs", "chapter", "two.txt") to "two"), extracted)
        assertTrue(reader.closed)
        assertEquals(1, reloads)
        assertEquals(OnlyFilesOperationDestination.VaultDirectory(vault, DirectoryId.of("Result")), state.value.operationFeedback?.destination)
        assertNull(state.value.completedArchive)
    }

    @Test
    fun `7z extraction opens selected entries in archive order across interleaved folders`() = runTest {
        val channel = SeekableInMemoryByteChannel()
        val payload = SevenZOutputFile(channel).use { archive ->
            for ((name, text) in listOf("docs/one.txt" to "one", "unselected.txt" to "skip", "docs/two.txt" to "two")) {
                archive.putArchiveEntry(SevenZArchiveEntry().apply { this.name = name })
                archive.write(text.encodeToByteArray())
                archive.closeArchiveEntry()
            }
            archive.finish()
            channel.array().copyOf(channel.size().toInt())
        }
        val source = file("source.7z", payload.size.toLong())
        val fs = mockk<VaultFileSystem>()
        val reader = MemoryReader(payload)
        every { fs.openReader(source.ref) } returns Result.success(reader)
        val extracted = linkedMapOf<List<String>, String>()
        coEvery { fs.extractArchiveToNamedFolder(vault, root, "Result", any(), any(), any()) } coAnswers {
            val entries = arg<List<VaultArchiveSource>>(3)
            for (entry in entries.filterIsInstance<VaultArchiveSource.File>()) {
                extracted[entry.path] = entry.open().use { it.readBytes().decodeToString() }
            }
            Result.success(namedExtraction())
        }
        val state = mounted()
        val controller = OnlyFilesArchiveController(fs, state, this, {}, { throw it })

        controller.extract(source, root, VaultZipSelection(listOf("docs"), emptySet()), "Result")
        state.first { !it.busy }

        assertEquals(listOf(listOf("docs", "one.txt"), listOf("docs", "two.txt")), extracted.keys.toList())
        assertEquals(listOf("one", "two"), extracted.values.toList())
        assertTrue(reader.closed)
        assertTrue(state.value.operationFeedback != null)
    }

    @Test
    fun `vault extraction streams selected tar gzip entries into the encrypted writer`() = runTest {
        val payload = ByteArrayOutputStream().also { bytes ->
            TarArchiveOutputStream(GZIPOutputStream(bytes)).use { output ->
                for ((path, text) in listOf("./docs/one.txt" to "one", "extra.txt" to "skip", "./docs/two.txt" to "two")) {
                    val body = text.encodeToByteArray()
                    output.putArchiveEntry(TarArchiveEntry(path).apply { size = body.size.toLong() })
                    output.write(body)
                    output.closeArchiveEntry()
                }
            }
        }.toByteArray()
        val source = file("source.tar.gz", payload.size.toLong())
        val reader = MemoryReader(payload)
        val fs = mockk<VaultFileSystem>()
        every { fs.openReader(source.ref) } returns Result.success(reader)
        val extracted = linkedMapOf<List<String>, String>()
        coEvery { fs.extractArchiveToNamedFolder(vault, root, "Result", any(), any(), any()) } coAnswers {
            for (entry in arg<List<VaultArchiveSource>>(3).filterIsInstance<VaultArchiveSource.File>()) {
                extracted[entry.path] = entry.open().use { it.readBytes().decodeToString() }
            }
            Result.success(namedExtraction())
        }
        val state = mounted()
        val controller = OnlyFilesArchiveController(fs, state, this, {}, { throw it })

        controller.extract(
            source, root, VaultZipSelection(listOf("docs"), setOf(listOf("docs", "two.txt"))),
            "Result"
        )
        state.first { !it.busy }

        assertEquals(mapOf(listOf("docs", "two.txt") to "two"), extracted)
        assertTrue(reader.closed)
        assertEquals(OnlyFilesOperationDestination.VaultDirectory(vault, DirectoryId.of("Result")), state.value.operationFeedback?.destination)
    }

    @Test
    fun `password protected 7z extraction passes decrypted entries only to the vault writer`() = runTest {
        val password = "secret".toCharArray()
        val channel = SeekableInMemoryByteChannel()
        val payload = SevenZOutputFile(channel, password).use { archive ->
            archive.putArchiveEntry(SevenZArchiveEntry().apply { name = "note.txt" })
            archive.write("protected text".encodeToByteArray())
            archive.closeArchiveEntry()
            archive.finish()
            channel.array().copyOf(channel.size().toInt())
        }
        val source = file("encrypted.7z", payload.size.toLong())
        val reader = MemoryReader(payload)
        val fs = mockk<VaultFileSystem>()
        every { fs.openReader(source.ref) } returns Result.success(reader)
        coEvery { fs.extractArchiveToNamedFolder(vault, root, "Result", any(), any(), any()) } coAnswers {
            val entry = arg<List<VaultArchiveSource>>(3).single() as VaultArchiveSource.File
            assertEquals(listOf("note.txt"), entry.path)
            assertEquals("protected text", entry.open().use { it.readBytes().decodeToString() })
            Result.success(namedExtraction())
        }
        val state = mounted()
        val controller = OnlyFilesArchiveController(fs, state, this, {}, { throw it })

        controller.extract(
            source, root, VaultZipSelection(emptyList(), emptySet()), "Result", password = password
        )
        state.first { !it.busy }

        assertTrue(state.value.operationFeedback != null)
        assertTrue(password.all { it == '\u0000' })
        assertTrue(reader.closed)
    }

    @Test
    fun `whole archive extraction rejects a traversal entry before writing output`() = runTest {
        val payload = zipBytes("valid.txt" to "valid", "../outside.txt" to "invalid")
        val source = file("source.zip", payload.size.toLong())
        val fs = mockk<VaultFileSystem>()
        every { fs.openReader(source.ref) } returns Result.success(MemoryReader(payload))
        val state = mounted()
        var failed = false
        val controller = OnlyFilesArchiveController(fs, state, this, { error("Unexpected reload") }, { failed = true })

        controller.extract(source, root, VaultZipSelection(emptyList(), emptySet()), "Result")
        state.first { !it.busy }

        assertTrue(failed)
        coVerify(exactly = 0) { fs.extractArchiveToNamedFolder(any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `creation streams a selected directory and reads children across pages`() = runTest {
        val docs = folder("docs")
        val parent = requireNotNull(docs.ref.directoryId)
        val first = file("one.txt", 3, parent)
        val second = file("two.txt", 3, parent)
        val fs = mockk<VaultFileSystem>()
        coEvery { fs.listDirectory(vault, parent, match { it.pageToken == null }) } returns
            Result.success(VaultPage(listOf(first), "next", 1))
        coEvery { fs.listDirectory(vault, parent, match { it.pageToken == "next" }) } returns
            Result.success(VaultPage(listOf(second), null, 1))
        every { fs.openReader(first.ref) } returns Result.success(MemoryReader("one".encodeToByteArray()))
        every { fs.openReader(second.ref) } returns Result.success(MemoryReader("two".encodeToByteArray()))
        val output = ByteArrayOutputStream()
        coEvery { fs.createZip(vault, root, "docs.zip", any()) } coAnswers {
            arg<(OutputStream) -> Unit>(3)(output)
            Result.success(file("docs.zip", output.size().toLong()))
        }
        val state = mounted()
        val controller = OnlyFilesArchiveController(fs, state, this, {}, { throw it })
        controller.create(listOf(docs), "docs.zip", 1, "Created")
        advanceUntilIdle()

        ZipInputStream(ByteArrayInputStream(output.toByteArray())).use { zip ->
            assertEquals("docs/", zip.nextEntry.name)
            assertEquals("docs/one.txt", zip.nextEntry.name)
            assertEquals("one", zip.readBytes().decodeToString())
            assertEquals("docs/two.txt", zip.nextEntry.name)
            assertEquals("two", zip.readBytes().decodeToString())
            assertNull(zip.nextEntry)
        }
        assertEquals("Created", state.value.message)
    }

    @Test
    fun `password ZIP creation and selected extraction stream authenticated bytes`() = runTest {
        val fs = mockk<VaultFileSystem>()
        val state = mounted()
        val original = "encrypted archive payload".encodeToByteArray()
        val input = file("secret.txt", original.size.toLong())
        every { fs.openReader(input.ref) } answers { Result.success(MemoryReader(original)) }
        val output = ByteArrayOutputStream()
        coEvery { fs.createZip(vault, root, "secret.zip", any()) } coAnswers {
            arg<(OutputStream) -> Unit>(3)(output)
            Result.success(file("secret.zip", output.size().toLong()))
        }
        val errors = mutableListOf<Throwable>()
        val controller = OnlyFilesArchiveController(fs, state, this, {}, { errors.add(it) })
        val password = "Zip-Password-7"
        val creationSecret = password.toCharArray()
        controller.create(listOf(input), "secret.zip", 1, "Created", creationSecret)
        advanceUntilIdle()
        assertTrue(errors.isEmpty())
        assertTrue(creationSecret.all { it == '\u0000' })
        val archiveBytes = output.toByteArray()
        assertFalse(archiveBytes.decodeToString().contains(original.decodeToString()))
        val archive = file("secret.zip", archiveBytes.size.toLong())
        every { fs.openReader(archive.ref) } answers { Result.success(MemoryReader(archiveBytes)) }
        var extracted: ByteArray? = null
        coEvery { fs.extractArchiveToNamedFolder(vault, root, "Result", any(), any(), any()) } coAnswers {
            val item = arg<List<VaultArchiveSource>>(3).single() as VaultArchiveSource.File
            assertEquals(listOf("secret.txt"), item.path)
            extracted = item.open().use { it.readBytes() }
            Result.success(namedExtraction())
        }
        val selection = VaultZipSelection(emptyList(), setOf(listOf("secret.txt")), requiresPassword = true)
        val wrong = "wrong-password".toCharArray()
        controller.extract(archive, root, selection, "Result", password = wrong)
        state.first { !it.busy }
        assertNull(extracted)
        assertTrue(errors.single().message.orEmpty().contains("password", ignoreCase = true))
        assertTrue(wrong.all { it == '\u0000' })

        val correct = password.toCharArray()
        controller.extract(archive, root, selection, "Result", password = correct)
        state.first { !it.busy }
        assertTrue(errors.map(Throwable::message).joinToString(), state.value.operationFeedback != null)
        assertTrue(original.contentEquals(extracted))
        assertTrue(correct.all { it == '\u0000' })

        val damaged = archiveBytes.copyOf()
        val tagByte = org.apache.commons.compress.archivers.zip.ZipFile.builder()
            .setSeekableByteChannel(SeekableInMemoryByteChannel(damaged)).get().use { zip ->
                val entry = zip.entries.nextElement()
                (entry.dataOffset + entry.compressedSize - 1L).toInt()
            }
        damaged[tagByte] = (damaged[tagByte].toInt() xor 1).toByte()
        every { fs.openReader(archive.ref) } answers { Result.success(MemoryReader(damaged)) }
        extracted = null
        val damagedSecret = password.toCharArray()
        controller.extract(archive, root, selection, "Result", password = damagedSecret)
        state.first { !it.busy }

        assertEquals(2, errors.size)
        assertNull(extracted)
        assertNull(state.value.completedArchive)
        assertTrue(damagedSecret.all { it == '\u0000' })
    }

    @Test
    fun `a result from a prior mount cannot expose its name after lock`() = runTest {
        val fs = mockk<VaultFileSystem>()
        val started = CompletableDeferred<Unit>()
        val finish = CompletableDeferred<Unit>()
        coEvery { fs.createZip(vault, root, "private.zip", any()) } coAnswers {
            started.complete(Unit)
            finish.await()
            Result.success(file("private.zip", 10))
        }
        val state = mounted()
        var reloads = 0
        val controller = OnlyFilesArchiveController(fs, state, this, { reloads++ }, { throw it })
        controller.create(listOf(file("source.txt", 0)), "private.zip", 1, "Created")
        runCurrent()
        assertTrue(started.isCompleted)

        state.value = OnlyFilesUiState(mountGeneration = 2)
        finish.complete(Unit)
        advanceUntilIdle()

        assertEquals(0, reloads)
        assertNull(state.value.completedArchive)
        assertNull(state.value.message)
        assertNull(state.value.archiveProgress)
    }

    @Test
    fun `cancelling before the writer starts releases the busy state`() = runTest {
        val fs = mockk<VaultFileSystem>()
        val state = mounted()
        val controller = OnlyFilesArchiveController(fs, state, this, { error("Unexpected reload") }, { throw it })

        controller.create(listOf(file("source.txt", 0)), "archive.zip", 1, "Created")
        assertTrue(state.value.busy)
        controller.cancel()
        advanceUntilIdle()

        coVerify(exactly = 0) { fs.createZip(any(), any(), any(), any()) }
        assertFalse(state.value.busy)
        assertNull(state.value.archiveProgress)
        assertNull(state.value.completedArchive)
    }

    @Test
    fun `cancel clears progress without announcing a completed archive`() = runTest {
        val source = file("source.txt", 0)
        val fs = mockk<VaultFileSystem>()
        val writerStarted = CompletableDeferred<Unit>()
        coEvery { fs.createZip(vault, root, "archive.zip", any()) } coAnswers {
            writerStarted.complete(Unit)
            kotlinx.coroutines.awaitCancellation()
        }
        val state = mounted()
        val controller = OnlyFilesArchiveController(fs, state, this, { error("Unexpected reload") }, { throw it })
        controller.create(listOf(source), "archive.zip", 1, "Created")
        runCurrent()
        assertTrue(writerStarted.isCompleted)

        controller.cancel()
        advanceUntilIdle()

        assertFalse(state.value.busy)
        assertNull(state.value.archiveProgress)
        assertNull(state.value.message)
    }

    @Test
    fun `TAR creation variants stream private files and folders through the shared writer`() = runTest {
        for (format in dev.qtremors.arcile.core.archive.TarArchiveWriter.formats) {
            val docs = folder("Docs")
            val parent = requireNotNull(docs.ref.directoryId)
            val source = file("Private.txt", 6, parent)
            val reader = MemoryReader("secret".encodeToByteArray())
            val fs = mockk<VaultFileSystem>()
            coEvery { fs.listDirectory(vault, parent, any()) } returns Result.success(VaultPage(listOf(source), null, 1))
            every { fs.openReader(source.ref) } returns Result.success(reader)
            val name = "Docs.${format.extension}"
            val output = ByteArrayOutputStream()
            coEvery { fs.createArchiveFile(vault, root, name, any(), any()) } coAnswers {
                arg<(OutputStream) -> Unit>(4)(output)
                Result.success(file(name, output.size().toLong()))
            }
            val state = mounted()
            OnlyFilesArchiveController(fs, state, this, {}, { throw it }).create(listOf(docs), name, 1, "Created")
            advanceUntilIdle()

            val result = requireNotNull(state.value.completedArchive)
            var contents: String? = null
            readVaultArchive(result, { Result.success(MemoryReader(output.toByteArray())) }, null,
                dev.qtremors.arcile.core.storage.domain.ArchiveNameEncoding.UTF_8) { archive ->
                assertEquals(listOf(listOf("Docs"), listOf("Docs", "Private.txt")), archive.entries.map { it.segments })
                contents = archive.entries.last().openStream().use { it.readBytes().decodeToString() }
            }
            assertEquals("secret", contents)
            assertTrue(reader.closed)
            assertEquals("Created", state.value.message)
            coVerify(exactly = 0) { fs.createZip(any(), any(), any(), any()) }
        }
    }

    @Test
    fun `TAR creation refuses archive passwords and clears their buffers`() = runTest {
        val fs = mockk<VaultFileSystem>()
        val state = mounted()
        val errors = mutableListOf<Throwable>()
        val password = "secret".toCharArray()
        OnlyFilesArchiveController(fs, state, this, {}, errors::add).create(
            listOf(file("Private.txt", 6)), "Result.tar", 1, "Created", password)
        advanceUntilIdle()
        assertEquals(1, errors.size)
        assertTrue(password.all { it == '\u0000' })
        assertNull(state.value.completedArchive)
        coVerify(exactly = 0) { fs.createArchiveFile(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `TAR source size changes abort creation and close the private reader`() = runTest {
        val source = file("Private.txt", 6)
        val reader = MemoryReader("changed bytes".encodeToByteArray())
        val fs = mockk<VaultFileSystem>()
        every { fs.openReader(source.ref) } returns Result.success(reader)
        coEvery { fs.createArchiveFile(vault, root, "Result.tar", any(), any()) } coAnswers {
            arg<(OutputStream) -> Unit>(4)(ByteArrayOutputStream())
            error("Changed sources cannot publish an archive")
        }
        val state = mounted()
        val errors = mutableListOf<Throwable>()
        OnlyFilesArchiveController(fs, state, this, {}, errors::add).create(listOf(source), "Result.tar", 1, "Created")
        advanceUntilIdle()
        assertEquals(1, errors.size)
        assertTrue(errors.single() is dev.qtremors.arcile.core.vault.domain.VaultFailure.SourceChanged)
        assertTrue(reader.closed)
        assertNull(state.value.completedArchive)
    }

    @Test
    fun `7z creation uses the shared encoder for every level and returns readable protected content`() = runTest {
        for (level in listOf(0, 1, -1, 9)) {
            val docs = folder("Docs")
            val parent = requireNotNull(docs.ref.directoryId)
            val source = file("Private.txt", 6, parent)
            val reader = MemoryReader("secret".encodeToByteArray())
            val fs = mockk<VaultFileSystem>()
            coEvery { fs.listDirectory(vault, parent, any()) } returns Result.success(VaultPage(listOf(source), null, 1))
            every { fs.openReader(source.ref) } returns Result.success(reader)
            val channel = SeekableInMemoryByteChannel()
            coEvery { fs.createSeekableArchiveFile(vault, root, "Result.7z", "application/x-7z-compressed", any()) } coAnswers {
                arg<(SeekableByteChannel) -> Unit>(4)(channel)
                assertTrue(channel.isOpen)
                Result.success(file("Result.7z", channel.size()))
            }
            val state = mounted()
            var reloads = 0
            val password = "secret".toCharArray()
            OnlyFilesArchiveController(fs, state, this, { reloads++ }, { throw it }).create(
                listOf(docs), "Result.7z", level, "Created", password)
            advanceUntilIdle()

            val result = requireNotNull(state.value.completedArchive)
            var contents: String? = null
            readVaultArchive(result, { Result.success(MemoryReader(channel.array().copyOf(channel.size().toInt()))) },
                "secret".toCharArray(), dev.qtremors.arcile.core.storage.domain.ArchiveNameEncoding.UTF_8) { archive ->
                assertEquals(listOf(listOf("Docs"), listOf("Docs", "Private.txt")), archive.entries.map { it.segments })
                contents = archive.entries.last().openStream().use { it.readBytes().decodeToString() }
            }
            assertEquals("secret", contents)
            assertTrue(reader.closed)
            assertTrue(password.all { it == '\u0000' })
            assertEquals("Created", state.value.message)
            assertEquals(1, reloads)
            assertFalse(state.value.busy)
            coVerify(exactly = 0) { fs.createZip(any(), any(), any(), any()) }
            coVerify(exactly = 0) { fs.createArchiveFile(any(), any(), any(), any(), any()) }
        }
    }

    @Test
    fun `changed sources abort 7z creation clear passwords and close readers`() = runTest {
        val source = file("Private.txt", 6)
        val reader = MemoryReader("changed bytes".encodeToByteArray())
        val fs = mockk<VaultFileSystem>()
        every { fs.openReader(source.ref) } returns Result.success(reader)
        coEvery { fs.createSeekableArchiveFile(vault, root, "Result.7z", any(), any()) } coAnswers {
            arg<(SeekableByteChannel) -> Unit>(4)(SeekableInMemoryByteChannel())
            error("Changed sources cannot publish an archive")
        }
        val state = mounted()
        val errors = mutableListOf<Throwable>()
        val password = "secret".toCharArray()
        OnlyFilesArchiveController(fs, state, this, {}, errors::add).create(listOf(source), "Result.7z", 1, "Created", password)
        advanceUntilIdle()
        assertEquals(1, errors.size)
        assertTrue(errors.single() is dev.qtremors.arcile.core.vault.domain.VaultFailure.SourceChanged)
        assertTrue(reader.closed)
        assertTrue(password.all { it == '\u0000' })
        assertNull(state.value.completedArchive)
        assertFalse(state.value.busy)
    }

    @Test
    fun `cancelling queued 7z work releases password and progress without completion feedback`() = runTest {
        val source = file("Private.txt", 6)
        val fs = mockk<VaultFileSystem>()
        val started = CompletableDeferred<Unit>()
        val pending = CompletableDeferred<Unit>()
        coEvery { fs.createSeekableArchiveFile(vault, root, "Result.7z", any(), any()) } coAnswers {
            started.complete(Unit)
            pending.await()
            error("Cancelled archives cannot publish")
        }
        val state = mounted()
        val password = "secret".toCharArray()
        val controller = OnlyFilesArchiveController(fs, state, this, {}, { throw it })
        controller.create(listOf(source), "Result.7z", 1, "Created", password)
        runCurrent()
        assertTrue(started.isCompleted)
        assertTrue(state.value.busy)
        controller.cancel()
        advanceUntilIdle()
        assertTrue(password.all { it == '\u0000' })
        assertNull(state.value.completedArchive)
        assertNull(state.value.archiveProgress)
        assertNull(state.value.message)
        assertFalse(state.value.busy)
    }

    @Test
    fun `7z completion from an old mount cannot replace a newer vault message`() = runTest {
        val fs = mockk<VaultFileSystem>()
        val released = CompletableDeferred<Unit>()
        coEvery { fs.createSeekableArchiveFile(vault, root, "Result.7z", any(), any()) } coAnswers {
            released.await()
            Result.success(file("Result.7z", 30))
        }
        val state = mounted()
        val password = "secret".toCharArray()
        OnlyFilesArchiveController(fs, state, this, {}, { throw it }).create(
            listOf(file("Private.txt", 6)), "Result.7z", 1, "Created", password)
        runCurrent()
        state.value = state.value.copy(mountGeneration = 2, message = "New mount")
        released.complete(Unit)
        advanceUntilIdle()
        assertEquals("New mount", state.value.message)
        assertNull(state.value.completedArchive)
        assertTrue(password.all { it == '\u0000' })
    }

    @Test
    fun `direct extraction uses protected streams selected paths and the submitted destination`() = runTest {
        val payload = zipBytes("Docs/One.txt" to "one", "Other.txt" to "other")
        val source = file("Source.zip", payload.size.toLong())
        val fs = mockk<VaultFileSystem>()
        val reader = MemoryReader(payload)
        every { fs.openReader(source.ref) } returns Result.success(reader)
        val target = DirectoryId.of("target")
        val removed = DirectoryId.of("old-folder")
        val extracted = mutableMapOf<List<String>, String>()
        coEvery { fs.extractArchiveInto(vault, target, any(), any(), any()) } coAnswers {
            for (entry in arg<List<VaultArchiveSource>>(2).filterIsInstance<VaultArchiveSource.File>()) {
                extracted[entry.path] = entry.open().use { it.readBytes().decodeToString() }
            }
            Result.success(VaultBatchResult(listOf(VaultItemResult("one", "One.txt", VaultItemOutcome.COMPLETED,
                removedFolders = setOf(removed)))))
        }
        val state = mounted()
        var reloads = 0
        val invalidated = mutableSetOf<DirectoryId>()
        val controller = OnlyFilesArchiveController(fs, state, this, { reloads++ }, { throw it }, { _, folders -> invalidated += folders })
        controller.extract(source, target, VaultZipSelection(listOf("Docs"), emptySet()), "Unused", intoExisting = true)
        state.first { !it.busy }
        assertEquals(mapOf(listOf("Docs", "One.txt") to "one"), extracted)
        assertTrue(reader.closed)
        assertEquals(setOf(removed), invalidated)
        assertEquals(1, reloads)
        assertEquals(OnlyFilesOperationDestination.VaultDirectory(vault, target), state.value.operationFeedback?.destination)
        assertNull(state.value.completedArchive)
        assertNull(state.value.message)
        coVerify(exactly = 0) { fs.extractArchiveToNamedFolder(any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `direct extraction shares conflict review and cancellation releases password and prompt`() = runTest {
        val payload = zipBytes("One.txt" to "one")
        val source = file("Source.zip", payload.size.toLong())
        val fs = mockk<VaultFileSystem>()
        val reader = MemoryReader(payload)
        every { fs.openReader(source.ref) } returns Result.success(reader)
        coEvery { fs.extractArchiveInto(vault, root, any(), any(), any()) } coAnswers {
            arg<VaultConflictResolver>(3).decide(VaultConflict("One.txt", "One.txt", false, false))
            error("Cancelled extraction must not complete")
        }
        val state = mounted()
        val password = "secret".toCharArray()
        val controller = OnlyFilesArchiveController(fs, state, this, {}, { throw it })
        controller.extract(source, root, VaultZipSelection(emptyList(), emptySet()), "Unused",
            password = password, intoExisting = true)
        state.first { it.pendingConflict != null }
        controller.cancel()
        state.first { !it.busy }
        assertTrue(reader.closed)
        assertTrue(password.all { it == '\u0000' })
        assertNull(state.value.pendingConflict)
        assertNull(state.value.operationFeedback)
    }

    @Test
    fun `direct extraction dispatches the chosen conflict decision and reports skips`() = runTest {
        val payload = zipBytes("One.txt" to "one")
        val source = file("Source.zip", payload.size.toLong())
        val fs = mockk<VaultFileSystem>()
        every { fs.openReader(source.ref) } returns Result.success(MemoryReader(payload))
        val decided = CompletableDeferred<VaultConflictDecision>()
        coEvery { fs.extractArchiveInto(vault, root, any(), any(), any()) } coAnswers {
            decided.complete(arg<VaultConflictResolver>(3).decide(VaultConflict("One.txt", "One.txt", false, false)))
            Result.success(VaultBatchResult(listOf(VaultItemResult("one", "One.txt", VaultItemOutcome.SKIPPED))))
        }
        val state = mounted()
        val controller = OnlyFilesArchiveController(fs, state, this, {}, { throw it })
        controller.extract(source, root, VaultZipSelection(emptyList(), emptySet()), "Unused", intoExisting = true)
        val prompt = requireNotNull(state.first { it.pendingConflict != null }.pendingConflict)
        controller.conflictReview.resolve(prompt.requestId, VaultConflictResponse(mapOf(0 to VaultConflictDecision.SKIP)))
        state.first { !it.busy }
        assertEquals(VaultConflictDecision.SKIP, decided.await())
        assertNull(state.value.operationFeedback?.destination)
        assertTrue(state.value.operationFeedback?.message is dev.qtremors.arcile.core.presentation.UiText.StringResource)
    }

    @Test
    fun `replacing the open archive clears its stale viewer and preserves completion feedback`() = runTest {
        val payload = zipBytes("Source.zip" to "replacement")
        val source = file("Source.zip", payload.size.toLong())
        val fs = mockk<VaultFileSystem>()
        every { fs.openReader(source.ref) } returnsMany listOf(Result.success(MemoryReader(payload)),
            Result.failure(VaultFailure.NodeNotFound(source.ref.nodeId)))
        coEvery { fs.extractArchiveInto(vault, root, any(), any(), any()) } returns Result.success(
            VaultBatchResult(listOf(VaultItemResult("source", "Source.zip", VaultItemOutcome.COMPLETED))))
        val state = mounted().apply { value = value.copy(viewer = source, viewerQueue = listOf(source)) }
        OnlyFilesArchiveController(fs, state, this, {}, { throw it }).extract(source, root,
            VaultZipSelection(emptyList(), emptySet()), "Unused", intoExisting = true)
        state.first { !it.busy }
        assertNull(state.value.viewer)
        assertTrue(state.value.viewerQueue.isEmpty())
        assertEquals(OnlyFilesOperationDestination.VaultDirectory(vault, root), state.value.operationFeedback?.destination)
    }

    private fun namedExtraction() = VaultArchiveDestinationResult(DirectoryId.of("Result"),
        VaultBatchResult(listOf(VaultItemResult("Result", "Result", VaultItemOutcome.COMPLETED))))

    private fun mounted() = MutableStateFlow(OnlyFilesUiState(
        selectedVaultId = vault, mountGeneration = 1L,
        directoryStack = listOf(VaultDirectoryCrumb(root, "", VaultPath.Root))
    ))

    private fun file(name: String, bytes: Long, parent: DirectoryId = root) = VaultNodeMetadata(
        VaultNodeRef(vault, NodeId.of(name), parent, VaultNodeCapabilities()),
        name, VaultNodeKind.FILE, bytes, 0L, 1L
    )

    private fun folder(name: String) = VaultNodeMetadata(
        VaultNodeRef(vault, NodeId.of(name), root, VaultNodeCapabilities(), directoryId = DirectoryId.of(name)),
        name, VaultNodeKind.DIRECTORY, 0L, 0L, 1L
    )

    private fun zipBytes(vararg files: Pair<String, String>): ByteArray {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            for ((path, content) in files) {
                zip.putNextEntry(ZipEntry(path))
                zip.write(content.encodeToByteArray())
                zip.closeEntry()
            }
        }
        return output.toByteArray()
    }

    private class MemoryReader(private val data: ByteArray) : VaultSeekableReader {
        var closed = false
            private set
        override val sizeBytes: Long get() = data.size.toLong()
        override fun readAt(position: Long, target: ByteArray, offset: Int, length: Int): Int {
            if (position >= data.size) return -1
            val count = minOf(length, data.size - position.toInt())
            data.copyInto(target, offset, position.toInt(), position.toInt() + count)
            return count
        }
        override fun close() {
            closed = true
        }
    }
}
