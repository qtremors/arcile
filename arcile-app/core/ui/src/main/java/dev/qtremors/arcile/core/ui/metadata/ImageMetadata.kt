package dev.qtremors.arcile.core.ui.metadata

import android.content.Context
import android.graphics.BitmapFactory
import android.media.MediaScannerConnection
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.net.toUri
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.util.Locale

data class ImageMetadataUpdate(
    val description: String?,
    val userComment: String?,
    val artist: String?,
    val copyright: String?,
    val cameraMaker: String?,
    val cameraModel: String?,
    val dateTaken: String?,
    val latitude: Double?,
    val longitude: Double?
)

sealed interface ImageMetadataWriteResult {
    data object Success : ImageMetadataWriteResult
    data object UnsupportedFormat : ImageMetadataWriteResult
    data object NotWritable : ImageMetadataWriteResult
    data class Failure(val cause: Throwable) : ImageMetadataWriteResult
}

object SharedImageMetadataReader {
    fun readMetadata(context: Context, reference: String, mimeType: String? = null): VisualMediaMetadata {
        val uri = runCatching { reference.toUri() }.getOrNull()
        return if (uri?.scheme == "content") {
            readContentMetadata(context, uri, mimeType)
        } else {
            readFileMetadata(reference, mimeType)
        }
    }

    fun readFileMetadata(filePath: String, mimeType: String? = null): VisualMediaMetadata {
        val file = File(filePath)
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(filePath, options)
        val exif = runCatching { ExifInterface(filePath) }.getOrNull()
        return metadataFrom(
            reference = filePath,
            size = file.takeIf { it.exists() }?.length() ?: 0L,
            mimeType = mimeType,
            width = options.outWidth.coerceAtLeast(0),
            height = options.outHeight.coerceAtLeast(0),
            exif = exif,
            isEditable = validateWritableMetadataFile(filePath) == null
        )
    }

    fun eraseFileMetadata(filePath: String, context: Context): Boolean {
        return when (eraseFileMetadataResult(filePath, context)) {
            ImageMetadataWriteResult.Success -> true
            else -> false
        }
    }

