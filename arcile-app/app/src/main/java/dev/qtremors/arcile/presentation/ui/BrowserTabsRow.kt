package dev.qtremors.arcile.presentation.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import dev.qtremors.arcile.core.ui.ArcileDropdownMenu
import dev.qtremors.arcile.core.ui.ArcileDropdownMenuItem
import dev.qtremors.arcile.core.ui.R
import dev.qtremors.arcile.core.ui.theme.bounceCombinedClickable

@Composable
internal fun BrowserTabsRow(
    tabs: List<BrowserTab>,
    activeTabId: Int,
    titles: Map<Int, String>,
    isRouteVisible: Boolean,
    onSelectTab: (Int) -> Unit,
    onNewTab: () -> Unit,
    onSetPinned: (Int, Boolean) -> Unit,
    onDuplicate: (Int) -> Unit,
    onMoveLeft: (Int) -> Unit,
    onMoveRight: (Int) -> Unit,
    onCloseOthers: (Int) -> Unit,
    onClose: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    var menuTabId by remember { mutableStateOf<Int?>(null) }
    val activeIndex = tabs.indexOfFirst { it.id == activeTabId }
    val tabActionsLabel = stringResource(R.string.browser_tab_actions)

    LaunchedEffect(activeTabId, tabs) {
        if (activeIndex >= 0) listState.animateScrollToItem(activeIndex)
        if (menuTabId != null && tabs.none { it.id == menuTabId }) menuTabId = null
    }
    LaunchedEffect(isRouteVisible) {
        if (!isRouteVisible) menuTabId = null
    }

    Box(
        modifier = modifier
            .height(48.dp)
    ) {
        LazyRow(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            contentPadding = PaddingValues(start = 8.dp, end = 56.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            items(tabs, key = BrowserTab::id) { tab ->
                val selected = tab.id == activeTabId
                val isPrimaryTab = tab.id == PRIMARY_BROWSER_TAB_ID
                val title = if (isPrimaryTab) {
                    stringResource(R.string.internal_storage)
                } else {
                    titles[tab.id]
                        ?.takeIf(String::isNotBlank)
                        ?: stringResource(R.string.browse_title)
                }
                val showMenu = menuTabId == tab.id
                Box(
                    modifier = Modifier
                        .height(48.dp)
                        .clip(MaterialTheme.shapes.extraLarge)
                        .semantics {
                            this.selected = selected
                            onLongClick(label = tabActionsLabel) {
                                menuTabId = tab.id
                                true
                            }
                        }
                        .bounceCombinedClickable(
                            role = Role.Tab,
                            onClick = { onSelectTab(tab.id) },
                            onLongClick = { menuTabId = tab.id }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        shape = MaterialTheme.shapes.extraLarge,
                        color = if (selected) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHigh
                        },
                        contentColor = if (selected) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    ) {
                        Row(
                            modifier = Modifier
                                .height(40.dp)
                                .padding(horizontal = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (isPrimaryTab) {
                                Icon(
                                    imageVector = Icons.Default.Home,
                                    contentDescription = title,
                                    modifier = Modifier.size(20.dp)
                                )
                            } else {
                                Text(
                                    text = title,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }
                        }
                    }

                    BrowserTabMenu(
                        tab = tab,
                        tabs = tabs,
                        expanded = showMenu,
                        onDismiss = { menuTabId = null },
                        onSetPinned = {
                            menuTabId = null
                            onSetPinned(tab.id, it)
                        },
                        onDuplicate = {
                            menuTabId = null
                            onDuplicate(tab.id)
                        },
                        onMoveLeft = {
                            menuTabId = null
                            onMoveLeft(tab.id)
                        },
                        onMoveRight = {
                            menuTabId = null
                            onMoveRight(tab.id)
                        },
                        onCloseOthers = {
                            menuTabId = null
                            onCloseOthers(tab.id)
                        },
                        onClose = {
                            menuTabId = null
                            onClose(tab.id)
                        }
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 8.dp)
                .size(48.dp)
                .clip(MaterialTheme.shapes.extraLarge)
                .bounceCombinedClickable(onClick = onNewTab),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier.size(40.dp),
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = stringResource(R.string.browser_new_tab),
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun BrowserTabMenu(
    tab: BrowserTab,
    tabs: List<BrowserTab>,
    expanded: Boolean,
    onDismiss: () -> Unit,
    onSetPinned: (Boolean) -> Unit,
    onDuplicate: () -> Unit,
    onMoveLeft: () -> Unit,
    onMoveRight: () -> Unit,
    onCloseOthers: () -> Unit,
    onClose: () -> Unit
) {
    val isPrimaryTab = tab.id == PRIMARY_BROWSER_TAB_ID
    val canCloseOthers = tabs.any { other ->
        other.id != tab.id && other.id != PRIMARY_BROWSER_TAB_ID && !other.isPinned
    }
    val items = buildList<@Composable () -> Unit> {
        if (!isPrimaryTab) {
            add {
                ArcileDropdownMenuItem(
                    text = stringResource(R.string.browser_move_tab_left),
                    leadingIcon = {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = null)
                    },
                    enabled = canMoveBrowserTab(tabs, tab.id, offset = -1),
                    onClick = onMoveLeft
                )
            }
            add {
                ArcileDropdownMenuItem(
                    text = stringResource(R.string.browser_move_tab_right),
                    leadingIcon = {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
                    },
                    enabled = canMoveBrowserTab(tabs, tab.id, offset = 1),
                    onClick = onMoveRight
                )
            }
            add {
                ArcileDropdownMenuItem(
                    text = stringResource(if (tab.isPinned) R.string.browser_unpin_tab else R.string.browser_pin_tab),
                    leadingIcon = { Icon(Icons.Default.PushPin, contentDescription = null) },
                    onClick = { onSetPinned(!tab.isPinned) }
                )
            }
        }
        add {
            ArcileDropdownMenuItem(
                text = stringResource(R.string.browser_duplicate_tab),
                leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null) },
                enabled = tabs.size < MAX_BROWSER_TABS,
                onClick = onDuplicate
            )
        }
        add {
            ArcileDropdownMenuItem(
                text = stringResource(R.string.browser_close_other_tabs),
                leadingIcon = { Icon(Icons.Default.Close, contentDescription = null) },
                enabled = canCloseOthers,
                onClick = onCloseOthers
            )
        }
        if (!isPrimaryTab && !tab.isPinned) {
            add {
                ArcileDropdownMenuItem(
                    text = stringResource(R.string.browser_close_tab),
                    leadingIcon = { Icon(Icons.Default.Close, contentDescription = null) },
                    enabled = tabs.size > 1,
                    onClick = onClose
                )
            }
        }
    }
    ArcileDropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        items = items
    )
}
