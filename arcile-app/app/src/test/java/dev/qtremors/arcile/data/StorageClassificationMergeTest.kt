package dev.qtremors.arcile.data

import dev.qtremors.arcile.data.util.*

import dev.qtremors.arcile.domain.StorageKind
import dev.qtremors.arcile.domain.StorageVolume
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StorageClassificationMergeTest {

    // Helper to create a dummy volume
    private fun createVolume(
        id: String,
        storageKey: String,
        path: String,
        isPrimary: Boolean = false
    ): StorageVolume {
        return StorageVolume(
            id = id,
            storageKey = storageKey,
            name = "Test Volume",
            path = path,
            totalBytes = 1000L,
            freeBytes = 500L,
            isPrimary = isPrimary,
            isRemovable = !isPrimary,
            kind = if (isPrimary) StorageKind.INTERNAL else StorageKind.EXTERNAL_UNCLASSIFIED,
            isUserClassified = false
        )
    }

    @Test
    fun `test mergeClassifications resolves to default INTERNAL for primary`() {
        val volume = createVolume("vol1", "primary", "/storage/emulated/0", isPrimary = true)
        val result = mergeStorageClassifications(listOf(volume), emptyMap())
        
        assertEquals(1, result.size)
        assertEquals(StorageKind.INTERNAL, result[0].kind)
        assertFalse(result[0].isUserClassified)
    }

    @Test
    fun `test mergeClassifications resolves to default EXTERNAL_UNCLASSIFIED for removable`() {
        val volume = createVolume("vol2", "uuid:abcd", "/storage/ABCD-1234", isPrimary = false)
        val result = mergeStorageClassifications(listOf(volume), emptyMap())
        
        assertEquals(1, result.size)
        assertEquals(StorageKind.EXTERNAL_UNCLASSIFIED, result[0].kind)
        assertFalse(result[0].isUserClassified)
    }

    @Test
    fun `test mergeClassifications applies classification by UUID storageKey`() {
        val volume = createVolume("vol2", "uuid:abcd", "/storage/ABCD-1234", isPrimary = false)
        val classifications = mapOf(
            "uuid:abcd" to StorageClassification(StorageKind.SD_CARD, null, null, 0)
        )
        val result = mergeStorageClassifications(listOf(volume), classifications)
        
        assertEquals(1, result.size)
        assertEquals(StorageKind.SD_CARD, result[0].kind)
        assertTrue(result[0].isUserClassified)
    }

    @Test
    fun `test mergeClassifications falls back to canonical path if UUID not in map but path is`() {
        val volume = createVolume("vol2", "uuid:abcd", "/storage/ABCD-1234", isPrimary = false)
        val classifications = mapOf(
            "path:/storage/abcd-1234" to StorageClassification(StorageKind.OTG, null, null, 0)
        )
        val result = mergeStorageClassifications(listOf(volume), classifications)
        
        assertEquals(1, result.size)
        assertEquals(StorageKind.OTG, result[0].kind)
        assertTrue(result[0].isUserClassified)
    }
}
