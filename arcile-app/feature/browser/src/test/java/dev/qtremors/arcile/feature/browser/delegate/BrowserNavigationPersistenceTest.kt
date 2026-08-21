package dev.qtremors.arcile.feature.browser.delegate

import androidx.lifecycle.SavedStateHandle
import dev.qtremors.arcile.core.storage.domain.StorageBrowserLocation
import dev.qtremors.arcile.core.storage.domain.StorageNodePath
import org.junit.Assert.assertEquals
import org.junit.Test

class BrowserNavigationPersistenceTest {
    @Test
    fun `restores direct filesystem location without volume id`() {
        val savedStateHandle = SavedStateHandle(
            mapOf(
                "currentPath" to "/system",
                "currentVolumeId" to null,
                "isVolumeRootScreen" to false,
                "isCategoryScreen" to false
            )
        )

        val restored = BrowserNavigationPersistence(savedStateHandle).restoreLocation()

        assertEquals(
            StorageBrowserLocation.DirectDirectory(StorageNodePath.of("/system")),
            restored
        )
    }
}
