package dev.qtremors.arcile.core.storage.data.source

import dev.qtremors.arcile.core.storage.data.MutationJournal
import dev.qtremors.arcile.core.storage.data.SourceCleanupIncompleteException
import dev.qtremors.arcile.core.storage.domain.ConflictResolution
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeNoException
import org.junit.Before
import org.junit.Test
import java.io.File
import java.io.IOException
import java.nio.file.Files

class FileTransferEngineTest {
    private lateinit var root: File

    @Before
    fun setup() {
        root = kotlin.io.path.createTempDirectory(prefix = "transfer-engine-test").toFile()
    }

    @After
    fun teardown() {
        root.deleteRecursively()
    }

    @Test
    fun `cancelled copy removes staged destination`() = runTest {
        val source = File(root, "source.bin").apply {
            writeBytes(ByteArray(DEFAULT_BUFFER_SIZE * 2) { 7 })
        }
        val destination = File(root, "dest").apply { mkdirs() }
        val engine = FileTransferEngine(validatePath = { Result.success(Unit) })

        var cancelled = false
        try {
            engine.copyFiles(
                sourcePaths = listOf(source.absolutePath),
                destination = destination,
                resolutions = emptyMap(),
                onProgress = {
                    throw CancellationException("test cancellation")
                }
            )
        } catch (_: CancellationException) {
            cancelled = true
        }

        assertTrue(cancelled)
        assertFalse(File(destination, source.name).exists())
        assertTrue(destination.listFiles().orEmpty().none { it.name.contains("arcile-transfer") })
    }

    @Test
    fun `replace copy preserves existing target when staged promotion fails`() = runTest {
        val source = File(root, "source.txt").apply { writeText("new") }
        val destination = File(root, "dest").apply { mkdirs() }
        val existing = File(destination, "source.txt").apply { writeText("old") }
        val engine = FileTransferEngine(
            validatePath = { Result.success(Unit) },
            rename = { from, to ->
                if (from.name.contains("arcile-transfer") && to.name == existing.name) {
                    false
                } else {
                    from.renameTo(to)
                }
            }
        )

        val result = engine.copyFiles(
            sourcePaths = listOf(source.absolutePath),
            destination = destination,
            resolutions = mapOf(source.absolutePath to ConflictResolution.REPLACE)
        )

        assertTrue(result.isFailure)
        assertTrue(existing.exists())
        assertEquals("old", existing.readText())
        assertTrue(destination.listFiles().orEmpty().none { it.name.contains("arcile-transfer") || it.name.contains("arcile-replace") })
    }

    @Test
    fun `move fallback verifies copy before deleting source`() = runTest {
        val source = File(root, "source.txt").apply { writeText("move me") }
        val destination = File(root, "dest").apply { mkdirs() }
        var checksumCalls = 0
        val engine = FileTransferEngine(
            validatePath = { Result.success(Unit) },
            rename = { from, to ->
                if (from == source) false else from.renameTo(to)
            },
            checksumFile = { file ->
                checksumCalls += 1
                java.security.MessageDigest.getInstance("SHA-256").digest(file.readBytes())
            }
        )

        val result = engine.moveFiles(
            sourcePaths = listOf(source.absolutePath),
            destination = destination,
            resolutions = emptyMap()
        )

        assertTrue(result.isSuccess)
        assertFalse(source.exists())
        assertEquals("move me", File(destination, "source.txt").readText())
        assertTrue(checksumCalls >= 2)
    }

