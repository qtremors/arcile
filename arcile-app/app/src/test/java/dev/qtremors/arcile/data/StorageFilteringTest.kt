package dev.qtremors.arcile.data

import dev.qtremors.arcile.data.util.*

import dev.qtremors.arcile.domain.StorageKind
import dev.qtremors.arcile.domain.StorageScope
import dev.qtremors.arcile.domain.StorageVolume
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StorageFilteringTest {

    private fun createVolume(id: String, kind: StorageKind): StorageVolume {
        return StorageVolume(
            id = id,
            storageKey = id,
            name = id,
            path = "/storage/$id",
            totalBytes = 1000L,
            freeBytes = 500L,
            isPrimary = kind == StorageKind.INTERNAL,
            isRemovable = kind != StorageKind.INTERNAL,
            kind = kind,
            isUserClassified = true
        )
    }

    @Test
    fun `test indexedVolumes filters only indexed volumes`() {
        val volumes = listOf(
            createVolume("internal", StorageKind.INTERNAL),
            createVolume("sd", StorageKind.SD_CARD),
            createVolume("otg", StorageKind.OTG),
            createVolume("unclass", StorageKind.EXTERNAL_UNCLASSIFIED)
        )

        val indexed = indexedVolumes(volumes)
        assertEquals(2, indexed.size)
        assertTrue(indexed.any { it.id == "internal" })
        assertTrue(indexed.any { it.id == "sd" })
    }

    @Test
    fun `test trashEnabledVolumes filters correctly`() {
        val volumes = listOf(
            createVolume("internal", StorageKind.INTERNAL),
            createVolume("sd", StorageKind.SD_CARD),
            createVolume("otg", StorageKind.OTG),
            createVolume("unclass", StorageKind.EXTERNAL_UNCLASSIFIED)
        )

        val trashEnabled = trashEnabledVolumes(volumes)
        assertEquals(2, trashEnabled.size)
        assertTrue(trashEnabled.any { it.id == "internal" })
        assertTrue(trashEnabled.any { it.id == "sd" })
    }

    @Test
    fun `test indexedVolumesForScope with AllStorage scope`() {
        val volumes = listOf(
            createVolume("internal", StorageKind.INTERNAL),
            createVolume("sd", StorageKind.SD_CARD),
            createVolume("otg", StorageKind.OTG)
        )

        val result = indexedVolumesForScope(StorageScope.AllStorage, volumes)
        assertEquals(2, result.size)
    }

    @Test
    fun `test indexedVolumesForScope with Volume scope`() {
        val volumes = listOf(
            createVolume("internal", StorageKind.INTERNAL),
            createVolume("sd", StorageKind.SD_CARD),
            createVolume("otg", StorageKind.OTG)
        )

        // Scoped to an indexed volume
        val resultSd = indexedVolumesForScope(StorageScope.Volume("sd"), volumes)
        assertEquals(1, resultSd.size)
        assertEquals("sd", resultSd[0].id)

        // Scoped to a non-indexed volume
        val resultOtg = indexedVolumesForScope(StorageScope.Volume("otg"), volumes)
        assertEquals(0, resultOtg.size)
    }
}
