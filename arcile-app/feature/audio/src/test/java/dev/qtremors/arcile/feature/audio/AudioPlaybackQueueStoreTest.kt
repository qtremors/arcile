package dev.qtremors.arcile.feature.audio

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AudioPlaybackQueueStoreTest {
    private lateinit var context: Context

    @Before
    fun reset() {
        context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("audio_playback_queue", Context.MODE_PRIVATE)
            .edit().clear().commit()
    }

    @Test
    fun `queue and position restore without restarting playback`() {
        val first = MediaItem.Builder()
            .setMediaId("/first.mp3")
            .setUri("file:///first.mp3")
            .setMediaMetadata(MediaMetadata.Builder().setTitle("First").build())
            .build()
        val second = MediaItem.Builder()
            .setMediaId("/second.mp3")
            .setUri("file:///second.mp3")
            .setMediaMetadata(MediaMetadata.Builder().setTitle("Second").build())
            .build()
        val player = mockk<Player>()
        every { player.mediaItemCount } returns 2
        every { player.getMediaItemAt(0) } returns first
        every { player.getMediaItemAt(1) } returns second
        every { player.currentMediaItemIndex } returns 1
        every { player.currentPosition } returns 12_345L
        every { player.repeatMode } returns Player.REPEAT_MODE_ALL
        every { player.shuffleModeEnabled } returns true

        val store = AudioPlaybackQueueStore(context)
        store.saveQueue(player)
        val restored = store.read()
        assertNotNull(restored)
        val saved = restored!!
        assertEquals(listOf("/first.mp3", "/second.mp3"), saved.items.map { it.mediaId })
        assertEquals("Second", saved.items[1].mediaMetadata.title.toString())
        assertEquals(1, saved.index)
        assertEquals(12_345L, saved.positionMs)
        assertEquals(Player.REPEAT_MODE_ALL, saved.repeatMode)
        assertEquals(true, saved.shuffleEnabled)

        every { player.mediaItemCount } returns 0
        store.saveQueue(player)
        assertFalse(store.hasSavedQueue())
    }
}
