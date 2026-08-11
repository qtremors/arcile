package dev.qtremors.arcile.core.storage.data.util

import dev.qtremors.arcile.core.storage.domain.StorageKind
import dev.qtremors.arcile.core.storage.domain.StorageVolume
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VolumeSelectionTest {
    @Test
    fun `Android path resolves independently of desktop separator`() {
        val primary = volume("primary", "/storage/emulated/0")

        val resolved = resolveVolumeForPath(
            "/storage/emulated/0/Documents/report.txt",
            listOf(primary)
        )

        assertEquals(primary, resolved)
    }

    @Test
    fun `Windows style path uses a real segment boundary`() {
        val drive = volume("drive", "D:\\Files")

        val resolved = resolveVolumeForPath(
            "D:\\Files\\Documents\\report.txt",
            listOf(drive)
        )

        assertEquals(drive, resolved)
    }

    @Test
    fun `similar volume prefix does not capture sibling path`() {
        val primary = volume("primary", "/storage/emulated/0")

        val resolved = resolveVolumeForPath(
            "/storage/emulated/01/Documents/report.txt",
            listOf(primary)
        )

        assertNull(resolved)
    }

    @Test
    fun `exact volume root resolves after trailing separators are normalized`() {
        val primary = volume("primary", "/storage/emulated/0/")

        val resolved = resolveVolumeForPath(
            "/storage/emulated/0",
            listOf(primary)
        )

        assertEquals(primary, resolved)
    }

    @Test
    fun `nested mount wins over its parent volume`() {
        val parent = volume("storage", "/storage")
        val primary = volume("primary", "/storage/emulated/0")

        val resolved = resolveVolumeForPath(
            "/storage/emulated/0/Download/archive.zip",
            listOf(parent, primary)
        )

        assertEquals(primary, resolved)
    }

    @Test
    fun `empty volume list cannot resolve any path`() {
        assertNull(resolveVolumeForPath("/storage/emulated/0/file.txt", emptyList()))
    }

    private fun volume(id: String, path: String) = StorageVolume(
        id = id,
        storageKey = id,
        name = id,
        path = path,
        totalBytes = 1_000L,
        freeBytes = 500L,
        isPrimary = id == "primary",
        isRemovable = false,
        kind = StorageKind.INTERNAL
    )
}
