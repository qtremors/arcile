package dev.qtremors.arcile.feature.imagegallery

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import dev.qtremors.arcile.core.storage.domain.FileModel
import java.io.File

fun imageRequestDataFor(file: FileModel): Any =
    file.nodeRef.contentUri?.takeIf { it.isNotBlank() }?.let(Uri::parse)
        ?: File(file.absolutePath)

fun imageRequestDataFor(context: Context, file: FileModel): Any =
    file.nodeRef.contentUri?.takeIf { it.isNotBlank() }?.let(Uri::parse)
        ?: File(file.absolutePath).takeIf { it.exists() }
        ?: resolveMediaStoreUri(context, file.absolutePath)
        ?: File(file.absolutePath)

fun galleryThumbnailRequestDataFor(file: FileModel, archiveThumbnailData: Any? = null): Any =
    archiveThumbnailData ?: imageRequestDataFor(file)

private fun resolveMediaStoreUri(context: Context, path: String): Uri? {
    val projection = arrayOf(
        MediaStore.Files.FileColumns._ID,
        MediaStore.MediaColumns.VOLUME_NAME
    )
    return context.contentResolver.query(
        MediaStore.Files.getContentUri("external"),
        projection,
        "${MediaStore.Files.FileColumns.DATA} = ?",
        arrayOf(path),
        null
    )?.use { cursor ->
        if (!cursor.moveToFirst()) return@use null
        val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID))
        val volumeIndex = cursor.getColumnIndex(MediaStore.MediaColumns.VOLUME_NAME)
        val volumeName = if (volumeIndex >= 0 && !cursor.isNull(volumeIndex)) {
            cursor.getString(volumeIndex)
        } else {
            MediaStore.VOLUME_EXTERNAL
        }
        ContentUris.withAppendedId(MediaStore.Files.getContentUri(volumeName ?: MediaStore.VOLUME_EXTERNAL), id)
    }
}
