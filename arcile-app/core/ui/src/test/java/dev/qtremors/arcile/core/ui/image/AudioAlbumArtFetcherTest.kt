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
class AudioAlbumArtFetcherTest {

    @Test
    fun `factory only creates fetcher for audio extensions`() {
        val factory = AudioAlbumArtFetcher.Factory()
        val options = mockk<Options> {
            every { context } returns ApplicationProvider.getApplicationContext()
        }

        assertNotNull(factory.create(coil3.Uri(scheme = "file", path = "track.mp3"), options, mockk(relaxed = true)))
        assertNotNull(factory.create(coil3.Uri(scheme = "file", path = "voice.m4a"), options, mockk(relaxed = true)))
        assertNull(factory.create(coil3.Uri(scheme = "file", path = "image.jpg"), options, mockk(relaxed = true)))
        assertNotNull(AudioAlbumArtFetcher.KeyFactory().create(ThumbnailKey.from(File("track.mp3")), options, mockk(relaxed = true)))
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

        val result = AudioAlbumArtFetcher(File("/storage/emulated/0/Music/song.mp3"), options).fetch()

        assertNull(result)
        assertEquals(0, queryCount)
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

        val result = AudioAlbumArtFetcher(
            File("/storage/emulated/0/Music/song.mp3"),
            options,
            contentUri = "content://media/external_primary/audio/media/42"
        ).fetch() as ImageFetchResult

        assertEquals(0, queryCount)
        assertEquals("content://media/external_primary/audio/media/42", requestedUri.toString())
        assertEquals(expectedBitmap, result.image.toBitmap())
    }

    @Test
    fun `fetch returns null when album art is unavailable`() = runTest {
        val baseContext = ApplicationProvider.getApplicationContext<Context>()
        val resolver = mockk<ContentResolver>()
        every {
            resolver.query(any(), any(), any<String>(), any<Array<String>>(), isNull())
        } returns MatrixCursor(arrayOf("_id"))
        val context = ThumbnailContext(baseContext, resolver)
        val options = mockk<Options> {
            every { this@mockk.context } returns context
            every { size } returns CoilSize(128, 128)
        }

        val result = AudioAlbumArtFetcher(File("/storage/emulated/0/Music/missing.mp3"), options).fetch()

        assertNull(result)
    }

    private class ThumbnailContext(
        base: Context,
        private val resolver: ContentResolver
    ) : ContextWrapper(base) {
        override fun getApplicationContext(): Context = this
        override fun getContentResolver(): ContentResolver = resolver
    }
}
