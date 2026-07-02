package dev.qtremors.arcile.core.storage.domain

enum class StorageCapability {
    ARCHIVE_LIST,
    ARCHIVE_METADATA,
    ARCHIVE_EXTRACT,
    ARCHIVE_CONFLICT_DETECTION,
    ARCHIVE_CREATE
}

class UnsupportedStorageCapabilityException(
    val capability: StorageCapability
) : UnsupportedOperationException("Storage capability is unavailable: $capability")

internal fun <T> unsupportedCapability(
    capability: StorageCapability
): Result<T> = Result.failure(UnsupportedStorageCapabilityException(capability))
