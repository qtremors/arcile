package dev.qtremors.arcile

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import dev.qtremors.arcile.core.ui.R

class FileOpenActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val viewerActivityName = resolveStandaloneViewerActivityName(this, intent)
        if (viewerActivityName == null) {
            Toast.makeText(this, getString(R.string.cannot_open_file, getString(R.string.error_unsupported_provider)), Toast.LENGTH_SHORT).show()
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
        else -> null
    }
