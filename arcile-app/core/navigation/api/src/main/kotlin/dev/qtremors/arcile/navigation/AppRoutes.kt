package dev.qtremors.arcile.navigation

import kotlinx.serialization.Serializable

object AppRoutes {
    const val IMAGE_VIEWER_CONTEXT_PATHS_KEY = "imageViewerContextPaths"
    const val IMAGE_VIEWER_CONTEXT_NAMES_KEY = "imageViewerContextNames"
    const val IMAGE_VIEWER_CONTEXT_EXTENSIONS_KEY = "imageViewerContextExtensions"
    const val IMAGE_VIEWER_CONTEXT_MIME_TYPES_KEY = "imageViewerContextMimeTypes"
    const val IMAGE_VIEWER_CONTEXT_SIZES_KEY = "imageViewerContextSizes"
    const val IMAGE_VIEWER_CONTEXT_MODIFIED_KEY = "imageViewerContextModified"
    const val IMAGE_VIEWER_SELECTION_PATHS_KEY = "imageViewerSelectionPaths"
    const val IMAGE_VIEWER_RETURN_SELECTION_PATHS_KEY = "imageViewerReturnSelectionPaths"
    const val MEDIA_VIEWER_RETURN_PATH_KEY = "image_viewer.return_path"

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
    @Serializable object OnlyFiles
    @Serializable object Settings
    @Serializable object Plugins
    @Serializable object Trash
    @Serializable data class RecentFiles(val volumeId: String? = null)
    @Serializable data class ImageGallery(
        val volumeId: String? = null,
        val categoryName: String = "Images"
    )
    @Serializable data class ImageViewer(
        val initialPath: String,
        val albumPath: String? = null,
        val searchQuery: String? = null,
        val volumeId: String? = null,
        val returnToBrowserPage: Boolean = false,
        val managedTrash: Boolean = false
    )
    @Serializable data class VideoViewer(val sessionToken: String)
    @Serializable data class StorageDashboard(val volumeId: String? = null)
    @Serializable object StorageCleaner
    @Serializable object StorageManagement
    @Serializable object QuickAccess
    @Serializable data class ArchiveViewer(val archivePath: String)
    @Serializable object About
    @Serializable object Licenses
}
