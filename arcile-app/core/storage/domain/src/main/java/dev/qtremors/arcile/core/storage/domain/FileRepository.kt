package dev.qtremors.arcile.core.storage.domain

class NativeConfirmationRequiredException(
    val intentSender: Any
) : Exception("Native confirmation required")

/**
 * Compatibility facade while features migrate toward narrow storage capabilities.
 */
interface FileRepository :
    FileBrowserRepository,
    SelectionPropertiesRepository,
    FileMutationRepository,
    SearchRepository,
    StorageAnalyticsRepository,
    TrashRepository,
    ArchiveRepository,
    VolumeRepository,
    ClipboardRepository
