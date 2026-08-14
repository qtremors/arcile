package dev.qtremors.arcile

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.OpenableColumns
import dev.qtremors.arcile.core.ui.showArcileToast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import androidx.compose.runtime.getValue
import dev.qtremors.arcile.core.ui.R
import dev.qtremors.arcile.core.ui.externalfile.ExternalFileAccessHelper
import dev.qtremors.arcile.core.ui.texteditor.StandaloneTextEditor
import dev.qtremors.arcile.core.ui.theme.ArcileTheme
import dev.qtremors.arcile.core.ui.theme.ThemePreferences
import dev.qtremors.arcile.core.ui.theme.ThemeState
import dev.qtremors.arcile.core.storage.domain.FileModel
import dev.qtremors.arcile.core.storage.domain.PrivilegedContentAccessManager
import dev.qtremors.arcile.core.storage.domain.PrivilegedContentGrantPurpose
import dev.qtremors.arcile.core.storage.domain.PrivilegedTextFileEditor
import dev.qtremors.arcile.core.storage.domain.StorageNodeCapabilities
import dev.qtremors.arcile.core.storage.domain.StorageNodeRef
import dev.qtremors.arcile.core.storage.domain.isPrivileged
import dev.qtremors.arcile.presentation.utils.ShareHelper
import java.io.File
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
open class TextEditorActivity : ComponentActivity() {
    @Inject lateinit var privilegedContentAccessManager: PrivilegedContentAccessManager
    @Inject lateinit var privilegedTextFileEditor: PrivilegedTextFileEditor

