package dev.qtremors.arcile.feature.imagegallery

import android.content.Context
import android.graphics.BitmapFactory
import android.media.MediaScannerConnection
import androidx.exifinterface.media.ExifInterface
import java.io.File

data class GalleryFileMetadata(
    val path: String,
    val size: Long,
    val mimeType: String?,
    val width: Int,
    val height: Int,
    val megapixel: Double,
    val cameraMaker: String?,
    val cameraModel: String?,
    val lensModel: String?,
    val iso: Int?,
    val exposureTime: String?,
    val fNumber: Double?,
    val focalLength: Double?,
    val whiteBalance: String?,
    val flash: String?,
    val dateTaken: String?,
    val latitude: Double?,
    val longitude: Double?,
    val altitude: Double?
)

object ExifMetadataReader {

    fun readMetadata(filePath: String, mimeType: String? = null): GalleryFileMetadata {
        val file = File(filePath)
        val size = file.length()

        // Fetch dimensions safely via BitmapFactory options
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(filePath, options)
        val width = options.outWidth.coerceAtLeast(0)
        val height = options.outHeight.coerceAtLeast(0)
        val megapixel = if (width > 0 && height > 0) {
            Math.round((width * height).toDouble() / 1_000_000.0 * 100.0) / 100.0
        } else {
            0.0
        }

        var cameraMaker: String? = null
        var cameraModel: String? = null
        var lensModel: String? = null
        var iso: Int? = null
        var exposureTime: String? = null
        var fNumber: Double? = null
        var focalLength: Double? = null
        var whiteBalance: String? = null
        var flash: String? = null
        var dateTaken: String? = null
        var latitude: Double? = null
        var longitude: Double? = null
        var altitude: Double? = null

        try {
            val exif = ExifInterface(filePath)
            cameraMaker = exif.getAttribute(ExifInterface.TAG_MAKE)
            cameraModel = exif.getAttribute(ExifInterface.TAG_MODEL)
            lensModel = exif.getAttribute(ExifInterface.TAG_LENS_MODEL)

            val isoVal = exif.getAttributeInt(ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY, -1)
            iso = if (isoVal != -1) isoVal else null

            val expTime = exif.getAttributeDouble(ExifInterface.TAG_EXPOSURE_TIME, 0.0)
            exposureTime = if (expTime > 0.0) {
                if (expTime < 1.0) {
                    val inverse = Math.round(1.0 / expTime)
                    "1/$inverse s"
                } else {
                    "${expTime} s"
                }
            } else {
                exif.getAttribute(ExifInterface.TAG_EXPOSURE_TIME)
            }

            val fNum = exif.getAttributeDouble(ExifInterface.TAG_F_NUMBER, 0.0)
            fNumber = if (fNum > 0.0) fNum else null

            val fLen = exif.getAttributeDouble(ExifInterface.TAG_FOCAL_LENGTH, 0.0)
            focalLength = if (fLen > 0.0) fLen else null

            val wb = exif.getAttributeInt(ExifInterface.TAG_WHITE_BALANCE, -1)
            whiteBalance = when (wb) {
                ExifInterface.WHITE_BALANCE_AUTO.toInt() -> "Auto"
                ExifInterface.WHITE_BALANCE_MANUAL.toInt() -> "Manual"
                else -> null
            }

            val flashVal = exif.getAttributeInt(ExifInterface.TAG_FLASH, -1)
            flash = if (flashVal != -1) {
                val fired = (flashVal and 1) != 0
                if (fired) "Fired" else "Did not fire"
            } else {
                null
            }

            dateTaken = exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)
                ?: exif.getAttribute(ExifInterface.TAG_DATETIME)

            val latLong = exif.latLong
            if (latLong != null && latLong.size >= 2) {
                latitude = latLong[0]
                longitude = latLong[1]
            }

            val alt = exif.getAttributeDouble(ExifInterface.TAG_GPS_ALTITUDE, -1.0)
            if (alt != -1.0) {
                val ref = exif.getAttributeInt(ExifInterface.TAG_GPS_ALTITUDE_REF, 0)
                altitude = if (ref == 1) -alt else alt
            }
        } catch (e: Exception) {
            // Log warning or ignore if EXIF reading is not supported by format
        }

        return GalleryFileMetadata(
            path = filePath,
            size = size,
            mimeType = mimeType,
            width = width,
            height = height,
            megapixel = megapixel,
            cameraMaker = cameraMaker,
            cameraModel = cameraModel,
            lensModel = lensModel,
            iso = iso,
            exposureTime = exposureTime,
            fNumber = fNumber,
            focalLength = focalLength,
            whiteBalance = whiteBalance,
            flash = flash,
            dateTaken = dateTaken,
            latitude = latitude,
            longitude = longitude,
            altitude = altitude
        )
    }

    fun eraseMetadata(filePath: String, context: Context): Boolean {
        return try {
            val exif = ExifInterface(filePath)
            val tagsToClear = listOf(
                ExifInterface.TAG_MAKE,
                ExifInterface.TAG_MODEL,
                ExifInterface.TAG_LENS_MAKE,
                ExifInterface.TAG_LENS_MODEL,
                ExifInterface.TAG_LENS_SPECIFICATION,
                ExifInterface.TAG_DATETIME,
                ExifInterface.TAG_DATETIME_ORIGINAL,
                ExifInterface.TAG_DATETIME_DIGITIZED,
                ExifInterface.TAG_GPS_LATITUDE,
                ExifInterface.TAG_GPS_LATITUDE_REF,
                ExifInterface.TAG_GPS_LONGITUDE,
                ExifInterface.TAG_GPS_LONGITUDE_REF,
                ExifInterface.TAG_GPS_ALTITUDE,
                ExifInterface.TAG_GPS_ALTITUDE_REF,
                ExifInterface.TAG_GPS_DATESTAMP,
                ExifInterface.TAG_GPS_TIMESTAMP,
                ExifInterface.TAG_GPS_PROCESSING_METHOD,
                ExifInterface.TAG_USER_COMMENT,
                ExifInterface.TAG_SOFTWARE,
                ExifInterface.TAG_ARTIST,
                ExifInterface.TAG_COPYRIGHT
            )
            for (tag in tagsToClear) {
                exif.setAttribute(tag, null)
            }
            exif.saveAttributes()
            // Rescan file so MediaStore catches the update
            MediaScannerConnection.scanFile(context, arrayOf(filePath), null, null)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}
