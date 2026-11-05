package dev.qtremors.arcile.feature.videoplayer

import android.app.Activity
import android.content.Intent
import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class VideoSessionActivityTest {
    private val video = Uri.parse("content://media/external/video/media/42")

    @Test
    fun `standalone viewer asks the notification to reopen itself with the same video`() {
        val source = Intent(Intent.ACTION_VIEW)
            .setDataAndType(video, "video/mp4")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_FORWARD_RESULT)
        val activity = Robolectric.buildActivity(Activity::class.java, source).get()

        val pendingIntent = requireNotNull(standaloneVideoSessionActivity(activity))
        val reopen = shadowOf(pendingIntent).savedIntent

        assertEquals(Activity::class.java.name, reopen.component?.className)
        assertEquals(Intent.ACTION_VIEW, reopen.action)
        assertEquals(video, reopen.data)
        assertEquals("video/mp4", reopen.type)
        assertTrue(reopen.flags and Intent.FLAG_ACTIVITY_SINGLE_TOP != 0)
        assertTrue(reopen.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
        // Launch flags from the original hand-off must not leak into the notification click.
        assertEquals(0, reopen.flags and Intent.FLAG_ACTIVITY_FORWARD_RESULT)
    }

    @Test
    fun `in-app viewer keeps the launcher as the notification target`() {
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val activity = Robolectric.buildActivity(Activity::class.java, launcher).get()

        assertNull(standaloneVideoSessionActivity(activity))
    }

    @Test
    fun `view intent without a video is not reopened`() {
        val activity = Robolectric.buildActivity(Activity::class.java, Intent(Intent.ACTION_VIEW)).get()

        assertNull(standaloneVideoSessionActivity(activity))
    }
}
