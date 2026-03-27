package dev.qtremors.arcile.data.source

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.qtremors.arcile.data.provider.VolumeProvider
import dev.qtremors.arcile.domain.CategoryStorage
import dev.qtremors.arcile.domain.ConflictResolution
import dev.qtremors.arcile.domain.FileConflict
import dev.qtremors.arcile.domain.FileModel
import dev.qtremors.arcile.domain.SearchFilters
import dev.qtremors.arcile.domain.StorageScope
import dev.qtremors.arcile.domain.StorageVolume
import dev.qtremors.arcile.presentation.operations.BulkFileOperationProgress
import dev.qtremors.arcile.testutil.createTempStorageRoot
import dev.qtremors.arcile.testutil.testVolume
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class FileSystemDataSourceTest {

    @Test
    fun `listFiles sorts directories before files alphabetically`() = runWithDataSource { root, dataSource, _ ->
        File(root, "beta.txt").writeText("b")
        File(root, "alpha").mkdirs()
        File(root, "gamma").mkdirs()
        File(root, "aardvark.txt").writeText("a")

        val names = dataSource.listFiles(root.absolutePath).getOrThrow().map { it.name }

        assertEquals(listOf("alpha", "gamma", "aardvark.txt", "beta.txt"), names)
    }

    @Test
    fun `createDirectory and createFile reject invalid names`() = runWithDataSource { root, dataSource, _ ->
        assertTrue(dataSource.createDirectory(root.absolutePath, "../bad").isFailure)
        assertTrue(dataSource.createFile(root.absolutePath, "bad/name.txt").isFailure)
    }

    @Test
    fun `renameFile updates file name on disk`() = runWithDataSource { root, dataSource, mediaStore ->
        val source = File(root, "before.txt").apply { writeText("hello") }

        val renamed = dataSource.renameFile(source.absolutePath, "after.txt").getOrThrow()

        assertEquals("after.txt", renamed.name)
        assertTrue(File(root, "after.txt").exists())
        assertEquals(listOf(source.absolutePath, File(root, "after.txt").absolutePath), mediaStore.invalidatedPaths.single())
    }

    @Test
    fun `detectCopyConflicts reports existing targets`() = runWithDataSource { root, dataSource, _ ->
        val source = File(root, "source.txt").apply { writeText("hello") }
        val destination = File(root, "dest").apply { mkdirs() }
        File(destination, "source.txt").writeText("existing")

        val conflicts = dataSource.detectCopyConflicts(listOf(source.absolutePath), destination.absolutePath).getOrThrow()

        assertEquals(1, conflicts.size)
        assertEquals("source.txt", conflicts.single().existingFile.name)
    }

    @Test
    fun `copyFiles supports keep both and emits progress`() = runWithDataSource { root, dataSource, mediaStore ->
        val source = File(root, "source.txt").apply { writeText("hello") }
        val destination = File(root, "dest").apply { mkdirs() }
        File(destination, "source.txt").writeText("existing")
        val progress = mutableListOf<BulkFileOperationProgress>()

        val result = dataSource.copyFiles(
            listOf(source.absolutePath),
            destination.absolutePath,
            resolutions = mapOf(source.absolutePath to ConflictResolution.KEEP_BOTH),
            onProgress = { progress += it }
        )

        assertTrue(result.isSuccess)
        assertTrue(File(destination, "source (1).txt").exists())
        assertEquals(File(destination, "source (1).txt").absolutePath, progress.single().currentPath)
        assertEquals(listOf(File(destination, "source (1).txt").absolutePath), mediaStore.invalidatedPaths.single())
    }

    @Test
    fun `moveFiles moves directories recursively and removes source`() = runWithDataSource { root, dataSource, _ ->
        val sourceDir = File(root, "folder").apply { mkdirs() }
        File(sourceDir, "child.txt").writeText("hello")
        val destination = File(root, "dest").apply { mkdirs() }

        val result = dataSource.moveFiles(listOf(sourceDir.absolutePath), destination.absolutePath, emptyMap(), onProgress = null)

        assertTrue(result.isSuccess)
        assertFalse(sourceDir.exists())
        assertTrue(File(destination, "folder/child.txt").exists())
    }

    @Test
    fun `deletePermanently removes files inside allowed roots`() = runWithDataSource { root, dataSource, mediaStore ->
        val file = File(root, "deleteme.txt").apply { writeText("bye") }

        val result = dataSource.deletePermanently(listOf(file.absolutePath))

        assertTrue(result.isSuccess)
        assertFalse(file.exists())
        assertEquals(listOf(file.absolutePath), mediaStore.invalidatedPaths.single())
    }
}

private fun runWithDataSource(
    block: suspend (root: File, dataSource: DefaultFileSystemDataSource, mediaStore: RecordingMediaStoreClient) -> Unit
) = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val root = createTempStorageRoot("filesystem-ds")
    val volumeProvider = RecordingVolumeProvider(testVolume("primary", root.absolutePath))
    val mediaStore = RecordingMediaStoreClient()
    val dataSource = DefaultFileSystemDataSource(context, volumeProvider, mediaStore)
    try {
        block(root, dataSource, mediaStore)
    } finally {
        root.deleteRecursively()
    }
}

private class RecordingVolumeProvider(private val storageVolume: StorageVolume) : VolumeProvider {
    override val activeStorageRoots: List<String> = listOf(storageVolume.path)
    override fun observeStorageVolumes(): Flow<List<StorageVolume>> = flowOf(listOf(storageVolume))
    override suspend fun getStorageVolumes(): Result<List<StorageVolume>> = Result.success(listOf(storageVolume))
    override suspend fun currentVolumes(): List<StorageVolume> = listOf(storageVolume)
    override fun invalidateCache() = Unit
}

private class RecordingMediaStoreClient : MediaStoreClient {
    val invalidatedPaths = mutableListOf<List<String>>()

    override suspend fun getRecentFiles(scope: StorageScope, limit: Int, offset: Int, minTimestamp: Long): Result<List<FileModel>> =
        Result.success(emptyList())
    override suspend fun getCategoryStorageSizes(scope: StorageScope): Result<List<CategoryStorage>> = Result.success(emptyList())
    override suspend fun getFilesByCategory(scope: StorageScope, categoryName: String): Result<List<FileModel>> = Result.success(emptyList())
    override suspend fun searchFiles(query: String, scope: StorageScope, filters: SearchFilters?): Result<List<FileModel>> = Result.success(emptyList())
    override suspend fun invalidateCache(vararg paths: String) {
        invalidatedPaths += paths.toList()
    }
}
