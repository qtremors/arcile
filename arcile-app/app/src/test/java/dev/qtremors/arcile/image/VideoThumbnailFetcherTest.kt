package dev.qtremors.arcile.image

import android.content.ContentResolver
import android.content.Context
import android.content.ContextWrapper
import android.database.MatrixCursor
import android.graphics.Bitmap
import android.net.Uri
import android.provider.MediaStore
import android.util.Size
import androidx.test.core.app.ApplicationProvider
import coil.fetch.DrawableResult
import coil.request.Options
import coil.size.Size as CoilSize
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class VideoThumbnailFetcherTest {
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
            MatrixCursor(arrayOf(MediaStore.Video.Media._ID))
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
        ).fetch() as DrawableResult

        assertEquals(0, queryCount)
        assertEquals("content://media/external_primary/video/media/42", requestedUri.toString())
        assertEquals(expectedBitmap, (result.drawable as android.graphics.drawable.BitmapDrawable).bitmap)
    }

    private class ThumbnailContext(
        base: Context,
        private val resolver: ContentResolver
    ) : ContextWrapper(base) {
        override fun getApplicationContext(): Context = this
        override fun getContentResolver(): ContentResolver = resolver
    }
}
