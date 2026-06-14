package dev.qtremors.arcile.core.storage.domain

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf

interface BrowserPreferencesStore {
    val preferencesFlow: Flow<BrowserPreferences>

    suspend fun updateGlobalPresentation(presentation: BrowserPresentationPreferences)
    suspend fun updateRecentPresentation(presentation: BrowserPresentationPreferences)
    suspend fun updateHomeRecentCarouselLimit(limit: Int)
    suspend fun updateShowHiddenFiles(show: Boolean)
    suspend fun updateImageGalleryShowFileDetails(show: Boolean)
    suspend fun updateImageGalleryAspectRatio(enabled: Boolean)
    suspend fun updateImageGallerySectioned(enabled: Boolean)
    suspend fun updateImageGalleryGrouping(grouping: ImageGalleryGrouping)
    suspend fun updateImageGalleryDefaultTab(tab: ImageGalleryDefaultTab)
    suspend fun updateAlbumPresentation(presentation: BrowserPresentationPreferences)
    suspend fun updateAlbumAspectRatio(enabled: Boolean)
    suspend fun updatePathPresentation(
        path: String,
        presentation: BrowserPresentationPreferences?,
        applyToSubfolders: Boolean = false
    )
    suspend fun updateLastOpenedLocation(path: String, volumeId: String?)
    suspend fun updateFavorite(path: String, isFavorite: Boolean)
    suspend fun updateAlbumCover(albumPath: String, coverPath: String)
}

interface OnboardingPreferencesStore {
    val preferencesFlow: Flow<OnboardingPreferences>

    suspend fun markCompleted(completedVersion: Int, notificationPermissionHandled: Boolean)
    suspend fun markNotificationPermissionHandled()
    suspend fun resetOnboarding()
}

interface QuickAccessPreferencesStore {
    val quickAccessItems: Flow<List<QuickAccessItem>>

    suspend fun updateItems(items: List<QuickAccessItem>)
    suspend fun addItem(item: QuickAccessItem)
    suspend fun removeItem(id: String)
}

interface UtilityPreferencesStore {
    val homeUtilityIds: Flow<Set<String>>

    suspend fun setHomeUtilityIds(ids: Set<String>)
}

object NoOpUtilityPreferencesStore : UtilityPreferencesStore {
    override val homeUtilityIds: Flow<Set<String>> = flowOf(setOf("trash", "cleaner"))

    override suspend fun setHomeUtilityIds(ids: Set<String>) = Unit
}

interface StorageCleanerPreferencesStore {
    val rulesFlow: Flow<StorageCleanerRules>

    suspend fun updateRules(rules: StorageCleanerRules)
    suspend fun updateSectionRule(type: CleanerGroupType, rule: CleanerSectionRule)
    suspend fun ignorePath(path: String)
    suspend fun unignorePath(path: String)
    suspend fun resetSection(type: CleanerGroupType)
}

object NoOpStorageCleanerPreferencesStore : StorageCleanerPreferencesStore {
    override val rulesFlow: Flow<StorageCleanerRules> = flowOf(StorageCleanerRules())

    override suspend fun updateRules(rules: StorageCleanerRules) = Unit
    override suspend fun updateSectionRule(type: CleanerGroupType, rule: CleanerSectionRule) = Unit
    override suspend fun ignorePath(path: String) = Unit
    override suspend fun unignorePath(path: String) = Unit
    override suspend fun resetSection(type: CleanerGroupType) = Unit
}

interface StorageClassificationStore {
    fun observeClassifications(): Flow<Map<String, StorageClassification>>
    suspend fun getClassification(storageKey: String): StorageClassification?
    suspend fun setClassification(
        storageKey: String,
        kind: StorageKind,
        lastSeenName: String? = null,
        lastSeenPath: String? = null
    )
    suspend fun resetClassification(storageKey: String)
}

interface StorageWorkCoordinator {
    val isMutationActive: StateFlow<Boolean>
    fun beginMutation()
    fun endMutation()
    suspend fun awaitLowPrioritySlot()
}

interface StorageUsageScanner {
    fun scanStorageUsage(
        rootPath: String,
        limits: StorageUsageScanLimits = StorageUsageScanLimits()
    ): Flow<StorageUsageScanState>

    fun invalidateStorageUsage(paths: Collection<String> = emptyList())
}

interface StorageCleanerScanner {
    suspend fun cachedScan(
        rootPaths: List<String>,
        limits: StorageCleanerScanLimits = StorageCleanerScanLimits(),
        rules: StorageCleanerRules = StorageCleanerRules()
    ): StorageCleanerResult? = null

    suspend fun scan(
        rootPaths: List<String>,
        now: Long = System.currentTimeMillis(),
        limits: StorageCleanerScanLimits = StorageCleanerScanLimits(),
        rules: StorageCleanerRules = StorageCleanerRules()
    ): StorageCleanerResult

    suspend fun invalidateStorageCleaner(paths: Collection<String> = emptyList()) = Unit
}
