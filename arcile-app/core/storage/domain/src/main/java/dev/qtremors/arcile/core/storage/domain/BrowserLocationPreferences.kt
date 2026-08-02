package dev.qtremors.arcile.core.storage.domain

import kotlinx.coroutines.flow.Flow

data class BrowserLocationPreferences(
    val appStartPage: AppStartPage = AppStartPage.HOME,
    val globalPresentation: FileListingPreferences = FileListingPreferences(),
    val pathPresentationOptions: Map<String, FileListingPreferences> = emptyMap(),
    val exactPathPresentationOptions: Map<String, FileListingPreferences> = emptyMap(),
    val showHiddenFiles: Boolean = true,
    val lastOpenedPath: String? = null,
    val lastOpenedVolumeId: String? = null,
    val fileOpenBehaviors: Map<String, FileOpenBehavior> = emptyMap(),
    val scrollbarEnabled: Boolean = true,
    val categoryGroupings: Map<String, CategoryGrouping> = emptyMap(),
    val categoryDefaultPages: Map<String, CategoryLibraryPage> = emptyMap(),
    val categoryShowFileDetails: Map<String, Boolean> = emptyMap()
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

    fun getPresentationForCategory(
        categoryName: String,
        page: CategoryLibraryPage? = null
    ): FileListingPreferences {
        val categoryKey = "category_$categoryName"
        val key = page?.let { "${categoryKey}_${it.preferenceSuffix}" } ?: categoryKey
        return exactPathPresentationOptions[key]
            ?: pathPresentationOptions[key]
            ?: if (page != null) {
                exactPathPresentationOptions[categoryKey] ?: pathPresentationOptions[categoryKey]
            } else {
                null
            }
            ?: globalPresentation.copy(
                sortOption = FileListingPreferences.DEFAULT_CATEGORY_SORT_OPTION
            )
    }

    fun getGroupingForCategory(categoryName: String): CategoryGrouping =
        categoryGroupings[categoryName] ?: CategoryGrouping.MONTH

    fun getDefaultPageForCategory(categoryName: String): CategoryLibraryPage =
        categoryDefaultPages[categoryName] ?: CategoryLibraryPage.ITEMS

    fun getShowFileDetailsForCategory(categoryName: String): Boolean =
        categoryShowFileDetails[categoryName] ?: true

    companion object {
        fun from(preferences: BrowserPreferences) = BrowserLocationPreferences(
            appStartPage = preferences.appStartPage,
            globalPresentation = preferences.globalPresentation,
            pathPresentationOptions = preferences.pathPresentationOptions,
            exactPathPresentationOptions = preferences.exactPathPresentationOptions,
            showHiddenFiles = preferences.showHiddenFiles,
            lastOpenedPath = preferences.lastOpenedPath,
            lastOpenedVolumeId = preferences.lastOpenedVolumeId,
            fileOpenBehaviors = preferences.fileOpenBehaviors,
            scrollbarEnabled = preferences.browserScrollbarEnabled,
            categoryGroupings = preferences.categoryGroupings,
            categoryDefaultPages = preferences.categoryDefaultPages,
            categoryShowFileDetails = preferences.categoryShowFileDetails
        )
    }
}

interface BrowserLocationPreferencesStore {
    val locationPreferencesFlow: Flow<BrowserLocationPreferences>
    suspend fun updateAppStartPage(page: AppStartPage)
    suspend fun updateGlobalPresentation(presentation: FileListingPreferences)
    suspend fun updateShowHiddenFiles(show: Boolean)
    suspend fun updateBrowserScrollbarEnabled(enabled: Boolean)
    suspend fun updatePathPresentation(
        path: String,
        presentation: FileListingPreferences?,
        applyToSubfolders: Boolean = false
    )
    suspend fun updateLastOpenedLocation(path: String, volumeId: String?)
    suspend fun updateFileOpenBehavior(categoryName: String, behavior: FileOpenBehavior)
    suspend fun updateCategoryGrouping(
        categoryName: String,
        grouping: CategoryGrouping
    )
    suspend fun updateCategoryDefaultPage(
        categoryName: String,
        page: CategoryLibraryPage
    )
    suspend fun updateCategoryShowFileDetails(categoryName: String, show: Boolean)
}
