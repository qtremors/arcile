package dev.qtremors.arcile.feature.browser

import androidx.lifecycle.SavedStateHandle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BrowserScrollPositionStoreTest {

    @Test
    fun `length-prefixed location keys do not collide on separators`() {
        assertNotEquals(
            browserScrollPositionKey("NAME_ASC", "/folder|", "archive"),
            browserScrollPositionKey("NAME_ASC", "/folder", "|archive")
        )
    }

    @Test
    fun `positions survive recreation including keys containing separators`() {
        val handle = SavedStateHandle()
        val expected = BrowserScrollPosition(3, 14, 5, 92)
        val key = "NAME_ASC:/storage/emulated/0:Pictures"

        BrowserScrollPositionStore(handle).save(key, expected)

        assertEquals(expected, BrowserScrollPositionStore(handle).get(key))
    }

    @Test
    fun `malformed restored entries are ignored`() {
        val handle = SavedStateHandle(
            mapOf(
                "browserScrollPositions" to arrayOf(
                    "not-a-length:key:1:2:3:4",
                    "50:short:1:2:3:4",
                    "3:key:1:2:three:4"
                )
            )
        )

        val store = BrowserScrollPositionStore(handle)

        assertNull(store.get("key"))
    }

    @Test
    fun `different folders retain independent positions`() {
        val handle = SavedStateHandle()
        val first = BrowserScrollPosition(8, 24, 4, 12)
        val second = BrowserScrollPosition(19, 6, 10, 3)
        val store = BrowserScrollPositionStore(handle)

        store.save("NAME_ASC|/storage/Documents||||", first)
        store.save("NAME_ASC|/storage/Download||||", second)

        val restoredStore = BrowserScrollPositionStore(handle)
        assertEquals(first, restoredStore.get("NAME_ASC|/storage/Documents||||"))
        assertEquals(second, restoredStore.get("NAME_ASC|/storage/Download||||"))
    }

    @Test
    fun `recently revisited folder survives cache trimming`() {
        val handle = SavedStateHandle()
        val store = BrowserScrollPositionStore(handle)
        repeat(32) { index ->
            store.save("folder-$index", BrowserScrollPosition(index, 0, index, 0))
        }
        val refreshed = BrowserScrollPosition(99, 7, 99, 7)
        store.save("folder-0", refreshed)
        store.save("folder-32", BrowserScrollPosition(32, 0, 32, 0))

        val restoredStore = BrowserScrollPositionStore(handle)
        assertEquals(refreshed, restoredStore.get("folder-0"))
        assertNull(restoredStore.get("folder-1"))
    }
}
