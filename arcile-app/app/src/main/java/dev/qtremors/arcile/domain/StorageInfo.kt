package dev.qtremors.arcile.domain

/**
 * Represents a storage volume on the device.
 *
 * @property name Display name of the volume (e.g. "Internal Storage", "SD Card").
 * @property path Absolute path to the volume's root directory.
 * @property totalBytes Total storage capacity in bytes.
 * @property freeBytes Currently available (free) storage in bytes.
 * @property isPrimary `true` if this is the device's primary internal storage.
 * @property isRemovable `true` if this volume can be removed (e.g. SD card, USB).
 */
data class StorageVolume(
    val id: String,
    val name: String,
    val path: String,
    val totalBytes: Long,
    val freeBytes: Long,
    val isPrimary: Boolean,
    val isRemovable: Boolean,
    val mountState: StorageMountState = StorageMountState.MOUNTED
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
    val totalBytes: Long get() = volumes.sumOf { it.totalBytes }
    val freeBytes: Long get() = volumes.sumOf { it.freeBytes }
    val primaryVolume: StorageVolume? get() = volumes.find { it.isPrimary }
}