    @Test
    fun `partial source deletion preserves verified destination and records recovery`() = runTest {
        val source = File(root, "source-dir").apply { mkdirs() }
        File(source, "a.txt").writeText("a")
        File(source, "b.txt").writeText("b")
        File(source, "c.txt").writeText("c")
        val destination = File(root, "dest").apply { mkdirs() }
        val journal = RecordingMutationJournal()
        val engine = FileTransferEngine(
            validatePath = { Result.success(Unit) },
            rename = { from, to -> if (from == source) false else from.renameTo(to) },
            deleteSourceEntry = { file ->
                if (file.name == "b.txt") false else file.delete()
            },
            mutationJournal = journal
        )

        val result = engine.moveFiles(
            sourcePaths = listOf(source.absolutePath),
            destination = destination,
            resolutions = emptyMap()
        )

        val failure = result.exceptionOrNull() as SourceCleanupIncompleteException
        val target = File(destination, source.name)
        assertEquals("a", File(target, "a.txt").readText())
        assertEquals("b", File(target, "b.txt").readText())
        assertEquals("c", File(target, "c.txt").readText())
        assertFalse(File(source, "a.txt").exists())
        assertTrue(File(source, "b.txt").exists())
        assertFalse(File(source, "c.txt").exists())
        assertTrue(failure.remainingSourcePaths.contains(File(source, "b.txt").absolutePath))
        assertEquals(setOf(source.absolutePath to target.absolutePath), journal.pendingSourceCleanups)
    }

    @Test
    fun `move failure before source deletion rolls back staged destination`() = runTest {
        val source = File(root, "source.txt").apply { writeText("original") }
        val destination = File(root, "dest-before-delete").apply { mkdirs() }
        val journal = RecordingMutationJournal()
        val engine = FileTransferEngine(
            validatePath = { Result.success(Unit) },
            rename = { from, to -> if (from == source) false else from.renameTo(to) },
            afterCopy = { _, staging -> staging.appendText("corrupt") },
            mutationJournal = journal
        )

        val result = engine.moveFiles(
            sourcePaths = listOf(source.absolutePath),
            destination = destination,
            resolutions = emptyMap()
        )

        assertTrue(result.isFailure)
        assertEquals("original", source.readText())
        assertFalse(File(destination, source.name).exists())
        assertTrue(journal.pendingSourceCleanups.isEmpty())
    }

    @Test
    fun `move to target keeps verified copy when source cleanup is incomplete`() = runTest {
        val source = File(root, "direct-source.txt").apply { writeText("safe copy") }
        val target = File(root, "direct-target.txt")
        val journal = RecordingMutationJournal()
        val engine = FileTransferEngine(
            validatePath = { Result.success(Unit) },
            deleteSourceEntry = { false },
            mutationJournal = journal
        )

        val result = engine.moveToTarget(source, target, attemptRename = false)

        assertTrue(result.exceptionOrNull() is SourceCleanupIncompleteException)
        assertEquals("safe copy", source.readText())
        assertEquals("safe copy", target.readText())
        assertEquals(setOf(source.absolutePath to target.absolutePath), journal.pendingSourceCleanups)
    }

    @Test
    fun `copy uses metadata verification without checksum`() = runTest {
        val source = File(root, "source.txt").apply { writeText("metadata only") }
        val destination = File(root, "copy-dest").apply { mkdirs() }
        var checksumCalls = 0
        val engine = FileTransferEngine(
            validatePath = { Result.success(Unit) },
            checksumFile = { file ->
                checksumCalls += 1
                java.security.MessageDigest.getInstance("SHA-256").digest(file.readBytes())
            }
        )

        val result = engine.copyFiles(
            sourcePaths = listOf(source.absolutePath),
            destination = destination,
            resolutions = emptyMap()
        )

        assertTrue(result.isSuccess)
        assertEquals("metadata only", File(destination, "source.txt").readText())
        assertEquals(0, checksumCalls)
    }

    @Test
    fun `metadata verification detects size mismatches`() = runTest {
        val source = File(root, "source.txt").apply { writeText("original") }
        val destination = File(root, "bad-dest").apply { mkdirs() }
        val engine = FileTransferEngine(
            validatePath = { Result.success(Unit) },
            afterCopy = { _, target ->
                target.appendText("corruption")
            }
        )

        val result = engine.copyFiles(
            sourcePaths = listOf(source.absolutePath),
            destination = destination,
            resolutions = emptyMap()
        )

        assertTrue(result.isFailure)
        assertFalse(File(destination, "source.txt").exists())
    }

