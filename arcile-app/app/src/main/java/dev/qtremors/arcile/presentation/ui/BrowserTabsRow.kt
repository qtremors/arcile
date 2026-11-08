package dev.qtremors.arcile.presentation.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.SdCard
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import dev.qtremors.arcile.core.storage.domain.StorageKind
import dev.qtremors.arcile.core.storage.domain.StorageVolume
import dev.qtremors.arcile.core.ui.ArcileDropdownMenu
import dev.qtremors.arcile.core.ui.ArcileDropdownMenuItem
import dev.qtremors.arcile.core.ui.R
import dev.qtremors.arcile.core.ui.WorkspaceTabMenu
import dev.qtremors.arcile.core.ui.WorkspaceTabPresentation
import dev.qtremors.arcile.core.ui.WorkspaceTabsRow

@Composable
internal fun BrowserTabsRow(
    tabs: List<BrowserTab>,
    activeTabId: Int,
    titles: Map<Int, String>,
    isRouteVisible: Boolean,
    onSelectTab: (Int) -> Unit,
    onNewTab: () -> Unit,
    modifier: Modifier = Modifier,
    onNewTabInternal: () -> Unit = onNewTab,
    onNewTabStorageVolume: (StorageVolume) -> Unit = {},
    storageVolumes: List<StorageVolume> = emptyList(),
    onSetPinned: (Int, Boolean) -> Unit,
    onDuplicate: (Int) -> Unit,
    onMoveLeft: (Int) -> Unit,
    onMoveRight: (Int) -> Unit,
    onCloseOthers: (Int) -> Unit,
    onClose: (Int) -> Unit
) {
    val tabPresentations = tabs.map { tab ->
        if (tab.id == PRIMARY_BROWSER_TAB_ID) {
            WorkspaceTabPresentation(tab.id, stringResource(R.string.internal_storage), Icons.Default.Home)
        } else {
            WorkspaceTabPresentation(
                tab.id,
                titles[tab.id]?.takeIf(String::isNotBlank) ?: stringResource(R.string.browse_title)
            )
        }
    }
    WorkspaceTabsRow(
        tabs = tabPresentations,
        activeTabId = activeTabId,
        onSelectTab = onSelectTab,
        onNewTab = onNewTab,
        modifier = modifier,
        isRouteVisible = isRouteVisible,
        tabMenu = { id, expanded, dismiss ->
            val tab = tabs.first { it.id == id }
            val primary = id == PRIMARY_BROWSER_TAB_ID
            WorkspaceTabMenu(
                expanded = expanded,
                onDismiss = dismiss,
                pinned = tab.isPinned.takeUnless { primary },
                onSetPinned = { onSetPinned(id, it) },
                onDuplicate = { onDuplicate(id) },
                canDuplicate = tabs.size < MAX_BROWSER_TABS,
                onMoveLeft = { onMoveLeft(id) }.takeIf { canMoveBrowserTab(tabs, id, -1) },
                onMoveRight = { onMoveRight(id) }.takeIf { canMoveBrowserTab(tabs, id, 1) },
                onCloseOthers = { onCloseOthers(id) },
                canCloseOthers = tabs.any { it.id != id && it.id != PRIMARY_BROWSER_TAB_ID && !it.isPinned },
                onClose = { onClose(id) }.takeUnless { primary || tab.isPinned }
            )
        },
        newTabMenu = { expanded, dismiss ->
            val items = buildList<@Composable () -> Unit> {
                add {
                    ArcileDropdownMenuItem(
                        text = { Text(stringResource(R.string.internal_storage)) },
                        leadingIcon = { Icon(Icons.Default.Storage, contentDescription = null) },
                        onClick = { dismiss(); onNewTabInternal() }
                    )
                }
                storageVolumes.filterNot { it.isPrimary }.forEach { volume ->
                    add {
                        ArcileDropdownMenuItem(
                            text = { Text(volume.name) },
                            leadingIcon = {
                                val icon = when (volume.kind) {
                                    StorageKind.OTG -> Icons.Default.Usb
                                    StorageKind.SD_CARD -> Icons.Default.SdCard
                                    else -> Icons.Default.Storage
                                }
                                Icon(icon, contentDescription = null)
                            },
                            onClick = { dismiss(); onNewTabStorageVolume(volume) }
                        )
                    }
                }
            }
            ArcileDropdownMenu(expanded = expanded, onDismissRequest = dismiss, items = items)
        }
    )
}
