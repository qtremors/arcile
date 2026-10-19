package dev.qtremors.arcile.feature.onlyfiles

import android.content.Intent
import dev.qtremors.arcile.core.vault.domain.DirectoryId
import dev.qtremors.arcile.core.storage.domain.VaultThumbnailRequest
import dev.qtremors.arcile.core.storage.domain.FileCategories
import dev.qtremors.arcile.core.storage.domain.FileOpenBehavior
import dev.qtremors.arcile.core.storage.domain.FileOpenAsType
import dev.qtremors.arcile.core.storage.domain.FileOpenPreferences
import dev.qtremors.arcile.core.vault.domain.NodeId
import dev.qtremors.arcile.core.vault.domain.VaultId
import dev.qtremors.arcile.core.vault.domain.VaultNodeCapabilities
import dev.qtremors.arcile.core.vault.domain.VaultNodeKind
import dev.qtremors.arcile.core.vault.domain.VaultNodeMetadata
import dev.qtremors.arcile.core.vault.domain.VaultNodeRef
import dev.qtremors.arcile.core.vault.domain.VaultExternalGrant
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
    fun `vault file opening uses the shared extension override before category preference`() {
        val image = node("photo.png", "image/png")
        val externalImages = mapOf(FileCategories.Images.id.value to FileOpenBehavior.EXTERNAL)
        assertFalse(image.prefersExternalOpen(emptyMap()))
        assertTrue(image.prefersExternalOpen(externalImages))
        assertFalse(image.prefersExternalOpen(
            externalImages + (FileOpenPreferences.extensionKey("png") to FileOpenBehavior.ARCILE)
        ))
    }

    @Test
    fun `vault file opening respects compound extensions and grants only to capable files`() {
        val archive = node("Backup.TAR.GZ", null)
        val externalArchives = mapOf(FileOpenPreferences.extensionKey("tar.gz") to FileOpenBehavior.EXTERNAL)
        assertTrue(archive.prefersExternalOpen(externalArchives))
        val readOnly = archive.copy(ref = archive.ref.copy(
            capabilities = archive.ref.capabilities.copy(canOpenWith = false)
        ))
        assertFalse(readOnly.prefersExternalOpen(externalArchives))
    }

    @Test
    fun `open as uses the selected type on the existing read-only vault URI`() {
        val context = org.robolectric.RuntimeEnvironment.getApplication()
        val grant = VaultExternalGrant(
            "token", "content://arcile.vault/grant/token", "data.bin", "application/octet-stream", 12L, 10_000L
        )
        for (type in FileOpenAsType.entries) {
            val intent = buildVaultExternalIntent(context, ExternalAction.OPEN_WITH, listOf(grant), type)

            assertEquals(Intent.ACTION_VIEW, intent.action)
            assertEquals(type.mimeType, intent.type)
            assertEquals(grant.contentUri, intent.dataString)
            assertEquals(intent.data, intent.clipData?.getItemAt(0)?.uri)
            assertTrue(intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
            assertEquals(0, intent.flags and Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        }
        val normal = buildVaultExternalIntent(context, ExternalAction.OPEN_WITH, listOf(grant))
        assertEquals(grant.mimeType, normal.type)
    }

    @Test
    fun `file sizes stay compact and readable`() {
        val context = org.robolectric.RuntimeEnvironment.getApplication()
        assertEquals("42 B", formatBytes(context, 42L))
        assertEquals("2.0 kB", formatBytes(context, 2000L))
        assertEquals("3.0 MB", formatBytes(context, 3_000_000L))
        assertEquals("1.5 GB", formatBytes(context, 1_500_000_000L))
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

        assertEquals(session.files?.single()?.reference, session.items.single().mediaItem.mediaId)
        assertTrue(session.screenshotProtectionEnabled)
        val thumbnailData = requireNotNull(session.thumbnailData)
        val file = requireNotNull(session.files).single()
        assertEquals(VaultThumbnailRequest("vault", "movie.mp4", "root", 1L, 128), thumbnailData(file, 128))
        assertEquals(null, thumbnailData(file.copy(reference = "unknown"), 128))
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