    private val themePreferences by lazy { ThemePreferences(applicationContext) }
    private var currentPrivilegedNode: StorageNodeRef? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val target = resolveStandaloneTextTarget(this, intent)
        if (target == null) {
            showArcileToast(
                getString(
                    R.string.cannot_open_file,
                    getString(R.string.error_unsupported_provider)
                )
            )
            finish()
            return
        }
        currentPrivilegedNode = target.nodeRef

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
                    onOpenWith = { openTargetWithChooser(target) },
                    persistContent = target.nodeRef?.takeIf { it.isPrivileged }?.let {
                        { content -> saveProtectedContent(content) }
                    }
                )
            }
        }
    }

    private fun shareTarget(target: StandaloneTextTarget) {
        lifecycleScope.launch {
            val reference = runCatching { target.toHandoffReference() }
                .getOrElse { return@launch showFailure() }
            val shared = ShareHelper.shareFileReferences(
                this@TextEditorActivity,
                listOf(reference)
            )
            if (!shared) showFailure()
        }
    }

    private fun openTargetWithChooser(target: StandaloneTextTarget) {
        lifecycleScope.launch {
            runCatching {
                val openIntent = ExternalFileAccessHelper.createOpenIntent(
                    this@TextEditorActivity,
                    target.toHandoffReference()
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
        showArcileToast(getString(R.string.cannot_open_file, getString(R.string.no_app_found)))
    }

    private suspend fun saveProtectedContent(content: String): Result<Unit> {
        val node = currentPrivilegedNode
            ?: return Result.failure(IllegalStateException("Protected document identity is unavailable"))
        return privilegedTextFileEditor.saveAtomically(node, content.toByteArray(Charsets.UTF_8))
            .map { updated ->
                currentPrivilegedNode = updated.nodeRef
                Unit
            }
    }

    private suspend fun StandaloneTextTarget.toHandoffReference(): ExternalFileAccessHelper.ExternalFileReference {
        val node = currentPrivilegedNode ?: nodeRef
        if (node?.isPrivileged != true) return toExternalReference()
        val grant = privilegedContentAccessManager.issue(
            node = node,
            displayName = displayName,
            mimeType = mimeType,
            purpose = PrivilegedContentGrantPurpose.EXTERNAL_HANDOFF,
            lifetimeMillis = PrivilegedContentAccessManager.DEFAULT_EXTERNAL_GRANT_LIFETIME_MILLIS,
            expectedConsumerUid = null
        ).getOrThrow()
        return ExternalFileAccessHelper.ExternalFileReference(
            path = node.displayPath.absolutePath,
            displayName = displayName,
            sizeBytes = grant.sizeBytes,
            mimeType = grant.mimeType,
            nodeRef = node.copy(contentUri = grant.contentUri)
        )
    }
}

/** Main-process host used only when the active privileged backend owns the document. */
class ProtectedTextEditorActivity : TextEditorActivity()

data class StandaloneTextTarget(
    val reference: String,
    val displayName: String,
    val sizeBytes: Long?,
    val mimeType: String,
    val writable: Boolean,
    val isMarkdown: Boolean,
    val nodeRef: StorageNodeRef? = null
) {
    fun toExternalReference() = ExternalFileAccessHelper.ExternalFileReference(
        path = reference,
        displayName = displayName,
        sizeBytes = sizeBytes,
        mimeType = mimeType,
        nodeRef = nodeRef
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
            writable = intent.action == Intent.ACTION_EDIT && (
                intent.getBooleanExtra(EXTRA_PROTECTED_CAN_WRITE, false) ||
                    context.hasWriteAccess(intent, uri)
                ),
            isMarkdown = isMarkdown,
            nodeRef = intent.protectedNodeRef(uri.toString())
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

fun createProtectedTextEditorIntent(context: Context, file: FileModel): Intent {
    require(file.nodeRef.isPrivileged) { "Text target is not a protected file" }
    val contentUri = requireNotNull(file.nodeRef.contentUri) {
        "Protected text target has no content capability"
    }
    return Intent(Intent.ACTION_EDIT).apply {
        setClass(context, ProtectedTextEditorActivity::class.java)
        setDataAndType(android.net.Uri.parse(contentUri), file.mimeType ?: "text/plain")
        putExtra(Intent.EXTRA_TITLE, file.name)
        putExtra(EXTRA_PROTECTED_BACKEND_ID, file.nodeRef.backendId)
        putExtra(EXTRA_PROTECTED_DISPLAY_PATH, file.nodeRef.displayPath.absolutePath)
        putExtra(EXTRA_PROTECTED_BACKEND_IDENTITY, file.nodeRef.backendIdentity)
        putExtra(EXTRA_PROTECTED_CAN_WRITE, file.nodeRef.capabilities.canWrite)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
}

private fun Intent.protectedNodeRef(contentUri: String): StorageNodeRef? {
    val backendId = getStringExtra(EXTRA_PROTECTED_BACKEND_ID) ?: return null
    val displayPath = getStringExtra(EXTRA_PROTECTED_DISPLAY_PATH) ?: return null
    val backendIdentity = getStringExtra(EXTRA_PROTECTED_BACKEND_IDENTITY) ?: return null
    if (backendId !in setOf(StorageNodeRef.ROOT_BACKEND_ID, StorageNodeRef.SHIZUKU_BACKEND_ID)) {
        return null
    }
    return StorageNodeRef.privileged(
        backendId = backendId,
        displayPath = displayPath,
        remoteCanonicalIdentity = backendIdentity,
        capabilities = StorageNodeCapabilities(
            canRead = true,
            canWrite = getBooleanExtra(EXTRA_PROTECTED_CAN_WRITE, false)
        )
    ).copy(contentUri = contentUri)
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

private const val EXTRA_PROTECTED_BACKEND_ID =
    "dev.qtremors.arcile.extra.PROTECTED_TEXT_BACKEND_ID"
private const val EXTRA_PROTECTED_DISPLAY_PATH =
    "dev.qtremors.arcile.extra.PROTECTED_TEXT_DISPLAY_PATH"
private const val EXTRA_PROTECTED_BACKEND_IDENTITY =
    "dev.qtremors.arcile.extra.PROTECTED_TEXT_BACKEND_IDENTITY"
private const val EXTRA_PROTECTED_CAN_WRITE =
    "dev.qtremors.arcile.extra.PROTECTED_TEXT_CAN_WRITE"
