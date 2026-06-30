package dev.qtremors.arcile.presentation.utils

import dev.qtremors.arcile.core.ui.externalfile.ExternalFileAccessHelper
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import io.mockk.coEvery
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.test.runTest
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
    fun `shareFiles rejects empty input`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()

        assertFalse(ShareHelper.shareFiles(context, emptyList()))
    }

    @Test
    fun `shareFiles returns false when no share uris can be created`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        mockkObject(ExternalFileAccessHelper)
        coEvery {
            ExternalFileAccessHelper.createShareTargets(
                context,
                any<List<ExternalFileAccessHelper.ExternalFileReference>>()
            )
        } returns emptyList()

        try {
            assertFalse(ShareHelper.shareFiles(context, listOf("/storage/emulated/0/file.txt")))
        } finally {
            unmockkObject(ExternalFileAccessHelper)
        }
    }

    @Test
    fun `shareFiles launches chooser intent when uris are available`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        mockkObject(ExternalFileAccessHelper)
        coEvery {
            ExternalFileAccessHelper.createShareTargets(
                context,
                any<List<ExternalFileAccessHelper.ExternalFileReference>>()
            )
        } returns listOf(
            ExternalFileAccessHelper.ShareTarget(
                uri = Uri.parse("content://dev.qtremors.arcile/test"),
                mimeType = "text/plain",
                displayName = "file.txt",
                sizeBytes = 12L
            )
        )

        try {
            assertTrue(ShareHelper.shareFiles(context, listOf("/storage/emulated/0/file.txt")))
            val startedIntent = shadowOf(context as android.app.Application).nextStartedActivity
            assertEquals(Intent.ACTION_CHOOSER, startedIntent.action)
            val sendIntent = startedIntent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
            assertEquals("file.txt", sendIntent?.getStringExtra(Intent.EXTRA_TITLE))
            assertEquals("file.txt", sendIntent?.clipData?.description?.label?.toString())
        } finally {
            unmockkObject(ExternalFileAccessHelper)
        }
    }
}
