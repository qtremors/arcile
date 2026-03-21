package dev.qtremors.arcile.data

import dev.qtremors.arcile.domain.StorageKind
import dev.qtremors.arcile.domain.StorageScope
import dev.qtremors.arcile.domain.StorageVolume
import org.junit.Assert.assertEquals
import org.junit.Test

class ReclassificationBehaviorTest {

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
    fun `test reclassification adds volume to indexed surfaces`() {
        val initialVolumes = listOf(
            createVolume("internal", StorageKind.INTERNAL),
            createVolume("ext", StorageKind.EXTERNAL_UNCLASSIFIED)
        )

        val initialIndexed = indexedVolumes(initialVolumes)
        assertEquals(1, initialIndexed.size)
        assertEquals("internal", initialIndexed[0].id)

        // Simulate reclassifying to SD_CARD
        val reclassifiedVolumes = listOf(
            createVolume("internal", StorageKind.INTERNAL),
            createVolume("ext", StorageKind.SD_CARD) // user classified as SD
        )

        val newIndexed = indexedVolumes(reclassifiedVolumes)
        assertEquals(2, newIndexed.size)
        assertEquals("internal", newIndexed[0].id)
        assertEquals("ext", newIndexed[1].id)
    }

    @Test
    fun `test reclassification removes volume from indexed surfaces`() {
        val initialVolumes = listOf(
            createVolume("internal", StorageKind.INTERNAL),
            createVolume("sd", StorageKind.SD_CARD)
        )

        val initialIndexed = indexedVolumes(initialVolumes)
        assertEquals(2, initialIndexed.size)

        // Simulate reclassifying to OTG
        val reclassifiedVolumes = listOf(
            createVolume("internal", StorageKind.INTERNAL),
            createVolume("sd", StorageKind.OTG) // user reset to OTG
        )

        val newIndexed = indexedVolumes(reclassifiedVolumes)
        assertEquals(1, newIndexed.size)
        assertEquals("internal", newIndexed[0].id)
    }
}
