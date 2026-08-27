package dev.qtremors.arcile

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import dev.qtremors.arcile.core.ui.showArcileToast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import dev.qtremors.arcile.core.runtime.di.ArcileDispatchers
import dev.qtremors.arcile.core.storage.domain.FileModel
import dev.qtremors.arcile.core.ui.ExternalViewerLoadScreen
import dev.qtremors.arcile.core.ui.R
import dev.qtremors.arcile.core.ui.externalfile.ExternalFileAccessHelper
import dev.qtremors.arcile.core.ui.externalfile.resolveExternalContentMetadata
import dev.qtremors.arcile.core.ui.pdf.StandalonePdfViewer
import dev.qtremors.arcile.core.ui.theme.ArcileTheme
import dev.qtremors.arcile.core.ui.theme.ThemeState
import dev.qtremors.arcile.presentation.utils.ShareHelper
import java.io.File
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@AndroidEntryPoint
open class PdfViewerActivity : ComponentActivity() {
    @Inject lateinit var dispatchers: ArcileDispatchers
    private var target by mutableStateOf<StandalonePdfTarget?>(null)
    private var loadError by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ArcileTheme(themeState = ThemeState()) {
                val resolved = target
                if (resolved == null) ExternalViewerLoadScreen(loadError, ::loadTarget) else {
                    StandalonePdfViewer(
                        reference = resolved.reference,
                        title = resolved.displayName,
                        sizeBytes = resolved.sizeBytes ?: 0L,
                        onNavigateBack = ::finish,
                        onShare = { shareTarget(resolved) },
                        onOpenWith = { openTargetWithChooser(resolved) },
                        onFileRenamed = { _, newFile ->
                            target = resolved.copy(
                                reference = newFile.absolutePath,
                                displayName = newFile.name,
                                sizeBytes = newFile.size
                            )
                        },
                        onFileDeleted = { finish() }
                    )
                }
            }
        }
        loadTarget()
    }

    private fun loadTarget() {
        loadError = null
        lifecycleScope.launch {
            target = resolveStandalonePdfTarget(this@PdfViewerActivity, intent, dispatchers.io)
            if (target == null) loadError = getString(R.string.error_unsupported_provider)
        }
    }

    private fun shareTarget(target: StandalonePdfTarget) {
        lifecycleScope.launch {
            val reference = target.toExternalReference()
            val shared = ShareHelper.shareFileReferences(
                this@PdfViewerActivity,
                listOf(reference)
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
        showArcileToast(getString(R.string.cannot_open_file, getString(R.string.no_app_found)))
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

internal fun resolveStandalonePdfTarget(context: Context, intent: Intent): StandalonePdfTarget? {
    if (intent.action != Intent.ACTION_VIEW) return null
    val uri = intent.data ?: return null
    val extension = uri.lastPathSegment?.substringAfterLast('.', "")?.lowercase().orEmpty()
    if (intent.type != PDF_MIME_TYPE && extension != PDF_EXTENSION) return null
    return when (uri.scheme) {
        "content" -> StandalonePdfTarget(
            reference = uri.toString(),
            displayName = intent.getStringExtra(Intent.EXTRA_TITLE)
                ?: uri.lastPathSegment
                ?: "Document.pdf",
            sizeBytes = null
        )
        "file", null -> {
            val file = File(uri.path.orEmpty())
            if (!file.isFile || !ExternalFileAccessHelper.isAllowedUserFile(context, file)) null
            else StandalonePdfTarget(file.absolutePath, file.name, file.length())
        }
        else -> null
    }
}

internal suspend fun resolveStandalonePdfTarget(
    context: Context,
    intent: Intent,
    ioDispatcher: kotlinx.coroutines.CoroutineDispatcher
): StandalonePdfTarget? {
    if (intent.action != Intent.ACTION_VIEW) return null
    val uri = intent.data ?: return null
    val extension = uri.lastPathSegment
        ?.substringAfterLast('.', "")
        ?.lowercase()
        .orEmpty()

    return when (uri.scheme) {
        "content" -> {
            val metadata = resolveExternalContentMetadata(
                context.contentResolver, uri, intent.type, ioDispatcher
            ).getOrNull() ?: return null
            if (metadata.mimeType != PDF_MIME_TYPE && extension != PDF_EXTENSION) return null
            StandalonePdfTarget(
                reference = uri.toString(),
                displayName = metadata.displayName ?: uri.lastPathSegment ?: "Document.pdf",
                sizeBytes = metadata.sizeBytes
            )
        }
        "file", null -> withContext(ioDispatcher) {
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
                return@withContext null
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
