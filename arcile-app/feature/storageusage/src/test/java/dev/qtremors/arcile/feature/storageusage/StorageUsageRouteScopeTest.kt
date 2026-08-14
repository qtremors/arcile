package dev.qtremors.arcile.feature.storageusage

import dev.qtremors.arcile.core.storage.domain.StorageNodeRef
import dev.qtremors.arcile.navigation.AppRoutes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StorageUsageRouteScopeTest {

    @Test
    fun `local dashboard scope reconstructs a local node`() {
        val node = AppRoutes.StorageDashboard(
            volumeId = "primary",
            scopePath = "/storage/emulated/0",
            scopeBackendId = StorageNodeRef.LOCAL_BACKEND_ID
        ).toStorageNodeRef()

        requireNotNull(node)
        assertEquals(StorageNodeRef.LOCAL_BACKEND_ID, node.backendId)
        assertEquals("/storage/emulated/0", node.displayPath.absolutePath)
        assertNull(node.backendIdentity)
    }

    @Test
    fun `root dashboard scope preserves backend identity independently of volume`() {
        val node = AppRoutes.StorageDashboard(
            volumeId = "primary",
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
    fun `shizuku dashboard scope preserves backend identity`() {
        val node = AppRoutes.StorageDashboard(
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
    fun `missing backend id keeps legacy dashboard scope local`() {
        val node = AppRoutes.StorageDashboard(
            scopePath = "/storage/emulated/0/Documents"
        ).toStorageNodeRef()

        requireNotNull(node)
        assertEquals(StorageNodeRef.LOCAL_BACKEND_ID, node.backendId)
        assertEquals("/storage/emulated/0/Documents", node.displayPath.absolutePath)
    }

    @Test
    fun `protected scope without identity is rejected before scanning`() {
        assertNull(
            AppRoutes.StorageDashboard(
                scopePath = "/data/local/tmp",
                scopeBackendId = StorageNodeRef.ROOT_BACKEND_ID
            ).toStorageNodeRef()
        )
        assertNull(
            AppRoutes.StorageDashboard(
                scopePath = "/storage/emulated/0/Android/data",
                scopeBackendId = StorageNodeRef.SHIZUKU_BACKEND_ID,
                scopeBackendIdentity = " "
            ).toStorageNodeRef()
        )
    }

    @Test
    fun `blank path and unsupported backend are rejected`() {
        assertNull(AppRoutes.StorageDashboard(scopePath = "").toStorageNodeRef())
        assertNull(
            AppRoutes.StorageDashboard(
                scopePath = "/opaque/node",
                scopeBackendId = StorageNodeRef.MEDIA_STORE_BACKEND_ID,
                scopeBackendIdentity = "mediastore:external:42"
            ).toStorageNodeRef()
        )
    }
}
