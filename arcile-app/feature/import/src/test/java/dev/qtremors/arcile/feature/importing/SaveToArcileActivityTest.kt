package dev.qtremors.arcile.feature.importing

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import dev.qtremors.arcile.core.operation.android.MAX_IMPORT_BYTES
import dev.qtremors.arcile.core.operation.android.MAX_IMPORT_ITEMS
import dev.qtremors.arcile.core.operation.android.sanitizeIncomingFileName
import java.io.File
import java.io.RandomAccessFile
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
    fun `preflight rejects unsupported and external file uri schemes`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val external = File("/storage/emulated/0/hostile.txt")
        val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            putExtra(
                Intent.EXTRA_STREAM,
                arrayListOf(Uri.parse("http://example.com/file.txt"), Uri.fromFile(external))
            )
        }

        val result = IncomingShareReader.preflightFromIntent(context, intent)

        assertTrue(result.accepted.isEmpty())
        assertEquals(
            listOf(IncomingShareFailureReason.UnsupportedScheme, IncomingShareFailureReason.ExternalFileUri),
            result.rejected.map { it.reason }
        )
    }

    @Test
    fun `preflight allows app owned file uri and records known size`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val file = File(context.cacheDir, "owned.txt").apply { writeText("hello") }
        val intent = Intent(Intent.ACTION_SEND).apply {
            putExtra(Intent.EXTRA_STREAM, Uri.fromFile(file))
        }

        val result = IncomingShareReader.preflightFromIntent(context, intent)

        assertEquals(1, result.accepted.size)
        assertEquals("owned.txt", result.accepted.single().displayName)
        assertEquals(5L, result.accepted.single().sizeBytes)
    }

    @Test
    fun `preflight enforces item and known byte limits`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val tooMany = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            putExtra(
                Intent.EXTRA_STREAM,
                ArrayList((0..MAX_IMPORT_ITEMS).map { Uri.parse("content://example/$it") })
            )
        }
        assertTrue(IncomingShareReader.preflightFromIntent(context, tooMany).limitExceeded)

        val huge = File(context.cacheDir, "huge.bin").apply {
            RandomAccessFile(this, "rw").use { it.setLength(MAX_IMPORT_BYTES + 1L) }
        }
        val hugeIntent = Intent(Intent.ACTION_SEND).apply {
            putExtra(Intent.EXTRA_STREAM, Uri.fromFile(huge))
        }

        val result = IncomingShareReader.preflightFromIntent(context, hugeIntent)

        assertTrue(result.accepted.isEmpty())
        assertEquals(IncomingShareFailureReason.TooLarge, result.rejected.single().reason)
        huge.delete()
    }

    @Test
    fun `preflight allows unknown size content uri with counted stream flag`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val intent = Intent(Intent.ACTION_SEND).apply {
            putExtra(Intent.EXTRA_STREAM, Uri.parse("content://example/path/report.txt"))
        }

        val result = IncomingShareReader.preflightFromIntent(context, intent)

        assertEquals(1, result.accepted.size)
        assertTrue(result.accepted.single().requiresCountedStream)
    }

    @Test
    fun `sanitize incoming file names removes hostile path and reserved content`() {
        val longName = "a".repeat(400) + ".txt"

        assertEquals("evil_name.txt", sanitizeIncomingFileName("../evil\u0000:name.txt"))
        assertEquals("shared-file.txt", sanitizeIncomingFileName("CON.txt"))
        assertEquals(255, sanitizeIncomingFileName(longName).length)
        assertEquals(
            "photo (1).jpg",
            sanitizeIncomingFileName("photo.jpg", existingNames = setOf("photo.jpg"))
        )
    }

    @Test
    fun `queued import result reports background start instead of saved`() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        val message = SaveIncomingResult(savedCount = 0, failures = emptyList(), queued = true)
            .userMessage(context)

        assertEquals(
            context.getString(dev.qtremors.arcile.core.ui.R.string.save_to_arcile_import_started),
            message
        )
    }
}
