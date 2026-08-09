package dev.qtremors.arcile.core.ui

import android.content.Context
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
class PluralResourcesTest {

    @Test
    fun `shared counts format zero one two and many with Polish plural rules`() {
        val resources = polishContext().resources

        assertEquals("0 selected items", resources.getQuantityString(R.plurals.archive_create_summary, 0, 0))
        assertEquals("1 selected item", resources.getQuantityString(R.plurals.archive_create_summary, 1, 1))
        assertEquals("2 selected items", resources.getQuantityString(R.plurals.archive_create_summary, 2, 2))
        assertEquals("5 selected items", resources.getQuantityString(R.plurals.archive_create_summary, 5, 5))
    }

    private fun polishContext(): Context {
        val app = RuntimeEnvironment.getApplication()
        val configuration = Configuration(app.resources.configuration).apply {
            setLocale(Locale.forLanguageTag("pl"))
        }
        return app.createConfigurationContext(configuration)
    }
}
