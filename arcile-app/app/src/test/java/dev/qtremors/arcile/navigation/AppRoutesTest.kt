package dev.qtremors.arcile.navigation

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class AppRoutesTest {

    private val json = Json

    @Test
    fun `serializes and deserializes route contracts from app shell`() {
        val route = AppRoutes.Main(
            initialPage = 1,
            path = "/storage/emulated/0/Download",
            seedInitialPathHistory = false
        )

        assertEquals(route, json.decodeFromString<AppRoutes.Main>(json.encodeToString(route)))
        assertEquals(AppRoutes.Trash, json.decodeFromString<AppRoutes.Trash>(json.encodeToString(AppRoutes.Trash)))
        assertEquals(AppRoutes.ArchiveViewer("archive.zip"), json.decodeFromString<AppRoutes.ArchiveViewer>(json.encodeToString(AppRoutes.ArchiveViewer("archive.zip"))))
    }
}