    @Test
    fun `directory metadata verification streams nested folders`() = runTest {
        val source = File(root, "source-dir").apply { mkdirs() }
        File(source, "a/b/c").mkdirs()
        File(source, "a/b/c/nested.txt").writeText("nested")
        val destination = File(root, "dir-dest").apply { mkdirs() }
        val progress = mutableListOf<String?>()
        val engine = FileTransferEngine(validatePath = { Result.success(Unit) })

        val result = engine.copyFiles(
            sourcePaths = listOf(source.absolutePath),
            destination = destination,
            resolutions = emptyMap(),
            onProgress = { progress += it.currentPath }
        )

        assertTrue(result.isSuccess)
        assertEquals("nested", File(destination, "source-dir/a/b/c/nested.txt").readText())
        assertTrue(progress.isNotEmpty())
    }

    @Test
    fun `directory copy handles a deeply nested tree without recursive calls`() = runTest {
        val source = File(root, "deep-source").apply { mkdirs() }
        var current = source
        repeat(50) {
            current = File(current, "d").apply { mkdir() }
        }
        File(current, "payload.txt").writeText("deep")
        val destination = File(root, "deep-dest").apply { mkdirs() }
        val engine = FileTransferEngine(validatePath = { Result.success(Unit) })

        val result = engine.copyFiles(listOf(source.absolutePath), destination, emptyMap())

        assertTrue(result.isSuccess)
        var copied = File(destination, source.name)
        repeat(50) { copied = File(copied, "d") }
        assertEquals("deep", File(copied, "payload.txt").readText())
    }

