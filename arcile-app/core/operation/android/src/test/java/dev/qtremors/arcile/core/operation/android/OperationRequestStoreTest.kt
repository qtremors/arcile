package dev.qtremors.arcile.core.operation.android

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.qtremors.arcile.core.operation.BulkFileOperationRequest
import dev.qtremors.arcile.core.operation.BulkFileOperationType
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

    private fun request(paths: List<String>) = BulkFileOperationRequest(
        operationId = "bounded-operation",
        type = BulkFileOperationType.DELETE,
        sourcePaths = paths
    )
}
