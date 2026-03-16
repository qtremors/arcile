package dev.qtremors.arcile.domain

/**
 * Represents the effective policy classification of a storage volume.
 */
enum class StorageKind {
    INTERNAL,
    EXTERNAL_UNCLASSIFIED,
    SD_CARD,
    OTG
}

data class StoragePolicy(
    val isIndexed: Boolean,
    val supportsTrash: Boolean,
    val showPermanentDeleteWarning: Boolean,
    val showTemporaryStorageBadge: Boolean
)

val StorageKind.policy: StoragePolicy
    get() = when (this) {
        StorageKind.INTERNAL,
        StorageKind.SD_CARD -> StoragePolicy(
            isIndexed = true,
            supportsTrash = true,
            showPermanentDeleteWarning = false,
            showTemporaryStorageBadge = false
        )
        StorageKind.EXTERNAL_UNCLASSIFIED,
        StorageKind.OTG -> StoragePolicy(
            isIndexed = false,
            supportsTrash = false,
            showPermanentDeleteWarning = true,
            showTemporaryStorageBadge = true
        )
    }

val StorageKind.isIndexed: Boolean get() = policy.isIndexed
val StorageKind.supportsTrash: Boolean get() = policy.supportsTrash
val StorageKind.showPermanentDeleteWarning: Boolean get() = policy.showPermanentDeleteWarning
val StorageKind.showTemporaryStorageBadge: Boolean get() = policy.showTemporaryStorageBadge

/**
 * Represents a storage volume on the device.
 *
 * @property id Unique identifier for the volume (UUID if available).
 * @property name Display name of the volume (e.g. "Internal Storage", "SD Card").
 * @property path Absolute path to the volume's root directory.
 * @property totalBytes Total storage capacity in bytes.
 * @property freeBytes Currently available (free) storage in bytes.
 * @property isPrimary `true` if this is the device's primary internal storage.
 * @property isRemovable `true` if this volume can be removed (e.g. SD card, USB).
 * @property mountState Current mount state of the volume.
 * @property kind The effective policy classification for this volume.
 * @property isUserClassified `true` if the user explicitly assigned this kind.
 */
data class StorageVolume(
    val id: String,
    val storageKey: String,
    val name: String,
    val path: String,
    val totalBytes: Long,
    val freeBytes: Long,
    val isPrimary: Boolean,
    val isRemovable: Boolean,
    val mountState: StorageMountState = StorageMountState.MOUNTED,
    val kind: StorageKind = if (isPrimary) StorageKind.INTERNAL else StorageKind.EXTERNAL_UNCLASSIFIED,
    val isUserClassified: Boolean = false
)

/**
 * Snapshot of the device's storage capacity across all volumes.
 *
 * @property volumes List of all detected storage volumes.
 */
data class StorageInfo(
    val volumes: List<StorageVolume>
) {
    // Helper for backward compatibility or cases where only summary is needed
    val totalBytes: Long get() = volumes.filter { it.kind.isIndexed }.sumOf { it.totalBytes }
    val freeBytes: Long get() = volumes.filter { it.kind.isIndexed }.sumOf { it.freeBytes }
    val primaryVolume: StorageVolume? get() = volumes.find { it.isPrimary }
}
