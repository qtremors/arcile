package dev.qtremors.arcile.presentation.utils

import android.content.Context
import android.content.ClipData
import android.content.Intent
import dev.qtremors.arcile.core.ui.externalfile.ExternalFileAccessHelper
import dev.qtremors.arcile.core.ui.R
import dev.qtremors.arcile.core.runtime.logging.AppLogger
import java.util.ArrayList

object ShareHelper {
    suspend fun shareFiles(context: Context, filePaths: List<String>): Boolean {
        return shareFileReferences(
            context,
            filePaths.map { ExternalFileAccessHelper.ExternalFileReference(path = it) }
        )
    }

    suspend fun shareFileReferences(
        context: Context,
        references: List<ExternalFileAccessHelper.ExternalFileReference>
    ): Boolean {
        if (references.isEmpty()) return false

        try {
            val targets = ExternalFileAccessHelper.createShareTargets(context, references)
            if (targets.isEmpty()) return false
            val uris = ArrayList(targets.map { it.uri })

            val isMultiple = targets.size > 1
            val intent = Intent(
                if (isMultiple) Intent.ACTION_SEND_MULTIPLE else Intent.ACTION_SEND
            ).apply {
                type = commonMimeType(targets.map { it.mimeType })
                if (isMultiple) {
                    putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                } else {
                    putExtra(Intent.EXTRA_STREAM, uris.single())
                    putExtra(Intent.EXTRA_TITLE, targets.single().displayName)
                    putExtra(Intent.EXTRA_SUBJECT, targets.single().displayName)
                }
                clipData = shareClipData(context, targets)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            val chooser = Intent.createChooser(intent, context.getString(R.string.share_files_via))
            chooser.addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
            context.startActivity(chooser)
            return true
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            AppLogger.e("ShareHelper", "Failed to share files", e)
            return false
        }
    }

    private fun shareClipData(
        context: Context,
        targets: List<ExternalFileAccessHelper.ShareTarget>
    ): ClipData {
        val first = targets.first()
        return ClipData.newUri(context.contentResolver, first.displayName, first.uri).apply {
            targets.drop(1).forEach { target ->
                addItem(ClipData.Item(target.uri))
            }
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


