package dev.qtremors.arcile.core.storage.data.source

import android.database.Cursor
import android.provider.MediaStore
import dev.qtremors.arcile.core.storage.data.util.matchesScope
import dev.qtremors.arcile.core.storage.domain.FileModel
import dev.qtremors.arcile.core.storage.domain.StorageScope
import dev.qtremors.arcile.core.storage.domain.StorageVolume
import java.io.File

internal data class MediaStoreFileRow(
    val rawPath: String?,
    val displayName: String,
    val relativePath: String?,
    val volumeName: String?,
    val size: Long,
    val lastModified: Long,
    val mimeType: String?
) {
    val extension: String
        get() = rawPath?.substringAfterLast('.', "")
            ?: displayName.substringAfterLast('.', "")

    fun displayPath(volumes: List<StorageVolume>): String {
        rawPath?.let { return it }
        val volume = volumeName?.let { name ->
            volumes.firstOrNull { it.mediaStoreVolumeNames().contains(name) }
        } ?: volumes.firstOrNull { it.isPrimary } ?: volumes.firstOrNull()

        val root = volume?.path?.trimEnd('/')
        val relative = relativePath.orEmpty().trim('/').trimEnd('/')
        return when {
            root == null -> listOf(relative, displayName).filter { it.isNotBlank() }.joinToString("/")
            relative.isBlank() -> File(root, displayName).path
            else -> File(File(root, relative), displayName).path
        }
    }

    fun toFileModel(volumes: List<StorageVolume>): FileModel {
        val path = displayPath(volumes)
        return FileModel(
            name = displayName,
            absolutePath = path,
            size = size,
            lastModified = lastModified,
            isDirectory = false,
            extension = extension,
            isHidden = displayName.startsWith(".") || path.contains("/."),
            mimeType = mimeType
        )
    }
}

internal fun StorageVolume.mediaStoreVolumeNames(): Set<String> {
    val names = mutableSetOf<String>()
    if (isPrimary) names += MediaStore.VOLUME_EXTERNAL_PRIMARY
    storageKey.removePrefix("uuid:")
        .takeIf { it != storageKey && it.isNotBlank() }
        ?.let {
            names += it
            names += it.lowercase()
            names += it.uppercase()
        }
    path.substringAfterLast('/', "")
        .takeIf { it.isNotBlank() }
        ?.let {
            names += it
            names += it.lowercase()
            names += it.uppercase()
        }
    return names
}

internal fun mediaProjection(): Array<String> = arrayOf(
    MediaStore.Files.FileColumns._ID,
    MediaStore.Files.FileColumns.DATA,
    MediaStore.Files.FileColumns.DISPLAY_NAME,
    MediaStore.Files.FileColumns.SIZE,
    MediaStore.Files.FileColumns.DATE_MODIFIED,
    MediaStore.Files.FileColumns.DATE_ADDED,
    MediaStore.Files.FileColumns.MIME_TYPE,
    MediaStore.MediaColumns.RELATIVE_PATH,
    MediaStore.MediaColumns.VOLUME_NAME
)

internal fun Cursor.readMediaStoreFileRow(): MediaStoreFileRow {
    val dataCol = optionalColumn(MediaStore.Files.FileColumns.DATA)
    val nameCol = optionalColumn(MediaStore.Files.FileColumns.DISPLAY_NAME)
    val sizeCol = optionalColumn(MediaStore.Files.FileColumns.SIZE)
    val dateAddedCol = optionalColumn(MediaStore.Files.FileColumns.DATE_ADDED)
    val dateModifiedCol = optionalColumn(MediaStore.Files.FileColumns.DATE_MODIFIED)
    val mimeTypeCol = optionalColumn(MediaStore.Files.FileColumns.MIME_TYPE)
    val relativePathCol = optionalColumn(MediaStore.MediaColumns.RELATIVE_PATH)
    val volumeNameCol = optionalColumn(MediaStore.MediaColumns.VOLUME_NAME)

    val path = getOptionalString(dataCol)
    val name = getOptionalString(nameCol)
        ?: path?.let { File(it).name }
        ?: ""
    val dateAdded = getOptionalLong(dateAddedCol) * 1000L
    val dateModified = getOptionalLong(dateModifiedCol) * 1000L

    return MediaStoreFileRow(
        rawPath = path,
        displayName = name,
        relativePath = getOptionalString(relativePathCol),
        volumeName = getOptionalString(volumeNameCol),
        size = getOptionalLong(sizeCol),
        lastModified = maxOf(dateAdded, dateModified),
        mimeType = getOptionalString(mimeTypeCol)
    )
}

internal fun rowMatchesScope(row: MediaStoreFileRow, scope: StorageScope, volumes: List<StorageVolume>): Boolean {
    if (row.displayName.startsWith(".")) return false
    row.rawPath?.let { path ->
        return !path.contains("/.") && matchesScope(path, scope, volumes)
    }

    val volumeName = row.volumeName
    return when (scope) {
        StorageScope.AllStorage -> volumeName == null || volumes.any { it.mediaStoreVolumeNames().contains(volumeName) }
        is StorageScope.Volume -> {
            val volume = volumes.find { it.id == scope.volumeId } ?: return false
            volumeName == null || volume.mediaStoreVolumeNames().contains(volumeName)
        }
        is StorageScope.Category -> {
            if (!scope.volumeId.isNullOrEmpty()) {
                val volume = volumes.find { it.id == scope.volumeId } ?: return false
                volumeName == null || volume.mediaStoreVolumeNames().contains(volumeName)
            } else {
                volumeName == null || volumes.any { it.mediaStoreVolumeNames().contains(volumeName) }
            }
        }
        is StorageScope.Path -> matchesScope(row.displayPath(volumes), scope, volumes)
    }
}

internal fun appendVolumeSelection(
    selectionParts: MutableList<String>,
    selectionArgs: MutableList<String>,
    volumes: List<StorageVolume>
) {
    if (volumes.isEmpty()) return
    val clauses = mutableListOf<String>()
    volumes.forEach { volume ->
        volume.mediaStoreVolumeNames().forEach { volumeName ->
            clauses += "${MediaStore.MediaColumns.VOLUME_NAME} = ?"
            selectionArgs += volumeName
        }
        clauses += "${MediaStore.Files.FileColumns.DATA} LIKE ?"
        selectionArgs += volume.path.trimEnd('/') + "/%"
    }
    if (clauses.isNotEmpty()) {
        selectionParts += clauses.joinToString(
            separator = " OR ",
            prefix = "(",
            postfix = ")"
        )
    }
}

private fun Cursor.optionalColumn(name: String): Int =
    getColumnIndex(name).takeIf { it >= 0 } ?: -1

private fun Cursor.getOptionalString(index: Int): String? =
    if (index >= 0 && !isNull(index)) getString(index) else null

private fun Cursor.getOptionalLong(index: Int, default: Long = 0L): Long =
    if (index >= 0 && !isNull(index)) getLong(index) else default
