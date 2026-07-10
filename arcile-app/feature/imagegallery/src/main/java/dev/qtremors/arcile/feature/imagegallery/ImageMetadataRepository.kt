package dev.qtremors.arcile.feature.imagegallery

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.qtremors.arcile.core.ui.metadata.ImageFileMetadata
import dev.qtremors.arcile.core.ui.metadata.ImageMetadataUpdate
import dev.qtremors.arcile.core.ui.metadata.ImageMetadataWriteResult
import dev.qtremors.arcile.core.ui.metadata.SharedImageMetadataReader
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal typealias GalleryFileMetadata = ImageFileMetadata

internal interface ImageMetadataRepository {
    suspend fun read(filePath: String, mimeType: String? = null): GalleryFileMetadata
    suspend fun erase(filePath: String): ImageMetadataWriteResult
    suspend fun update(
        filePath: String,
        update: ImageMetadataUpdate
    ): ImageMetadataWriteResult
}

internal class DefaultImageMetadataRepository @Inject constructor(
    @param:ApplicationContext private val context: Context
) : ImageMetadataRepository {
    override suspend fun read(
        filePath: String,
        mimeType: String?
    ): GalleryFileMetadata = withContext(Dispatchers.IO) {
        SharedImageMetadataReader.readFileMetadata(filePath, mimeType)
    }

    override suspend fun erase(filePath: String): ImageMetadataWriteResult =
        withContext(Dispatchers.IO) {
            SharedImageMetadataReader.eraseFileMetadataResult(filePath, context)
        }

    override suspend fun update(
        filePath: String,
        update: ImageMetadataUpdate
    ): ImageMetadataWriteResult = withContext(Dispatchers.IO) {
        SharedImageMetadataReader.updateFileMetadata(filePath, update, context)
    }
}
