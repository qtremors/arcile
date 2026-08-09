package dev.qtremors.arcile

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import dev.qtremors.arcile.core.ui.showArcileToast
import dev.qtremors.arcile.core.ui.R
import dev.qtremors.arcile.feature.audio.canResolveStandaloneAudio
import dev.qtremors.arcile.core.storage.domain.FileCategories

class FileOpenActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val viewerActivityName = resolveStandaloneViewerActivityName(this, intent)
        if (viewerActivityName == null) {
            showArcileToast(getString(R.string.cannot_open_file, getString(R.string.error_unsupported_provider)))
            finish()
            return
        }
        startActivity(Intent(intent).setClassName(this, viewerActivityName))
        finish()
    }
}

internal fun resolveStandaloneViewerActivityName(context: Context, intent: Intent): String? =
    when {
        intent.action != Intent.ACTION_VIEW || intent.data == null -> null
        intent.type?.startsWith("image/") == true || intent.extension() in IMAGE_EXTENSIONS ->
            ImageViewerActivity::class.java.name
        intent.type?.startsWith("video/") == true || intent.extension() in FileCategories.Videos.extensions ->
            "dev.qtremors.arcile.feature.videoplayer.VideoViewerActivity"
        canResolveStandaloneAudio(context, intent) -> "dev.qtremors.arcile.feature.audio.AudioPlayerActivity"
        intent.type == "application/pdf" || intent.extension() == "pdf" -> PdfViewerActivity::class.java.name
        resolveStandaloneTextTarget(context, intent) != null -> TextEditorActivity::class.java.name
        else -> null
    }

private fun Intent.extension(): String =
    data?.lastPathSegment?.substringAfterLast('.', "")?.lowercase().orEmpty()

private val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp", "heic", "heif", "gif", "bmp", "avif")
