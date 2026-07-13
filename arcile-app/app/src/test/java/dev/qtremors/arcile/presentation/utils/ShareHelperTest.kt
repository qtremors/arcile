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
import org.junit.Assert.assertNotNull
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
            assertEquals(Intent.ACTION_SEND, sendIntent?.action)
            assertEquals(
                Uri.parse("content://dev.qtremors.arcile/test"),
                sendIntent?.getParcelableExtra(Intent.EXTRA_STREAM)
            )
            assertEquals("file.txt", sendIntent?.getStringExtra(Intent.EXTRA_TITLE))
            assertEquals("file.txt", sendIntent?.clipData?.description?.label?.toString())
            assertTrue(sendIntent?.flags?.and(Intent.FLAG_GRANT_READ_URI_PERMISSION) != 0)
            assertTrue(startedIntent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
        } finally {
            unmockkObject(ExternalFileAccessHelper)
        }
    }

    @Test
    fun `multiple files retain every stream uri and grant`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val targets = listOf("one", "two", "three").map { name ->
            ExternalFileAccessHelper.ShareTarget(
                uri = Uri.parse("content://dev.qtremors.arcile/$name.pdf"),
                mimeType = "application/pdf",
                displayName = "$name.pdf",
                sizeBytes = 12L
            )
        }
        mockkObject(ExternalFileAccessHelper)
        coEvery {
            ExternalFileAccessHelper.createShareTargets(
                context,
                any<List<ExternalFileAccessHelper.ExternalFileReference>>()
            )
        } returns targets

        try {
            assertTrue(
                ShareHelper.shareFiles(
                    context,
                    listOf("/one.pdf", "/two.pdf", "/three.pdf")
                )
            )
            val chooser = shadowOf(context as android.app.Application).nextStartedActivity
            val sendIntent = chooser.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
            assertNotNull(sendIntent)
            assertEquals(Intent.ACTION_SEND_MULTIPLE, sendIntent?.action)
            assertEquals("application/pdf", sendIntent?.type)
            assertEquals(
                targets.map { it.uri },
                sendIntent?.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)
            )
            assertEquals(3, sendIntent?.clipData?.itemCount)
            assertEquals(targets[2].uri, sendIntent?.clipData?.getItemAt(2)?.uri)
            assertTrue(sendIntent?.flags?.and(Intent.FLAG_GRANT_READ_URI_PERMISSION) != 0)
            assertTrue(chooser.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
        } finally {
            unmockkObject(ExternalFileAccessHelper)
        }
    }

    @Test
    fun `multi share does not launch a partial selection when preparation fails`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        mockkObject(ExternalFileAccessHelper)
        coEvery {
            ExternalFileAccessHelper.createShareTargets(
                context,
                any<List<ExternalFileAccessHelper.ExternalFileReference>>()
            )
        } throws IllegalArgumentException("Unable to prepare every selected file for sharing")

        try {
            assertFalse(ShareHelper.shareFiles(context, listOf("/one.pdf", "/missing.pdf")))
            assertEquals(null, shadowOf(context as android.app.Application).nextStartedActivity)
        } finally {
            unmockkObject(ExternalFileAccessHelper)
        }
    }
}
