package dev.qtremors.arcile.navigation

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class AppRoutesTest {

    private val json = Json

    @Test
    fun `serializes and deserializes object routes`() {
        assertEquals(AppRoutes.Home, json.decodeFromString<AppRoutes.Home>(json.encodeToString(AppRoutes.Home)))
        assertEquals(AppRoutes.Tools, json.decodeFromString<AppRoutes.Tools>(json.encodeToString(AppRoutes.Tools)))
        assertEquals(AppRoutes.Settings, json.decodeFromString<AppRoutes.Settings>(json.encodeToString(AppRoutes.Settings)))
        assertEquals(AppRoutes.Trash, json.decodeFromString<AppRoutes.Trash>(json.encodeToString(AppRoutes.Trash)))
    }

    @Test
    fun `serializes explorer route with nullable arguments`() {
        val route = AppRoutes.Explorer(path = "/storage/emulated/0/Download", category = "Images", volumeId = "primary")

        assertEquals(route, json.decodeFromString<AppRoutes.Explorer>(json.encodeToString(route)))
        assertEquals(AppRoutes.Explorer(), json.decodeFromString<AppRoutes.Explorer>(json.encodeToString(AppRoutes.Explorer())))
    }

    @Test
    fun `serializes other typed routes`() {
        val recent = AppRoutes.RecentFiles(volumeId = "sd")
        val dashboard = AppRoutes.StorageDashboard(volumeId = "primary")

        assertEquals(recent, json.decodeFromString<AppRoutes.RecentFiles>(json.encodeToString(recent)))
        assertEquals(dashboard, json.decodeFromString<AppRoutes.StorageDashboard>(json.encodeToString(dashboard)))
    }
}
