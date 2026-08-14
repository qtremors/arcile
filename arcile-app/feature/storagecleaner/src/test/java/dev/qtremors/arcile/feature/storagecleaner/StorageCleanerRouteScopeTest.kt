package dev.qtremors.arcile.feature.storagecleaner

import dev.qtremors.arcile.core.storage.domain.StorageNodeRef
import dev.qtremors.arcile.navigation.AppRoutes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StorageCleanerRouteScopeTest {

    @Test
    fun `local route scope reconstructs a local node`() {
        val node = AppRoutes.StorageCleaner(
            scopePath = "/storage/emulated/0/Download",
            scopeBackendId = StorageNodeRef.LOCAL_BACKEND_ID
        ).toStorageNodeRef()

        requireNotNull(node)
        assertEquals(StorageNodeRef.LOCAL_BACKEND_ID, node.backendId)
        assertEquals("/storage/emulated/0/Download", node.displayPath.absolutePath)
        assertNull(node.backendIdentity)
    }

    @Test
    fun `missing backend id keeps legacy scoped routes local`() {
        val node = AppRoutes.StorageCleaner(
            scopePath = "/storage/emulated/0/Download"
        ).toStorageNodeRef()

        requireNotNull(node)
        assertEquals(StorageNodeRef.LOCAL_BACKEND_ID, node.backendId)
        assertEquals("/storage/emulated/0/Download", node.displayPath.absolutePath)
    }

    @Test
    fun `root route scope preserves remote canonical identity`() {
        val node = AppRoutes.StorageCleaner(
            scopePath = "/data/local/tmp",
            scopeBackendId = StorageNodeRef.ROOT_BACKEND_ID,
            scopeBackendIdentity = "root-device:/data/local/tmp"
        ).toStorageNodeRef()

        requireNotNull(node)
        assertEquals(StorageNodeRef.ROOT_BACKEND_ID, node.backendId)
        assertEquals("root-device:/data/local/tmp", node.backendIdentity)
        assertEquals("root:root-device:/data/local/tmp", node.canonicalIdentity.value)
    }

    @Test
    fun `shizuku route scope preserves remote canonical identity`() {
        val node = AppRoutes.StorageCleaner(
            scopePath = "/storage/emulated/0/Android/data",
            scopeBackendId = StorageNodeRef.SHIZUKU_BACKEND_ID,
            scopeBackendIdentity = "shizuku-user-0:/storage/emulated/0/Android/data"
        ).toStorageNodeRef()

        requireNotNull(node)
        assertEquals(StorageNodeRef.SHIZUKU_BACKEND_ID, node.backendId)
        assertEquals(
            "shizuku-user-0:/storage/emulated/0/Android/data",
            node.backendIdentity
        )
    }

    @Test
    fun `protected route without remote identity is rejected`() {
        val route = AppRoutes.StorageCleaner(
            scopePath = "/data/local/tmp",
            scopeBackendId = StorageNodeRef.ROOT_BACKEND_ID
        )

        assertNull(route.toStorageNodeRef())
    }

    @Test
    fun `blank path and unsupported backend are rejected`() {
        assertNull(AppRoutes.StorageCleaner(scopePath = " ").toStorageNodeRef())
        assertNull(
            AppRoutes.StorageCleaner(
                scopePath = "/vault/item",
                scopeBackendId = StorageNodeRef.ONLYFILES_BACKEND_ID,
                scopeBackendIdentity = "vault:item"
            ).toStorageNodeRef()
        )
    }
}
