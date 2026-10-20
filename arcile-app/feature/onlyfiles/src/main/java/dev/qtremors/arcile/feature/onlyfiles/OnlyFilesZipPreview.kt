package dev.qtremors.arcile.feature.onlyfiles

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.unit.dp
import dev.qtremors.arcile.core.storage.domain.FileModel
import dev.qtremors.arcile.core.storage.domain.StorageNodeRef
import dev.qtremors.arcile.core.storage.domain.StorageNodeCapabilities
import dev.qtremors.arcile.core.ui.Breadcrumbs
import dev.qtremors.arcile.core.ui.lists.FileList
import dev.qtremors.arcile.core.vault.domain.VaultNodeMetadata
import dev.qtremors.arcile.core.vault.domain.VaultNodeRef
import dev.qtremors.arcile.core.vault.domain.VaultSeekableReader
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.ClosedChannelException
import java.nio.channels.NonWritableChannelException
import java.nio.channels.SeekableByteChannel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.apache.commons.compress.archivers.zip.ZipFile
import dev.qtremors.arcile.core.storage.domain.ArchiveFormat

@Composable
internal fun VaultArchivePreview(
    node: VaultNodeMetadata,
    openReader: (VaultNodeRef) -> Result<VaultSeekableReader>,
    folder: List<String>,
    operationActive: Boolean,
    onOpenFolder: (List<String>) -> Unit,
    onExtract: (VaultZipSelection) -> Unit
) {
    val contents by produceState<Result<List<VaultZipEntry>>?>(null, node.ref, node.revision) {
        value = withContext(Dispatchers.IO) {
            val coroutineContext = currentCoroutineContext()
            runCatching {
                openReader(node.ref).getOrThrow().use { reader ->
                    VaultZipDirectoryChannel(reader) { coroutineContext.ensureActive() }.use { channel ->
                        if (ArchiveFormat.fromPath(node.name) == ArchiveFormat.SEVEN_Z) {
                            openVaultSevenZ(channel).use { archive ->
                                archive.entries.take(MAX_PREVIEW_ENTRIES + 1).map {
                                    VaultZipEntry(it.name.orEmpty(), it.isDirectory, it.size)
                                }
                            }
                        } else {
                            ZipFile.builder().setSeekableByteChannel(channel).setIgnoreLocalFileHeader(true).get().use { zip ->
                                zip.entries.asSequence().take(MAX_PREVIEW_ENTRIES + 1).map {
                                    VaultZipEntry(it.name, it.isDirectory, it.size, it.generalPurposeBit.usesEncryption())
                                }.toList()
                            }
                        }
                    }
                }
            }
        }
    }
    val entries = contents?.getOrNull()
    val tree = remember(entries) { entries?.let { VaultZipTree(it.take(MAX_PREVIEW_ENTRIES)) } }
    Box(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(top = 72.dp),
        contentAlignment = Alignment.Center
    ) {
        when {
            contents == null -> CircularProgressIndicator()
            tree == null || entries == null -> Text(stringResource(R.string.onlyfiles_zip_unavailable), color = Color.White)
            else -> ZipFolderListing(
                node, tree, folder, entries.size > MAX_PREVIEW_ENTRIES, operationActive, onOpenFolder, onExtract
            )
        }
    }
}

