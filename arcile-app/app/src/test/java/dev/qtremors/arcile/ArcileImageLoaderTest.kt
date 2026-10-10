package dev.qtremors.arcile

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import coil3.request.Options
import coil3.toUri
import io.mockk.mockk
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ArcileImageLoaderTest {
    @Test fun `application loader invalidates raw file keys when files change`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val app = ArcileApp().apply { vaultThumbnailCache = mockk() }
        val loader = app.newImageLoader(context)
        val file = File.createTempFile("image-cache-", ".png", context.cacheDir)
        try {
            val options = Options(context, extras = loader.defaults.extras)
            val data = loader.components.map(file, options)
            assertTrue(file.setLastModified(100000L))
            val before = loader.components.key(data, options)
            assertTrue(file.setLastModified(200000L))
            val after = loader.components.key(data, options)
            assertNotNull(before)
            assertNotEquals(before, after)
        } finally {
            loader.shutdown()
            file.delete()
        }
    }

    @Test fun `application loader has no HTTP image fetcher`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val app = ArcileApp().apply { vaultThumbnailCache = mockk() }
        val loader = app.newImageLoader(context)
        try {
            val options = Options(context)
            listOf("http", "https").forEach { scheme ->
                val data = loader.components.map("$scheme://example.com/photo.jpg".toUri(), options)
                assertNull(loader.components.newFetcher(data, options, loader))
            }
        } finally {
            loader.shutdown()
        }
    }
}
