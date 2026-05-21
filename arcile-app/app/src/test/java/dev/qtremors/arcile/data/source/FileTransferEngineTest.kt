package dev.qtremors.arcile.data.source

import dev.qtremors.arcile.domain.ConflictResolution
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

class FileTransferEngineTest {
    private lateinit var root: File

    @Before
    fun setup() {
        root = createTempDir(prefix = "transfer-engine-test")
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
}
