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
        val viewerActivity = resolveStandaloneViewerActivity(this, intent)
        if (viewerActivity == null) {
            Toast.makeText(this, getString(R.string.cannot_open_file, getString(R.string.error_unsupported_provider)), Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        startActivity(Intent(intent).setClass(this, viewerActivity))
        finish()
    }
}

internal fun resolveStandaloneViewerActivity(context: Context, intent: Intent): Class<out Activity>? =
    when {
        resolveStandaloneImageTarget(context, intent) != null -> ImageViewerActivity::class.java
        else -> null
    }
