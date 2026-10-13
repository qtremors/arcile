package dev.qtremors.arcile.feature.onlyfiles

import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import dev.qtremors.arcile.core.ui.ArcileDropdownMenu
import dev.qtremors.arcile.core.ui.ArcileDropdownMenuItem
import dev.qtremors.arcile.core.ui.FloatingSelectionOverflowButton
import dev.qtremors.arcile.core.ui.FloatingSelectionToolbar
import dev.qtremors.arcile.core.ui.fileSelectionToolbarActions
import dev.qtremors.arcile.core.vault.domain.VaultNodeMetadata
import dev.qtremors.arcile.core.ui.R as UiR

@Composable
internal fun VaultSelectionToolbar(
    nodes: List<VaultNodeMetadata>,
    viewModel: OnlyFilesViewModel,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onShare: () -> Unit,
    onOpenWith: () -> Unit,
    onExport: () -> Unit,
    onMoveOut: () -> Unit,
    onReveal: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    val single = nodes.singleOrNull()
    var showMenu by remember { mutableStateOf(false) }
    FloatingSelectionToolbar(
        isVisible = nodes.isNotEmpty(),
        modifier = modifier,
        actions = fileSelectionToolbarActions(
            onCopy = { viewModel.copy(nodes) }.takeIf { nodes.all { it.ref.capabilities.canCopy } },
            onCut = { viewModel.move(nodes) }.takeIf { nodes.all { it.ref.capabilities.canMove } },
            onDelete = onDelete.takeIf { nodes.all { it.ref.capabilities.canDeletePermanently } },
            onRename = onRename.takeIf { single?.ref?.capabilities?.canRename == true }
        ),
        moreContent = {
            Box {
                FloatingSelectionOverflowButton { showMenu = true }
                val items = buildList<@Composable () -> Unit> {
                    fun item(label: Int, icon: ImageVector, action: () -> Unit) {
                        val content: @Composable () -> Unit = {
                            ArcileDropdownMenuItem(
                                text = { Text(stringResource(label)) },
                                leadingIcon = { Icon(icon, contentDescription = null) },
                                onClick = { showMenu = false; action() }
                            )
                        }
                        add(content)
                    }
                    if (single?.isDirectory == false && single.ref.capabilities.canOpenWith) {
                        item(UiR.string.viewer_open_with, Icons.AutoMirrored.Filled.OpenInNew, onOpenWith)
                    }
                    if (nodes.any { !it.isDirectory && it.ref.capabilities.canShare }) {
                        item(UiR.string.share, Icons.Default.Share, onShare)
                    }
                    item(UiR.string.properties_title, Icons.Default.Info) { viewModel.showProperties(nodes) }
                    if (onReveal != null) {
                        item(R.string.onlyfiles_reveal_in_folder, Icons.AutoMirrored.Filled.OpenInNew, onReveal)
                    }
                    if (nodes.all { it.ref.capabilities.canExport }) {
                        item(R.string.onlyfiles_export, Icons.Default.UploadFile, onExport)
                        if (nodes.all { it.ref.capabilities.canDeletePermanently }) {
                            item(R.string.onlyfiles_move_out, Icons.AutoMirrored.Filled.OpenInNew, onMoveOut)
                        }
                    }
                }
                ArcileDropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }, items = items)
            }
        }
    )
}
