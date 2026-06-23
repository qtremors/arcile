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
import dev.qtremors.arcile.feature.imagegallery.ModelViewerScreen
import dev.qtremors.arcile.presentation.utils.ExternalFileAccessHelper
import dev.qtremors.arcile.presentation.utils.ShareHelper
import dev.qtremors.arcile.ui.theme.ArcileTheme
import dev.qtremors.arcile.ui.theme.ThemeState
import kotlinx.coroutines.launch
import java.io.File

class ModelViewerActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val target = resolveStandaloneModelTarget(this, intent)
        if (target == null) {
            Toast.makeText(this, getString(R.string.cannot_open_file, getString(R.string.error_unsupported_provider)), Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        setContent {
            ArcileTheme(themeState = ThemeState()) {
                ModelViewerScreen(
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

    private fun shareTarget(target: StandaloneModelTarget) {
        lifecycleScope.launch {
            val shared = ShareHelper.shareFileReferences(
                this@ModelViewerActivity,
                listOf(target.toExternalReference())
            )
            if (!shared) {
                Toast.makeText(this@ModelViewerActivity, getString(R.string.cannot_open_file, getString(R.string.no_app_found)), Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun openTargetWithChooser(target: StandaloneModelTarget) {
        lifecycleScope.launch {
            runCatching {
                val openIntent = ExternalFileAccessHelper.createOpenIntent(
                    this@ModelViewerActivity,
                    target.toExternalReference()
                )
                startActivity(Intent.createChooser(openIntent, getString(R.string.image_gallery_open_with)))
            }.onFailure {
                Toast.makeText(this@ModelViewerActivity, getString(R.string.cannot_open_file, it.localizedMessage ?: ""), Toast.LENGTH_SHORT).show()
            }
        }
    }
}

data class StandaloneModelTarget(
    val reference: String,
    val displayName: String,
    val mimeType: String?,
    val sizeBytes: Long?
) {
    fun toExternalReference(): ExternalFileAccessHelper.ExternalFileReference =
        ExternalFileAccessHelper.ExternalFileReference(
            path = reference,
            displayName = displayName,
            sizeBytes = sizeBytes,
            mimeType = mimeType ?: MODEL_GLB_MIME_TYPE
        )
}

internal const val MODEL_GLB_MIME_TYPE = "model/gltf-binary"

internal fun resolveStandaloneModelTarget(context: Context, intent: Intent): StandaloneModelTarget? {
    if (intent.action != Intent.ACTION_VIEW) return null
    val uri = intent.data ?: return null
    val displayName = displayNameForModelUri(context, uri)
    val rawMimeType = intent.type
        ?: context.contentResolver.getType(uri)
        ?: mimeTypeForModelUri(uri)
    val mimeType = normalizedModelMimeType(rawMimeType, uri, displayName)
    if (!isSupportedModelMimeOrExtension(mimeType, uri, displayName)) return null

    return when (uri.scheme) {
        "content" -> StandaloneModelTarget(
            reference = uri.toString(),
            displayName = displayName ?: uri.lastPathSegment ?: "Model.glb",
            mimeType = mimeType ?: MODEL_GLB_MIME_TYPE,
            sizeBytes = queryModelOpenableColumn(context, uri, OpenableColumns.SIZE) { cursor, index ->
                cursor.getLong(index)
            }
        )
        "file", null -> {
            val file = if (uri.scheme == "file") File(uri.path.orEmpty()) else File(uri.toString())
            if (!file.exists() || !file.isFile || !ExternalFileAccessHelper.isAllowedUserFile(context, file)) return null
            StandaloneModelTarget(
                reference = file.absolutePath,
                displayName = file.name,
                mimeType = mimeType ?: MODEL_GLB_MIME_TYPE,
                sizeBytes = file.length()
            )
        }
        else -> null
    }
}

private fun isSupportedModelMimeOrExtension(mimeType: String?, uri: Uri, displayName: String? = null): Boolean {
    val normalizedMime = mimeType?.lowercase()
    return normalizedMime == MODEL_GLB_MIME_TYPE ||
        uriLooksLikeGlb(uri) ||
        nameLooksLikeGlb(displayName)
}

private fun normalizedModelMimeType(mimeType: String?, uri: Uri, displayName: String?): String? =
    if ((uriLooksLikeGlb(uri) || nameLooksLikeGlb(displayName)) && (mimeType == null || mimeType == "application/octet-stream" || mimeType == "*/*")) {
        MODEL_GLB_MIME_TYPE
    } else {
        mimeType
    }

private fun mimeTypeForModelUri(uri: Uri): String? {
    val extension = uri.lastPathSegment?.substringAfterLast('.', "")?.lowercase().orEmpty()
    return when (extension) {
        "glb" -> MODEL_GLB_MIME_TYPE
        else -> MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
    }
}

private fun uriLooksLikeGlb(uri: Uri): Boolean =
    nameLooksLikeGlb(uri.lastPathSegment)

private fun nameLooksLikeGlb(name: String?): Boolean =
    name?.substringAfterLast('.', "")?.lowercase() == "glb"

private fun displayNameForModelUri(context: Context, uri: Uri): String? =
    if (uri.scheme == "content") {
        queryModelOpenableColumn(context, uri, OpenableColumns.DISPLAY_NAME) { cursor, index ->
            cursor.getString(index)
        }
    } else {
        null
    }

private fun <T> queryModelOpenableColumn(
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
