package dev.qtremors.arcile.feature.imagegallery

import android.content.Context
import android.net.Uri
import dev.qtremors.arcile.core.storage.domain.FileModel
import java.io.File

fun imageRequestDataFor(file: FileModel): Any =
    file.nodeRef.contentUri?.takeIf { it.isNotBlank() }?.let(Uri::parse)
        ?: File(file.absolutePath)

fun imageRequestDataFor(_context: Context, file: FileModel): Any =
    file.nodeRef.contentUri?.takeIf { it.isNotBlank() }?.let(Uri::parse)
        ?: File(file.absolutePath).takeIf { it.exists() }
        ?: File(file.absolutePath)

fun galleryThumbnailRequestDataFor(file: FileModel, archiveThumbnailData: Any? = null): Any =
    archiveThumbnailData ?: imageRequestDataFor(file)
