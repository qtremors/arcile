package dev.qtremors.arcile.data.util

import dev.qtremors.arcile.data.StorageClassification
import dev.qtremors.arcile.domain.FileCategories
import dev.qtremors.arcile.domain.StorageScope
import dev.qtremors.arcile.domain.StorageVolume
import dev.qtremors.arcile.domain.isIndexed
import dev.qtremors.arcile.domain.supportsTrash
import java.io.File
import java.util.Locale

fun browsableVolumes(volumes: List<StorageVolume>): List<StorageVolume> = volumes

fun indexedVolumes(volumes: List<StorageVolume>): List<StorageVolume> =
    volumes.filter { it.kind.isIndexed }

fun scopedVolumes(scope: StorageScope, volumes: List<StorageVolume>): List<StorageVolume> =
    when (scope) {
        StorageScope.AllStorage -> volumes
        is StorageScope.Volume -> volumes.filter { it.id == scope.volumeId }
        is StorageScope.Path -> volumes.filter { it.id == scope.volumeId }
        is StorageScope.Category -> {
            if (scope.volumeId.isNullOrEmpty()) volumes
            else volumes.filter { it.id == scope.volumeId }
        }
    }

fun indexedVolumesForScope(scope: StorageScope, volumes: List<StorageVolume>): List<StorageVolume> =
    scopedVolumes(scope, indexedVolumes(volumes))

fun trashEnabledVolumes(volumes: List<StorageVolume>): List<StorageVolume> =
    volumes.filter { it.kind.supportsTrash }

fun resolveVolumeForPath(path: String, volumes: List<StorageVolume>): StorageVolume? {
    val canonicalPath = runCatching { File(path).canonicalPath }.getOrElse { return null }
    return volumes
        .sortedByDescending { it.path.length }
        .firstOrNull { canonicalPath == it.path || canonicalPath.startsWith(it.path + File.separator) }
}

fun matchesScope(path: String, scope: StorageScope, volumes: List<StorageVolume>): Boolean {
    val canonicalPath = runCatching { File(path).canonicalPath }.getOrNull() ?: return false
    return when (scope) {
        StorageScope.AllStorage -> resolveVolumeForPath(canonicalPath, volumes) != null
        is StorageScope.Volume -> {
            val volume = volumes.find { it.id == scope.volumeId } ?: return false
            canonicalPath == volume.path || canonicalPath.startsWith(volume.path + File.separator)
        }
        is StorageScope.Path -> {
            val volume = volumes.find { it.id == scope.volumeId } ?: return false
            (canonicalPath == scope.absolutePath || canonicalPath.startsWith(scope.absolutePath + File.separator)) &&
                (canonicalPath == volume.path || canonicalPath.startsWith(volume.path + File.separator))
        }
        is StorageScope.Category -> {
            if (path.contains("${File.separator}.") || path.contains("/.")) return false
            
            // 1. Check volume if specified
            if (!scope.volumeId.isNullOrEmpty()) {
                val volume = volumes.find { it.id == scope.volumeId } ?: return false
                if (!(canonicalPath == volume.path || canonicalPath.startsWith(volume.path + File.separator))) {
                    return false
                }
            } else if (resolveVolumeForPath(canonicalPath, volumes) == null) {
                return false
            }

            // 2. Check category membership
            val category = FileCategories.all.find { it.name == scope.categoryName } ?: return false
            val extension = canonicalPath.substringAfterLast('.', "")
            FileCategories.getCategoryForFile(extension, null) == category
        }
    }
}

fun mergeStorageClassifications(
    volumes: List<StorageVolume>,
    classifications: Map<String, StorageClassification>
): List<StorageVolume> {
    return volumes.map { vol ->
        val classification = classifications[vol.storageKey]
            ?: classifications["path:${vol.path.lowercase(Locale.US)}"]
            ?: classifications[vol.path]
        if (classification != null) {
            vol.copy(kind = classification.assignedKind, isUserClassified = true)
        } else {
            vol.copy(
                kind = if (vol.isPrimary) dev.qtremors.arcile.domain.StorageKind.INTERNAL else dev.qtremors.arcile.domain.StorageKind.EXTERNAL_UNCLASSIFIED,
                isUserClassified = false
            )
        }
    }
}