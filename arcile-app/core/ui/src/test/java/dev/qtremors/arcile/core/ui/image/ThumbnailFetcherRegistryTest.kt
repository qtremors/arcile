package dev.qtremors.arcile.core.ui.image

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import coil3.ImageLoader
import coil3.request.Options
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ThumbnailFetcherRegistryTest {
    @Test fun `mapped files and Android file URIs use the custom thumbnail fetchers`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val loader = ImageLoader.Builder(context).diskCache(null).components {
            add(PdfThumbnailFetcher.Factory())
            add(VideoThumbnailFetcher.Factory())
            add(ApkIconFetcher.Factory())
            add(AudioAlbumArtFetcher.Factory())
        }.build()
        try {
            val options = Options(context)
            val expected = mapOf(
                "report.PDF" to PdfThumbnailFetcher::class.java,
                "clip.mp4" to VideoThumbnailFetcher::class.java,
                "bundle.xapk" to ApkIconFetcher::class.java,
                "song.mp3" to AudioAlbumArtFetcher::class.java
            )
            expected.forEach { (name, fetcherClass) ->
                val file = File(context.cacheDir, name)
                listOf(file, Uri.fromFile(file)).forEach { input ->
                    val mapped = loader.components.map(input, options)
                    val selected = loader.components.newFetcher(mapped, options, loader)?.first
                    assertEquals("Custom fetcher for $input", fetcherClass, selected?.javaClass)
                }
            }
        } finally {
            loader.shutdown()
        }
    }

    @Test fun `local thumbnail factories reject content and network URIs`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val loader = ImageLoader.Builder(context).diskCache(null).build()
        try {
            val options = Options(context)
            listOf("content", "https").forEach { scheme ->
                val uri = coil3.Uri(scheme = scheme, authority = "provider", path = "/report.pdf")
                assertNull(PdfThumbnailFetcher.Factory().create(uri, options, loader))
                assertNull(VideoThumbnailFetcher.Factory().create(uri.newBuilder().path("/clip.mp4").build(), options, loader))
                assertNull(ApkIconFetcher.Factory().create(uri.newBuilder().path("/app.apk").build(), options, loader))
                assertNull(AudioAlbumArtFetcher.Factory().create(uri.newBuilder().path("/song.mp3").build(), options, loader))
            }
        } finally {
            loader.shutdown()
        }
    }
}