    @Test
    fun `directory copy rejects a symbolic link cycle and removes staging`() = runTest {
        val source = File(root, "cycle-source").apply { mkdirs() }
        try {
            Files.createSymbolicLink(File(source, "loop").toPath(), source.toPath())
        } catch (error: Exception) {
            assumeNoException(error)
        }
        val destination = File(root, "cycle-dest").apply { mkdirs() }
        val engine = FileTransferEngine(validatePath = { Result.success(Unit) })

        val result = engine.copyFiles(listOf(source.absolutePath), destination, emptyMap())

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message.orEmpty().contains("cycle"))
        assertTrue(destination.listFiles().orEmpty().none { it.name.contains("arcile-transfer") })
    }

    @Test
    fun `replacement move preserves original destination on staging failure or cancellation`() = runTest {
        for (cancel in listOf(false, true)) {
            val source = File(root, "source-$cancel.txt").apply { writeText("new source") }
            val destination = File(root, "dest-$cancel").apply { mkdirs() }
            val target = File(destination, source.name).apply { writeText("old destination") }
            val engine = FileTransferEngine(
                validatePath = { Result.success(Unit) },
                afterCopy = { _, _ ->
                    if (cancel) throw CancellationException("Injected cancellation")
                    else throw IOException("Injected write failure")
                }
            )
            try {
                assertTrue(engine.moveFiles(listOf(source.path), destination, mapOf(source.path to ConflictResolution.REPLACE)).isFailure)
                assertFalse(cancel)
            } catch (_: CancellationException) {
                assertTrue(cancel)
            }
            assertEquals("new source", source.readText())
            assertEquals("old destination", target.readText())
            assertTrue(destination.listFiles().orEmpty().none { it.name.contains("arcile-") })
        }
    }

    @Test
    fun `replacement move preserves restored destination after promotion failure`() = runTest {
        val source = File(root, "source.txt").apply { writeText("new source") }
        val destination = File(root, "destination").apply { mkdirs() }
        val target = File(destination, source.name).apply { writeText("old destination") }
        val engine = FileTransferEngine(
            validatePath = { Result.success(Unit) },
            rename = { from, to -> if (from.name.contains("arcile-transfer")) false else from.renameTo(to) }
        )
        assertTrue(engine.moveFiles(listOf(source.path), destination, mapOf(source.path to ConflictResolution.REPLACE)).isFailure)
        assertEquals("new source", source.readText())
        assertEquals("old destination", target.readText())
        assertTrue(destination.listFiles().orEmpty().none { it.name.contains("arcile-") })
    }

    @Test
    fun `occupied direct move target is never deleted on conflict rejection`() = runTest {
        val source = File(root, "source.txt").apply { writeText("source") }
        val target = File(root, "target.txt").apply { writeText("destination") }
        val engine = FileTransferEngine(validatePath = { Result.success(Unit) })
        assertTrue(engine.moveToTarget(source, target).isFailure)
        assertEquals("source", source.readText())
        assertEquals("destination", target.readText())
    }

    @Test
    fun `replacement move restores original when verification fails after publication`() = runTest {
        val source = File(root, "source.txt").apply { writeText("new source") }
        val destination = File(root, "destination").apply { mkdirs() }
        val target = File(destination, source.name).apply { writeText("old destination") }
        val engine = FileTransferEngine(
            validatePath = { Result.success(Unit) },
            checksumFile = { file ->
                if (file == target) byteArrayOf(0) else java.security.MessageDigest.getInstance("SHA-256").digest(file.readBytes())
            }
        )
        assertTrue(engine.moveFiles(listOf(source.path), destination, mapOf(source.path to ConflictResolution.REPLACE)).isFailure)
        assertEquals("new source", source.readText())
        assertEquals("old destination", target.readText())
    }

    @Test
    fun `replacement move retains original backup after partial source cleanup`() = runTest {
        val source = File(root, "source").apply { mkdirs() }
        File(source, "a.txt").writeText("a")
        File(source, "b.txt").writeText("b")
        val destination = File(root, "destination").apply { mkdirs() }
        File(destination, source.name).mkdirs()
        File(destination, "${source.name}/old.txt").writeText("old destination")
        val engine = FileTransferEngine(
            validatePath = { Result.success(Unit) },
            deleteSourceEntry = { file -> if (file.name == "b.txt") false else file.delete() }
        )
        assertTrue(engine.moveFiles(listOf(source.path), destination, mapOf(source.path to ConflictResolution.REPLACE)).isFailure)
        assertEquals("a", File(destination, "${source.name}/a.txt").readText())
        assertEquals("b", File(destination, "${source.name}/b.txt").readText())
        val backup = destination.listFiles().orEmpty().single { it.name.contains("arcile-replace") }
        assertEquals("old destination", File(backup, "old.txt").readText())
        assertEquals("b", File(source, "b.txt").readText())
    }

    @Test
    fun `replacement copy rejects same sized corruption before touching original destination`() = runTest {
        val source = File(root, "source.txt").apply { writeText("new") }
        val destination = File(root, "destination").apply { mkdirs() }
        val target = File(destination, source.name).apply { writeText("old") }
        val engine = FileTransferEngine(
            validatePath = { Result.success(Unit) },
            afterCopy = { _, staging -> staging.writeText("bad"); staging.setLastModified(source.lastModified()) }
        )
        assertTrue(engine.copyFiles(listOf(source.path), destination, mapOf(source.path to ConflictResolution.REPLACE)).isFailure)
        assertEquals("old", target.readText())
        assertEquals("new", source.readText())
        assertTrue(destination.listFiles().orEmpty().none { it.name.contains("arcile-") })
    }

    private class RecordingMutationJournal : MutationJournal {
        val pendingSourceCleanups = mutableSetOf<Pair<String, String>>()

        override fun recordTemporaryPath(path: String) = Unit
        override fun forgetTemporaryPath(path: String) = Unit
        override fun recordTrashFallback(sourcePath: String, payloadPath: String, metadataPath: String) = Unit
        override fun forgetTrashFallback(payloadPath: String, metadataPath: String) = Unit
        override fun recordSourceCleanup(sourcePath: String, destinationPath: String) {
            pendingSourceCleanups += sourcePath to destinationPath
        }
        override fun forgetSourceCleanup(sourcePath: String, destinationPath: String) {
            pendingSourceCleanups -= sourcePath to destinationPath
        }
        override suspend fun cleanupAbandonedMutations() = Unit
    }
}
