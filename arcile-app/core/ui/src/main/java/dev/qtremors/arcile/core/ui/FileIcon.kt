package dev.qtremors.arcile.core.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.automirrored.outlined.InsertDriveFile
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.AudioFile
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.FolderZip
import androidx.compose.material.icons.outlined.Html
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Javascript
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Screenshot
import androidx.compose.material.icons.outlined.ViewInAr
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.Slideshow
import androidx.compose.material.icons.outlined.TableView
import androidx.compose.material.icons.outlined.VideoFile
import androidx.compose.ui.graphics.vector.ImageVector
import dev.qtremors.arcile.core.storage.domain.FileModel

internal enum class FolderIconKind {
    Arcile,
    Download,
    Camera,
    Screenshots,
    Images,
    Video,
    Recordings,
    Audio,
    Documents,
    Archive,
    Generic
}

fun getFolderIconVector(
    folderName: String,
    path: String? = null,
    folderIconsEnabled: Boolean = true
): ImageVector {
    if (!folderIconsEnabled) return Icons.Outlined.Folder
    return when (folderIconKind(folderName, path)) {
        FolderIconKind.Arcile -> Icons.Outlined.Folder
        FolderIconKind.Download -> Icons.Outlined.Download
        FolderIconKind.Camera -> Icons.Outlined.CameraAlt
        FolderIconKind.Screenshots -> Icons.Outlined.Screenshot
        FolderIconKind.Images -> Icons.Outlined.Image
        FolderIconKind.Video -> Icons.Outlined.VideoFile
        FolderIconKind.Recordings -> Icons.Outlined.Mic
        FolderIconKind.Audio -> Icons.Outlined.AudioFile
        FolderIconKind.Documents -> Icons.Outlined.Description
        FolderIconKind.Archive -> Icons.Outlined.FolderZip
        FolderIconKind.Generic -> Icons.Outlined.Folder
    }
}

internal fun folderIconKind(folderName: String, path: String? = null): FolderIconKind {
    val name = folderName.lowercase(java.util.Locale.ROOT).trim()
    val relativeSegments = path?.storageRelativeSegments().orEmpty()
    val isTopLevel = relativeSegments.size == 1
    val isDcimChild = relativeSegments.size == 2 && relativeSegments.first() == "dcim"
    val isPicturesChild = relativeSegments.size == 2 && relativeSegments.first() == "pictures"
    return when {
        isTopLevel && name == ".arcile" -> FolderIconKind.Arcile
        isTopLevel && name in DOWNLOAD_FOLDER_NAMES -> FolderIconKind.Download
        (isTopLevel && name == "dcim") ||
            ((isTopLevel || isDcimChild) && name == "camera") -> FolderIconKind.Camera
        (isTopLevel || isDcimChild || isPicturesChild) && name == "screenshots" ->
            FolderIconKind.Screenshots
        (isTopLevel || isDcimChild) && name in IMAGE_FOLDER_NAMES -> FolderIconKind.Images
        isTopLevel && name in VIDEO_FOLDER_NAMES -> FolderIconKind.Video
        isTopLevel && name == "recordings" -> FolderIconKind.Recordings
        isTopLevel && name in AUDIO_FOLDER_NAMES -> FolderIconKind.Audio
        isTopLevel && name in DOCUMENT_FOLDER_NAMES -> FolderIconKind.Documents
        isTopLevel && name in ARCHIVE_FOLDER_NAMES -> FolderIconKind.Archive
        else -> FolderIconKind.Generic
    }
}

private fun String.storageRelativeSegments(): List<String> {
    val segments = replace('\\', '/')
        .trim('/')
        .split('/')
        .filter(String::isNotBlank)
        .map { it.lowercase(java.util.Locale.ROOT) }
    val storageIndex = segments.indexOf("storage")
    val relativeStart = when {
        storageIndex >= 0 && segments.getOrNull(storageIndex + 1) == "emulated" -> storageIndex + 3
        storageIndex >= 0 && segments.getOrNull(storageIndex + 1) != null -> storageIndex + 2
        segments.firstOrNull() == "sdcard" -> 1
        else -> return emptyList()
    }
    return segments.drop(relativeStart)
}

private val DOWNLOAD_FOLDER_NAMES = setOf("download", "downloads")
private val IMAGE_FOLDER_NAMES = setOf("pictures", "photos", "images")
private val VIDEO_FOLDER_NAMES = setOf("movies", "videos", "video")
private val AUDIO_FOLDER_NAMES = setOf("music", "audio", "podcasts")
private val DOCUMENT_FOLDER_NAMES = setOf("documents", "docs")
private val ARCHIVE_FOLDER_NAMES = setOf("archive", "archives", "compressed", "zips")

fun getFileIconVector(file: FileModel, folderIconsEnabled: Boolean = true): ImageVector {
    if (file.isDirectory) {
        return getFolderIconVector(file.name, file.reference, folderIconsEnabled)
    }

    val ext = file.extension.lowercase()
    
    return when (ext) {
        // Text / Docs
        "txt", "md", "rtf", "log" -> Icons.AutoMirrored.Outlined.Article
        "pdf" -> Icons.Outlined.PictureAsPdf
        "doc", "docx", "odt" -> Icons.Outlined.Description
        "xls", "xlsx", "csv", "sheets", "ods" -> Icons.Outlined.TableView
        "ppt", "pptx", "slides", "odp" -> Icons.Outlined.Slideshow
        
        // Audio
        "mp3", "opus", "flac", "wav", "ogg", "m4a", "aac", "wma", "amr", "mid", "midi" -> Icons.Outlined.AudioFile
        
        // Video
        "mp4", "mkv", "avi", "webm", "mov", "wmv", "flv", "m4v", "3gp", "3g2",
        "ts", "mts", "m2ts", "mpeg", "mpg", "vob", "ogv" -> Icons.Outlined.VideoFile
        
        // Image
        "jpg", "jpeg", "png", "gif", "webp", "bmp", "svg", "heic", "heif", "ico", "raw" -> Icons.Outlined.Image

        // 3D models
        "glb" -> Icons.Outlined.ViewInAr
        
        // Code / Web
        "html", "htm" -> Icons.Outlined.Html
        "js", "ts", "jsx", "tsx" -> Icons.Outlined.Javascript
        "css", "scss", "sass", "py", "java", "kt", "cpp", "c", "h", "cs", "go", "rs", "json", "xml", "yml", "yaml", "sh", "bat" -> Icons.Outlined.Code
        
        // Archives
        "zip", "7z", "rar", "tar", "gz", "bz2", "xz", "zst" -> Icons.Outlined.FolderZip
        
        // APK
        "apk", "xapk", "apks", "apkm" -> Icons.Outlined.Archive
        
        else -> Icons.AutoMirrored.Outlined.InsertDriveFile
    }
}
