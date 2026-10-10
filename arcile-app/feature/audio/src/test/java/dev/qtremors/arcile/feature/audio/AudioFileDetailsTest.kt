package dev.qtremors.arcile.feature.audio

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import androidx.test.core.app.ApplicationProvider
import dev.qtremors.arcile.core.storage.domain.AudioTrack
import dev.qtremors.arcile.core.storage.domain.FileModel
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockkConstructor
import io.mockk.unmockkConstructor
import io.mockk.verify
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AudioFileDetailsTest {
    @Before fun setUp() {
        mockkConstructor(MediaExtractor::class)
        every { anyConstructed<MediaExtractor>().setDataSource(any<String>()) } just Runs
        every { anyConstructed<MediaExtractor>().release() } just Runs
    }

    @After fun tearDown() {
        unmockkConstructor(MediaExtractor::class)
    }

    @Test fun `reads audio details and releases the extractor`() = runTest {
        val format = MediaFormat.createAudioFormat("audio/mp4a-latm", 48000, 2)
            .apply { setInteger(MediaFormat.KEY_BIT_RATE, 192000) }
        every { anyConstructed<MediaExtractor>().trackCount } returns 1
        every { anyConstructed<MediaExtractor>().getTrackFormat(0) } returns format

        assertEquals(AudioFileDetails(192000, 48000, 2), editor().fileDetails(track()))
        verify(exactly = 1) { anyConstructed<MediaExtractor>().release() }
    }

    @Test fun `unreadable audio returns no details and releases the extractor`() = runTest {
        every { anyConstructed<MediaExtractor>().setDataSource(any<String>()) } throws IOException()

        assertNull(editor().fileDetails(track()))
        verify(exactly = 1) { anyConstructed<MediaExtractor>().release() }
    }

    @Test fun `cancellation propagates and releases the extractor`() = runTest {
        val cancelled = CancellationException("cancelled")
        every { anyConstructed<MediaExtractor>().setDataSource(any<String>()) } throws cancelled

        try {
            editor().fileDetails(track())
            fail("Cancellation must propagate")
        } catch (actual: CancellationException) {
            assertEquals(cancelled.message, actual.message)
        }
        verify(exactly = 1) { anyConstructed<MediaExtractor>().release() }
    }

    private fun editor() = AudioTagEditor(ApplicationProvider.getApplicationContext<Context>())

    private fun track() = AudioTrack(
        file = FileModel(name = "song.mp3", reference = "/music/song.mp3", size = 1L,
            lastModified = 1L, isDirectory = false, extension = "mp3"),
        title = "Song"
    )
}
