package dev.qtremors.arcile.core.vault.data

import dev.qtremors.arcile.core.vault.domain.VaultFailure
import dev.qtremors.arcile.core.vault.domain.VaultSeekableReader
import java.nio.file.Files
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class LocalVaultMoveTest {
    @get:Rule
    val temporary = TemporaryFolder()

    @Test
    fun `a verified copy permits removal only when explicitly requested`() = runTest {
        val file = temporary.newFile("original.txt").apply { writeText("original") }
        val move = LocalVaultMove.prepare(file.absolutePath, emptyList())
        try {
            val original = move.openOriginal().use { it.readBytes() }
            move.verifyCopy(MemoryReader(original))
            assertTrue(file.exists())
            move.removeOriginal()
            assertFalse(file.exists())
        } finally {
            move.clear()
        }
    }

    @Test
    fun `mismatched encrypted bytes leave the original untouched`() = runTest {
        val file = temporary.newFile("original.txt").apply { writeText("original") }
        val move = LocalVaultMove.prepare(file.absolutePath, emptyList())
        try {
            move.openOriginal().use { it.readBytes() }
            val error = runCatching { move.verifyCopy(MemoryReader("modified".encodeToByteArray())) }.exceptionOrNull()
            assertTrue(error is VaultFailure.IntegrityFailed)
            assertTrue(runCatching { move.removeOriginal() }.isFailure)
            assertEquals("original", file.readText())
        } finally {
            move.clear()
        }
    }

    @Test
    fun `same length content changes after verification prevent deletion`() = runTest {
        val file = temporary.newFile("original.txt").apply { writeText("original") }
        val originalTime = Files.getLastModifiedTime(file.toPath())
        val move = LocalVaultMove.prepare(file.absolutePath, emptyList())
        try {
            val original = move.openOriginal().use { it.readBytes() }
            move.verifyCopy(MemoryReader(original))
            file.writeText("modified")
            Files.setLastModifiedTime(file.toPath(), originalTime)

            assertTrue(runCatching { move.removeOriginal() }.exceptionOrNull() is VaultFailure.SourceChanged)
            assertEquals("modified", file.readText())
        } finally {
            move.clear()
        }
    }

    @Test
    fun `removal cannot proceed before the copy has been verified`() = runTest {
        val file = temporary.newFile("original.txt").apply { writeText("original") }
        val move = LocalVaultMove.prepare(file.absolutePath, emptyList())
        try {
            move.openOriginal().use { it.readBytes() }
            assertTrue(runCatching { move.removeOriginal() }.isFailure)
            assertEquals("original", file.readText())
        } finally {
            move.clear()
        }
    }

    @Test
    fun `directories and files inside registered vault storage cannot be removed by a move`() {
        val vault = temporary.newFolder("vault")
        val objectFile = vault.resolve("encrypted.bin").also { it.writeText("object") }
        assertTrue(runCatching {
            LocalVaultMove.prepare(objectFile.absolutePath, listOf(vault.canonicalFile.toPath()))
        }.exceptionOrNull() is VaultFailure.InvalidPath)
        assertTrue(runCatching {
            LocalVaultMove.prepare(vault.absolutePath, emptyList())
        }.exceptionOrNull() is VaultFailure.InvalidPath)
        assertEquals("object", objectFile.readText())
    }

    private class MemoryReader(private val bytes: ByteArray) : VaultSeekableReader {
        override val sizeBytes: Long get() = bytes.size.toLong()
        override fun readAt(position: Long, target: ByteArray, offset: Int, length: Int): Int {
            val start = position.toInt()
            val count = minOf(bytes.size - start, length)
            bytes.copyInto(target, offset, start, start + count)
            return count
        }
        override fun close() = Unit
    }
}
