package dev.qtremors.arcile.core.ui.externalfile

import android.content.ContentResolver
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.test.core.app.ApplicationProvider
import dev.qtremors.arcile.core.storage.domain.StorageNodeRef
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowEnvironment
import java.io.File
import java.io.RandomAccessFile

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ExternalFileAccessHelperTest {

    private fun configureExternalStorageRoot(): File {
        val root = File("build/tmp/arcile-open-with-external").absoluteFile.apply { mkdirs() }
        ShadowEnvironment.setExternalStorageDirectory(root.toPath())
        return root
    }

    private fun installDirectOpenUriFactory() {
        ExternalFileAccessHelper.directOpenUriFactory = { context, file ->
            Uri.parse("content://${context.packageName}.fileprovider/external/${file.name}")
        }
    }

    @Test
    fun `clearStagingArea removes staged open and share cache files`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val root = File(context.cacheDir, "external_access")
        File(root, "open").mkdirs()
        File(root, "share").mkdirs()
        File(root, "open/test.txt").writeText("open")
        File(root, "share/test.txt").writeText("share")

        val before = ExternalFileAccessHelper.getStagingCacheStats(context)
        assertEquals(2, before.fileCount)
        assertTrue(before.sizeBytes > 0L)

        val after = ExternalFileAccessHelper.clearStagingArea(context)

        assertEquals(0, after.fileCount)
        assertEquals(0L, after.sizeBytes)
        assertEquals(0, ExternalFileAccessHelper.getStagingCacheStats(context).fileCount)
    }

    @Test
    fun `createShareTargets rejects oversized staged handoff files`() = runTest {
        configureExternalStorageRoot()
        val context = ApplicationProvider.getApplicationContext<Context>()
        val oversized = File(context.cacheDir, "oversized-share.bin")
        RandomAccessFile(oversized, "rw").use { it.setLength(257L * 1024L * 1024L) }

        try {
            ExternalFileAccessHelper.createShareTargets(context, listOf(oversized.absolutePath))
            fail("Expected oversized share handoff to be rejected")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message.orEmpty().contains("too large"))
        }
    }

    @Test
    fun `createOpenIntent stages local user file before creating provider uri`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        configureExternalStorageRoot()
        installDirectOpenUriFactory()
        ExternalFileAccessHelper.clearStagingArea(context)
        val source = File(Environment.getExternalStorageDirectory(), "open-direct.txt").apply {
            parentFile?.mkdirs()
            writeText("direct")
        }

        val intent = try {
            ExternalFileAccessHelper.createOpenIntent(context, source.absolutePath)
        } finally {
            ExternalFileAccessHelper.resetDirectOpenUriFactoryForTest()
        }

        assertEquals(Intent.ACTION_VIEW, intent.action)
        assertTrue(intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
        assertTrue(intent.data.toString().endsWith(".txt"))
        assertEquals("open-direct.txt", intent.getStringExtra(Intent.EXTRA_TITLE))
        assertEquals(1, File(context.cacheDir, "external_access/open").walkTopDown().filter { it.isFile }.count())
    }

    @Test
    fun `createOpenIntent stages local handoff without share size caps`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        configureExternalStorageRoot()
        installDirectOpenUriFactory()
        ExternalFileAccessHelper.clearStagingArea(context)
        val source = File(Environment.getExternalStorageDirectory(), "large-open.bin").apply {
            parentFile?.mkdirs()
            writeText("open")
        }

        val intent = try {
            ExternalFileAccessHelper.createOpenIntent(context, source.absolutePath)
        } finally {
            ExternalFileAccessHelper.resetDirectOpenUriFactoryForTest()
        }

        assertEquals(Intent.ACTION_VIEW, intent.action)
        assertEquals(1, File(context.cacheDir, "external_access/open").walkTopDown().filter { it.isFile }.count())
    }

    @Test
    fun `createOpenIntent rejects missing path without media store DATA lookup`() = runTest {
        configureExternalStorageRoot()
        val baseContext = ApplicationProvider.getApplicationContext<Context>()
        val missingPath = File(Environment.getExternalStorageDirectory(), "Pictures/Screenshots/photo.png").absolutePath
        val resolver = mockk<ContentResolver>()
        var queryCount = 0
        every {
            resolver.query(any(), any(), any<String>(), any<Array<String>>(), isNull())
        } answers {
            queryCount += 1
            mediaStoreCursor(
                id = 42L,
                displayName = "photo.png",
                mimeType = "image/png",
                volumeName = MediaStore.VOLUME_EXTERNAL_PRIMARY
            )
        }
        val context = ResolverContext(baseContext, resolver)

        try {
            ExternalFileAccessHelper.createOpenIntent(context, missingPath)
            fail("Expected missing path to be rejected")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message.orEmpty().contains("Source file does not exist"))
        }

        assertEquals(0, queryCount)
    }

    @Test
    fun `createOpenIntent uses storage node content uri without media store DATA lookup`() = runTest {
        val baseContext = ApplicationProvider.getApplicationContext<Context>()
        val resolver = mockk<ContentResolver>()
        val uri = Uri.parse("content://media/external_primary/file/42")
        var queryCount = 0
        every { resolver.getType(uri) } returns "image/png"
        every { resolver.query(any(), any(), any<String>(), any<Array<String>>(), isNull()) } answers {
            queryCount += 1
            MatrixCursor(arrayOf(MediaStore.Files.FileColumns._ID))
        }
        val context = ResolverContext(baseContext, resolver)

        val intent = ExternalFileAccessHelper.createOpenIntent(
            context,
            ExternalFileAccessHelper.ExternalFileReference(
                path = "/storage/emulated/0/Pictures/photo.png",
                displayName = "photo.png",
                mimeType = "image/png",
                nodeRef = StorageNodeRef.mediaStore(
                    id = 42L,
                    volumeName = MediaStore.VOLUME_EXTERNAL_PRIMARY,
                    contentUri = uri.toString(),
                    displayPath = "/storage/emulated/0/Pictures/photo.png"
                )
            )
        )

        assertEquals(Intent.ACTION_VIEW, intent.action)
        assertEquals(uri, intent.data)
        assertEquals("image/png", intent.type)
        assertEquals("photo.png", intent.getStringExtra(Intent.EXTRA_TITLE))
        assertEquals(0, queryCount)
    }

    @Test
    fun `createOpenIntent accepts content uri references directly`() = runTest {
        val baseContext = ApplicationProvider.getApplicationContext<Context>()
        ExternalFileAccessHelper.clearStagingArea(baseContext)
        val resolver = mockk<ContentResolver>()
        val uri = Uri.parse("content://media/external_primary/images/media/42")
        every { resolver.getType(uri) } returns "image/png"
        every { resolver.query(any(), any(), isNull(), isNull(), isNull()) } answers {
            val projection = invocation.args[1] as Array<String>
            openableCursor(projection.single(), "photo.png", 123L)
        }
        val context = ResolverContext(baseContext, resolver)

        val intent = ExternalFileAccessHelper.createOpenIntent(context, uri.toString())

        assertEquals(Intent.ACTION_VIEW, intent.action)
        assertEquals(uri, intent.data)
        assertEquals("image/png", intent.type)
        assertTrue(intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
        assertEquals(0, ExternalFileAccessHelper.getStagingCacheStats(context).fileCount)
    }

    @Test
    fun `createShareTargets accepts content uri references without staging file`() = runTest {
        val baseContext = ApplicationProvider.getApplicationContext<Context>()
        val resolver = mockk<ContentResolver>()
        val uri = Uri.parse("content://media/external_primary/images/media/42")
        every { resolver.getType(uri) } returns "image/png"
        every { resolver.query(any(), any(), isNull(), isNull(), isNull()) } answers {
            val projection = invocation.args[1] as Array<String>
            openableCursor(projection.single(), "photo.png", 123L)
        }
        val context = ResolverContext(baseContext, resolver)

        val targets = ExternalFileAccessHelper.createShareTargets(context, listOf(uri.toString()))

        assertEquals(1, targets.size)
        assertEquals(uri, targets.single().uri)
        assertEquals("image/png", targets.single().mimeType)
        assertEquals("photo.png", targets.single().displayName)
        assertEquals(123L, targets.single().sizeBytes)
    }

    @Test
    fun `createShareTargets stages local files with collision safe original names`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val root = configureExternalStorageRoot()
        ExternalFileAccessHelper.clearStagingArea(context)
        val first = File(root, "Album A/photo.jpg").apply {
            parentFile?.mkdirs()
            writeText("first")
        }
        val second = File(root, "Album B/photo.jpg").apply {
            parentFile?.mkdirs()
            writeText("second")
        }

        val targets = ExternalFileAccessHelper.createShareTargets(context, listOf(first.absolutePath, second.absolutePath))

        assertEquals(2, targets.size)
        assertEquals("photo.jpg", targets[0].displayName)
        assertEquals("photo.jpg", targets[1].displayName)
        val stagedFiles = File(context.cacheDir, "external_access/share").listFiles()?.map { it.name }.orEmpty()
        assertTrue(stagedFiles.contains("photo.jpg"))
        assertTrue(stagedFiles.contains("photo (1).jpg"))
    }

    @Test
    fun `createShareTargets skips over existing generated collision names`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val root = configureExternalStorageRoot()
        ExternalFileAccessHelper.clearStagingArea(context)
        val first = File(root, "Album A/photo.jpg").apply {
            parentFile?.mkdirs()
            writeText("first")
        }
        val second = File(root, "Album B/photo.jpg").apply {
            parentFile?.mkdirs()
            writeText("second")
        }
        val third = File(root, "Album C/photo (1).jpg").apply {
            parentFile?.mkdirs()
            writeText("third")
        }

        val targets = ExternalFileAccessHelper.createShareTargets(
            context,
            listOf(first.absolutePath, second.absolutePath, third.absolutePath)
        )

        assertEquals(3, targets.size)
        val stagedDir = File(context.cacheDir, "external_access/share")
        val stagedFiles = stagedDir.listFiles()?.associate { it.name to it.readText() }.orEmpty()
        assertEquals("first", stagedFiles["photo.jpg"])
        assertEquals("second", stagedFiles["photo (1).jpg"])
        assertEquals("third", stagedFiles["photo (1) (1).jpg"])
    }

    @Test
    fun `staged share provider exposes original display name and size`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val root = configureExternalStorageRoot()
        ExternalFileAccessHelper.clearStagingArea(context)
        val source = File(root, "Report Final.pdf").apply {
            writeText("shared bytes")
        }

        val target = ExternalFileAccessHelper.createShareTargets(context, listOf(source.absolutePath)).single()
        val cursor = context.contentResolver.query(
            target.uri,
            arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
            null,
            null,
            null
        )

        assertTrue(target.uri.toString().startsWith("content://${context.packageName}.externalfileaccess/"))
        assertEquals("Report Final.pdf", target.displayName)
        cursor.use {
            assertTrue(it?.moveToFirst() == true)
            assertEquals("Report Final.pdf", it!!.getString(it.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME)))
            assertEquals(source.length(), it.getLong(it.getColumnIndexOrThrow(OpenableColumns.SIZE)))
        }
    }

    @Test
    fun `createOpenIntent rejects sensitive cache paths`() = runTest {
        configureExternalStorageRoot()
        val context = ApplicationProvider.getApplicationContext<Context>()
        val source = File(context.cacheDir, "sensitive-open.txt").apply { writeText("nope") }

        try {
            ExternalFileAccessHelper.createOpenIntent(context, source.absolutePath)
            fail("Expected sensitive path to be rejected")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message.orEmpty().contains("Unsupported file path"))
        }
    }

    @Test
    fun `createOpenIntent rejects app metadata and Android restricted paths`() = runTest {
        configureExternalStorageRoot()
        val context = ApplicationProvider.getApplicationContext<Context>()
        val arcilePath = File(Environment.getExternalStorageDirectory(), ".arcile/metadata.json").apply {
            parentFile?.mkdirs()
            writeText("private")
        }
        val androidDataPath = File(Environment.getExternalStorageDirectory(), "Android/data/com.example/cache.txt").apply {
            parentFile?.mkdirs()
            writeText("private")
        }
        val androidObbPath = File(Environment.getExternalStorageDirectory(), "Android/obb/com.example/main.obb").apply {
            parentFile?.mkdirs()
            writeText("private")
        }

        listOf(arcilePath, androidDataPath, androidObbPath).forEach { source ->
            try {
                ExternalFileAccessHelper.createOpenIntent(context, source.absolutePath)
                fail("Expected restricted path to be rejected: ${source.absolutePath}")
            } catch (expected: IllegalArgumentException) {
                assertTrue(expected.message.orEmpty().contains("Unsupported file path"))
            }
        }
    }

    private fun mediaStoreCursor(
        id: Long,
        displayName: String,
        mimeType: String,
        volumeName: String
    ): Cursor = MatrixCursor(
        arrayOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.DISPLAY_NAME,
            MediaStore.Files.FileColumns.MIME_TYPE,
            MediaStore.MediaColumns.VOLUME_NAME
        )
    ).apply {
        addRow(arrayOf<Any?>(id, displayName, mimeType, volumeName))
    }

    private fun openableCursor(
        column: String,
        displayName: String,
        sizeBytes: Long
    ): Cursor = MatrixCursor(arrayOf(column)).apply {
        addRow(
            arrayOf(
                when (column) {
                    OpenableColumns.DISPLAY_NAME -> displayName
                    OpenableColumns.SIZE -> sizeBytes
                    else -> null
                }
            )
        )
    }
}

private class ResolverContext(
    base: Context,
    private val resolver: ContentResolver
) : ContextWrapper(base) {
    override fun getApplicationContext(): Context = this
    override fun getContentResolver(): ContentResolver = resolver
}
