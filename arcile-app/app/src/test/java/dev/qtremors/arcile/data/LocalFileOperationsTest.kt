package dev.qtremors.arcile.data

import dev.qtremors.arcile.data.source.FileConflictNameGenerator
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

class LocalFileOperationsTest {

    @Test
    fun `generateKeepBothTarget appends numeric suffix to duplicate files`() {
        val testRoot = File(System.getProperty("java.io.tmpdir"), "test_ops_root").apply { mkdirs() }
        val targetDir = File(testRoot, "target").apply { mkdirs() }

        try {
            File(targetDir, "document.txt").createNewFile()

            val uniqueName1 = FileConflictNameGenerator.generateKeepBothTarget(targetDir, File(testRoot, "document.txt")).name

            assertEquals("document (1).txt", uniqueName1)

            File(targetDir, "document (1).txt").createNewFile()

            val uniqueName2 = FileConflictNameGenerator.generateKeepBothTarget(targetDir, File(testRoot, "document.txt")).name

            assertEquals("document (2).txt", uniqueName2)
        } finally {
            testRoot.deleteRecursively()
        }
    }

    @Test
    fun `generateKeepBothTarget handles files without extensions`() {
        val testRoot = File(System.getProperty("java.io.tmpdir"), "test_ops_root_2").apply { mkdirs() }
        val targetDir = File(testRoot, "target").apply { mkdirs() }

        try {
            File(targetDir, "README").createNewFile()

            val uniqueName = FileConflictNameGenerator.generateKeepBothTarget(targetDir, File(testRoot, "README")).name

            assertEquals("README (1)", uniqueName)
        } finally {
            testRoot.deleteRecursively()
        }
    }
}
