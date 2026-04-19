package dev.qtremors.arcile.testutil

import dev.qtremors.arcile.domain.FileModel
import dev.qtremors.arcile.domain.StorageKind
import dev.qtremors.arcile.domain.StorageVolume
import java.io.File
import java.nio.file.Files

fun testVolume(
    id: String,
    path: String,
    name: String = id,
    storageKey: String = id,
    kind: StorageKind = if (path.contains("emulated")) StorageKind.INTERNAL else StorageKind.EXTERNAL_UNCLASSIFIED,
    isPrimary: Boolean = kind == StorageKind.INTERNAL,
    isRemovable: Boolean = !isPrimary,
    isUserClassified: Boolean = false,
    totalBytes: Long = 1_000L,
    freeBytes: Long = 250L
) = StorageVolume(
    id = id,
    storageKey = storageKey,
    name = name,
    path = path,
    totalBytes = totalBytes,
    freeBytes = freeBytes,
    isPrimary = isPrimary,
    isRemovable = isRemovable,
    kind = kind,
    isUserClassified = isUserClassified
)

fun testFile(
    name: String,
    path: String,
    isDirectory: Boolean = false,
    size: Long = if (isDirectory) 0L else 1L,
    lastModified: Long = 1L,
    isHidden: Boolean = name.startsWith("."),
    mimeType: String? = null
) = FileModel(
    name = name,
    absolutePath = path,
    size = size,
    lastModified = lastModified,
    isDirectory = isDirectory,
    extension = if (isDirectory) "" else name.substringAfterLast('.', ""),
    isHidden = isHidden,
    mimeType = mimeType
)

fun createTempStorageRoot(prefix: String = "arcile-test"): File =
    Files.createTempDirectory(prefix).toFile().apply { deleteOnExit() }
