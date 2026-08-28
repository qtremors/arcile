package dev.qtremors.arcile.core.presentation

import android.content.Context
import android.content.res.Configuration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class FormatFileSizeTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
    }

    private fun createLocalizedContext(locale: Locale): Context {
        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)
        return context.createConfigurationContext(config)
    }

    @Test
    fun `formatFileSize returns zero bytes for non-positive sizes`() {
        val formattedZero = formatFileSize(context, 0)
        val formattedNegative = formatFileSize(context, -128)
        assertEquals(formattedZero, formattedNegative)
        assertTrue(formattedZero.contains("0"))
    }

    @Test
    fun `formatFileSize handles boundary and large byte sizes`() {
        val usContext = createLocalizedContext(Locale.US)
        assertEquals("0 B", formatFileSize(usContext, 0))
        assertEquals("1.0 kB", formatFileSize(usContext, 1000))
        assertEquals("1.5 MB", formatFileSize(usContext, 1_500_000))
        assertEquals("1.0 GB", formatFileSize(usContext, 1_000_000_000L))
        assertEquals("1.0 TB", formatFileSize(usContext, 1_000_000_000_000L))
    }

    @Test
    fun `formatFileSize respects comma decimal in German locale`() {
        val deContext = createLocalizedContext(Locale.GERMANY)
        val formatted = formatFileSize(deContext, 1_500_000)
        assertEquals("1,5 MB", formatted)
    }

    @Test
    fun `formatFileSize clamps negative values to zero`() {
        val usContext = createLocalizedContext(Locale.US)
        assertEquals("0 B", formatFileSize(usContext, -1024L))
        assertEquals("0 B", formatFileSize(usContext, Long.MIN_VALUE))
    }
}