    fun updateFileMetadata(
        filePath: String,
        update: ImageMetadataUpdate,
        context: Context
    ): ImageMetadataWriteResult {
        val writable = validateWritableMetadataFile(filePath)
        if (writable != null) return writable

        return try {
            val exif = ExifInterface(filePath)
            exif.setAttribute(ExifInterface.TAG_IMAGE_DESCRIPTION, update.description.normalizedMetadataValue())
            exif.setAttribute(ExifInterface.TAG_USER_COMMENT, update.userComment.normalizedMetadataValue())
            exif.setAttribute(ExifInterface.TAG_ARTIST, update.artist.normalizedMetadataValue())
            exif.setAttribute(ExifInterface.TAG_COPYRIGHT, update.copyright.normalizedMetadataValue())
            exif.setAttribute(ExifInterface.TAG_MAKE, update.cameraMaker.normalizedMetadataValue())
            exif.setAttribute(ExifInterface.TAG_MODEL, update.cameraModel.normalizedMetadataValue())
            val dateTaken = update.dateTaken.normalizedMetadataValue()
            exif.setAttribute(ExifInterface.TAG_DATETIME, dateTaken)
            exif.setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL, dateTaken)
            exif.setAttribute(ExifInterface.TAG_DATETIME_DIGITIZED, dateTaken)

            if (update.latitude != null && update.longitude != null) {
                exif.setLatLong(update.latitude, update.longitude)
            } else {
                GPS_TAGS.forEach { tag -> exif.setAttribute(tag, null) }
            }
            exif.saveAttributes()
            MediaScannerConnection.scanFile(context, arrayOf(filePath), null, null)
            ImageMetadataWriteResult.Success
        } catch (error: Throwable) {
            ImageMetadataWriteResult.Failure(error)
        }
    }

    fun eraseFileMetadataResult(filePath: String, context: Context): ImageMetadataWriteResult {
        val writable = validateWritableMetadataFile(filePath)
        if (writable != null) return writable

        return try {
            val exif = ExifInterface(filePath)
            REMOVABLE_METADATA_TAGS.forEach { tag -> exif.setAttribute(tag, null) }
            exif.saveAttributes()
            MediaScannerConnection.scanFile(context, arrayOf(filePath), null, null)
            ImageMetadataWriteResult.Success
        } catch (error: Throwable) {
            ImageMetadataWriteResult.Failure(error)
        }
    }

    private fun readContentMetadata(context: Context, uri: Uri, mimeType: String?): VisualMediaMetadata {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { input ->
            BitmapFactory.decodeStream(input, null, options)
        }
        val exif = runCatching {
            context.contentResolver.openInputStream(uri)?.use { input -> ExifInterface(input) }
        }.getOrNull()
        return metadataFrom(
            reference = uri.toString(),
            size = contentSize(context, uri) ?: 0L,
            mimeType = mimeType ?: context.contentResolver.getType(uri),
            width = options.outWidth.coerceAtLeast(0),
            height = options.outHeight.coerceAtLeast(0),
            exif = exif,
            isEditable = false
        )
    }

    private fun metadataFrom(
        reference: String,
        size: Long,
        mimeType: String?,
        width: Int,
        height: Int,
        exif: ExifInterface?,
        isEditable: Boolean
    ): VisualMediaMetadata {
        val megapixel = if (width > 0 && height > 0) {
            Math.round((width * height).toDouble() / 1_000_000.0 * 100.0) / 100.0
        } else {
            0.0
        }
        val isoVal = exif?.getAttributeInt(ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY, -1) ?: -1
        val expTime = exif?.getAttributeDouble(ExifInterface.TAG_EXPOSURE_TIME, 0.0) ?: 0.0
        val fNum = exif?.getAttributeDouble(ExifInterface.TAG_F_NUMBER, 0.0) ?: 0.0
        val fLen = exif?.getAttributeDouble(ExifInterface.TAG_FOCAL_LENGTH, 0.0) ?: 0.0
        val wb = exif?.getAttributeInt(ExifInterface.TAG_WHITE_BALANCE, -1) ?: -1
        val flashVal = exif?.getAttributeInt(ExifInterface.TAG_FLASH, -1) ?: -1
        val latLong = exif?.latLong
        val alt = exif?.getAttributeDouble(ExifInterface.TAG_GPS_ALTITUDE, -1.0) ?: -1.0
        val altRef = exif?.getAttributeInt(ExifInterface.TAG_GPS_ALTITUDE_REF, 0) ?: 0

        return VisualMediaMetadata(
            path = reference,
            size = size,
            mimeType = mimeType,
            width = width,
            height = height,
            megapixel = megapixel,
            cameraMaker = exif?.getAttribute(ExifInterface.TAG_MAKE),
            cameraModel = exif?.getAttribute(ExifInterface.TAG_MODEL),
            lensModel = exif?.getAttribute(ExifInterface.TAG_LENS_MODEL),
            iso = isoVal.takeIf { it != -1 },
            exposureTime = if (expTime > 0.0) {
                if (expTime < 1.0) "1/${Math.round(1.0 / expTime)} s" else "${expTime} s"
            } else {
                exif?.getAttribute(ExifInterface.TAG_EXPOSURE_TIME)
            },
            fNumber = fNum.takeIf { it > 0.0 },
            focalLength = fLen.takeIf { it > 0.0 },
            whiteBalance = when (wb) {
                ExifInterface.WHITE_BALANCE_AUTO.toInt() -> "Auto"
                ExifInterface.WHITE_BALANCE_MANUAL.toInt() -> "Manual"
                else -> null
            },
            flash = if (flashVal != -1) {
                if ((flashVal and 1) != 0) "Fired" else "Did not fire"
            } else {
                null
            },
            dateTaken = exif?.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)
                ?: exif?.getAttribute(ExifInterface.TAG_DATETIME),
            latitude = latLong?.getOrNull(0),
            longitude = latLong?.getOrNull(1),
            altitude = if (alt != -1.0) {
                if (altRef == 1) -alt else alt
            } else {
                null
            },
            description = exif?.getAttribute(ExifInterface.TAG_IMAGE_DESCRIPTION),
            userComment = exif?.getAttribute(ExifInterface.TAG_USER_COMMENT),
            artist = exif?.getAttribute(ExifInterface.TAG_ARTIST),
            copyright = exif?.getAttribute(ExifInterface.TAG_COPYRIGHT),
            isEditable = isEditable
        )
    }

    private fun contentSize(context: Context, uri: Uri): Long? =
        runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
                if (!cursor.moveToFirst()) return@use null
                val index = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (index < 0 || cursor.isNull(index)) null else cursor.getLong(index)
            }
        }.getOrNull()

    private fun validateWritableMetadataFile(filePath: String): ImageMetadataWriteResult? {
        val file = File(filePath)
        if (!file.exists() || !file.isFile || !file.canWrite()) {
            return ImageMetadataWriteResult.NotWritable
        }
        if (file.extension.lowercase() !in WRITABLE_METADATA_EXTENSIONS) {
            return ImageMetadataWriteResult.UnsupportedFormat
        }
        return null
    }

    private val WRITABLE_METADATA_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp")

    private val GPS_TAGS = listOf(
        ExifInterface.TAG_GPS_LATITUDE,
        ExifInterface.TAG_GPS_LATITUDE_REF,
        ExifInterface.TAG_GPS_LONGITUDE,
        ExifInterface.TAG_GPS_LONGITUDE_REF,
        ExifInterface.TAG_GPS_ALTITUDE,
        ExifInterface.TAG_GPS_ALTITUDE_REF,
        ExifInterface.TAG_GPS_DATESTAMP,
        ExifInterface.TAG_GPS_TIMESTAMP,
        ExifInterface.TAG_GPS_PROCESSING_METHOD
    )

    private val REMOVABLE_METADATA_TAGS = listOf(
        ExifInterface.TAG_IMAGE_DESCRIPTION,
        ExifInterface.TAG_USER_COMMENT,
        ExifInterface.TAG_ARTIST,
        ExifInterface.TAG_COPYRIGHT,
        ExifInterface.TAG_SOFTWARE,
        ExifInterface.TAG_MAKE,
        ExifInterface.TAG_MODEL,
        ExifInterface.TAG_LENS_MAKE,
        ExifInterface.TAG_LENS_MODEL,
        ExifInterface.TAG_LENS_SPECIFICATION,
        ExifInterface.TAG_DATETIME,
        ExifInterface.TAG_DATETIME_ORIGINAL,
        ExifInterface.TAG_DATETIME_DIGITIZED,
        ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY,
        ExifInterface.TAG_EXPOSURE_TIME,
        ExifInterface.TAG_F_NUMBER,
        ExifInterface.TAG_FOCAL_LENGTH,
        ExifInterface.TAG_FOCAL_LENGTH_IN_35MM_FILM,
        ExifInterface.TAG_WHITE_BALANCE,
        ExifInterface.TAG_FLASH,
        ExifInterface.TAG_METERING_MODE,
        ExifInterface.TAG_EXPOSURE_PROGRAM,
        ExifInterface.TAG_EXPOSURE_BIAS_VALUE,
        ExifInterface.TAG_SCENE_CAPTURE_TYPE
    ) + GPS_TAGS
}

private fun String?.normalizedMetadataValue(): String? = this?.trim()?.takeIf(String::isNotEmpty)

fun imageHasExif(metadata: VisualMediaMetadata?): Boolean =
    metadata != null && (
        metadata.cameraMaker != null ||
            metadata.cameraModel != null ||
            metadata.lensModel != null ||
            metadata.iso != null ||
            metadata.exposureTime != null ||
            metadata.fNumber != null ||
            metadata.focalLength != null ||
            metadata.whiteBalance != null ||
            metadata.flash != null ||
            metadata.dateTaken != null ||
            metadata.latitude != null ||
            metadata.longitude != null ||
            metadata.description != null ||
            metadata.userComment != null ||
            metadata.artist != null ||
            metadata.copyright != null
        )
