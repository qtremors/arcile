package dev.qtremors.arcile.image

import androidx.test.core.app.ApplicationProvider
import coil.request.Options
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PdfThumbnailFetcherTest {
    @Test
    fun `factory only creates fetcher for pdf extension`() {
        val factory = PdfThumbnailFetcher.Factory()
        val options = mockk<Options> {
            every { context } returns ApplicationProvider.getApplicationContext()
        }

        assertNotNull(factory.create(File("report.pdf"), options, mockk(relaxed = true)))
        assertNotNull(factory.create(File("REPORT.PDF"), options, mockk(relaxed = true)))
        assertNull(factory.create(File("report.txt"), options, mockk(relaxed = true)))
    }

    @Test
    fun `fetch returns null for missing pdf`() = runTest {
        val tempFile = File("missing.pdf")
        val options = mockk<Options> {
            every { context } returns ApplicationProvider.getApplicationContext()
        }

        assertNull(PdfThumbnailFetcher(tempFile, options).fetch())
    }
}
