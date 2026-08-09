package dev.qtremors.arcile.feature.apk

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
class ApkPluralResourcesTest {

    @Test
    fun `package count formats zero one two and many with Polish plural rules`() {
        val app = RuntimeEnvironment.getApplication()
        val configuration = Configuration(app.resources.configuration).apply {
            setLocale(Locale.forLanguageTag("pl"))
        }
        val resources = app.createConfigurationContext(configuration).resources

        assertEquals("0 packages", resources.getQuantityString(R.plurals.apk_folder_count, 0, 0))
        assertEquals("1 package", resources.getQuantityString(R.plurals.apk_folder_count, 1, 1))
        assertEquals("2 packages", resources.getQuantityString(R.plurals.apk_folder_count, 2, 2))
        assertEquals("5 packages", resources.getQuantityString(R.plurals.apk_folder_count, 5, 5))
    }
}
