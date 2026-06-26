package dev.qtremors.arcile.navigation

import kotlinx.serialization.Serializable

object AppRoutes {
    const val IMAGE_VIEWER_CONTEXT_PATHS_KEY = "imageViewerContextPaths"

    @Serializable data class Main(
        val initialPage: Int = 0,
        val path: String? = null,
        val archivePath: String? = null,
        val category: String? = null,
        val volumeId: String? = null,
        val focusPath: String? = null,
        val restorePersistentLocation: Boolean = true,
        val seedInitialPathHistory: Boolean = true
    )
    @Serializable object Home
    @Serializable data class Explorer(
        val path: String? = null,
        val category: String? = null,
        val volumeId: String? = null,
        val restorePersistentLocation: Boolean = true
    )
    @Serializable object Tools
    @Serializable object ActivityLog
    @Serializable object Settings
    @Serializable object Plugins
    @Serializable object Trash
    @Serializable data class RecentFiles(val volumeId: String? = null)
    @Serializable data class ImageGallery(val volumeId: String? = null)
    @Serializable data class ImageViewer(
        val initialPath: String,
        val albumPath: String? = null,
        val searchQuery: String? = null,
        val volumeId: String? = null,
        val returnToBrowserPage: Boolean = false
    )
    @Serializable data class StorageDashboard(val volumeId: String? = null)
    @Serializable object StorageCleaner
    @Serializable object StorageManagement
    @Serializable object QuickAccess
    @Serializable data class ArchiveViewer(val archivePath: String)
    @Serializable object About
    @Serializable object Licenses
}
