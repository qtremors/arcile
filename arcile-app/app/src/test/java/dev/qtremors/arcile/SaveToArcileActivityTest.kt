package dev.qtremors.arcile

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import dev.qtremors.arcile.core.storage.domain.StorageKind
import dev.qtremors.arcile.core.storage.domain.StorageVolume
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.File
import java.io.RandomAccessFile

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
                arrayListOf(
                    Uri.parse("http://example.com/file.txt"),
                    Uri.fromFile(external)
                )
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
        assertEquals("photo (1).jpg", sanitizeIncomingFileName("photo.jpg", existingNames = setOf("photo.jpg")))
    }

    @Test
    fun `saveIncomingFiles keeps duplicate names and records partial stream failures`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val destination = File(context.cacheDir, "save-test").apply {
            deleteRecursively()
            mkdirs()
        }
        val good = Uri.parse("content://example/good")
        val bad = Uri.parse("content://example/bad")
        var finalizedPath: String? = null

        val result = saveIncomingFiles(
            destination = destination,
            incoming = listOf(
                IncomingSharedFile(good, "same.txt"),
                IncomingSharedFile(bad, "same.txt")
            ),
            openInputStream = { uri ->
                if (uri == bad) null else ByteArrayInputStream("payload".toByteArray())
            },
            finalizeDestination = { finalizedPath = it },
            invalidDestinationMessage = "invalid",
            insufficientSpaceMessage = "space",
            failedOpenStreamMessage = "open failed"
        ).getOrThrow()

        assertEquals(1, result.savedCount)
        assertEquals(IncomingShareFailureReason.CopyFailed, result.failures.single().reason)
        assertTrue(File(destination, "same.txt").exists())
        assertFalse(File(destination, "same (1).txt").exists())
        assertEquals(destination.absolutePath, finalizedPath)
    }

    @Test
    fun `saveIncomingFiles refuses insufficient destination space before copy`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val destination = File(context.cacheDir, "space-test").apply {
            deleteRecursively()
            mkdirs()
        }
        var opened = false

        val result = runCatching {
            saveIncomingFiles(
                destination = destination,
                incoming = listOf(IncomingSharedFile(Uri.parse("content://example/a"), "a.txt", sizeBytes = 100L)),
                openInputStream = {
                    opened = true
                    ByteArrayInputStream(byteArrayOf(1))
                },
                finalizeDestination = {},
                invalidDestinationMessage = "invalid",
                insufficientSpaceMessage = "space",
                failedOpenStreamMessage = "open failed",
                usableSpaceProvider = { 1L }
            )
        }

        assertTrue(result.isFailure)
        assertFalse(opened)
    }

    @Test
    fun `queued import result reports background start instead of saved`() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        val message = SaveIncomingResult(savedCount = 0, failures = emptyList(), queued = true)
            .userMessage(context)

        assertEquals(context.getString(dev.qtremors.arcile.core.ui.R.string.save_to_arcile_import_started), message)
    }

    @Test
    fun `default save to arcile folder opens first when it is valid`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val volumeRoot = File(context.cacheDir, "default-volume").apply {
            deleteRecursively()
            mkdirs()
        }
        val defaultFolder = File(volumeRoot, "Imports").apply { mkdirs() }

        val resolved = resolveInitialSaveToArcileDirectory(
            defaultPath = defaultFolder.absolutePath,
            volumes = listOf(testSaveVolume(volumeRoot))
        )

        assertEquals(defaultFolder.canonicalFile, resolved?.canonicalFile)
        volumeRoot.deleteRecursively()
    }

    @Test
    fun `default save to arcile folder falls back when missing or outside volumes`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val volumeRoot = File(context.cacheDir, "valid-volume").apply {
            deleteRecursively()
            mkdirs()
        }
        val outsideRoot = File(context.cacheDir, "outside-volume").apply {
            deleteRecursively()
            mkdirs()
        }
        val outsideFolder = File(outsideRoot, "Imports").apply { mkdirs() }
        val missingFolder = File(volumeRoot, "Missing")

        assertEquals(
            null,
            resolveInitialSaveToArcileDirectory(missingFolder.absolutePath, listOf(testSaveVolume(volumeRoot)))
        )
        assertEquals(
            null,
            resolveInitialSaveToArcileDirectory(outsideFolder.absolutePath, listOf(testSaveVolume(volumeRoot)))
        )

        volumeRoot.deleteRecursively()
        outsideRoot.deleteRecursively()
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
                Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse("content://example/file.txt"), "text/plain")
            )
        )
    }

    @Test
    fun `manifest exposes standalone image viewer in separate process`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val matches = context.packageManager.queryIntentActivities(
            Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse("content://example/photo"), "image/jpeg"),
            0
        )

        val activity = matches.first { it.activityInfo.name == ImageViewerActivity::class.java.name }.activityInfo
        assertEquals("${context.packageName}:imageviewer", activity.processName)
    }


    @Test
    fun `manifest exposes generic file opener for binary fallback`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val matches = context.packageManager.queryIntentActivities(
            Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse("content://example/model.glb"), "application/octet-stream"),
            0
        )

        assertTrue(matches.any { it.activityInfo.name == FileOpenActivity::class.java.name })
    }
}

private fun testSaveVolume(root: File) = StorageVolume(
    id = root.name,
    storageKey = root.name,
    name = root.name,
    path = root.absolutePath,
    totalBytes = 100L,
    freeBytes = 50L,
    isPrimary = false,
    isRemovable = true,
    kind = StorageKind.INTERNAL
)
