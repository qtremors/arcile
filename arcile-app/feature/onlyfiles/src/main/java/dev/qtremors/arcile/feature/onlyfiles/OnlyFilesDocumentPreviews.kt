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
import dev.qtremors.arcile.core.vault.domain.VaultNodeMetadata
import dev.qtremors.arcile.core.vault.domain.VaultNodeRef
import dev.qtremors.arcile.core.vault.domain.VaultSeekableReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

@Composable
internal fun VaultPdfPreview(
    node: VaultNodeMetadata,
    openReader: (VaultNodeRef) -> Result<VaultSeekableReader>,
    onBack: () -> Unit,
    onShare: () -> Unit,
    onOpenWith: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    StandalonePdfViewer(
        reference = node.toSharedFileModel().reference,
        title = node.name,
        sizeBytes = node.sizeBytes,
        onNavigateBack = onBack,
        onShare = onShare,
        onOpenWith = onOpenWith,
        modifier = modifier,
        openPrivateDescriptor = { openVaultPdfDescriptor(context, openReader(node.ref).getOrThrow()) }
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
        loadContent = { readVaultText(node.ref, viewModel::openReader) },
        persistContent = { text ->
            viewModel.saveVaultText(node.ref, revision, text).map { saved ->
                revision = saved.revision
            }
        },
        privateSession = true
    )
}

private suspend fun readVaultText(
    ref: VaultNodeRef,
    openReader: (VaultNodeRef) -> Result<VaultSeekableReader>
): String = withContext(Dispatchers.IO) {
    openReader(ref).getOrThrow().use { reader ->
        require(reader.sizeBytes in 0..MAX_VAULT_EDITOR_BYTES.toLong()) { "This file exceeds the text editor limit." }
        val bytes = ByteArray(reader.sizeBytes.toInt())
        try {
            var offset = 0
            while (offset < bytes.size) {
                ensureActive()
                val read = reader.readAt(offset.toLong(), bytes, offset, bytes.size - offset)
                check(read > 0) { "The encrypted file ended before its declared size." }
                offset += read
            }
            bytes.decodeToString(throwOnInvalidSequence = true).removePrefix("\uFEFF")
        } finally {
            bytes.fill(0)
        }
    }
}

internal const val MAX_VAULT_EDITOR_BYTES = 4 * 1024 * 1024
