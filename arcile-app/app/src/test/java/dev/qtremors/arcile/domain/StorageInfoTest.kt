package dev.qtremors.arcile.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StorageInfoTest {

    @Test
    fun `storage kind policies expose expected flags`() {
        assertTrue(StorageKind.INTERNAL.isIndexed)
        assertTrue(StorageKind.INTERNAL.supportsTrash)
        assertFalse(StorageKind.INTERNAL.showPermanentDeleteWarning)
        assertFalse(StorageKind.INTERNAL.showTemporaryStorageBadge)

        assertFalse(StorageKind.OTG.isIndexed)
        assertFalse(StorageKind.OTG.supportsTrash)
        assertTrue(StorageKind.OTG.showPermanentDeleteWarning)
        assertTrue(StorageKind.OTG.showTemporaryStorageBadge)
    }

    @Test
    fun `StorageVolume defaults external storage to unclassified when not primary`() {
        val volume = StorageVolume(
            id = "sd-1",
            storageKey = "sd-1",
            name = "Card",
            path = "/storage/1234-5678",
            totalBytes = 128_000L,
            freeBytes = 64_000L,
            isPrimary = false,
            isRemovable = true
        )

        assertEquals(StorageKind.EXTERNAL_UNCLASSIFIED, volume.kind)
    }

    @Test
    fun `StorageVolume defaults primary storage to internal`() {
        val volume = StorageVolume(
            id = "internal",
            storageKey = "primary",
            name = "Internal",
            path = "/storage/emulated/0",
            totalBytes = 256_000L,
            freeBytes = 128_000L,
            isPrimary = true,
            isRemovable = false
        )

        assertEquals(StorageKind.INTERNAL, volume.kind)
    }

    @Test
    fun `StorageInfo aggregates only indexed volumes and exposes primary volume`() {
        val internal = StorageVolume(
            id = "internal",
            storageKey = "primary",
            name = "Internal",
            path = "/storage/emulated/0",
            totalBytes = 1000L,
            freeBytes = 250L,
            isPrimary = true,
            isRemovable = false
        )
        val sdCard = StorageVolume(
            id = "sd",
            storageKey = "sd",
            name = "SD Card",
            path = "/storage/1234-5678",
            totalBytes = 2000L,
            freeBytes = 750L,
            isPrimary = false,
            isRemovable = true,
            kind = StorageKind.SD_CARD
        )
        val otg = StorageVolume(
            id = "otg",
            storageKey = "otg",
            name = "USB",
            path = "/storage/otg",
            totalBytes = 4000L,
            freeBytes = 4000L,
            isPrimary = false,
            isRemovable = true,
            kind = StorageKind.OTG
        )

        val storageInfo = StorageInfo(listOf(internal, sdCard, otg))

        assertEquals(3000L, storageInfo.totalBytes)
        assertEquals(1000L, storageInfo.freeBytes)
        assertEquals(internal, storageInfo.primaryVolume)
    }

    @Test
    fun `StorageInfo handles empty volume lists`() {
        val storageInfo = StorageInfo(emptyList())

        assertEquals(0L, storageInfo.totalBytes)
        assertEquals(0L, storageInfo.freeBytes)
        assertEquals(null, storageInfo.primaryVolume)
    }
}
