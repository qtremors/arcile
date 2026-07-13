package dev.qtremors.arcile.core.storage.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StoragePathTest {
    @Test
    fun `path components normalize separators without filesystem access`() {
        assertEquals("C:/Photos/Trip", normalizeStoragePath("C:\\Photos\\Trip"))
        assertEquals("Trip", storagePathName("/storage/Photos/Trip/"))
        assertEquals("archive.tar", storagePathNameWithoutExtension("/Download/archive.tar.gz"))
        assertEquals("/storage/Photos", storageParentPath("/storage/Photos/Trip"))
        assertEquals("C:/", storageParentPath("C:/Photos"))
        assertEquals(null, storageParentPath("/"))
        assertEquals(null, storageParentPath("C:/"))
    }

    @Test
    fun `join and containment use complete path segments`() {
        assertEquals("/storage/Photos/Trip", joinStoragePath("/storage/Photos/", "/Trip"))
        assertEquals("/Trip", joinStoragePath("/", "Trip"))
        assertEquals("C:/Trip", joinStoragePath("C:/", "Trip"))
        assertTrue(isStorageDescendantOrSelf("/storage/Photos/Trip", "/storage/Photos"))
        assertTrue(isStorageDescendantOrSelf("/storage/Photos", "/storage/Photos"))
        assertTrue(isStorageDescendantOrSelf("/storage/Photos", "/"))
        assertTrue(isStorageDescendantOrSelf("C:/Photos", "C:/"))
        assertFalse(isStorageDescendantOrSelf("/storage/Photos-old", "/storage/Photos"))
        assertFalse(isStorageDescendantOrSelf("/storage/Photos", ""))
    }
}
