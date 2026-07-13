package dev.qtremors.arcile.core.storage.domain

import kotlinx.coroutines.flow.Flow

data class BrowserLocationPreferences(
    val globalPresentation: FileListingPreferences = FileListingPreferences(),
    val pathPresentationOptions: Map<String, FileListingPreferences> = emptyMap(),
    val exactPathPresentationOptions: Map<String, FileListingPreferences> = emptyMap(),
    val showHiddenFiles: Boolean = true,
    val lastOpenedPath: String? = null,
    val lastOpenedVolumeId: String? = null,
    val scrollbarEnabled: Boolean = true
) {
    fun getPresentationForPath(path: String): FileListingPreferences {
        var currentPath = path.trimEnd('/').ifEmpty { "/" }
        exactPathPresentationOptions[currentPath]?.let { return it }

        while (currentPath.isNotEmpty()) {
            pathPresentationOptions[currentPath]?.let { return it }
            val lastSlash = currentPath.lastIndexOf('/')
            when {
                lastSlash > 0 -> currentPath = currentPath.substring(0, lastSlash)
                lastSlash == 0 -> {
                    pathPresentationOptions["/"]?.let { return it }
                    break
                }
                else -> break
            }
        }
        return globalPresentation
    }

    fun getPresentationForCategory(categoryName: String): FileListingPreferences {
        val key = "category_$categoryName"
        return exactPathPresentationOptions[key]
            ?: pathPresentationOptions[key]
            ?: globalPresentation.copy(
                sortOption = FileListingPreferences.DEFAULT_CATEGORY_SORT_OPTION
            )
    }

    companion object {
        fun from(preferences: BrowserPreferences) = BrowserLocationPreferences(
            globalPresentation = preferences.globalPresentation,
            pathPresentationOptions = preferences.pathPresentationOptions,
            exactPathPresentationOptions = preferences.exactPathPresentationOptions,
            showHiddenFiles = preferences.showHiddenFiles,
            lastOpenedPath = preferences.lastOpenedPath,
            lastOpenedVolumeId = preferences.lastOpenedVolumeId,
            scrollbarEnabled = preferences.browserScrollbarEnabled
        )
    }
}

interface BrowserLocationPreferencesStore {
    val locationPreferencesFlow: Flow<BrowserLocationPreferences>
    suspend fun updateGlobalPresentation(presentation: FileListingPreferences)
    suspend fun updateShowHiddenFiles(show: Boolean)
    suspend fun updateBrowserScrollbarEnabled(enabled: Boolean)
    suspend fun updatePathPresentation(
        path: String,
        presentation: FileListingPreferences?,
        applyToSubfolders: Boolean = false
    )
    suspend fun updateLastOpenedLocation(path: String, volumeId: String?)
}
