package dev.qtremors.arcile.presentation.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ShareHelperTest {

    @Test
    fun `shareFiles rejects empty input`() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        assertFalse(ShareHelper.shareFiles(context, emptyList()))
    }

    @Test
    fun `shareFiles returns false when no share uris can be created`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        mockkObject(ExternalFileAccessHelper)
        every { ExternalFileAccessHelper.createShareUris(context, any()) } returns emptyList()

        try {
            assertFalse(ShareHelper.shareFiles(context, listOf("/storage/emulated/0/file.txt")))
        } finally {
            unmockkObject(ExternalFileAccessHelper)
        }
    }

    @Test
    fun `shareFiles launches chooser intent when uris are available`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        mockkObject(ExternalFileAccessHelper)
        every { ExternalFileAccessHelper.createShareUris(context, any()) } returns listOf(Uri.parse("content://dev.qtremors.arcile/test"))

        try {
            assertTrue(ShareHelper.shareFiles(context, listOf("/storage/emulated/0/file.txt")))
            val startedIntent = shadowOf(context as android.app.Application).nextStartedActivity
            assertEquals(Intent.ACTION_CHOOSER, startedIntent.action)
        } finally {
            unmockkObject(ExternalFileAccessHelper)
        }
    }
}
