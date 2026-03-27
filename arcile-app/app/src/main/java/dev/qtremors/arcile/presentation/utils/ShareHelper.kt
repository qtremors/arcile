package dev.qtremors.arcile.presentation.utils

import android.content.Context
import android.content.Intent
import dev.qtremors.arcile.utils.AppLogger
import java.util.ArrayList

object ShareHelper {
    fun shareFiles(context: Context, filePaths: List<String>): Boolean {
        if (filePaths.isEmpty()) return false

        try {
            val uris = ArrayList(ExternalFileAccessHelper.createShareUris(context, filePaths))
            if (uris.isEmpty()) return false

            val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = "*/*"
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
            }

            val chooser = Intent.createChooser(intent, "Share files via")
            chooser.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            context.startActivity(chooser)
            return true
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            AppLogger.e("ShareHelper", "Failed to share files", e)
            return false
        }
    }
}


