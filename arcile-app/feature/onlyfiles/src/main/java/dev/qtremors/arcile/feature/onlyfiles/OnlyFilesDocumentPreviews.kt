package dev.qtremors.arcile.feature.onlyfiles

import android.content.Context
import android.os.Handler
import android.os.HandlerThread
import android.os.ParcelFileDescriptor
import android.os.ProxyFileDescriptorCallback
import android.os.storage.StorageManager
import android.system.ErrnoException
import android.system.OsConstants
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import dev.qtremors.arcile.core.ui.pdf.StandalonePdfViewer
import dev.qtremors.arcile.core.ui.texteditor.StandaloneTextEditor
import dev.qtremors.arcile.core.ui.viewer.PrivateViewerActions
import dev.qtremors.arcile.core.ui.viewer.ViewerFileAction
import dev.qtremors.arcile.core.vault.domain.VaultNodeMetadata
import dev.qtremors.arcile.core.vault.domain.VaultNodeRef
import dev.qtremors.arcile.core.vault.domain.VaultSeekableReader
import dev.qtremors.arcile.core.vault.domain.VaultFailure
import dev.qtremors.arcile.core.ui.asString

@Composable
internal fun VaultPdfPreview(
    node: VaultNodeMetadata,
    openReader: (VaultNodeRef) -> Result<VaultSeekableReader>,
    onBack: () -> Unit,
    onShare: () -> Unit,
    onOpenWith: () -> Unit,
    modifier: Modifier = Modifier,
    onFileAction: (ViewerFileAction) -> Unit = {}
) {
    val context = LocalContext.current
    val capabilities = node.toSharedFileModel().nodeRef.capabilities
    val currentAction by androidx.compose.runtime.rememberUpdatedState(onFileAction)
    val fileActions = remember(node, capabilities) { PrivateViewerActions(capabilities) { currentAction(it) } }
    StandalonePdfViewer(
        reference = node.toSharedFileModel().reference,
        title = node.name,
        sizeBytes = node.sizeBytes,
        onNavigateBack = onBack,
        onShare = onShare,
        onOpenWith = onOpenWith,
        modifier = modifier,
        openPrivateDescriptor = { openVaultPdfDescriptor(context, openReader(node.ref).getOrThrow()) },
        privateFileActions = fileActions
    )
}

private fun openVaultPdfDescriptor(context: Context, reader: VaultSeekableReader): ParcelFileDescriptor {
    val worker = HandlerThread("OnlyFiles PDF reader")
    return try {
        worker.start()
        context.getSystemService(StorageManager::class.java).openProxyFileDescriptor(
            ParcelFileDescriptor.MODE_READ_ONLY,
            object : ProxyFileDescriptorCallback() {
                override fun onGetSize(): Long = reader.sizeBytes

                override fun onRead(offset: Long, size: Int, data: ByteArray): Int {
                    if (offset >= reader.sizeBytes || size == 0) return 0
                    return try {
                        reader.readAt(offset, data, 0, minOf(size.toLong(), reader.sizeBytes - offset).toInt())
                            .also { check(it > 0) }
                    } catch (_: Exception) {
                        throw ErrnoException("read", OsConstants.EIO)
                    }
                }

                override fun onRelease() {
                    try { reader.close() } finally { worker.quitSafely() }
                }
            },
            Handler(worker.looper)
        )
    } catch (error: Throwable) {
        try { reader.close() } finally { worker.quitSafely() }
        throw error
    }
}

@Composable
internal fun VaultTextEditor(
    node: VaultNodeMetadata,
    viewModel: OnlyFilesViewModel,
    onBack: () -> Unit,
    onShare: () -> Unit,
    onOpenWith: () -> Unit,
    modifier: Modifier = Modifier
) {
    var revision by remember(node.ref) { mutableLongStateOf(node.revision) }
    val context = LocalContext.current
    StandaloneTextEditor(
        reference = node.toSharedFileModel().reference,
        title = node.name,
        sizeBytes = node.sizeBytes,
        writable = node.ref.capabilities.canRename,
        supportsMarkdownPreview = node.extension in setOf("md", "markdown") || node.mimeType == "text/markdown",
        onNavigateBack = onBack,
        onShare = onShare,
        onOpenWith = onOpenWith,
        modifier = modifier,
        // The shared editor shows exception text, so vault failures are resolved to localized messages first.
        loadContent = {
            try {
                readVaultText(node.ref, viewModel::openReader)
            } catch (error: Exception) {
                throw localizedVaultError(error, context)
            }
        },
        persistContent = { text ->
            viewModel.saveVaultText(node.ref, revision, text).map { saved ->
                revision = saved.revision
            }.recoverCatching { error -> throw localizedVaultError(error, context) }
        },
        privateSession = true
    )
}

/** Keeps cancellation intact; any other failure becomes its localized vault message for display. */
private fun localizedVaultError(error: Throwable, context: Context): Throwable =
    if (error is kotlinx.coroutines.CancellationException) error
    else IllegalStateException(vaultOperationErrorText(error, R.string.onlyfiles_error_operation).asString(context), error)
