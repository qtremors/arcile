package dev.qtremors.arcile.domain

/**
 * Snapshot of the device's primary external storage capacity.
 *
 * Retrieved via [FileRepository.getStorageInfo], which uses [android.os.StatFs] internally.
 *
 * @property totalBytes Total storage capacity in bytes.
 * @property freeBytes Currently available (free) storage in bytes.
 */
data class StorageInfo(
    val totalBytes: Long,
    val freeBytes: Long
)
