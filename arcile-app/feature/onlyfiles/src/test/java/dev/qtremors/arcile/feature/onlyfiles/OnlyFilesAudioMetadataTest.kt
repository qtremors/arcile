package dev.qtremors.arcile.feature.onlyfiles

import android.media.MediaMetadataRetriever
import dev.qtremors.arcile.core.vault.domain.DirectoryId
import dev.qtremors.arcile.core.vault.domain.NodeId
import dev.qtremors.arcile.core.vault.domain.VaultId
import dev.qtremors.arcile.core.vault.domain.VaultNodeCapabilities
import dev.qtremors.arcile.core.vault.domain.VaultNodeKind
import dev.qtremors.arcile.core.vault.domain.VaultNodeMetadata
import dev.qtremors.arcile.core.vault.domain.VaultNodeRef
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class OnlyFilesAudioMetadataTest {
    private val node = VaultNodeMetadata(
        VaultNodeRef(VaultId.of("vault"), NodeId.of("opaque"), DirectoryId.Root, VaultNodeCapabilities()),
        "fallback.mp3", VaultNodeKind.FILE, 1L, 1L, 1L
    )

    @Test
    fun `private metadata uses tags while retaining the opaque source identity`() {
        val tags = mapOf(
            MediaMetadataRetriever.METADATA_KEY_TITLE to "  Title  ",
            MediaMetadataRetriever.METADATA_KEY_ARTIST to "Artist",
            MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER to "2/12",
            MediaMetadataRetriever.METADATA_KEY_DURATION to "12345"
        )
        val track = vaultAudioTrack(node, tags::get)
        assertEquals("Title", track.displayTitle)
        assertEquals("Artist", track.artist)
        assertEquals(2, track.trackNumber)
        assertEquals(12_345L, track.durationMs)
        assertFalse(track.file.reference.contains(node.name))
    }

    @Test
    fun `missing and malformed metadata preserves playback with filename fallback`() {
        val track = vaultAudioTrack(node) { " " }
        assertEquals("fallback", track.displayTitle)
        assertEquals(0L, track.durationMs)
        assertEquals(null, track.artist)
        assertEquals(null, track.trackNumber)
    }
}
