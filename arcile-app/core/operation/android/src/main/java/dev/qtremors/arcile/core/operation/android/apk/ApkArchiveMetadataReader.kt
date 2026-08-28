package dev.qtremors.arcile.core.operation.android.apk

interface ApkArchiveMetadataReader {
    suspend fun readMetadata(filePath: String): ApkArchiveMetadata?
    suspend fun readMetadataFresh(filePath: String): ApkArchiveMetadata? = readMetadata(filePath)
    suspend fun readMetadataForContentUri(contentUri: String, displayName: String): ApkArchiveMetadata?
}
