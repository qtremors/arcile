package dev.qtremors.arcile.feature.audio

import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AudioPlayerIntentTest {
    @Test
    fun `content backed launch retains internal path for surrounding queue resolution`() {
        val context: Context = RuntimeEnvironment.getApplication()

        val intent = createAudioPlayerIntent(
            context = context,
            path = "/storage/emulated/0/Music/song.mp3",
            contentUri = "content://media/external/audio/media/42",
            mimeType = "audio/mpeg"
        )

        assertEquals(
            "/storage/emulated/0/Music/song.mp3",
            intent.getStringExtra("dev.qtremors.arcile.feature.audio.extra.INTERNAL_PATH")
        )
    }
}
