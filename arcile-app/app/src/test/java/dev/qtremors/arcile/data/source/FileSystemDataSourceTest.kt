package dev.qtremors.arcile.data.source

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.qtremors.arcile.data.provider.VolumeProvider
import dev.qtremors.arcile.domain.ConflictResolution
import dev.qtremors.arcile.testutil.createTempStorageRoot
import dev.qtremors.arcile.testutil.testVolume
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
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

        dataSource = DefaultFileSystemDataSource(context, volumeProvider, mediaStoreClient)
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
