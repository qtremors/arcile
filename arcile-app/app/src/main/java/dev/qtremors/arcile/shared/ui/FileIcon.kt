package dev.qtremors.arcile.shared.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Html
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Javascript
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Slideshow
import androidx.compose.material.icons.filled.TableView
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.ui.graphics.vector.ImageVector
import dev.qtremors.arcile.core.storage.domain.FileModel

fun getFileIconVector(file: FileModel): ImageVector {
    if (file.isDirectory) return Icons.Default.Folder

    val ext = file.extension.lowercase()
    
    return when (ext) {
        // Text / Docs
        "txt", "md", "rtf", "log" -> Icons.AutoMirrored.Filled.Article
        "pdf" -> Icons.Default.PictureAsPdf
        "doc", "docx", "odt" -> Icons.Default.Description
        "xls", "xlsx", "csv", "sheets", "ods" -> Icons.Default.TableView
        "ppt", "pptx", "slides", "odp" -> Icons.Default.Slideshow
        
        // Audio
        "mp3", "opus", "flac", "wav", "ogg", "m4a", "aac", "wma", "amr", "mid", "midi" -> Icons.Default.AudioFile
        
        // Video
        "mp4", "mkv", "avi", "webm", "mov", "wmv", "flv", "m4v", "3gp", "ts" -> Icons.Default.VideoFile
        
        // Image
        "jpg", "jpeg", "png", "gif", "webp", "bmp", "svg", "heic", "heif", "ico", "tiff", "tif", "raw" -> Icons.Default.Image
        
        // Code / Web
        "html", "htm" -> Icons.Default.Html
        "js", "ts", "jsx", "tsx" -> Icons.Default.Javascript
        "css", "scss", "sass", "py", "java", "kt", "cpp", "c", "h", "cs", "go", "rs", "json", "xml", "yml", "yaml", "sh", "bat" -> Icons.Default.Code
        
        // Archives
        "zip", "7z", "rar", "tar", "gz", "bz2", "xz", "zst" -> Icons.Default.FolderZip
        
        // APK
        "apk", "xapk", "apks", "apkm" -> Icons.Default.Archive
        
        else -> Icons.AutoMirrored.Filled.InsertDriveFile
    }
}
