package dev.qtremors.arcile.shared.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.automirrored.outlined.InsertDriveFile
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.AudioFile
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.FolderZip
import androidx.compose.material.icons.outlined.Html
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Javascript
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.Slideshow
import androidx.compose.material.icons.outlined.TableView
import androidx.compose.material.icons.outlined.VideoFile
import androidx.compose.ui.graphics.vector.ImageVector
import dev.qtremors.arcile.core.storage.domain.FileModel

fun getFileIconVector(file: FileModel): ImageVector {
    if (file.isDirectory) return Icons.Outlined.Folder

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
        "jpg", "jpeg", "png", "gif", "webp", "bmp", "svg", "heic", "heif", "ico", "tiff", "tif", "raw" -> Icons.Outlined.Image
        
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
