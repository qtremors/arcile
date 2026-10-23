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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import dev.qtremors.arcile.core.archive.ArchivePasswordRequired
import dev.qtremors.arcile.core.storage.domain.FileModel
import dev.qtremors.arcile.core.storage.domain.StorageNodeRef
import dev.qtremors.arcile.core.storage.domain.StorageNodeCapabilities
import dev.qtremors.arcile.core.ui.Breadcrumbs
import dev.qtremors.arcile.core.ui.lists.FileList
import dev.qtremors.arcile.core.vault.domain.VaultNodeMetadata
import dev.qtremors.arcile.core.vault.domain.VAULT_ARCHIVE_MAX_ENTRIES
import dev.qtremors.arcile.core.vault.domain.VaultNodeRef
import dev.qtremors.arcile.core.vault.domain.VaultSeekableReader
import kotlinx.coroutines.CancellationException
import dev.qtremors.arcile.core.storage.domain.ArchiveFormat
import dev.qtremors.arcile.core.storage.domain.ArchiveNameEncoding

private data class ArchivePreviewListing(val entries: List<VaultZipEntry>, val truncated: Boolean)

@Composable
internal fun VaultArchivePreview(
    node: VaultNodeMetadata,
    openReader: (VaultNodeRef) -> Result<VaultSeekableReader>,
    folder: List<String>,
    operationActive: Boolean,
    onOpenFolder: (List<String>) -> Unit,
    onExtract: (VaultZipSelection) -> Unit
) {
    val format = ArchiveFormat.fromPath(node.name)
    var encoding by remember(node.ref, node.revision) { mutableStateOf(ArchiveNameEncoding.UTF_8) }
    var password by remember(node.ref, node.revision) { mutableStateOf<CharArray?>(null) }
    val secret = password
    DisposableEffect(secret) {
        onDispose { secret?.fill('\u0000') }
    }
    val contents by produceState<Result<ArchivePreviewListing>?>(null, node.ref, node.revision, encoding, secret) {
        value = null
        value = try {
            Result.success(readVaultArchive(node, openReader, secret, encoding, preview = true) { archive ->
                val entries = archive.entries.take(VAULT_ARCHIVE_MAX_ENTRIES).mapNotNull { entry ->
                    val segments = entry.segments ?: return@mapNotNull null
                    if (!entry.canRead) return@mapNotNull null
                    VaultZipEntry(segments.joinToString("/"), entry.isDirectory, entry.size, entry.encrypted)
                }
                ArchivePreviewListing(entries, archive.entries.size > VAULT_ARCHIVE_MAX_ENTRIES)
            })
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Result.failure(error)
        }
    }
    val listing = contents?.getOrNull()
    val tree = remember(listing) { listing?.entries?.let(::VaultZipTree) }
    Box(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(top = 72.dp),
        contentAlignment = Alignment.Center
    ) {
        when {
            contents == null -> CircularProgressIndicator()
            contents?.exceptionOrNull() is ArchivePasswordRequired || (tree == null && secret != null) -> {
                VaultArchivePasswordPrompt(retry = secret != null) { password = it }
            }
            tree == null || listing == null -> Text(stringResource(R.string.onlyfiles_zip_unavailable), color = Color.White)
            else -> ZipFolderListing(
                node, tree, folder, listing.truncated, operationActive, onOpenFolder,
                encoding = encoding.takeIf { format == ArchiveFormat.ZIP },
                onEncodingChange = {
                    encoding = it
                    onOpenFolder(emptyList())
                },
                onExtract = {
                    onExtract(it.copy(encoding = encoding, requiresPassword = it.requiresPassword || secret != null))
                }
            )
        }
    }
}

@Composable
private fun VaultArchivePasswordPrompt(retry: Boolean, onPassword: (CharArray) -> Unit) {
    var draft by remember(retry) { mutableStateOf("") }
    var reveal by remember(retry) { mutableStateOf(false) }
    Surface(color = MaterialTheme.colorScheme.surface, shape = MaterialTheme.shapes.large) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(if (retry) R.string.onlyfiles_archive_password_retry else R.string.onlyfiles_archive_password_required))
            PasswordField(draft, { draft = it }, reveal, { reveal = !reveal })
            FilledTonalButton(enabled = draft.isNotEmpty(), onClick = {
                val submitted = draft.toCharArray()
                draft = ""
                reveal = false
                onPassword(submitted)
            }) {
                Text(stringResource(R.string.onlyfiles_archive_open))
            }
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
    encoding: ArchiveNameEncoding?,
    onEncodingChange: (ArchiveNameEncoding) -> Unit,
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
            if (encoding != null) VaultArchiveEncodingMenu(encoding, onEncodingChange)
            if (truncated) Text(
                stringResource(R.string.onlyfiles_zip_truncated, VAULT_ARCHIVE_MAX_ENTRIES),
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

@Composable
private fun VaultArchiveEncodingMenu(current: ArchiveNameEncoding, onSelect: (ArchiveNameEncoding) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box(Modifier.padding(horizontal = 12.dp)) {
        TextButton(onClick = { expanded = true }) {
            Text(stringResource(R.string.onlyfiles_archive_filename_encoding, current.displayName))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            ArchiveNameEncoding.entries.forEach { option ->
                DropdownMenuItem(text = { Text(option.displayName) }, onClick = {
                    expanded = false
                    onSelect(option)
                })
            }
        }
    }
}
