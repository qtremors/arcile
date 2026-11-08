package dev.qtremors.arcile.core.ui.image

import coil3.Uri
import coil3.filePath
import java.io.File

internal fun Uri.localThumbnailFile(): File? {
    if (scheme != null && scheme != "file") return null
    return filePath?.let(::File)
}
