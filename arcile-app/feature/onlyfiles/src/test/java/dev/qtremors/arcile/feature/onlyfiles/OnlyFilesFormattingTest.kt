package dev.qtremors.arcile.feature.onlyfiles

import dev.qtremors.arcile.core.vault.domain.DirectoryId
import dev.qtremors.arcile.core.vault.domain.NodeId
import dev.qtremors.arcile.core.vault.domain.VaultId
import dev.qtremors.arcile.core.vault.domain.VaultNodeCapabilities
import dev.qtremors.arcile.core.vault.domain.VaultNodeKind
import dev.qtremors.arcile.core.vault.domain.VaultNodeMetadata
import dev.qtremors.arcile.core.vault.domain.VaultNodeRef
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class OnlyFilesFormattingTest {
    @Test
    fun `media recognition uses mime type and safe extension fallback`() {
        assertTrue(node("photo.HEIC", null).isViewableImage())
        assertTrue(node("opaque", "image/png").isViewableImage())
        assertTrue(node("movie.mkv", null).isViewableVideo())
        assertFalse(node("notes.txt", "text/plain").isViewableImage())
        assertFalse(node("notes.txt", "text/plain").isViewableVideo())
    }

    @Test
    fun `file sizes stay compact and readable`() {
        assertEquals("42 B", formatBytes(42L))
        assertEquals("2 KB", formatBytes(2048L))
        assertEquals("3 MB", formatBytes(3L * 1024L * 1024L))
        assertEquals("1.5 GB", formatBytes(3L * 1024L * 1024L * 1024L / 2L))
    }

    @Test
    fun `vault video playback identity matches the displayed file`() {
        val video = node("movie.mp4", "video/mp4")

        val session = createVaultVideoPlaybackSession(
            nodes = listOf(video),
            vaultId = VaultId.of("vault"),
            selectedNode = video,
            screenshotProtectionEnabled = true,
            openReader = { Result.failure(IllegalStateException("Not opened by this test")) }
        )

        assertEquals(session.files?.single()?.absolutePath, session.items.single().mediaItem.mediaId)
        assertTrue(session.screenshotProtectionEnabled)
    }

    private fun node(name: String, mimeType: String?) = VaultNodeMetadata(
        ref = VaultNodeRef(
            VaultId.of("vault"), NodeId.of(name), DirectoryId.Root, VaultNodeCapabilities()
        ),
        name = name,
        kind = VaultNodeKind.FILE,
        sizeBytes = 0L,
        modifiedAtMillis = 0L,
        revision = 1L,
        mimeType = mimeType
    )
}
