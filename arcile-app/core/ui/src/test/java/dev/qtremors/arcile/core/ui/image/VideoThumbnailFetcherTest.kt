package dev.qtremors.arcile.core.ui.image

import android.content.ContentResolver
import android.content.Context
import android.content.ContextWrapper
import android.database.MatrixCursor
import android.graphics.Bitmap
import android.net.Uri
import android.util.Size
import androidx.test.core.app.ApplicationProvider
import coil3.toBitmap
import coil3.fetch.ImageFetchResult
import coil3.request.Options
import coil3.size.Size as CoilSize
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class VideoThumbnailFetcherTest {
    @Test
    fun `factory creates fetcher for common video extensions`() {
        val factory = VideoThumbnailFetcher.Factory()
        val options = mockk<Options> {
            every { context } returns ApplicationProvider.getApplicationContext()
        }

        assertNotNull(factory.create(coil3.Uri(scheme = "file", path = "clip.mp4"), options, mockk(relaxed = true)))
        assertNotNull(factory.create(coil3.Uri(scheme = "file", path = "clip.M2TS"), options, mockk(relaxed = true)))
        assertNotNull(factory.create(coil3.Uri(scheme = "file", path = "clip.mpg"), options, mockk(relaxed = true)))
        assertNull(factory.create(coil3.Uri(scheme = "file", path = "photo.jpg"), options, mockk(relaxed = true)))
        assertNotNull(VideoThumbnailFetcher.KeyFactory().create(ThumbnailKey.from(File("clip.m2ts")), options, mockk(relaxed = true)))
    }

    @Test
    fun `fetch uses content uri directly when thumbnail key has media store uri`() = runTest {
        val baseContext = ApplicationProvider.getApplicationContext<Context>()
        val expectedBitmap = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888)
        val resolver = mockk<ContentResolver>()
        var queryCount = 0
        var requestedUri: Uri? = null
        every {
            resolver.query(any(), any(), any<String>(), any<Array<String>>(), isNull())
        } answers {
            queryCount += 1
            MatrixCursor(arrayOf("_id"))
        }
        every { resolver.loadThumbnail(any(), any(), any()) } answers {
            requestedUri = arg(0)
            expectedBitmap
        }
        val context = ThumbnailContext(baseContext, resolver)
        val options = mockk<Options> {
            every { this@mockk.context } returns context
            every { size } returns CoilSize(320, 320)
        }

        val result = VideoThumbnailFetcher(
            File("/storage/emulated/0/Movies/clip.mp4"),
            options,
            contentUri = "content://media/external_primary/video/media/42"
        ).fetch() as ImageFetchResult

        assertEquals(0, queryCount)
        assertEquals("content://media/external_primary/video/media/42", requestedUri.toString())
        assertEquals(expectedBitmap, result.image.toBitmap())
    }

    @Test
    fun `fetch without content uri does not query media store by raw DATA`() = runTest {
        val baseContext = ApplicationProvider.getApplicationContext<Context>()
        val resolver = mockk<ContentResolver>()
        var queryCount = 0
        every {
            resolver.query(any(), any(), any<String>(), any<Array<String>>(), isNull())
        } answers {
            queryCount += 1
            MatrixCursor(arrayOf("_id"))
        }
        val context = ThumbnailContext(baseContext, resolver)
        val options = mockk<Options> {
            every { this@mockk.context } returns context
            every { size } returns CoilSize(320, 320)
        }

        val result = VideoThumbnailFetcher(File("/storage/emulated/0/Movies/clip.mp4"), options).fetch()

        assertEquals(null, result)
        assertEquals(0, queryCount)
    }

    private class ThumbnailContext(
        base: Context,
        private val resolver: ContentResolver
    ) : ContextWrapper(base) {
        override fun getApplicationContext(): Context = this
        override fun getContentResolver(): ContentResolver = resolver
    }
}
