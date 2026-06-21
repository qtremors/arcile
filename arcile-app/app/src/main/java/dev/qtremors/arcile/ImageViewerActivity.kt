package dev.qtremors.arcile

import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.lifecycleScope
import dev.qtremors.arcile.core.ui.R
import dev.qtremors.arcile.presentation.utils.ExternalFileAccessHelper
import dev.qtremors.arcile.presentation.utils.ShareHelper
import dev.qtremors.arcile.shared.ui.StandaloneImageViewer
import dev.qtremors.arcile.ui.theme.ArcileTheme
import dev.qtremors.arcile.ui.theme.ThemeState
import kotlinx.coroutines.launch
import java.io.File

class ImageViewerActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val target = resolveStandaloneImageTarget(this, intent)
        if (target == null) {
            Toast.makeText(this, getString(R.string.cannot_open_file, getString(R.string.error_unsupported_provider)), Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        setContent {
            ArcileTheme(themeState = ThemeState()) {
                StandaloneImageViewer(
                    reference = target.reference,
                    title = target.displayName,
                    sizeBytes = target.sizeBytes ?: 0L,
                    mimeType = target.mimeType,
                    onNavigateBack = { finish() },
                    onShare = { shareTarget(target) },
                    onOpenWith = { openTargetWithChooser(target) }
                )
            }
        }
    }

    private fun shareTarget(target: StandaloneImageTarget) {
        lifecycleScope.launch {
            val shared = ShareHelper.shareFileReferences(
                this@ImageViewerActivity,
                listOf(
                    ExternalFileAccessHelper.ExternalFileReference(
                        path = target.reference,
                        displayName = target.displayName,
                        sizeBytes = target.sizeBytes,
                        mimeType = target.mimeType
                    )
                )
            )
            if (!shared) {
                Toast.makeText(this@ImageViewerActivity, getString(R.string.cannot_open_file, getString(R.string.no_app_found)), Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun openTargetWithChooser(target: StandaloneImageTarget) {
        lifecycleScope.launch {
            runCatching {
                val openIntent = ExternalFileAccessHelper.createOpenIntent(
                    this@ImageViewerActivity,
                    ExternalFileAccessHelper.ExternalFileReference(
                        path = target.reference,
                        displayName = target.displayName,
                        sizeBytes = target.sizeBytes,
                        mimeType = target.mimeType
                    )
                )
                startActivity(Intent.createChooser(openIntent, getString(R.string.image_gallery_open_with)))
            }.onFailure {
                Toast.makeText(this@ImageViewerActivity, getString(R.string.cannot_open_file, it.localizedMessage ?: ""), Toast.LENGTH_SHORT).show()
            }
        }
    }
}

data class StandaloneImageTarget(
    val reference: String,
    val displayName: String,
    val mimeType: String?,
    val sizeBytes: Long?
)

internal fun resolveStandaloneImageTarget(context: Context, intent: Intent): StandaloneImageTarget? {
    if (intent.action != Intent.ACTION_VIEW) return null
    val uri = intent.data ?: return null
    val mimeType = intent.type
        ?: context.contentResolver.getType(uri)
        ?: mimeTypeForUri(uri)
    if (mimeType?.startsWith("image/") != true && !uriLooksLikeImage(uri)) return null

    return when (uri.scheme) {
        "content" -> StandaloneImageTarget(
            reference = uri.toString(),
            displayName = queryOpenableColumn(context, uri, OpenableColumns.DISPLAY_NAME) { cursor, index ->
                cursor.getString(index)
            } ?: uri.lastPathSegment ?: "Image",
            mimeType = mimeType,
            sizeBytes = queryOpenableColumn(context, uri, OpenableColumns.SIZE) { cursor, index ->
                cursor.getLong(index)
            }
        )
        "file", null -> {
            val file = if (uri.scheme == "file") File(uri.path.orEmpty()) else File(uri.toString())
            if (!file.exists() || !file.isFile || !ExternalFileAccessHelper.isAllowedUserFile(context, file)) return null
            StandaloneImageTarget(
                reference = file.absolutePath,
                displayName = file.name,
                mimeType = mimeType ?: mimeTypeForUri(Uri.fromFile(file)),
                sizeBytes = file.length()
            )
        }
        else -> null
    }
}

private fun mimeTypeForUri(uri: Uri): String? =
    MimeTypeMap.getSingleton()
        .getMimeTypeFromExtension(uri.lastPathSegment?.substringAfterLast('.', "")?.lowercase().orEmpty())

private fun uriLooksLikeImage(uri: Uri): Boolean =
    uri.lastPathSegment?.substringAfterLast('.', "")?.lowercase() in
        setOf("jpg", "jpeg", "png", "webp", "heic", "heif", "gif", "bmp", "avif")

private fun <T> queryOpenableColumn(
    context: Context,
    uri: Uri,
    column: String,
    read: (Cursor, Int) -> T
): T? = runCatching {
    context.contentResolver.query(uri, arrayOf(column), null, null, null)?.use { cursor ->
        if (!cursor.moveToFirst()) return@use null
        val index = cursor.getColumnIndex(column)
        if (index < 0 || cursor.isNull(index)) null else read(cursor, index)
    }
}.getOrNull()
