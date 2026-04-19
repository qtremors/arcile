package dev.qtremors.arcile.image

import android.content.ContentResolver
import android.content.Context
import android.content.ContextWrapper
import android.database.MatrixCursor
import android.graphics.Bitmap
import android.provider.MediaStore
import android.util.Size
import androidx.test.core.app.ApplicationProvider
import coil.fetch.DrawableResult
import coil.request.Options
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
class AudioAlbumArtFetcherTest {

    @Test
    fun `factory only creates fetcher for audio extensions`() {
        val factory = AudioAlbumArtFetcher.Factory()
        val options = mockk<Options> {
            every { context } returns ApplicationProvider.getApplicationContext()
        }

        assertNotNull(factory.create(File("track.mp3"), options, mockk(relaxed = true)))
        assertNotNull(factory.create(File("voice.m4a"), options, mockk(relaxed = true)))
        assertNull(factory.create(File("image.jpg"), options, mockk(relaxed = true)))
    }

    @Test
    fun `fetch uses media store thumbnail when indexed audio row is found`() = runTest {
        val baseContext = ApplicationProvider.getApplicationContext<Context>()
        val expectedBitmap = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888)
        val resolver = mockk<ContentResolver>()
        var queryCount = 0
        var lastRequestedSize: Size? = null
        every {
            resolver.query(any(), any(), any<String>(), any<Array<String>>(), isNull())
        } answers {
            queryCount += 1
            MatrixCursor(arrayOf(MediaStore.Audio.Media._ID)).apply {
                addRow(arrayOf(42L))
            }
        }
        every { resolver.loadThumbnail(any(), any(), any()) } answers {
            lastRequestedSize = arg(1)
            expectedBitmap
        }
        val context = ThumbnailContext(baseContext, resolver)
        val options = mockk<Options> {
            every { this@mockk.context } returns context
        }

        val result = AudioAlbumArtFetcher(File("/storage/emulated/0/Music/song.mp3"), options).fetch() as DrawableResult

        assertEquals(1, queryCount)
        assertEquals(Size(500, 500), lastRequestedSize)
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
