package dev.qtremors.arcile.core.storage.data.provider

import org.junit.Assert.assertEquals
import org.junit.Test

class VolumeProviderDisplayNameTest {
    @Test
    fun `primary volume always uses Arcile internal storage label`() {
        assertEquals(
            "Internal Storage",
            storageVolumeDisplayName(
                isPrimary = true,
                preferredName = "Internal shared storage",
                fallbackName = "emulated"
            )
        )
    }

    @Test
    fun `secondary volume keeps platform name or fallback`() {
        assertEquals("SD card", storageVolumeDisplayName(false, "SD card", "1234-5678"))
        assertEquals("1234-5678", storageVolumeDisplayName(false, "", "1234-5678"))
    }
}
