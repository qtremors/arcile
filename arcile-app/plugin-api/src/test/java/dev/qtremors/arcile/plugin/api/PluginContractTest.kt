package dev.qtremors.arcile.plugin.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PluginContractTest {
    @Test
    fun `contract uses stable namespaced values`() {
        assertEquals(1, PluginContract.PLUGIN_API_VERSION)
        assertEquals("dev.qtremors.arcile.plugin.REGISTER", PluginContract.ACTION_REGISTER)
        assertEquals("dev.qtremors.arcile.plugin.VIEW_FILE", PluginContract.ACTION_VIEW_FILE)
        assertTrue(PluginContract.EXTRA_FILE_URI.startsWith("dev.qtremors.arcile.plugin.extra."))
    }
}
