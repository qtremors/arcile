package dev.qtremors.arcile.feature.onlyfiles

import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import dev.qtremors.arcile.core.ui.video.VideoPlaybackItem
import dev.qtremors.arcile.core.vault.domain.DirectoryId
import dev.qtremors.arcile.core.vault.domain.NodeId
import dev.qtremors.arcile.core.vault.domain.VaultId
import dev.qtremors.arcile.core.vault.domain.VaultNodeCapabilities
import dev.qtremors.arcile.core.vault.domain.VaultNodeKind
import dev.qtremors.arcile.core.vault.domain.VaultNodeMetadata
import dev.qtremors.arcile.core.vault.domain.VaultNodeRef
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class OnlyFilesMediaQueueTest {
    @Test
    fun `undo move refreshes the protected parent reference while preserving the playing identity`() {
        val first = node("first.mp4")
        val second = node("second.mp4")
        val queue = OnlyFilesVideoQueue(listOf(first, second), ::video)
        val unchanged = queue.sourceUpdates.value.items[1]
        val restored = first.copy(ref = first.ref.copy(parentId = DirectoryId.of("original-parent")))

        queue.applyUndo(dev.qtremors.arcile.core.vault.domain.VaultUndoResult(updated = listOf(restored)))

        assertEquals(restored.ref, queue.refsByOpaqueId[first.ref.nodeId.value])
        assertEquals(restored, queue.node(first.toSharedFileModel().reference))
        assertEquals(first.toSharedFileModel().reference, queue.sourceUpdates.value.items.first().mediaItem.mediaId)
        assertSame(unchanged, queue.sourceUpdates.value.items[1])
    }

    @Test
    fun `undo creation removes a deleted video from the playback queue and ignores stale pages`() {
        val created = node("created.mp4")
        val queue = OnlyFilesVideoQueue(listOf(created), ::video)
        queue.applyUndo(dev.qtremors.arcile.core.vault.domain.VaultUndoResult(removedNodeIds = setOf(created.ref.nodeId)))
        queue.merge(listOf(created))
        assertTrue(queue.sourceUpdates.value.items.isEmpty())
        assertTrue(queue.refsByOpaqueId.isEmpty())
    }
    @Test
    fun `video queue retains loaded pages and item callbacks across a first page refresh`() {
        val first = node("first.mp4")
        val second = node("second.mp4")
        val third = node("third.mp4")
        val queue = OnlyFilesVideoQueue(listOf(first, second), ::video)
        val unchanged = queue.sourceUpdates.value.items[1]

        queue.merge(listOf(third))
        queue.overlay(true)
        queue.merge(listOf(first.copy(name = "renamed.mp4")))

        assertEquals(listOf("renamed.mp4", "second.mp4", "third.mp4"), queue.sourceUpdates.value.files.map { it.name })
        assertSame(unchanged, queue.sourceUpdates.value.items[1])
        assertTrue(queue.sourceUpdates.value.overlayVisible)
        assertEquals(3, queue.refsByOpaqueId.size)
    }

    @Test
    fun `deleted video cannot return from a stale page or another vault`() {
        val first = node("first.mp4")
        val queue = OnlyFilesVideoQueue(listOf(first), ::video)
        queue.remove(setOf(first.ref.nodeId.value))
        queue.merge(listOf(first, node("different.mp4").let {
            it.copy(ref = it.ref.copy(vaultId = VaultId.of("other-vault")))
        }))

        assertTrue(queue.sourceUpdates.value.files.isEmpty())
        assertTrue(queue.refsByOpaqueId.isEmpty())
    }

    @Test
    fun `audio queue edits survive new pages without changing files`() {
        val first = node("first.mp3")
        val second = node("second.mp3")
        val third = node("third.mp3")
        val queue = OnlyFilesAudioQueue(listOf(first, second))
        queue.move(second.toSharedFileModel().reference, -1)
        queue.remove(first.toSharedFileModel().reference)
        queue.merge(listOf(first, second.copy(name = "renamed.mp3"), third, node("video.mp4")))

        assertEquals(listOf("renamed.mp3", "third.mp3"), queue.nodes.value.map { it.name })
        assertFalse(queue.refs.containsKey(first.ref.nodeId.value))
        queue.clear()
        queue.merge(listOf(first, second, third))
        assertTrue(queue.nodes.value.isEmpty())
        assertTrue(queue.refs.isEmpty())
    }

    @Test
    fun `audio playlist starts at the selected track and keeps its playback position when pages append`() {
        val first = node("first.mp3")
        val selected = node("selected.mp3")
        val third = node("third.mp3")
        val playlist = Playlist()
        val reference = selected.toSharedFileModel().reference

        syncVaultAudioPlaylist(playlist.player, listOf(first, selected), reference)
        assertEquals(reference, playlist.current)
        playlist.position = 12_345
        syncVaultAudioPlaylist(playlist.player, listOf(first, selected, third), reference)

        assertEquals(reference, playlist.current)
        assertEquals(12_345L, playlist.position)
        verify(exactly = 0) { playlist.player.clearMediaItems() }
        verify(exactly = 1) { playlist.player.seekTo(any<Int>(), any<Long>()) }
    }

    @Test
    fun `moving and removing queued tracks preserve a surviving current track`() {
        val first = node("first.mp3")
        val selected = node("selected.mp3")
        val third = node("third.mp3")
        val playlist = Playlist()
        val reference = selected.toSharedFileModel().reference
        syncVaultAudioPlaylist(playlist.player, listOf(first, selected, third), reference)
        playlist.position = 12_345

        syncVaultAudioPlaylist(playlist.player, listOf(third, selected))

        assertEquals(listOf(third, selected).map { it.toSharedFileModel().reference }, playlist.items.map { it.mediaId })
        assertEquals(reference, playlist.current)
        assertEquals(12_345L, playlist.position)
        verify(exactly = 1) { playlist.player.seekTo(any<Int>(), any<Long>()) }
    }

    private class Playlist {
        val items = mutableListOf<MediaItem>()
        var current: String? = null
        var position = 0L
        val player = mockk<Player>(relaxed = true)
        init {
            every { player.mediaItemCount } answers { items.size }
            every { player.currentMediaItem } answers { items.firstOrNull { it.mediaId == current } }
            every { player.currentMediaItemIndex } answers { items.indexOfFirst { it.mediaId == current }.coerceAtLeast(0) }
            every { player.getMediaItemAt(any()) } answers { items[firstArg()] }
            every { player.addMediaItem(any<Int>(), any<MediaItem>()) } answers {
                val item = secondArg<MediaItem>()
                items.add(firstArg<Int>(), item)
                if (current == null) current = item.mediaId
            }
            every { player.moveMediaItem(any(), any()) } answers {
                items.add(secondArg(), items.removeAt(firstArg()))
            }
            every { player.removeMediaItem(any()) } answers { items.removeAt(firstArg<Int>()); Unit }
            every { player.seekTo(any<Int>(), any<Long>()) } answers {
                current = items[firstArg<Int>()].mediaId
                position = secondArg()
            }
        }
    }

    private fun video(node: VaultNodeMetadata) =
        VideoPlaybackItem(MediaItem.Builder().setMediaId(node.toSharedFileModel().reference).build(), node.name)

    private fun node(name: String) = VaultNodeMetadata(
        VaultNodeRef(VaultId.of("vault"), NodeId.of(name), DirectoryId.Root, VaultNodeCapabilities()),
        name, VaultNodeKind.FILE, 1L, 1L, 1L
    )
}
