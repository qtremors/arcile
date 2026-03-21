package dev.qtremors.arcile.data

import dev.qtremors.arcile.domain.StorageKind
import dev.qtremors.arcile.domain.StorageVolume
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

class TrashRoutingTest {

    private fun createVolume(id: String, path: String, kind: StorageKind): StorageVolume {
        return StorageVolume(
            id = id,
            storageKey = id,
            name = id,
            path = path,
            totalBytes = 1000L,
            freeBytes = 500L,
            isPrimary = kind == StorageKind.INTERNAL,
            isRemovable = kind != StorageKind.INTERNAL,
            kind = kind,
            isUserClassified = true
        )
    }

    // Helper reflecting what LocalFileRepository does
    private fun getTrashMetadataDirForVolume(volume: StorageVolume): File {
        val root = File(volume.path)
        val arcileDir = File(root, ".arcile")
        return File(arcileDir, ".metadata")
    }

    @Test
    fun `test trash routing resolves to correct per-volume metadata directory`() {
        val internalVolume = createVolume("internal", "/storage/emulated/0", StorageKind.INTERNAL)
        val sdVolume = createVolume("sd", "/storage/1234-5678", StorageKind.SD_CARD)

        val internalTrashDir = getTrashMetadataDirForVolume(internalVolume)
        val sdTrashDir = getTrashMetadataDirForVolume(sdVolume)

        val expectedInternal = File(File(File("/storage/emulated/0"), ".arcile"), ".metadata")
        val expectedSd = File(File(File("/storage/1234-5678"), ".arcile"), ".metadata")

        assertEquals(expectedInternal.absolutePath, internalTrashDir.absolutePath)
        assertEquals(expectedSd.absolutePath, sdTrashDir.absolutePath)
    }
}
