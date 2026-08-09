package dev.qtremors.arcile

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import dev.qtremors.arcile.core.ui.showArcileToast
import dev.qtremors.arcile.core.ui.R
import dev.qtremors.arcile.feature.audio.canResolveStandaloneAudio

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
        resolveStandaloneImageTarget(context, intent) != null -> ImageViewerActivity::class.java.name
        resolveStandaloneVideoTarget(context, intent) != null -> "dev.qtremors.arcile.feature.videoplayer.VideoViewerActivity"
        canResolveStandaloneAudio(context, intent) -> "dev.qtremors.arcile.feature.audio.AudioPlayerActivity"
        resolveStandalonePdfTarget(context, intent) != null -> PdfViewerActivity::class.java.name
        resolveStandaloneTextTarget(context, intent) != null -> TextEditorActivity::class.java.name
        else -> null
    }
