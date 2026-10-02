package dev.qtremors.arcile.core.storage.domain

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class StoredNamingCompatibilityTest {
    @Test
    fun `saved quick access URIs retain their existing path field`() {
        val legacy = """{"id":"saf","label":"Documents","path":"content://provider/tree/42","type":"SAF_TREE"}"""
        val item = Json.decodeFromString<QuickAccessItem>(legacy)

        assertEquals("content://provider/tree/42", item.targetReference)
        val stored = Json.parseToJsonElement(Json.encodeToString(item)).jsonObject
        assertEquals(item.targetReference, stored.getValue("path").jsonPrimitive.content)
        assertFalse(stored.containsKey("targetReference"))
    }

    @Test
    fun `pending operation progress retains its existing byte field`() {
        val legacy = """{"completedItems":0,"totalItems":1,"bytesCopied":42,"totalBytes":100}"""
        val progress = Json.decodeFromString<FileOperationProgress>(legacy)

        assertEquals(42L, progress.bytesProcessed)
        val stored = Json.parseToJsonElement(Json.encodeToString(progress)).jsonObject
        assertEquals("42", stored.getValue("bytesCopied").jsonPrimitive.content)
        assertFalse(stored.containsKey("bytesProcessed"))
    }
}
