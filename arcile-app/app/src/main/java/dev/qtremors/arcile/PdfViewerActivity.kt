package dev.qtremors.arcile

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.lifecycleScope
import dev.qtremors.arcile.core.ui.R
import dev.qtremors.arcile.core.ui.externalfile.ExternalFileAccessHelper
import dev.qtremors.arcile.core.ui.pdf.StandalonePdfViewer
import dev.qtremors.arcile.core.ui.theme.ArcileTheme
import dev.qtremors.arcile.core.ui.theme.ThemeState
import dev.qtremors.arcile.presentation.utils.ShareHelper
import java.io.File
import kotlinx.coroutines.launch

class PdfViewerActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val target = resolveStandalonePdfTarget(this, intent)
        if (target == null) {
            Toast.makeText(
                this,
                getString(
                    R.string.cannot_open_file,
                    getString(R.string.error_unsupported_provider)
                ),
                Toast.LENGTH_SHORT
            ).show()
            finish()
            return
        }

        setContent {
            ArcileTheme(themeState = ThemeState()) {
                StandalonePdfViewer(
                    reference = target.reference,
                    title = target.displayName,
                    sizeBytes = target.sizeBytes ?: 0L,
                    onNavigateBack = ::finish,
                    onShare = { shareTarget(target) },
                    onOpenWith = { openTargetWithChooser(target) }
                )
            }
        }
    }

    private fun shareTarget(target: StandalonePdfTarget) {
        lifecycleScope.launch {
            val shared = ShareHelper.shareFileReferences(
                this@PdfViewerActivity,
                listOf(target.toExternalReference())
            )
            if (!shared) showFailure()
        }
    }

    private fun openTargetWithChooser(target: StandalonePdfTarget) {
        lifecycleScope.launch {
            runCatching {
                val openIntent = ExternalFileAccessHelper.createOpenIntent(
                    this@PdfViewerActivity,
                    target.toExternalReference()
                )
                startActivity(
                    ExternalFileAccessHelper.createExternalOpenChooser(
                        this@PdfViewerActivity,
                        openIntent,
                        target.displayName
                    )
                )
            }.onFailure { showFailure() }
        }
    }

    private fun showFailure() {
        Toast.makeText(
            this,
            getString(R.string.cannot_open_file, getString(R.string.no_app_found)),
            Toast.LENGTH_SHORT
        ).show()
    }
}

data class StandalonePdfTarget(
    val reference: String,
    val displayName: String,
    val sizeBytes: Long?
) {
    fun toExternalReference() = ExternalFileAccessHelper.ExternalFileReference(
        path = reference,
        displayName = displayName,
        sizeBytes = sizeBytes,
        mimeType = PDF_MIME_TYPE
    )
}

internal fun resolveStandalonePdfTarget(
    context: Context,
    intent: Intent
): StandalonePdfTarget? {
    if (intent.action != Intent.ACTION_VIEW) return null
    val uri = intent.data ?: return null
    val mimeType = intent.type ?: context.contentResolver.getType(uri)
    val extension = uri.lastPathSegment
        ?.substringAfterLast('.', "")
        ?.lowercase()
        .orEmpty()
    if (mimeType != PDF_MIME_TYPE && extension != PDF_EXTENSION) return null

    return when (uri.scheme) {
        "content" -> StandalonePdfTarget(
            reference = uri.toString(),
            displayName = queryOpenableColumn(
                context,
                uri,
                OpenableColumns.DISPLAY_NAME
            ) { cursor, index ->
                cursor.getString(index)
            } ?: uri.lastPathSegment ?: "Document.pdf",
            sizeBytes = queryOpenableColumn(
                context,
                uri,
                OpenableColumns.SIZE
            ) { cursor, index ->
                cursor.getLong(index)
            }
        )
        "file", null -> {
            val file = if (uri.scheme == "file") {
                File(uri.path.orEmpty())
            } else {
                File(uri.toString())
            }
            if (
                !file.isFile ||
                !ExternalFileAccessHelper.isAllowedUserFile(context, file) ||
                !file.extension.equals(PDF_EXTENSION, ignoreCase = true)
            ) {
                return null
            }
            StandalonePdfTarget(
                reference = file.absolutePath,
                displayName = file.name,
                sizeBytes = file.length()
            )
        }
        else -> null
    }
}

private const val PDF_MIME_TYPE = "application/pdf"
private const val PDF_EXTENSION = "pdf"
