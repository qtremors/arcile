package dev.qtremors.arcile.core.storage.data

import dev.qtremors.arcile.core.runtime.di.ArcileDispatchers
import dev.qtremors.arcile.core.storage.domain.StorageKind
import dev.qtremors.arcile.core.storage.domain.StorageVolume
import java.io.File
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class DefaultSaveDestinationBrowserTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `resolve accepts readable directories only inside an available volume`() = runTest {
        val volumeRoot = temporaryFolder.newFolder("volume")
        val imports = File(volumeRoot, "Imports").apply { mkdirs() }
        val outside = temporaryFolder.newFolder("outside")
        val browser = browser()
        val volumes = listOf(volume(volumeRoot))

        val resolved = browser.resolve(imports.path, volumes).getOrThrow()

        assertEquals(imports.canonicalPath, resolved?.path)
        assertEquals(imports.canWrite(), resolved?.canSave)
        assertNull(browser.resolve(File(volumeRoot, "missing").path, volumes).getOrThrow())
        assertNull(browser.resolve(outside.path, volumes).getOrThrow())
    }

    @Test
    fun `children include only readable directories in case insensitive order`() = runTest {
        val root = temporaryFolder.newFolder("volume")
        File(root, "zeta").mkdirs()
        File(root, "Alpha").mkdirs()
        File(root, "file.txt").writeText("not a directory")

        val children = browser().children(root.path, listOf(volume(root))).getOrThrow()

        assertEquals(listOf("Alpha", "zeta"), children.map { it.name })
    }

    @Test
    fun `children reject a starting directory outside available volumes`() = runTest {
        val root = temporaryFolder.newFolder("volume")
        val outside = temporaryFolder.newFolder("outside")

        val result = browser().children(outside.path, listOf(volume(root)))

        assert(result.isFailure)
    }

    @Test
    fun `parent returns null at volume root and never escapes the volume`() = runTest {
        val root = temporaryFolder.newFolder("volume")
        val nested = File(root, "one/two").apply { mkdirs() }
        val volumes = listOf(volume(root))
        val browser = browser()

        assertEquals(File(root, "one").canonicalPath, browser.parent(nested.path, volumes).getOrThrow()?.path)
        assertNull(browser.parent(root.path, volumes).getOrThrow())
    }

    private fun browser(): DefaultSaveDestinationBrowser {
        val dispatcher = UnconfinedTestDispatcher()
        return DefaultSaveDestinationBrowser(
            ArcileDispatchers(dispatcher, dispatcher, dispatcher, dispatcher)
        )
    }

    private fun volume(root: File) = StorageVolume(
        id = root.name,
        storageKey = root.name,
        name = root.name,
        path = root.path,
        totalBytes = 100L,
        freeBytes = 50L,
        isPrimary = true,
        isRemovable = false,
        kind = StorageKind.INTERNAL
    )
}
