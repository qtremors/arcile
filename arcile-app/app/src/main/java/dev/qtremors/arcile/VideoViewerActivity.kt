package dev.qtremors.arcile

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import dev.qtremors.arcile.core.storage.domain.FileCategories
import dev.qtremors.arcile.core.ui.R
import dev.qtremors.arcile.core.ui.StandaloneVideoViewer
import dev.qtremors.arcile.core.ui.externalfile.ExternalFileAccessHelper
import dev.qtremors.arcile.core.ui.theme.ArcileTheme
import dev.qtremors.arcile.core.ui.theme.ThemeState
import dev.qtremors.arcile.presentation.utils.ShareHelper
import java.io.File
import kotlinx.coroutines.launch

class VideoViewerActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val target = resolveStandaloneVideoTarget(this, intent)
        if (target == null) {
            Toast.makeText(this, getString(R.string.cannot_open_file, getString(R.string.error_unsupported_provider)), Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        setContent {
            ArcileTheme(themeState = ThemeState()) {
                StandaloneVideoViewer(
                    mediaItem = MediaItem.fromUri(target.uri),
                    title = target.displayName,
                    onNavigateBack = ::finish,
                    onShare = { share(target) },
                    onOpenWith = { openWith(target) }
                )
            }
        }
    }

    private fun share(target: StandaloneVideoTarget) {
        lifecycleScope.launch {
            ShareHelper.shareFileReferences(
                this@VideoViewerActivity,
                listOf(target.toExternalReference())
            )
        }
    }

    private fun openWith(target: StandaloneVideoTarget) {
        lifecycleScope.launch {
            runCatching {
                val openIntent = ExternalFileAccessHelper.createOpenIntent(
                    this@VideoViewerActivity,
                    target.toExternalReference()
                )
                startActivity(Intent.createChooser(openIntent, target.displayName))
            }.onFailure {
                Toast.makeText(this@VideoViewerActivity, getString(R.string.cannot_open_file, it.localizedMessage ?: ""), Toast.LENGTH_SHORT).show()
            }
        }
    }
}

internal data class StandaloneVideoTarget(
    val reference: String,
    val uri: Uri,
    val displayName: String,
    val mimeType: String?
) {
    fun toExternalReference() = ExternalFileAccessHelper.ExternalFileReference(
        path = reference,
        displayName = displayName,
        mimeType = mimeType
    )
}

internal fun resolveStandaloneVideoTarget(context: Context, intent: Intent): StandaloneVideoTarget? {
    if (intent.action != Intent.ACTION_VIEW) return null
    val uri = intent.data ?: return null
    val mimeType = intent.type ?: context.contentResolver.getType(uri) ?: mimeTypeForUri(uri)
    val extension = uri.lastPathSegment?.substringAfterLast('.', "")?.lowercase().orEmpty()
    if (mimeType?.startsWith("video/") != true && extension !in FileCategories.Videos.extensions) return null
    return when (uri.scheme) {
        "content" -> StandaloneVideoTarget(
            reference = uri.toString(),
            uri = uri,
            displayName = queryOpenableColumn(context, uri, OpenableColumns.DISPLAY_NAME) { cursor, index ->
                cursor.getString(index)
            } ?: uri.lastPathSegment ?: "Video",
            mimeType = mimeType
        )
        "file", null -> {
            val file = if (uri.scheme == "file") File(uri.path.orEmpty()) else File(uri.toString())
            if (!file.isFile || !ExternalFileAccessHelper.isAllowedUserFile(context, file)) return null
            StandaloneVideoTarget(file.absolutePath, Uri.fromFile(file), file.name, mimeType)
        }
        else -> null
    }
}
