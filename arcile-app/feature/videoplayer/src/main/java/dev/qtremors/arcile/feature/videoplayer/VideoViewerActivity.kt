package dev.qtremors.arcile.feature.videoplayer

import android.content.ClipData
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
import dev.qtremors.arcile.core.ui.R
import dev.qtremors.arcile.core.ui.externalfile.ExternalFileAccessHelper
import dev.qtremors.arcile.core.ui.theme.ArcileTheme
import dev.qtremors.arcile.core.ui.theme.ThemeState
import dev.qtremors.arcile.core.ui.video.VideoPlaybackItem
import dev.qtremors.arcile.core.ui.video.VideoPlaybackSession
import java.io.File
import kotlinx.coroutines.launch

internal class VideoViewerActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val target = resolveExternalVideoTarget(this, intent)
        if (target == null) {
            Toast.makeText(this, getString(R.string.cannot_open_file, getString(R.string.error_unsupported_provider)), Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        setContent {
            ArcileTheme(ThemeState()) {
                GlobalVideoViewer(
                    VideoPlaybackSession(
                        listOf(
                            VideoPlaybackItem(
                                MediaItem.fromUri(target.uri),
                                target.displayName,
                                onShare = { share(target) },
                                onOpenWith = { openWith(target) }
                            )
                        )
                    ),
                    ::finish
                )
            }
        }
    }

    private fun share(target: ExternalVideoTarget) {
        lifecycleScope.launch {
            val shareTarget = ExternalFileAccessHelper.createShareTargets(this@VideoViewerActivity, listOf(target.reference))
                .singleOrNull() ?: return@launch showFailure()
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = shareTarget.mimeType
                putExtra(Intent.EXTRA_STREAM, shareTarget.uri)
                clipData = ClipData.newUri(contentResolver, shareTarget.displayName, shareTarget.uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            runCatching { startActivity(Intent.createChooser(intent, shareTarget.displayName)) }.onFailure { showFailure() }
        }
    }

    private fun openWith(target: ExternalVideoTarget) {
        lifecycleScope.launch {
            runCatching {
                val intent = ExternalFileAccessHelper.createOpenIntent(this@VideoViewerActivity, target.reference)
                startActivity(Intent.createChooser(intent, target.displayName))
            }.onFailure { showFailure() }
        }
    }

    private fun showFailure() {
        Toast.makeText(this, getString(R.string.cannot_open_file, ""), Toast.LENGTH_SHORT).show()
    }
}

private data class ExternalVideoTarget(val reference: ExternalFileAccessHelper.ExternalFileReference, val uri: Uri, val displayName: String)

private fun resolveExternalVideoTarget(context: Context, intent: Intent): ExternalVideoTarget? {
    if (intent.action != Intent.ACTION_VIEW) return null
    val uri = intent.data ?: return null
    val mime = intent.type ?: context.contentResolver.getType(uri)
    val extension = uri.lastPathSegment?.substringAfterLast('.', "")?.lowercase().orEmpty()
    if (mime?.startsWith("video/") != true && extension !in VIDEO_EXTENSIONS) return null
    val name = if (uri.scheme == "content") {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            cursor.takeIf { it.moveToFirst() }?.getString(0)
        } ?: uri.lastPathSegment ?: "Video"
    } else {
        File(uri.path.orEmpty()).name
    }
    val reference = when (uri.scheme) {
        "content" -> ExternalFileAccessHelper.ExternalFileReference(uri.toString(), name, mimeType = mime)
        "file", null -> {
            val file = File(uri.path.orEmpty())
            if (!file.isFile || !ExternalFileAccessHelper.isAllowedUserFile(context, file)) return null
            ExternalFileAccessHelper.ExternalFileReference(file.absolutePath, file.name, mimeType = mime)
        }
        else -> return null
    }
    return ExternalVideoTarget(reference, uri, name)
}

private val VIDEO_EXTENSIONS = setOf("mp4", "mkv", "webm", "avi", "mov", "m4v", "3gp", "ts", "mts", "m2ts")
