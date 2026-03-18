package dev.qtremors.arcile.navigation

/**
 * Centralised navigation route constants — eliminates raw string typo risk.
 */
import kotlinx.serialization.Serializable

/**
 * Centralised navigation route definitions — replaces raw strings with type-safe objects.
 */
object AppRoutes {
    @Serializable object Home
    @Serializable data class Explorer(
        val path: String? = null,
        val category: String? = null,
        val volumeId: String? = null
    )
    @Serializable object Tools
    @Serializable object Settings
    @Serializable object Trash
    @Serializable data class RecentFiles(val volumeId: String? = null)
    @Serializable data class StorageDashboard(val volumeId: String? = null)
    @Serializable object StorageManagement
    @Serializable object About
}
