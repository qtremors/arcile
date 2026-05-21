package dev.qtremors.arcile.data.util

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files
import kotlin.io.path.createTempDirectory

class PathSafetyTest {

    private lateinit var root: File
    private lateinit var outside: File

    @Before
    fun setup() {
        root = createTempDirectory(prefix = "path-safety-root").toFile()
        outside = createTempDirectory(prefix = "path-safety-outside").toFile()
    }

    @After
    fun teardown() {
        root.deleteRecursively()
        outside.deleteRecursively()
    }

    @Test
    fun `validatePath allows root and descendants`() {
        val nested = File(root, "Documents/report.txt").apply {
            parentFile!!.mkdirs()
            writeText("ok")
        }

        assertTrue(PathSafety.validatePath(root, listOf(root.absolutePath)).isSuccess)
        assertTrue(PathSafety.validatePath(nested, listOf(root.absolutePath)).isSuccess)
    }

    @Test
    fun `validatePath rejects sibling paths with matching prefixes`() {
        val sibling = File(root.parentFile, "${root.name}-sibling").apply { mkdirs() }

        try {
            val result = PathSafety.validatePath(sibling, listOf(root.absolutePath))

            assertFalse(result.isSuccess)
            assertTrue(result.exceptionOrNull() is SecurityException)
        } finally {
            sibling.deleteRecursively()
        }
    }

    @Test
    fun `validatePath rejects paths outside active storage roots`() {
        val outsideFile = File(outside, "secret.txt").apply { writeText("nope") }

        val result = PathSafety.validatePath(outsideFile, listOf(root.absolutePath))

        assertFalse(result.isSuccess)
        assertTrue(result.exceptionOrNull() is SecurityException)
    }

    @Test
    fun `pathWithAncestors returns canonical path up to longest matching root`() {
        val storageRoot = File(root, "storage").apply { mkdirs() }
        val nestedRoot = File(storageRoot, "primary").apply { mkdirs() }
        val target = File(nestedRoot, "Download/file.txt").apply {
            parentFile!!.mkdirs()
            writeText("ok")
        }

        val paths = PathSafety.pathWithAncestors(
            target.absolutePath,
            listOf(storageRoot.absolutePath, nestedRoot.absolutePath)
        )

        assertEquals(
            listOf(
                target.canonicalPath,
                target.parentFile!!.canonicalPath,
                nestedRoot.canonicalPath
            ),
            paths
        )
    }

    @Test
    fun `mutation policies reject symbolic link traversal`() {
        val realTarget = File(root, "real").apply { mkdirs() }
        val link = File(root, "link")
        val created = runCatching {
            Files.createSymbolicLink(link.toPath(), realTarget.toPath())
        }.isSuccess
        assumeTrue("Symbolic links are not available in this test environment", created)

        assertTrue(PathSafety.validatePath(link, listOf(root.absolutePath)).isSuccess)

        val result = PathSafety.validatePath(
            link,
            listOf(root.absolutePath),
            PathSafety.OperationPolicy.RECURSIVE_MUTATE
        )

        assertFalse(result.isSuccess)
        assertTrue(result.exceptionOrNull() is SecurityException)
    }
}
