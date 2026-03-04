package dev.qtremors.arcile.domain

import java.io.File

data class FileModel(
    val file: File,
    val name: String = file.name,
    val absolutePath: String = file.absolutePath,
    val size: Long = file.length(),
    val lastModified: Long = file.lastModified(),
    val isDirectory: Boolean = file.isDirectory,
    val extension: String = file.extension,
    val isHidden: Boolean = file.isHidden
)
