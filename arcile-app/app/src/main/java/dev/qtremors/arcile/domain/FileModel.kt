package dev.qtremors.arcile.domain

import java.io.File

data class FileModel(
    val file: File? = null,
    val name: String = file?.name ?: "",
    val absolutePath: String = file?.absolutePath ?: "",
    val size: Long = if (file != null && file.isFile) file.length() else 0L,
    val lastModified: Long = file?.lastModified() ?: 0L,
    val isDirectory: Boolean = file?.isDirectory ?: false,
    val extension: String = file?.extension ?: "",
    val isHidden: Boolean = file?.isHidden ?: false
)
