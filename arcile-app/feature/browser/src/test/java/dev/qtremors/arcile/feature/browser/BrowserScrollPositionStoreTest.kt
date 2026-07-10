package dev.qtremors.arcile.feature.browser

import androidx.lifecycle.SavedStateHandle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BrowserScrollPositionStoreTest {

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
    fun `clear removes a persisted position`() {
        val handle = SavedStateHandle()
        val key = "folder"
        val store = BrowserScrollPositionStore(handle)
        store.save(key, BrowserScrollPosition(1, 2, 3, 4))

        store.clear(key)

        assertNull(BrowserScrollPositionStore(handle).get(key))
    }
}
