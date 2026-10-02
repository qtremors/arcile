package dev.qtremors.arcile.core.operation.android

import dev.qtremors.arcile.core.operation.BulkFileOperationRequest
import dev.qtremors.arcile.core.operation.BulkFileOperationType
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class SyntheticOperationCompatibilityTest {
    @Test
    fun `pending synthetic requests retain the old serialized operation and size`() {
        val legacy = """{"operationId":"pending","type":"CREATE_FAKE","sourcePaths":[],"destinationPath":"/files/output.bin","fakeFileSize":1024}"""
        val request = Json.decodeFromString<BulkFileOperationRequest>(legacy)

        assertEquals(BulkFileOperationType.CREATE_SYNTHETIC, request.type)
        assertEquals(1024L, request.syntheticFileSize)
        val stored = Json.parseToJsonElement(Json.encodeToString(request)).jsonObject
        assertEquals("CREATE_FAKE", stored.getValue("type").jsonPrimitive.content)
        assertEquals("1024", stored.getValue("fakeFileSize").jsonPrimitive.content)
        assertFalse(stored.containsKey("syntheticFileSize"))
    }
}
