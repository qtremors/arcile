package dev.qtremors.arcile.feature.audio

import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AudioQueueDeletionTest {
    @Test
    fun `deleting current track follows shuffle order and preserves pause state`() {
        val player = player(current = "b", shuffledOrder = listOf(2, 1, 0), playing = false)

        removeAudioQueueItems(player, setOf("b"))

        verify { player.seekToDefaultPosition(0) }
        verify { player.playWhenReady = false }
    }

    @Test
    fun `deleting final track falls back to previous remaining track`() {
        val player = player(current = "c", shuffledOrder = listOf(0, 1, 2), playing = true)

        removeAudioQueueItems(player, setOf("c"))

        verify { player.seekToDefaultPosition(1) }
        verify { player.playWhenReady = true }
    }

    @Test
    fun `deleting another track does not restart the current track`() {
        val player = player(current = "b", shuffledOrder = listOf(0, 1, 2), playing = true)

        removeAudioQueueItems(player, setOf("a"))

        verify(exactly = 0) { player.seekToDefaultPosition(any()) }
        verify(exactly = 0) { player.playWhenReady = any() }
    }

    @Test
    fun `batch removal skips deleted successors and an empty queue has no successor`() {
        assertEquals("d", audioQueueSuccessor(listOf("a", "b", "c", "d"), "b", setOf("b", "c")))
        assertEquals(null, audioQueueSuccessor(listOf("a", "b"), "b", setOf("a", "b")))
        val player = player(current = "b", shuffledOrder = listOf(0, 1, 2), playing = true)
        removeAudioQueueItems(player, setOf("a", "b", "c"))
        assertEquals(0, player.mediaItemCount)
        verify(exactly = 0) { player.seekToDefaultPosition(any()) }
    }

    private fun player(current: String, shuffledOrder: List<Int>, playing: Boolean): Player {
        val items = listOf("a", "b", "c").map { MediaItem.Builder().setMediaId(it).build() }.toMutableList()
        val currentItem = items.first { it.mediaId == current }
        val timeline = mockk<Timeline> {
            every { getFirstWindowIndex(true) } returns shuffledOrder.first()
            every { getNextWindowIndex(any(), Player.REPEAT_MODE_OFF, true) } answers {
                shuffledOrder.getOrNull(shuffledOrder.indexOf(firstArg<Int>()) + 1) ?: C.INDEX_UNSET
            }
        }
        return mockk(relaxed = true) {
            every { mediaItemCount } answers { items.size }
            every { currentMediaItem } returns currentItem
            every { playWhenReady } returns playing
            every { shuffleModeEnabled } returns true
            every { currentTimeline } returns timeline
            every { getMediaItemAt(any()) } answers { items[firstArg<Int>()] }
            every { removeMediaItem(any()) } answers { items.removeAt(firstArg<Int>()); Unit }
        }
    }
}
