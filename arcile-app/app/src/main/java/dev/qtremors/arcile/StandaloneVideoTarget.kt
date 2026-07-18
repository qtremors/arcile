package dev.qtremors.arcile

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import dev.qtremors.arcile.core.storage.domain.FileCategories
import dev.qtremors.arcile.core.ui.externalfile.ExternalFileAccessHelper
import java.io.File

internal data class StandaloneVideoTarget(
    val reference: String,
    val uri: Uri,
    val displayName: String,
    val mimeType: String?
)

internal fun resolveStandaloneVideoTarget(context: Context, intent: Intent): StandaloneVideoTarget? {
    if (intent.action != Intent.ACTION_VIEW) return null
    val uri = intent.data ?: return null
    val mimeType = intent.type ?: context.contentResolver.getType(uri) ?: mimeTypeForUri(uri)
    val extension = uri.lastPathSegment?.substringAfterLast('.', "")?.lowercase().orEmpty()
    if (mimeType?.startsWith("video/") != true && extension !in FileCategories.Videos.extensions) return null
    return when (uri.scheme) {
        "content" -> StandaloneVideoTarget(
            uri.toString(),
            uri,
            queryOpenableColumn(context, uri, OpenableColumns.DISPLAY_NAME) { cursor, index -> cursor.getString(index) }
                ?: uri.lastPathSegment ?: "Video",
            mimeType
        )
        "file", null -> {
            val file = if (uri.scheme == "file") File(uri.path.orEmpty()) else File(uri.toString())
            if (!file.isFile || !ExternalFileAccessHelper.isAllowedUserFile(context, file)) return null
            StandaloneVideoTarget(file.absolutePath, Uri.fromFile(file), file.name, mimeType)
        }
        else -> null
    }
}
