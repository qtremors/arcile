package dev.qtremors.arcile.feature.imagegallery

import android.content.Context
import dev.qtremors.arcile.shared.ui.metadata.ImageFileMetadata
import dev.qtremors.arcile.shared.ui.metadata.ImageMetadataUpdate
import dev.qtremors.arcile.shared.ui.metadata.ImageMetadataWriteResult
import dev.qtremors.arcile.shared.ui.metadata.SharedImageMetadataReader

typealias GalleryFileMetadata = ImageFileMetadata

object ExifMetadataReader {

    fun readMetadata(filePath: String, mimeType: String? = null): GalleryFileMetadata =
        SharedImageMetadataReader.readFileMetadata(filePath, mimeType)

    fun eraseMetadata(filePath: String, context: Context): Boolean =
        SharedImageMetadataReader.eraseFileMetadata(filePath, context)

    fun eraseMetadataResult(filePath: String, context: Context): ImageMetadataWriteResult =
        SharedImageMetadataReader.eraseFileMetadataResult(filePath, context)

    fun updateMetadata(
        filePath: String,
        update: ImageMetadataUpdate,
        context: Context
    ): ImageMetadataWriteResult =
        SharedImageMetadataReader.updateFileMetadata(filePath, update, context)
}
