package dev.qtremors.arcile.feature.audio

import android.view.WindowManager
import dev.qtremors.arcile.core.storage.domain.AudioTrack
import dev.qtremors.arcile.core.storage.domain.FileModel
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioPlayerPresentationTest {
    @Test
    fun `expansion waits until the mini player is anchored in a full-height parent`() {
        assertFalse(
            shouldStartAudioPlayerExpansion(
                presentation = AudioPlayerPresentation.PREPARING_EXPANSION,
                parentHeightPx = 120,
                miniPlayerHeightPx = 120
            )
        )

        assertTrue(
            shouldStartAudioPlayerExpansion(
                presentation = AudioPlayerPresentation.PREPARING_EXPANSION,
                parentHeightPx = 2400,
                miniPlayerHeightPx = 120
            )
        )
    }

    @Test
    fun `layout changes cannot restart expansion from another player state`() {
        AudioPlayerPresentation.entries
            .filterNot { it == AudioPlayerPresentation.PREPARING_EXPANSION }
            .forEach { presentation ->
                assertFalse(
                    shouldStartAudioPlayerExpansion(
                        presentation = presentation,
                        parentHeightPx = 2400,
                        miniPlayerHeightPx = 120
                    )
                )
            }
    }

    @Test
    fun `mini player swipe up expands and swipe down dismisses`() {
        assertEquals(
            AudioMiniPlayerGesture.EXPAND,
            resolveAudioMiniPlayerGesture(dragOffsetPx = -49f, thresholdPx = 48f)
        )
        assertEquals(
            AudioMiniPlayerGesture.DISMISS,
            resolveAudioMiniPlayerGesture(dragOffsetPx = 49f, thresholdPx = 48f)
        )
    }

    @Test
    fun `mini player gesture ignores short and invalid drags`() {
        assertEquals(
            AudioMiniPlayerGesture.NONE,
            resolveAudioMiniPlayerGesture(dragOffsetPx = 47f, thresholdPx = 48f)
        )
        assertEquals(
            AudioMiniPlayerGesture.NONE,
            resolveAudioMiniPlayerGesture(dragOffsetPx = -47f, thresholdPx = 48f)
        )
        assertEquals(
            AudioMiniPlayerGesture.NONE,
            resolveAudioMiniPlayerGesture(dragOffsetPx = 100f, thresholdPx = 0f)
        )
    }

    @Test
    fun `mini player fast fling settles by velocity`() {
        assertEquals(
            AudioMiniPlayerGesture.DISMISS,
            resolveAudioMiniPlayerGesture(12f, 48f, 1_400f, 800f)
        )
        assertEquals(
            AudioMiniPlayerGesture.EXPAND,
            resolveAudioMiniPlayerGesture(-12f, 48f, -1_400f, 800f)
        )
    }

    @Test
    fun `mini window yields focus and ime while expanded window restores them`() {
        val unrelatedFlag = WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        val mini = audioPlayerWindowFlags(unrelatedFlag, expanded = false)

        assertTrue(mini and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE != 0)
        assertTrue(mini and WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL != 0)
        assertTrue(mini and WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM != 0)
        assertTrue(mini and unrelatedFlag != 0)

        val expanded = audioPlayerWindowFlags(mini, expanded = true)
        assertFalse(expanded and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE != 0)
        assertFalse(expanded and WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL != 0)
        assertFalse(expanded and WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM != 0)
        assertTrue(expanded and unrelatedFlag != 0)
    }

    @Test
    fun `surrounding audio queue preserves the visible category order`() {
        val indexed = listOf("one.mp3", "two.mp3", "three.mp3").map(::audioTrack)

        val result = orderAudioTracksByContext(
            indexedTracks = indexed,
            contextPaths = listOf("/music/three.mp3", "/music/one.mp3", "/music/two.mp3")
        )

        assertEquals(
            listOf("three.mp3", "one.mp3", "two.mp3"),
            result.map { it.file.name }
        )
    }

    private fun audioTrack(name: String) = AudioTrack(
        file = FileModel(
            name = name,
            absolutePath = "/music/$name",
            size = 1L,
            lastModified = 1L,
            isDirectory = false,
            extension = "mp3",
            isHidden = false
        ),
        title = name
    )
}
