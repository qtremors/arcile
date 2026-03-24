package dev.qtremors.arcile.presentation.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.util.ArrayList

object ShareHelper {
    fun shareFiles(context: Context, filePaths: List<String>): Boolean {
        if (filePaths.isEmpty()) return false

        try {
            val uris = ArrayList<Uri>()
            for (path in filePaths) {
                val file = File(path)
                val canonicalPath = file.canonicalPath
                if (canonicalPath.contains("/.arcile") || canonicalPath.startsWith(context.cacheDir.canonicalPath)) {
                    continue
                }
                if (file.exists() && file.isFile) {
                    val uri = FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        file
                    )
                    uris.add(uri)
                }
            }
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
            android.util.Log.e("ShareHelper", "Failed to share files: ${e::class.java.simpleName}")
            return false
        }
    }
}
