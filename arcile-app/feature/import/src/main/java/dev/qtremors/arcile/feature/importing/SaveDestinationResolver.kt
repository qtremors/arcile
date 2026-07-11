package dev.qtremors.arcile.feature.importing

import dev.qtremors.arcile.core.storage.domain.StorageVolume
import java.io.File

internal fun resolveInitialSaveToArcileDirectory(
    defaultPath: String?,
    volumes: List<StorageVolume>
): File? {
    val defaultDirectory = defaultPath
        ?.takeIf(String::isNotBlank)
        ?.let(::File)
        ?: return null
    return defaultDirectory.takeIf { isValidSaveToArcileDirectory(it, volumes) }
}

internal fun isValidSaveToArcileDirectory(
    directory: File,
    volumes: List<StorageVolume>
): Boolean {
    if (!directory.exists() ||
        !directory.isDirectory ||
        !directory.canRead() ||
        !directory.canWrite()
    ) {
        return false
    }
    val canonicalDirectory = runCatching { directory.canonicalFile }.getOrNull() ?: return false
    return volumes.any { volume ->
        val canonicalVolume = runCatching { File(volume.path).canonicalFile }.getOrNull()
            ?: return@any false
        canonicalDirectory == canonicalVolume ||
            canonicalDirectory.absolutePath.startsWith(
                canonicalVolume.absolutePath + File.separator
            )
    }
}
