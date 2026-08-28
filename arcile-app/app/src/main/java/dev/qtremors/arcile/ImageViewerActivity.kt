package dev.qtremors.arcile

import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import dev.qtremors.arcile.core.ui.showArcileToast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import dev.qtremors.arcile.core.runtime.di.ArcileDispatchers
import dev.qtremors.arcile.core.ui.ExternalViewerLoadScreen
import dev.qtremors.arcile.core.ui.R
import dev.qtremors.arcile.core.ui.externalfile.ExternalFileAccessHelper
import dev.qtremors.arcile.core.ui.externalfile.resolveExternalContentMetadata
import dev.qtremors.arcile.presentation.utils.ShareHelper
import dev.qtremors.arcile.core.ui.StandaloneImageViewer
import dev.qtremors.arcile.core.ui.theme.ArcileTheme
import dev.qtremors.arcile.core.ui.theme.ThemeState
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

@AndroidEntryPoint
class ImageViewerActivity : ComponentActivity() {
    @Inject lateinit var dispatchers: ArcileDispatchers
    private var target by mutableStateOf<StandaloneImageTarget?>(null)
    private var loadError by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ArcileTheme(themeState = ThemeState()) {
                val resolved = target
                if (resolved == null) {
                    ExternalViewerLoadScreen(loadError, ::loadTarget)
                } else {
                    StandaloneImageViewer(
                        reference = resolved.reference,
                        title = resolved.displayName,
                        sizeBytes = resolved.sizeBytes ?: 0L,
                        mimeType = resolved.mimeType,
                        onNavigateBack = { finish() },
                        onShare = { shareTarget(resolved) },
                        onOpenWith = { openTargetWithChooser(resolved) },
                        onFileRenamed = { _, newFile ->
                            target = resolved.copy(
                                reference = newFile.absolutePath,
                                displayName = newFile.name,
                                mimeType = newFile.mimeType,
                                sizeBytes = newFile.size
                            )
                        },
                        onFileDeleted = { finish() }
                    )
                }
            }
        }
        loadTarget()
    }

    private fun loadTarget() {
        loadError = null
        lifecycleScope.launch {
            target = resolveStandaloneImageTarget(this@ImageViewerActivity, intent, dispatchers.io)
            if (target == null) loadError = getString(R.string.error_unsupported_provider)
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
                showArcileToast(getString(R.string.cannot_open_file, getString(R.string.no_app_found)))
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
                showArcileToast(getString(R.string.cannot_open_file, it.localizedMessage ?: ""))
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
    val mimeType = intent.type ?: mimeTypeForUri(uri)
    if (mimeType?.startsWith("image/") != true && !uriLooksLikeImage(uri)) return null
    return when (uri.scheme) {
        "content" -> StandaloneImageTarget(uri.toString(), uri.lastPathSegment ?: "Image", mimeType, null)
        "file", null -> {
            val file = File(uri.path.orEmpty())
            if (!file.isFile || !ExternalFileAccessHelper.isAllowedUserFile(context, file)) null
            else StandaloneImageTarget(file.absolutePath, file.name, mimeType, file.length())
        }
        else -> null
    }
}

internal suspend fun resolveStandaloneImageTarget(
    context: Context,
    intent: Intent,
    ioDispatcher: kotlinx.coroutines.CoroutineDispatcher
): StandaloneImageTarget? {
    if (intent.action != Intent.ACTION_VIEW) return null
    val uri = intent.data ?: return null

    return when (uri.scheme) {
        "content" -> {
            val metadata = resolveExternalContentMetadata(
                context.contentResolver, uri, intent.type, ioDispatcher
            ).getOrNull() ?: return null
            val mimeType = metadata.mimeType ?: mimeTypeForUri(uri)
            if (mimeType?.startsWith("image/") != true && !uriLooksLikeImage(uri)) return null
            StandaloneImageTarget(
                reference = uri.toString(),
                displayName = metadata.displayName ?: uri.lastPathSegment ?: "Image",
                mimeType = mimeType,
                sizeBytes = metadata.sizeBytes
            )
        }
        "file", null -> withContext(ioDispatcher) {
            val file = if (uri.scheme == "file") File(uri.path.orEmpty()) else File(uri.toString())
            if (!file.exists() || !file.isFile || !ExternalFileAccessHelper.isAllowedUserFile(context, file)) return@withContext null
            val mimeType = intent.type ?: mimeTypeForUri(Uri.fromFile(file))
            if (mimeType?.startsWith("image/") != true && !uriLooksLikeImage(uri)) return@withContext null
            StandaloneImageTarget(
                reference = file.absolutePath,
                displayName = file.name,
                mimeType = mimeType,
                sizeBytes = file.length()
            )
        }
        else -> null
    }
}

internal fun mimeTypeForUri(uri: Uri): String? =
    MimeTypeMap.getSingleton()
        .getMimeTypeFromExtension(uri.lastPathSegment?.substringAfterLast('.', "")?.lowercase().orEmpty())

private fun uriLooksLikeImage(uri: Uri): Boolean =
    uri.lastPathSegment?.substringAfterLast('.', "")?.lowercase() in
        setOf("jpg", "jpeg", "png", "webp", "heic", "heif", "gif", "bmp", "avif")

internal fun <T> queryOpenableColumn(
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