@Composable
private fun ZipFolderListing(
    archive: VaultNodeMetadata,
    tree: VaultZipTree,
    folder: List<String>,
    truncated: Boolean,
    operationActive: Boolean,
    onOpenFolder: (List<String>) -> Unit,
    onExtract: (VaultZipSelection) -> Unit
) {
    val items = remember(tree, folder) { tree.list(folder) }
    val models = remember(items, archive.ref) {
        items.mapIndexed { index, item ->
            val ref = StorageNodeRef.vault(
                archive.ref.vaultId.value, "${archive.ref.nodeId.value}:zip:$index",
                StorageNodeCapabilities(canRead = false, canWrite = false, canDelete = false, canArchive = false)
            )
            FileModel(
                name = item.name,
                reference = ref.displayPath.absolutePath,
                size = item.size,
                isDirectory = item.isDirectory,
                extension = if (item.isDirectory) "" else item.name.substringAfterLast('.', "").lowercase(),
                nodeRef = ref
            )
        }
    }
    val byReference = remember(items, models) {
        models.zip(items).associate { it.first.reference to it.second }
    }
    var selected by remember(archive.ref, folder) { mutableStateOf(emptySet<String>()) }
    fun toggle(reference: String) {
        if (operationActive) return
        selected = if (reference in selected) selected - reference else selected + reference
    }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize()) {
            Breadcrumbs(
                rootLabel = archive.name,
                rootKey = "0",
                segments = folder.mapIndexed { index, name -> name to (index + 1).toString() },
                onSegmentClick = { onOpenFolder(folder.take(it.toInt())) }
            )
            if (truncated) Text(
                stringResource(R.string.onlyfiles_zip_truncated, MAX_PREVIEW_ENTRIES),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
            if (items.isEmpty()) {
                Text(stringResource(R.string.onlyfiles_zip_empty), modifier = Modifier.padding(16.dp))
            } else {
                val folderLabel = stringResource(R.string.onlyfiles_folder)
                androidx.compose.runtime.key(folder) {
                    FileList(
                        files = models,
                        selectedFiles = selected,
                        onNavigateTo = { reference ->
                            if (!operationActive) byReference[reference]?.takeIf { it.isDirectory }?.let { onOpenFolder(it.path) }
                        },
                        onOpenFile = ::toggle,
                        onToggleSelection = ::toggle,
                        onSelectMultiple = { if (!operationActive) selected = selected + it },
                        modifier = Modifier.weight(1f),
                        presentation = dev.qtremors.arcile.core.ui.lists.FileItemPresentation(
                            showThumbnails = false,
                            contextLabel = { folderLabel.takeIf { _ -> it.isDirectory } }
                        )
                    )
                }
                if (!operationActive) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = {
                            selected = if (selected.size == models.size) emptySet()
                            else models.mapTo(linkedSetOf(), FileModel::reference)
                        }) {
                            Text(stringResource(if (selected.size == models.size) {
                                R.string.onlyfiles_clear_zip_selection
                            } else R.string.onlyfiles_select_zip_items))
                        }
                        FilledTonalButton(
                            enabled = !truncated,
                            onClick = {
                                val paths = selected.mapNotNull { byReference[it]?.path }.toSet()
                                val selection = VaultZipSelection(folder, paths)
                                onExtract(selection.copy(requiresPassword = tree.requiresPassword(selection)))
                            }
                        ) {
                            Text(if (selected.isEmpty()) stringResource(R.string.onlyfiles_extract_zip)
                            else pluralStringResource(R.plurals.onlyfiles_extract_zip_selected, selected.size, selected.size))
                        }
                    }
                }
            }
        }
    }
}

internal class VaultZipDirectoryChannel(
    private val reader: VaultSeekableReader,
    readBudget: Long = MAX_DIRECTORY_BYTES,
    private val checkActive: () -> Unit
) : SeekableByteChannel {
    private var open = true
    private var offset = 0L
    private var remainingBudget = readBudget
    private val buffer = ByteArray(8192)

    override fun read(dst: ByteBuffer): Int {
        checkActive()
        if (!open) throw ClosedChannelException()
        if (!dst.hasRemaining()) return 0
        if (offset >= reader.sizeBytes) return -1
        if (remainingBudget <= 0) throw IOException("ZIP directory exceeds the preview limit")
        val count = minOf(dst.remaining().toLong(), buffer.size.toLong(), remainingBudget, reader.sizeBytes - offset).toInt()
        val read = reader.readAt(offset, buffer, 0, count)
        if (read <= 0) throw IOException("ZIP directory is incomplete")
        dst.put(buffer, 0, read)
        offset += read
        remainingBudget -= read
        return read
    }

    override fun position(): Long = offset
    override fun position(newPosition: Long): SeekableByteChannel {
        if (!open) throw ClosedChannelException()
        require(newPosition >= 0L)
        offset = newPosition
        return this
    }

    override fun size(): Long = reader.sizeBytes
    override fun isOpen(): Boolean = open
    override fun close() {
        open = false
        buffer.fill(0)
    }
    override fun write(src: ByteBuffer): Int = throw NonWritableChannelException()
    override fun truncate(size: Long): SeekableByteChannel = throw NonWritableChannelException()
}

private const val MAX_PREVIEW_ENTRIES = 1_000
private const val MAX_DIRECTORY_BYTES = 2L * 1024 * 1024
