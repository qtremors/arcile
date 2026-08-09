package dev.qtremors.arcile.feature.audio

import android.content.res.Configuration
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AudioPluralResourcesTest {

    @Test
    fun `track count formats zero one two and many with Polish plural rules`() {
        val app = RuntimeEnvironment.getApplication()
        val configuration = Configuration(app.resources.configuration).apply {
            setLocale(Locale.forLanguageTag("pl"))
        }
        val resources = app.createConfigurationContext(configuration).resources

        assertEquals("0 tracks", resources.getQuantityString(R.plurals.audio_track_count, 0, 0))
        assertEquals("1 track", resources.getQuantityString(R.plurals.audio_track_count, 1, 1))
        assertEquals("2 tracks", resources.getQuantityString(R.plurals.audio_track_count, 2, 2))
        assertEquals("5 tracks", resources.getQuantityString(R.plurals.audio_track_count, 5, 5))
    }
}
