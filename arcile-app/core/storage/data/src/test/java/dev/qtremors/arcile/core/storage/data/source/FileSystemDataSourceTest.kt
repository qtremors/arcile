package dev.qtremors.arcile.core.storage.data.source

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.qtremors.arcile.core.storage.data.FolderStatsStore
import dev.qtremors.arcile.core.storage.data.MutationFinalizer
import dev.qtremors.arcile.core.storage.data.provider.VolumeProvider
import dev.qtremors.arcile.core.storage.domain.ConflictResolution
import dev.qtremors.arcile.core.storage.domain.FolderStatUpdate
import dev.qtremors.arcile.core.storage.domain.FolderStats
import dev.qtremors.arcile.core.storage.domain.StorageNodePath
import dev.qtremors.arcile.testutil.createTempStorageRoot
import dev.qtremors.arcile.testutil.testVolume
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FileSystemDataSourceTest {

    private lateinit var context: Context
    private lateinit var volumeProvider: VolumeProvider
    private lateinit var mediaStoreClient: MediaStoreClient
    private lateinit var folderStatsStore: FolderStatsStore
    private lateinit var dataSource: DefaultFileSystemDataSource
    private lateinit var root: File

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        root = createTempStorageRoot("fs-test")

        volumeProvider = mockk(relaxed = true)
        every { volumeProvider.activeStorageRoots } returns listOf(root.absolutePath)
        coEvery { volumeProvider.currentVolumes() } returns listOf(testVolume("test-vol", root.absolutePath))
        every { volumeProvider.observeStorageVolumes() } returns flowOf(listOf(testVolume("test-vol", root.absolutePath)))

        mediaStoreClient = mockk(relaxed = true)
        folderStatsStore = object : FolderStatsStore {
            override suspend fun getCached(paths: Collection<String>): Map<String, FolderStats> = emptyMap()
            override fun observeUpdates() = emptyFlow<FolderStatUpdate>()
            override fun queue(paths: List<String>) = Unit
            override fun invalidate(paths: Collection<String>) = Unit
        }

        dataSource = DefaultFileSystemDataSource(
            context,
            volumeProvider,
            MutationFinalizer(context, mediaStoreClient, volumeProvider, folderStatsStore)
        )
    }

    @After
    fun teardown() {
        root.deleteRecursively()
    }

    @Test
    fun `listFiles returns correct models for directory`() = runTest {
        val file = File(root, "test.txt").apply { createNewFile() }
        val dir = File(root, "folder").apply { mkdirs() }

        val result = dataSource.listFiles(root.absolutePath)
        assertTrue(result.isSuccess)
        val files = result.getOrThrow()
        
        assertEquals(2, files.size)
        // Directories should be sorted first
        assertTrue(files[0].isDirectory)
        assertEquals("folder", files[0].name)
        assertFalse(files[1].isDirectory)
        assertEquals("test.txt", files[1].name)
    }

    @Test
    fun `list emits directory pages incrementally`() = runTest {
        File(root, "zeta.txt").apply { createNewFile() }
        File(root, "alpha").apply { mkdirs() }
        File(root, "beta.txt").apply { createNewFile() }

        val pages = dataSource.list(StorageNodePath.of(root.absolutePath), pageSize = 2).toList()

        assertEquals(2, pages.size)
        assertEquals(0, pages[0].pageIndex)
        assertFalse(pages[0].isComplete)
        assertEquals(1, pages[1].pageIndex)
        assertTrue(pages[1].isComplete)
        assertEquals(3, pages.sumOf { it.files.size })
    }

    @Test
    fun `listFiles compatibility returns globally sorted large directory`() = runTest {
        repeat(1_000) { index ->
            File(root, "file-${index.toString().padStart(4, '0')}.txt").createNewFile()
        }
        File(root, "folder").mkdirs()

        val files = dataSource.listFiles(root.absolutePath).getOrThrow()

        assertEquals(1_001, files.size)
        assertTrue(files.first().isDirectory)
        assertEquals("folder", files.first().name)
    }

    @Test
    fun `createDirectory creates new folder`() = runTest {
        val result = dataSource.createDirectory(root.absolutePath, "new_folder")
        assertTrue(result.isSuccess)
        val dir = File(root, "new_folder")
        assertTrue(dir.exists())
        assertTrue(dir.isDirectory)
    }

    @Test
    fun `createFile creates new file`() = runTest {
        val result = dataSource.createFile(root.absolutePath, "new_file.txt")
        assertTrue(result.isSuccess)
        val file = File(root, "new_file.txt")
        assertTrue(file.exists())
        assertTrue(file.isFile)
    }

    @Test
    fun `deletePermanently deletes multiple files and folders`() = runTest {
        val file1 = File(root, "f1.txt").apply { createNewFile() }
        val file2 = File(root, "f2.txt").apply { createNewFile() }
        val dir = File(root, "f_dir").apply { mkdirs() }
        File(dir, "child.txt").createNewFile()

        val paths = listOf(file1.absolutePath, file2.absolutePath, dir.absolutePath)
        val result = dataSource.deletePermanently(paths)
        
        assertTrue(result.isSuccess)
        assertFalse(file1.exists())
        assertFalse(file2.exists())
        assertFalse(dir.exists())
    }

    @Test
    fun `renameFile renames successfully`() = runTest {
        val file = File(root, "old.txt").apply { createNewFile() }
        val result = dataSource.renameFile(file.absolutePath, "new.txt")
        
        assertTrue(result.isSuccess)
        assertFalse(File(root, "old.txt").exists())
        assertTrue(File(root, "new.txt").exists())
    }

    @Test
    fun `renameFile supports case-only file rename`() = runTest {
        val file = File(root, "file.txt").apply {
            createNewFile()
            writeText("case")
        }

        val result = dataSource.renameFile(file.absolutePath, "File.txt")

        assertTrue(result.isSuccess)
        assertEquals("File.txt", result.getOrThrow().name)
        assertTrue(File(root, "File.txt").exists())
        assertEquals("case", File(root, "File.txt").readText())
    }

    @Test
    fun `renameFile supports case-only folder rename`() = runTest {
        val folder = File(root, "folder").apply { mkdirs() }
        File(folder, "child.txt").writeText("child")

        val result = dataSource.renameFile(folder.absolutePath, "Folder")

        assertTrue(result.isSuccess)
        assertEquals("Folder", result.getOrThrow().name)
        assertTrue(File(root, "Folder/child.txt").exists())
    }

    @Test
    fun `renameFile supports case-only extension rename`() = runTest {
        val file = File(root, "photo.jpg").apply { createNewFile() }

        val result = dataSource.renameFile(file.absolutePath, "photo.JPG")

        assertTrue(result.isSuccess)
        assertEquals("photo.JPG", result.getOrThrow().name)
        assertTrue(File(root, "photo.JPG").exists())
    }

    @Test
    fun `renameFile rejects different existing sibling`() = runTest {
        val file = File(root, "source.txt").apply { createNewFile() }
        File(root, "target.txt").createNewFile()

        val result = dataSource.renameFile(file.absolutePath, "target.txt")

        assertTrue(result.isFailure)
        assertTrue(file.exists())
        assertTrue(File(root, "target.txt").exists())
    }

    @Test
    fun `copyFiles copies files and directories correctly`() = runTest {
        val srcDir = File(root, "src").apply { mkdirs() }
        val destDir = File(root, "dest").apply { mkdirs() }
        
        val f1 = File(srcDir, "a.txt").apply { 
            createNewFile()
            writeText("hello")
        }
        val subDir = File(srcDir, "sub").apply { mkdirs() }
        val f2 = File(subDir, "b.txt").apply { 
            createNewFile()
            writeText("world")
        }

        val result = dataSource.copyFiles(
            sourcePaths = listOf(f1.absolutePath, subDir.absolutePath),
            destinationPath = destDir.absolutePath,
            resolutions = emptyMap()
        )

        assertTrue(result.isSuccess)
        
        val copiedA = File(destDir, "a.txt")
        assertTrue(copiedA.exists())
        assertEquals("hello", copiedA.readText())

        val copiedSub = File(destDir, "sub")
        assertTrue(copiedSub.isDirectory)
        val copiedB = File(copiedSub, "b.txt")
        assertTrue(copiedB.exists())
        assertEquals("world", copiedB.readText())
        
        // original files still exist
        assertTrue(f1.exists())
        assertTrue(f2.exists())
    }

    @Test
    fun `moveFiles moves files correctly`() = runTest {
        val srcDir = File(root, "src").apply { mkdirs() }
        val destDir = File(root, "dest").apply { mkdirs() }
        
        val f1 = File(srcDir, "a.txt").apply { 
            createNewFile()
            writeText("hello")
        }

        val result = dataSource.moveFiles(
            sourcePaths = listOf(f1.absolutePath),
            destinationPath = destDir.absolutePath,
            resolutions = emptyMap()
        )

        assertTrue(result.isSuccess)
        
        val movedA = File(destDir, "a.txt")
        assertTrue(movedA.exists())
        assertEquals("hello", movedA.readText())

        // original should be gone
        assertFalse(f1.exists())
    }

    @Test
    fun `copyFiles handles conflicts based on resolutions`() = runTest {
        val srcDir = File(root, "src").apply { mkdirs() }
        val destDir = File(root, "dest").apply { mkdirs() }
        
        val srcFile = File(srcDir, "a.txt").apply { 
            createNewFile()
            writeText("source")
        }
        
        val destFile = File(destDir, "a.txt").apply { 
            createNewFile()
            writeText("existing")
        }

        // Test SKIP
        val resultSkip = dataSource.copyFiles(
            sourcePaths = listOf(srcFile.absolutePath),
            destinationPath = destDir.absolutePath,
            resolutions = mapOf(srcFile.absolutePath to ConflictResolution.SKIP)
        )
        assertTrue(resultSkip.isSuccess)
        assertEquals("existing", destFile.readText()) // untouched

        // Test REPLACE
        val resultReplace = dataSource.copyFiles(
            sourcePaths = listOf(srcFile.absolutePath),
            destinationPath = destDir.absolutePath,
            resolutions = mapOf(srcFile.absolutePath to ConflictResolution.REPLACE)
        )
        assertTrue(resultReplace.isSuccess)
        assertEquals("source", destFile.readText()) // replaced
    }
}
