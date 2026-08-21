package dev.qtremors.arcile.core.storage.domain

import kotlinx.serialization.Serializable

@Serializable
sealed interface ActivityLogEntry {
    val id: String
    val timestampMillis: Long

    @Serializable
    data class FolderOpened(
        override val id: String,
        override val timestampMillis: Long,
        val path: String,
        val volumeId: String?
    ) : ActivityLogEntry

    @Serializable
    data class FileOpened(
        override val id: String,
        override val timestampMillis: Long,
        val path: String
    ) : ActivityLogEntry

    @Serializable
    data class PageVisited(
        override val id: String,
        override val timestampMillis: Long,
        val page: ActivityLogPage,
        val detail: String? = null
    ) : ActivityLogEntry

    @Serializable
    data class FileOperation(
        override val id: String,
        override val timestampMillis: Long,
        val operationId: String,
        val operationType: String,
        val status: ActivityLogOperationStatus,
        val sourceCount: Int,
        val destinationPath: String? = null,
        val errorMessage: String? = null
    ) : ActivityLogEntry
}

@Serializable
enum class ActivityLogPage {
    HOME,
    BROWSER,
    CATEGORY,
    TOOLS,
    ACTIVITY,
    ONLY_FILES,
    SETTINGS,
    PLUGINS,
    TRASH,
    RECENT_FILES,
    IMAGE_GALLERY,
    IMAGE_VIEWER,
    VIDEO_VIEWER,
    AUDIO_LIBRARY,
    DOCUMENT_LIBRARY,
    APK_LIBRARY,
    STORAGE_DASHBOARD,
    STORAGE_CLEANER,
    STORAGE_MANAGEMENT,
    QUICK_ACCESS,
    ARCHIVE_VIEWER,
    ABOUT,
    LICENSES
}

@Serializable
enum class ActivityLogOperationStatus {
    RUNNING,
    COMPLETED,
    FAILED,
    CANCELLED
}
