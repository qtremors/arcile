package dev.qtremors.arcile.core.ui.image

import androidx.test.core.app.ApplicationProvider
import coil3.request.Options
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

        assertNotNull(factory.create(coil3.Uri(scheme = "file", path = "report.pdf"), options, mockk(relaxed = true)))
        assertNotNull(factory.create(coil3.Uri(scheme = "file", path = "REPORT.PDF"), options, mockk(relaxed = true)))
        assertNull(factory.create(coil3.Uri(scheme = "file", path = "report.txt"), options, mockk(relaxed = true)))
        assertNotNull(PdfThumbnailFetcher.KeyFactory().create(ThumbnailKey.from(File("report.pdf")), options, mockk(relaxed = true)))
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
