package dev.qtremors.arcile.feature.imagegallery

import android.content.Context
import dev.qtremors.arcile.shared.ui.metadata.ImageFileMetadata
import dev.qtremors.arcile.shared.ui.metadata.SharedImageMetadataReader

typealias GalleryFileMetadata = ImageFileMetadata

object ExifMetadataReader {

    fun readMetadata(filePath: String, mimeType: String? = null): GalleryFileMetadata =
        SharedImageMetadataReader.readFileMetadata(filePath, mimeType)

    fun eraseMetadata(filePath: String, context: Context): Boolean =
        SharedImageMetadataReader.eraseFileMetadata(filePath, context)
}
