package dev.qtremors.arcile

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SaveToArcileActivityTest {

    @Test
    fun `reader parses single and multiple stream uris without duplicates`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val first = Uri.parse("content://example/one")
        val second = Uri.parse("content://example/two")
        val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            putExtra(Intent.EXTRA_STREAM, arrayListOf(first, second, first))
            clipData = ClipData.newUri(context.contentResolver, "shared", second)
        }

        val files = IncomingShareReader.fromIntent(context, intent)

        assertEquals(listOf(first, second), files.map { it.uri })
    }

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
}
