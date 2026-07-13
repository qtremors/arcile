package dev.qtremors.arcile.feature.imagegallery

import android.net.Uri
import dev.qtremors.arcile.core.storage.domain.FileModel
import java.io.File

internal fun imageRequestDataFor(file: FileModel): Any =
    file.nodeRef.contentUri?.takeIf { it.isNotBlank() }?.let(Uri::parse)
        ?: File(file.absolutePath)

internal fun galleryThumbnailRequestDataFor(file: FileModel, archiveThumbnailData: Any? = null): Any =
    archiveThumbnailData ?: imageRequestDataFor(file)
