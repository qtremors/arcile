package dev.qtremors.arcile.presentation.ui

import androidx.navigation.NavBackStackEntry
import androidx.navigation.toRoute
import dev.qtremors.arcile.core.storage.domain.ActivityLogPage
import dev.qtremors.arcile.navigation.AppRoutes
import java.io.File

internal data class ActivityPageVisit(
    val page: ActivityLogPage,
    val detail: String? = null
)

internal fun NavBackStackEntry.toActivityPageVisit(): ActivityPageVisit? {
    val routeName = destination.route
        ?.substringAfterLast('.')
        ?.substringBefore('/')
        ?.substringBefore('?')
        ?: return null
    return when (routeName) {
        "Main" -> null
        "Explorer" -> ActivityPageVisit(
            ActivityLogPage.BROWSER,
            runCatching { toRoute<AppRoutes.Explorer>().path }.getOrNull()
        )
        "Home" -> ActivityPageVisit(ActivityLogPage.HOME)
        "Category" -> ActivityPageVisit(
            ActivityLogPage.CATEGORY,
            runCatching { toRoute<AppRoutes.Category>().id }.getOrNull()
        )
        "Tools" -> ActivityPageVisit(ActivityLogPage.TOOLS)
        "ActivityLog" -> ActivityPageVisit(ActivityLogPage.ACTIVITY)
        "OnlyFiles" -> ActivityPageVisit(ActivityLogPage.ONLY_FILES)
        "Settings" -> ActivityPageVisit(ActivityLogPage.SETTINGS)
        "Plugins" -> ActivityPageVisit(ActivityLogPage.PLUGINS)
        "Trash" -> ActivityPageVisit(ActivityLogPage.TRASH)
        "RecentFiles" -> ActivityPageVisit(ActivityLogPage.RECENT_FILES)
        "ImageGallery" -> ActivityPageVisit(
            ActivityLogPage.IMAGE_GALLERY,
            runCatching { toRoute<AppRoutes.MediaGallery>().categoryId }.getOrNull()
        )
        "ImageViewer" -> ActivityPageVisit(
            ActivityLogPage.IMAGE_VIEWER,
            runCatching { File(toRoute<AppRoutes.ImageViewer>().initialPath).name }.getOrNull()
        )
        "VideoViewer" -> ActivityPageVisit(ActivityLogPage.VIDEO_VIEWER)
        "AudioLibrary" -> ActivityPageVisit(ActivityLogPage.AUDIO_LIBRARY)
        "DocumentLibrary" -> ActivityPageVisit(ActivityLogPage.DOCUMENT_LIBRARY)
        "ApkLibrary" -> ActivityPageVisit(ActivityLogPage.APK_LIBRARY)
        "StorageDashboard" -> ActivityPageVisit(ActivityLogPage.STORAGE_DASHBOARD)
        "StorageCleaner", "StorageCleanerOverview", "StorageCleanerGroup" ->
            ActivityPageVisit(ActivityLogPage.STORAGE_CLEANER)
        "StorageManagement" -> ActivityPageVisit(ActivityLogPage.STORAGE_MANAGEMENT)
        "QuickAccess" -> ActivityPageVisit(ActivityLogPage.QUICK_ACCESS)
        "ArchiveViewer" -> ActivityPageVisit(
            ActivityLogPage.ARCHIVE_VIEWER,
            runCatching { File(toRoute<AppRoutes.ArchiveViewer>().archivePath).name }.getOrNull()
        )
        "About" -> ActivityPageVisit(ActivityLogPage.ABOUT)
        "Licenses" -> ActivityPageVisit(ActivityLogPage.LICENSES)
        else -> null
    }
}
