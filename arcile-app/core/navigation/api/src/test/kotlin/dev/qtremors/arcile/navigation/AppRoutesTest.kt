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
        assertEquals(AppRoutes.ActivityLog, json.decodeFromString<AppRoutes.ActivityLog>(json.encodeToString(AppRoutes.ActivityLog)))
        assertEquals(AppRoutes.Settings, json.decodeFromString<AppRoutes.Settings>(json.encodeToString(AppRoutes.Settings)))
        assertEquals(AppRoutes.Plugins, json.decodeFromString<AppRoutes.Plugins>(json.encodeToString(AppRoutes.Plugins)))
        assertEquals(AppRoutes.Trash, json.decodeFromString<AppRoutes.Trash>(json.encodeToString(AppRoutes.Trash)))
        assertEquals(
            AppRoutes.StorageCleanerOverview,
            json.decodeFromString<AppRoutes.StorageCleanerOverview>(json.encodeToString(AppRoutes.StorageCleanerOverview))
        )
        assertEquals(
            AppRoutes.StorageManagement,
            json.decodeFromString<AppRoutes.StorageManagement>(json.encodeToString(AppRoutes.StorageManagement))
        )
        assertEquals(
            AppRoutes.QuickAccess,
            json.decodeFromString<AppRoutes.QuickAccess>(json.encodeToString(AppRoutes.QuickAccess))
        )
        assertEquals(AppRoutes.About, json.decodeFromString<AppRoutes.About>(json.encodeToString(AppRoutes.About)))
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
        val browserImageViewer = AppRoutes.ImageViewer(
            initialPath = "/storage/emulated/0/DCIM/photo.jpg",
            returnToBrowserPage = true
        )
        val externalBrowserEntry = AppRoutes.Main(
            initialPage = 1,
            path = "/storage/emulated/0/Download",
            focusPath = "/storage/emulated/0/Download/report.pdf",
            seedInitialPathHistory = false
        )

        assertEquals(recent, json.decodeFromString<AppRoutes.RecentFiles>(json.encodeToString(recent)))
        assertEquals(dashboard, json.decodeFromString<AppRoutes.StorageDashboard>(json.encodeToString(dashboard)))
        assertEquals(browserImageViewer, json.decodeFromString<AppRoutes.ImageViewer>(json.encodeToString(browserImageViewer)))
        val videoViewer = AppRoutes.VideoViewer("opaque-session-token")
        assertEquals(videoViewer, json.decodeFromString<AppRoutes.VideoViewer>(json.encodeToString(videoViewer)))
        val audioLibrary = AppRoutes.AudioLibrary("primary")
        assertEquals(audioLibrary, json.decodeFromString<AppRoutes.AudioLibrary>(json.encodeToString(audioLibrary)))
        val documents = AppRoutes.DocumentLibrary("primary")
        assertEquals(documents, json.decodeFromString<AppRoutes.DocumentLibrary>(json.encodeToString(documents)))
        val apks = AppRoutes.ApkLibrary("primary")
        val cleanerGroup = AppRoutes.StorageCleanerGroup("Junk")
        assertEquals(apks, json.decodeFromString<AppRoutes.ApkLibrary>(json.encodeToString(apks)))
        assertEquals(
            cleanerGroup,
            json.decodeFromString<AppRoutes.StorageCleanerGroup>(json.encodeToString(cleanerGroup))
        )
        assertEquals(externalBrowserEntry, json.decodeFromString<AppRoutes.Main>(json.encodeToString(externalBrowserEntry)))
    }

    @Test
    fun `serializes backend aware storage tool routes without losing identity`() {
        val dashboard = AppRoutes.StorageDashboard(
            volumeId = "primary",
            scopePath = "/storage/emulated/0/Android/data",
            scopeBackendId = "shizuku",
            scopeBackendIdentity = "shizuku-user-0:/storage/emulated/0/Android/data"
        )
        val cleaner = AppRoutes.StorageCleaner(
            scopePath = "/data/local/tmp",
            scopeBackendId = "root",
            scopeBackendIdentity = "root-device:/data/local/tmp"
        )

        assertEquals(
            dashboard,
            json.decodeFromString<AppRoutes.StorageDashboard>(json.encodeToString(dashboard))
        )
        assertEquals(
            cleaner,
            json.decodeFromString<AppRoutes.StorageCleaner>(json.encodeToString(cleaner))
        )
    }

    @Test
    fun `storage tool route defaults remain local volume compatible`() {
        val dashboard = AppRoutes.StorageDashboard()
        val cleaner = AppRoutes.StorageCleaner()

        assertEquals(dashboard, json.decodeFromString<AppRoutes.StorageDashboard>(json.encodeToString(dashboard)))
        assertEquals(cleaner, json.decodeFromString<AppRoutes.StorageCleaner>(json.encodeToString(cleaner)))
        assertEquals(null, dashboard.scopePath)
        assertEquals(null, dashboard.scopeBackendIdentity)
        assertEquals(null, cleaner.scopeBackendId)
    }
}
