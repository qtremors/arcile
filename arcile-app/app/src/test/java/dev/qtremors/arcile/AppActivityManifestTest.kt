package dev.qtremors.arcile

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import dev.qtremors.arcile.feature.importing.SaveToArcileActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AppActivityManifestTest {

    @Test
    fun `manifest exposes send and send multiple share target`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val packageManager = context.packageManager
        val single = packageManager.queryIntentActivities(
            Intent(Intent.ACTION_SEND).setType("image/png"),
            0
        )
        val multiple = packageManager.queryIntentActivities(
            Intent(Intent.ACTION_SEND_MULTIPLE).setType("*/*"),
            0
        )

        assertTrue(single.any { it.activityInfo.name == SaveToArcileActivity::class.java.name })
        assertTrue(multiple.any { it.activityInfo.name == SaveToArcileActivity::class.java.name })
    }

    @Test
    fun `standalone image viewer resolves valid image view intent`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val uri = Uri.parse("content://example/photo")
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "image/png")
        }

        val target = resolveStandaloneImageTarget(context, intent)

        assertEquals(uri.toString(), target?.reference)
        assertEquals("image/png", target?.mimeType)
    }

    @Test
    fun `standalone image viewer rejects missing uri and unsupported mime`() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        assertEquals(null, resolveStandaloneImageTarget(context, Intent(Intent.ACTION_VIEW).setType("image/png")))
        assertEquals(
            null,
            resolveStandaloneImageTarget(
                context,
                Intent(Intent.ACTION_VIEW).setDataAndType(
                    Uri.parse("content://example/file.txt"),
                    "text/plain"
                )
            )
        )
    }

    @Test
    fun `manifest exposes standalone image viewer in separate process`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val matches = context.packageManager.queryIntentActivities(
            Intent(Intent.ACTION_VIEW).setDataAndType(
                Uri.parse("content://example/photo"),
                "image/jpeg"
            ),
            0
        )

        val activity = matches.first {
            it.activityInfo.name == ImageViewerActivity::class.java.name
        }.activityInfo
        assertEquals("${context.packageName}:imageviewer", activity.processName)
    }

    @Test
    fun `manifest exposes generic file opener for binary fallback`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val matches = context.packageManager.queryIntentActivities(
            Intent(Intent.ACTION_VIEW).setDataAndType(
                Uri.parse("content://example/file.bin"),
                "application/octet-stream"
            ),
            0
        )

        assertTrue(matches.any { it.activityInfo.name == FileOpenActivity::class.java.name })
    }
}
