package dev.qtremors.arcile.navigation

/**
 * Centralised navigation route constants — eliminates raw string typo risk.
 */
import kotlinx.serialization.Serializable

/**
 * Centralised navigation route definitions — replaces raw strings with type-safe objects.
 */
object AppRoutes {
    @Serializable data class Main(
        val initialPage: Int = 0,
        val path: String? = null,
        val category: String? = null,
        val volumeId: String? = null,
        val restorePersistentLocation: Boolean = true
    )
    @Serializable object Home
    @Serializable data class Explorer(
        val path: String? = null,
        val category: String? = null,
        val volumeId: String? = null,
        val restorePersistentLocation: Boolean = true
    )
    @Serializable object Tools
    @Serializable object Settings
    @Serializable object Trash
    @Serializable data class RecentFiles(val volumeId: String? = null)
    @Serializable data class StorageDashboard(val volumeId: String? = null)
    @Serializable object StorageManagement
    @Serializable object QuickAccess
    @Serializable object About
    @Serializable object Licenses
}
