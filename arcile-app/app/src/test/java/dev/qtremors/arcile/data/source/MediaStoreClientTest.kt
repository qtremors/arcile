package dev.qtremors.arcile.data.source

import android.content.ContentResolver
import android.content.Context
import android.content.ContextWrapper
import android.database.Cursor
import android.database.MatrixCursor
import android.provider.MediaStore
import androidx.test.core.app.ApplicationProvider
import dev.qtremors.arcile.data.provider.VolumeProvider
import dev.qtremors.arcile.domain.CategoryStorage
import dev.qtremors.arcile.domain.StorageKind
import dev.qtremors.arcile.domain.StorageScope
import dev.qtremors.arcile.domain.StorageVolume
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MediaStoreClientTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `getCategoryStorageSizes filters to requested volume and reuses cached result`() = runTest {
        val baseContext = ApplicationProvider.getApplicationContext<Context>()
        val primaryRoot = fsPath("storage", "emulated", "0")
        val sdRoot = fsPath("storage", "1234-5678")
        val resolver = mockk<ContentResolver>()
        var queryCount = 0
        var lastSelection: String? = null
        var lastSelectionArgs: Array<String> = emptyArray()
        every {
            resolver.query(any(), any(), any<String>(), any<Array<String>>(), isNull())
        } answers {
            queryCount += 1
            lastSelection = arg(2)
            lastSelectionArgs = arg<Array<String>>(3)
            categoryCursor(
                Triple(fsPath("storage", "emulated", "0", "DCIM", "cat.jpg"), 125L, "image/jpeg"),
                Triple(fsPath("storage", "emulated", "0", "Music", "song.mp3"), 250L, "audio/mpeg"),
                Triple(fsPath("storage", "emulated", "0", "Documents", "readme.pdf"), 75L, "application/pdf"),
                Triple(fsPath("storage", "1234-5678", "DCIM", "other.jpg"), 999L, "image/jpeg")
            )
        }
        val context = TestContext(
            base = baseContext,
            cacheDir = temporaryFolder.root,
            contentResolver = resolver
        )
        val client = DefaultMediaStoreClient(
            context = context,
            volumeProvider = FakeVolumeProvider(
                listOf(
                    storageVolume("primary", primaryRoot, isPrimary = true),
                    storageVolume("sd", sdRoot, isPrimary = false)
                )
            )
        )

        val first = client.getCategoryStorageSizes(StorageScope.Volume("primary")).getOrThrow()
        val second = client.getCategoryStorageSizes(StorageScope.Volume("primary")).getOrThrow()

        assertEquals(1, queryCount)
        assertCategorySize(first, "Images", 125L)
        assertCategorySize(first, "Audio", 250L)
        assertCategorySize(first, "Docs", 75L)
        assertEquals(first, second)
        assertTrue(lastSelection.orEmpty().contains(MediaStore.Files.FileColumns.MIME_TYPE))
        assertTrue(lastSelectionArgs.any { it == "%.jpg" })
    }

    @Test
    fun `invalidateCache clears cached global stats for affected volume paths`() = runTest {
        val baseContext = ApplicationProvider.getApplicationContext<Context>()
        val primaryRoot = fsPath("storage", "emulated", "0")
        var imageBytes = 120L
        val resolver = mockk<ContentResolver>()
        var queryCount = 0
        every {
            resolver.query(any(), any(), any<String>(), any<Array<String>>(), isNull())
        } answers {
            queryCount += 1
            categoryCursor(
                Triple(fsPath("storage", "emulated", "0", "DCIM", "cat.jpg"), imageBytes, "image/jpeg")
            )
        }
        val context = TestContext(
            base = baseContext,
            cacheDir = temporaryFolder.root,
            contentResolver = resolver
        )
        val client = DefaultMediaStoreClient(
            context = context,
            volumeProvider = FakeVolumeProvider(listOf(storageVolume("primary", primaryRoot, isPrimary = true)))
        )

        val first = client.getCategoryStorageSizes(StorageScope.AllStorage).getOrThrow()
        imageBytes = 480L
        val cached = client.getCategoryStorageSizes(StorageScope.AllStorage).getOrThrow()
        client.invalidateCache(fsPath("storage", "emulated", "0", "DCIM", "cat.jpg"))
        val refreshed = client.getCategoryStorageSizes(StorageScope.AllStorage).getOrThrow()

        assertCategorySize(first, "Images", 120L)
        assertEquals(first, cached)
        assertCategorySize(refreshed, "Images", 480L)
        assertEquals(2, queryCount)
    }

    private fun assertCategorySize(data: List<CategoryStorage>, name: String, expectedSize: Long) {
        assertEquals(expectedSize, data.first { it.name == name }.sizeBytes)
    }

    private fun categoryCursor(vararg rows: Triple<String, Long, String>): Cursor {
        return MatrixCursor(
            arrayOf(
                MediaStore.Files.FileColumns.DATA,
                MediaStore.Files.FileColumns.SIZE,
                MediaStore.Files.FileColumns.MIME_TYPE
            )
        ).apply {
            rows.forEach { (path, size, mimeType) ->
                addRow(arrayOf<Any>(path, size, mimeType))
            }
        }
    }

    private fun fsPath(vararg parts: String): String = parts.joinToString(File.separator, prefix = File.separator)
}

private class FakeVolumeProvider(
    private val volumes: List<StorageVolume>
) : VolumeProvider {
    override val activeStorageRoots: List<String> = volumes.map { it.path }

    override fun observeStorageVolumes(): Flow<List<StorageVolume>> = flowOf(volumes)

    override suspend fun getStorageVolumes(): Result<List<StorageVolume>> = Result.success(volumes)

    override suspend fun currentVolumes(): List<StorageVolume> = volumes

    override fun invalidateCache() = Unit
}

private class TestContext(
    base: Context,
    private val cacheDir: File,
    private val contentResolver: ContentResolver
) : ContextWrapper(base) {

    override fun getApplicationContext(): Context = this

    override fun getCacheDir(): File = cacheDir

    override fun getContentResolver(): ContentResolver = contentResolver

    override fun getSystemService(name: String): Any? {
        return when (name) {
            Context.STORAGE_STATS_SERVICE -> null
            else -> super.getSystemService(name)
        }
    }
}

private fun storageVolume(
    id: String,
    path: String,
    isPrimary: Boolean
) = StorageVolume(
    id = id,
    storageKey = id,
    name = if (isPrimary) "Internal" else "External",
    path = path,
    totalBytes = 1_000L,
    freeBytes = 250L,
    isPrimary = isPrimary,
    isRemovable = !isPrimary,
    kind = if (isPrimary) StorageKind.INTERNAL else StorageKind.SD_CARD
)
