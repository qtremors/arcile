package dev.qtremors.arcile.core.operation.android

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.qtremors.arcile.core.operation.BulkFileOperationRequest
import dev.qtremors.arcile.core.operation.BulkFileOperationType
import dev.qtremors.arcile.core.operation.OperationStorageNodeRef
import dev.qtremors.arcile.core.storage.domain.StorageNodeCapabilities
import dev.qtremors.arcile.core.storage.domain.StorageNodeRef
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class OperationRequestStoreTest {
    private lateinit var store: OperationRequestStore

    @Before
    fun setup() {
        store = OperationRequestStore(ApplicationProvider.getApplicationContext<Context>())
        store.clearForTest()
    }

    @Test
    fun `maximum selection is stored and can only be claimed once`() {
        val request = request((0 until 2_000).map { "/storage/root/${it.toString().padStart(4, '0')}/${"x".repeat(160)}" })

        assertTrue(store.store(request))
        assertEquals(request, store.claim(request.operationId))
        assertNull(store.claim(request.operationId))
    }

    @Test
    fun `selection above the item limit is rejected before storage`() {
        val request = request((0..MAX_OPERATION_SOURCE_ITEMS).map { "/storage/$it" })

        assertFalse(store.store(request))
        assertNull(store.claim(request.operationId))
    }

    @Test
    fun `privileged node references survive durable handoff exactly`() {
        val source = StorageNodeRef.root(
            displayPath = "/data/user/0/example/files/report.txt",
            remoteCanonicalIdentity = "/data/user/0/example/files/report.txt",
            volumeId = "private-data",
            capabilities = StorageNodeCapabilities(
                canRead = true,
                canWrite = false,
                canDelete = false,
                canTrash = false,
                canArchive = true,
                canRename = false,
                canCopy = true,
                canMove = false,
                canExport = true,
                canShare = false,
                canOpenWith = false
            )
        )
        val destination = StorageNodeRef.shizuku(
            displayPath = "/storage/emulated/0/Documents",
            remoteCanonicalIdentity = "/storage/emulated/0/Documents"
        )
        val request = BulkFileOperationRequest(
            operationId = "node-round-trip",
            type = BulkFileOperationType.COPY,
            sourcePaths = listOf(source.displayPath.absolutePath),
            destinationPath = destination.displayPath.absolutePath,
            sourceNodeRefs = listOf(OperationStorageNodeRef.from(source)),
            destinationNodeRef = OperationStorageNodeRef.from(destination)
        )

        assertTrue(store.store(request))
        val restored = requireNotNull(store.claim(request.operationId))

        assertEquals(request, restored)
        assertEquals(listOf(source), restored.sourceRefs)
        assertEquals(destination, restored.destinationRef)
        assertEquals(StorageNodeRef.ROOT_BACKEND_ID, restored.sourceRefs.single().backendId)
        assertFalse(restored.sourceRefs.single().capabilities.canWrite)
        assertFalse(restored.sourceRefs.single().capabilities.canShare)
    }

    @Test
    fun `legacy path-only request restores local node references`() {
        val request = BulkFileOperationRequest(
            operationId = "legacy-path-request",
            type = BulkFileOperationType.MOVE,
            sourcePaths = listOf("/storage/emulated/0/Download/one.txt", "/storage/emulated/0/Download/two.txt"),
            destinationPath = "/storage/emulated/0/Documents"
        )

        assertTrue(store.store(request))
        val restored = requireNotNull(store.claim(request.operationId))

        assertEquals(2, restored.sourceRefs.size)
        assertTrue(restored.sourceRefs.all { it.backendId == StorageNodeRef.LOCAL_BACKEND_ID })
        assertEquals(StorageNodeRef.LOCAL_BACKEND_ID, restored.destinationRef?.backendId)
    }

    @Test
    fun `node count must match compatibility path count`() {
        val request = BulkFileOperationRequest(
            operationId = "mismatched-node-count",
            type = BulkFileOperationType.DELETE,
            sourcePaths = listOf("/one", "/two"),
            sourceNodeRefs = listOf(OperationStorageNodeRef.from(StorageNodeRef.root("/one", "/one")))
        )

        assertFalse(store.store(request))
        assertNull(store.claim(request.operationId))
    }

    @Test
    fun `oversized backend identity is rejected before writing request`() {
        val oversized = OperationStorageNodeRef(
            backendId = StorageNodeRef.ROOT_BACKEND_ID,
            displayPath = "/storage/emulated/0/file.txt",
            canonicalIdentity = "root:/storage/emulated/0/file.txt",
            backendIdentity = "/" + "x".repeat(4_096)
        )
        val request = BulkFileOperationRequest(
            operationId = "oversized-node-field",
            type = BulkFileOperationType.DELETE,
            sourcePaths = listOf(oversized.displayPath),
            sourceNodeRefs = listOf(oversized)
        )

        assertFalse(store.store(request))
        assertNull(store.claim(request.operationId))
    }

    @Test
    fun `semantically invalid node reference is rejected`() {
        val malformed = OperationStorageNodeRef(
            backendId = StorageNodeRef.ROOT_BACKEND_ID,
            displayPath = "/storage/emulated/0/file.txt",
            canonicalIdentity = "",
            backendIdentity = "/storage/emulated/0/file.txt"
        )
        val request = BulkFileOperationRequest(
            operationId = "malformed-node",
            type = BulkFileOperationType.DELETE,
            sourcePaths = listOf(malformed.displayPath),
            sourceNodeRefs = listOf(malformed)
        )

        assertFalse(store.store(request))
        assertNull(store.claim(request.operationId))
    }

    @Test
    fun `retire removes an unclaimed node request`() {
        val source = StorageNodeRef.root("/storage/emulated/0/a.txt", "/storage/emulated/0/a.txt")
        val request = BulkFileOperationRequest(
            operationId = "retired-node-request",
            type = BulkFileOperationType.DELETE,
            sourcePaths = listOf(source.displayPath.absolutePath),
            sourceNodeRefs = listOf(OperationStorageNodeRef.from(source))
        )

        assertTrue(store.store(request))
        store.retire(request.operationId)

        assertNull(store.claim(request.operationId))
    }

    private fun request(paths: List<String>) = BulkFileOperationRequest(
        operationId = "bounded-operation",
        type = BulkFileOperationType.DELETE,
        sourcePaths = paths
    )
}
