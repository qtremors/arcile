package dev.qtremors.arcile

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.compose.runtime.getValue
import dev.qtremors.arcile.core.ui.R
import dev.qtremors.arcile.core.ui.externalfile.ExternalFileAccessHelper
import dev.qtremors.arcile.core.ui.texteditor.StandaloneTextEditor
import dev.qtremors.arcile.core.ui.theme.ArcileTheme
import dev.qtremors.arcile.core.ui.theme.ThemePreferences
import dev.qtremors.arcile.core.ui.theme.ThemeState
import dev.qtremors.arcile.presentation.utils.ShareHelper
import java.io.File
import kotlinx.coroutines.launch

class TextEditorActivity : ComponentActivity() {
    private val themePreferences by lazy { ThemePreferences(applicationContext) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val target = resolveStandaloneTextTarget(this, intent)
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
            val themeState by themePreferences.themeState.collectAsStateWithLifecycle(
                initialValue = ThemeState()
            )
            ArcileTheme(themeState = themeState) {
                StandaloneTextEditor(
                    reference = target.reference,
                    title = target.displayName,
                    sizeBytes = target.sizeBytes ?: 0L,
                    writable = target.writable,
                    supportsMarkdownPreview = target.isMarkdown,
                    onNavigateBack = ::finish,
                    onShare = { shareTarget(target) },
                    onOpenWith = { openTargetWithChooser(target) }
                )
            }
        }
    }

    private fun shareTarget(target: StandaloneTextTarget) {
        lifecycleScope.launch {
            val shared = ShareHelper.shareFileReferences(
                this@TextEditorActivity,
                listOf(target.toExternalReference())
            )
            if (!shared) showFailure()
        }
    }

    private fun openTargetWithChooser(target: StandaloneTextTarget) {
        lifecycleScope.launch {
            runCatching {
                val openIntent = ExternalFileAccessHelper.createOpenIntent(
                    this@TextEditorActivity,
                    target.toExternalReference()
                )
                startActivity(
                    ExternalFileAccessHelper.createExternalOpenChooser(
                        this@TextEditorActivity,
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

data class StandaloneTextTarget(
    val reference: String,
    val displayName: String,
    val sizeBytes: Long?,
    val mimeType: String,
    val writable: Boolean,
    val isMarkdown: Boolean
) {
    fun toExternalReference() = ExternalFileAccessHelper.ExternalFileReference(
        path = reference,
        displayName = displayName,
        sizeBytes = sizeBytes,
        mimeType = mimeType
    )
}

internal fun resolveStandaloneTextTarget(
    context: Context,
    intent: Intent
): StandaloneTextTarget? {
    if (intent.action != Intent.ACTION_VIEW && intent.action != Intent.ACTION_EDIT) return null
    val uri = intent.data ?: return null
    val mimeType = intent.type ?: context.contentResolver.getType(uri)
    val displayName = when (uri.scheme) {
        "content" -> queryOpenableColumn(
            context,
            uri,
            OpenableColumns.DISPLAY_NAME
        ) { cursor, index ->
            cursor.getString(index)
        } ?: uri.lastPathSegment ?: "document.txt"
        "file", null -> {
            val file = if (uri.scheme == "file") File(uri.path.orEmpty()) else File(uri.toString())
            file.name
        }
        else -> return null
    }
    val extension = displayName.substringAfterLast('.', "").lowercase()
    val isTextMime = mimeType?.startsWith("text/") == true ||
        mimeType == "application/json" ||
        mimeType == "application/xml" ||
        mimeType == "application/markdown" ||
        mimeType == "application/x-markdown"
    if (!isTextMime && extension !in TEXT_EXTENSIONS) return null
    val resolvedMimeType = mimeType ?: when (displayName.substringAfterLast('.', "").lowercase()) {
        "md", "markdown" -> "text/markdown"
        else -> "text/plain"
    }
    val isMarkdown = resolvedMimeType == "text/markdown" ||
        resolvedMimeType == "text/x-markdown" ||
        resolvedMimeType == "application/markdown" ||
        resolvedMimeType == "application/x-markdown" ||
        displayName.substringAfterLast('.', "").lowercase() in setOf("md", "markdown")

    return when (uri.scheme) {
        "content" -> StandaloneTextTarget(
            reference = uri.toString(),
            displayName = displayName,
            sizeBytes = queryOpenableColumn(
                context,
                uri,
                OpenableColumns.SIZE
            ) { cursor, index ->
                cursor.getLong(index)
            },
            mimeType = resolvedMimeType,
            writable = intent.action == Intent.ACTION_EDIT && context.hasWriteAccess(intent, uri),
            isMarkdown = isMarkdown
        )
        "file", null -> {
            val file = if (uri.scheme == "file") {
                File(uri.path.orEmpty())
            } else {
                File(uri.toString())
            }
            if (
                !file.isFile ||
                !ExternalFileAccessHelper.isAllowedUserFile(context, file)
            ) {
                return null
            }
            StandaloneTextTarget(
                reference = file.absolutePath,
                displayName = file.name,
                sizeBytes = file.length(),
                mimeType = resolvedMimeType,
                writable = intent.action == Intent.ACTION_EDIT && file.canWrite(),
                isMarkdown = isMarkdown
            )
        }
        else -> null
    }
}

private fun Context.hasWriteAccess(intent: Intent, uri: android.net.Uri): Boolean {
    val explicitGrant = intent.flags and Intent.FLAG_GRANT_WRITE_URI_PERMISSION != 0
    val persistedGrant = contentResolver.persistedUriPermissions.any { permission ->
        permission.uri == uri && permission.isWritePermission
    }
    return explicitGrant || persistedGrant
}

private val TEXT_EXTENSIONS = setOf(
    "txt", "md", "markdown", "log", "json", "xml", "yaml", "yml",
    "csv", "ini", "conf", "properties", "kt", "java", "py", "js", "html", "css"
)
