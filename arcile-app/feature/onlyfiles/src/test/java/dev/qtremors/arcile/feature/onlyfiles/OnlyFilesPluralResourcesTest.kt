package dev.qtremors.arcile.feature.onlyfiles

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
class OnlyFilesPluralResourcesTest {

    @Test
    fun `batch count formats zero one two and many with Polish plural rules`() {
        val app = RuntimeEnvironment.getApplication()
        val configuration = Configuration(app.resources.configuration).apply {
            setLocale(Locale.forLanguageTag("pl"))
        }
        val resources = app.createConfigurationContext(configuration).resources

        assertEquals("0 of 0 items processed", resources.getQuantityString(R.plurals.onlyfiles_batch_progress, 0, 0, 0))
        assertEquals("1 of 1 item processed", resources.getQuantityString(R.plurals.onlyfiles_batch_progress, 1, 1, 1))
        assertEquals("2 of 2 items processed", resources.getQuantityString(R.plurals.onlyfiles_batch_progress, 2, 2, 2))
        assertEquals("5 of 5 items processed", resources.getQuantityString(R.plurals.onlyfiles_batch_progress, 5, 5, 5))
    }
}
