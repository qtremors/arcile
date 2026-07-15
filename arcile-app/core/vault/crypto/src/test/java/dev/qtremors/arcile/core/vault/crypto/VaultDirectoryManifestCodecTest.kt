package dev.qtremors.arcile.core.vault.crypto

import dev.qtremors.arcile.core.vault.domain.DirectoryId
import dev.qtremors.arcile.core.vault.domain.NodeId
import dev.qtremors.arcile.core.vault.domain.VaultId
import dev.qtremors.arcile.core.vault.domain.VaultNodeKind
import dev.qtremors.arcile.core.vault.domain.VaultObjectId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.nio.file.Files

class VaultDirectoryManifestCodecTest {
    private val vaultId = VaultId.of("manifest-vault")
    private val directoryId = DirectoryId.of("root-directory")
    private val key = ByteArray(32) { (it + 7).toByte() }

    @Test
    fun `manifests page at 256 entries and newest authenticated root wins`() {
        val directory = FileVaultDirectory(Files.createTempDirectory("manifest-pages").toFile())
        val codec = VaultDirectoryManifestCodec()
        codec.createRoot(directory, vaultId, directoryId, key)
        val entries = (0 until 600).map(::fileEntry)
        val prepared = codec.prepare(vaultId, directoryId, key, 1L, entries)
        assertEquals(3, prepared.pages.size)
        codec.publish(directory, prepared)

        val opened = codec.read(directory, vaultId, directoryId, key)
        assertEquals(1L, opened.generation)
        assertEquals(600, opened.entries.size)
        assertEquals(entries.map { it.name }.sorted(), opened.entries.map { it.name }.sorted())
    }

    @Test
    fun `case-insensitive collisions and non-NFC names are rejected`() {
        val codec = VaultDirectoryManifestCodec()
        assertThrows(IllegalArgumentException::class.java) {
            codec.prepare(vaultId, directoryId, key, 1L, listOf(fileEntry(1, "Report"), fileEntry(2, "report")))
        }
        assertThrows(IllegalArgumentException::class.java) {
            codec.prepare(vaultId, directoryId, key, 1L, listOf(fileEntry(1, "e\u0301.txt")))
        }
    }

    @Test
    fun `reordered page is rejected by page index authentication`() {
        val directory = FileVaultDirectory(Files.createTempDirectory("manifest-reorder").toFile())
        val codec = VaultDirectoryManifestCodec()
        val prepared = codec.prepare(vaultId, directoryId, key, 0L, (0 until 300).map(::fileEntry))
        codec.publish(directory, prepared)
        val first = prepared.pages[0]
        val second = prepared.pages[1]
        directory.writeAtomic(first.relativePath, directory.readBytes(second.relativePath))

        assertThrows(Exception::class.java) { codec.read(directory, vaultId, directoryId, key) }
    }

    private fun fileEntry(index: Int, name: String = "file-${index.toString().padStart(4, '0')}.bin") =
        VaultManifestEntry(
            nodeId = NodeId.of("node-$index"),
            name = name,
            kind = VaultNodeKind.FILE,
            revision = 1L,
            modifiedAtMillis = index.toLong(),
            sizeBytes = index.toLong(),
            mimeType = "application/octet-stream",
            objectId = VaultObjectId.of(index.toString(16).padStart(64, '0')),
            childDirectoryId = null,
            protectedKey = ByteArray(32) { index.toByte() }
        )
}
