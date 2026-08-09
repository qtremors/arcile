package dev.qtremors.arcile.feature.videoplayer

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
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
import androidx.media3.common.MediaItem
import dev.qtremors.arcile.core.ui.R
import dev.qtremors.arcile.core.ui.externalfile.ExternalFileAccessHelper
import dev.qtremors.arcile.core.ui.externalfile.resolveExternalContentMetadata
import dev.qtremors.arcile.core.ui.theme.ArcileTheme
import dev.qtremors.arcile.core.ui.theme.ThemeState
import dev.qtremors.arcile.core.ui.video.VideoPlaybackItem
import dev.qtremors.arcile.core.ui.video.VideoPlaybackSession
import java.io.File
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@AndroidEntryPoint
internal class VideoViewerActivity : ComponentActivity() {
    @Inject lateinit var dispatchers: ArcileDispatchers
    private var target by mutableStateOf<ExternalVideoTarget?>(null)
    private var loadError by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ArcileTheme(ThemeState()) {
                val resolved = target
                if (resolved == null) ExternalViewerLoadScreen(loadError, ::loadTarget) else {
                    GlobalVideoViewer(
                        VideoPlaybackSession(
                            listOf(
                                VideoPlaybackItem(
                                    MediaItem.fromUri(resolved.uri),
                                    resolved.displayName,
                                    onShare = { share(resolved) },
                                    onOpenWith = { openWith(resolved) }
                                )
                            )
                        ),
                        ::finish
                    )
                }
            }
        }
        loadTarget()
    }

    private fun loadTarget() {
        loadError = null
        lifecycleScope.launch {
            target = resolveExternalVideoTarget(this@VideoViewerActivity, intent, dispatchers.io)
            if (target == null) loadError = getString(R.string.error_unsupported_provider)
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
        showArcileToast(getString(R.string.cannot_open_file, ""))
    }
}

private data class ExternalVideoTarget(val reference: ExternalFileAccessHelper.ExternalFileReference, val uri: Uri, val displayName: String)

private suspend fun resolveExternalVideoTarget(
    context: Context,
    intent: Intent,
    ioDispatcher: kotlinx.coroutines.CoroutineDispatcher
): ExternalVideoTarget? {
    if (intent.action != Intent.ACTION_VIEW) return null
    val uri = intent.data ?: return null
    val extension = uri.lastPathSegment?.substringAfterLast('.', "")?.lowercase().orEmpty()
    return when (uri.scheme) {
        "content" -> {
            val metadata = resolveExternalContentMetadata(
                context.contentResolver, uri, intent.type, ioDispatcher
            ).getOrNull() ?: return null
            val mime = metadata.mimeType
            if (mime?.startsWith("video/") != true && extension !in VIDEO_EXTENSIONS) return null
            val name = metadata.displayName ?: uri.lastPathSegment ?: "Video"
            val reference = ExternalFileAccessHelper.ExternalFileReference(
                uri.toString(), name, metadata.sizeBytes, mime
            )
            ExternalVideoTarget(reference, uri, name)
        }
        "file", null -> withContext(ioDispatcher) {
            val file = File(uri.path.orEmpty())
            if (!file.isFile || !ExternalFileAccessHelper.isAllowedUserFile(context, file)) return@withContext null
            val mime = intent.type
            if (mime?.startsWith("video/") != true && extension !in VIDEO_EXTENSIONS) return@withContext null
            val reference = ExternalFileAccessHelper.ExternalFileReference(
                file.absolutePath, file.name, file.length(), mime
            )
            ExternalVideoTarget(reference, Uri.fromFile(file), file.name)
        }
        else -> null
    }
}

private val VIDEO_EXTENSIONS = setOf("mp4", "mkv", "webm", "avi", "mov", "m4v", "3gp", "ts", "mts", "m2ts")
