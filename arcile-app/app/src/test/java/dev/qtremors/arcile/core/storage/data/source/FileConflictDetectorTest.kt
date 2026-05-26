package dev.qtremors.arcile.core.storage.data.source

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import kotlin.io.path.createTempDirectory

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FileConflictDetectorTest {

    private lateinit var root: File
    private lateinit var source: File
    private lateinit var destination: File
    private val detector = FileConflictDetector()

    @Before
    fun setup() {
        root = createTempDirectory(prefix = "conflict-detector").toFile()
        source = File(root, "source").apply { mkdirs() }
        destination = File(root, "destination").apply { mkdirs() }
    }

    @After
    fun teardown() {
        root.deleteRecursively()
    }

    @Test
    fun `detectCopyConflicts returns conflict models for existing destination names`() {
        val sourceFile = File(source, "report.txt").apply { writeText("new") }
        val existingFile = File(destination, "report.txt").apply { writeText("old") }

        val conflicts = detector.detectCopyConflicts(listOf(sourceFile.absolutePath), destination)

        assertEquals(1, conflicts.size)
        val conflict = conflicts.single()
        assertEquals(sourceFile.absolutePath, conflict.sourcePath)
        assertEquals(sourceFile.name, conflict.sourceFile.name)
        assertEquals(sourceFile.length(), conflict.sourceFile.size)
        assertEquals(existingFile.name, conflict.existingFile.name)
        assertEquals(existingFile.absolutePath, conflict.existingFile.absolutePath)
    }

    @Test
    fun `detectCopyConflicts ignores missing sources and non-conflicting files`() {
        val uniqueFile = File(source, "unique.txt").apply { writeText("only here") }
        val missingFile = File(source, "missing.txt")

        val conflicts = detector.detectCopyConflicts(
            listOf(uniqueFile.absolutePath, missingFile.absolutePath),
            destination
        )

        assertTrue(conflicts.isEmpty())
    }
}
