package dev.qtremors.arcile.data

import dev.qtremors.arcile.domain.StorageKind
import dev.qtremors.arcile.domain.StorageMountState
import dev.qtremors.arcile.domain.StorageScope
import dev.qtremors.arcile.domain.StorageVolume
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class CategoryScopeMatchingTest {

    private fun createVolume(id: String, path: String): StorageVolume {
        val canonical = File(path).canonicalPath
        return StorageVolume(
            id = id,
            storageKey = id,
            name = id,
            path = canonical,
            totalBytes = 1000L,
            freeBytes = 500L,
            isPrimary = id == "primary",
            isRemovable = id != "primary",
            mountState = StorageMountState.MOUNTED,
            kind = if (id == "primary") StorageKind.INTERNAL else StorageKind.SD_CARD,
            isUserClassified = true
        )
    }

    @Test
    fun `test matchesScope with Category and null volumeId`() {
        val volumes = listOf(
            createVolume("primary", "/storage/emulated/0"),
            createVolume("sdcard", "/storage/1234-5678")
        )

        val scope = StorageScope.Category(volumeId = null, categoryName = "Images")

        // Path in primary volume
        assertTrue(matchesScope(File("/storage/emulated/0/DCIM/photo.jpg").path, scope, volumes))
        
        // Path in SD card volume
        assertTrue(matchesScope(File("/storage/1234-5678/Pictures/image.png").path, scope, volumes))
        
        // Path outside any volume
        assertFalse(matchesScope(File("/system/etc/hosts").path, scope, volumes))
    }

    @Test
    fun `test matchesScope with Category and non-empty volumeId`() {
        val volumes = listOf(
            createVolume("primary", "/storage/emulated/0"),
            createVolume("sdcard", "/storage/1234-5678")
        )

        val scopePrimary = StorageScope.Category(volumeId = "primary", categoryName = "Images")
        val scopeSd = StorageScope.Category(volumeId = "sdcard", categoryName = "Images")

        // Check primary scope
        assertTrue(matchesScope(File("/storage/emulated/0/DCIM/photo.jpg").path, scopePrimary, volumes))
        assertFalse(matchesScope(File("/storage/1234-5678/Pictures/image.png").path, scopePrimary, volumes))

        // Check SD scope
        assertFalse(matchesScope(File("/storage/emulated/0/DCIM/photo.jpg").path, scopeSd, volumes))
        assertTrue(matchesScope(File("/storage/1234-5678/Pictures/image.png").path, scopeSd, volumes))
    }

    @Test
    fun `test indexedVolumesForScope with Category and null volumeId`() {
        val volumes = listOf(
            createVolume("primary", "/storage/emulated/0"),
            createVolume("sdcard", "/storage/1234-5678")
        )

        val scope = StorageScope.Category(volumeId = null, categoryName = "Images")
        val result = indexedVolumesForScope(scope, volumes)

        assertEquals(2, result.size)
        assertTrue(result.any { it.id == "primary" })
        assertTrue(result.any { it.id == "sdcard" })
    }

    @Test
    fun `test matchesScope excludes hidden files for Category`() {
        val volumes = listOf(createVolume("primary", "/storage/emulated/0"))
        val scope = StorageScope.Category(categoryName = "Images")

        // Normal file
        assertTrue(matchesScope(File("/storage/emulated/0/DCIM/photo.jpg").path, scope, volumes))

        // Hidden file
        assertFalse(matchesScope(File("/storage/emulated/0/DCIM/.photo.jpg").path, scope, volumes))

        // File in hidden directory
        assertFalse(matchesScope(File("/storage/emulated/0/.hidden/photo.jpg").path, scope, volumes))
        
        // File in .arcile (hidden storage)
        assertFalse(matchesScope(File("/storage/emulated/0/.arcile/trash/some_file").path, scope, volumes))
    }

    @Test
    fun `test matchesScope with Category specifically for SD card`() {
        // SD cards often have different path patterns (e.g., /storage/XXXX-XXXX)
        val sdPath = "/storage/A1B2-C3D4"
        val volumes = listOf(
            createVolume("primary", "/storage/emulated/0"),
            createVolume("sdcard_id", sdPath)
        )

        val sdScope = StorageScope.Category(volumeId = "sdcard_id", categoryName = "Videos")

        // Correct SD card path
        assertTrue(matchesScope(File("$sdPath/Movies/video.mp4").path, sdScope, volumes))
        
        // Root SD card path (folder is not a video)
        assertFalse(matchesScope(File(sdPath).path, sdScope, volumes))

        // Path in another volume
        assertFalse(matchesScope(File("/storage/emulated/0/Download/movie.mp4").path, sdScope, volumes))
        
        // Correct volume but wrong category (image in videos scope)
        assertFalse(matchesScope(File("$sdPath/Pictures/photo.jpg").path, sdScope, volumes))
    }

}
