package dev.qtremors.arcile.presentation.utils

import android.content.Context
import android.content.Intent
import dev.qtremors.arcile.utils.AppLogger
import java.util.ArrayList

object ShareHelper {
    suspend fun shareFiles(context: Context, filePaths: List<String>): Boolean {
        if (filePaths.isEmpty()) return false

        try {
            val targets = ExternalFileAccessHelper.createShareTargets(context, filePaths)
            if (targets.isEmpty()) return false
            val uris = ArrayList(targets.map { it.uri })

            val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = commonMimeType(targets.map { it.mimeType })
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

    private fun commonMimeType(mimeTypes: List<String>): String {
        val concreteTypes = mimeTypes.filter { it != "*/*" }
        if (concreteTypes.isEmpty()) return "*/*"
        val distinct = concreteTypes.distinct()
        if (distinct.size == 1) return distinct.single()
        val topLevelTypes = distinct.map { it.substringBefore('/') }.distinct()
        return if (topLevelTypes.size == 1) "${topLevelTypes.single()}/*" else "*/*"
    }
}


