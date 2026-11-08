package dev.qtremors.arcile.core.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.qtremors.arcile.core.ui.theme.bounceCombinedClickable

data class WorkspaceTabPresentation(val id: Int, val title: String, val icon: ImageVector? = null)

@Composable
fun WorkspaceTabMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    onDuplicate: () -> Unit,
    onCloseOthers: () -> Unit,
    canDuplicate: Boolean,
    canCloseOthers: Boolean,
    pinned: Boolean? = null,
    onSetPinned: (Boolean) -> Unit = {},
    onMoveLeft: (() -> Unit)? = null,
    onMoveRight: (() -> Unit)? = null,
    onClose: (() -> Unit)? = null
) {
    val items = buildList<@Composable () -> Unit> {
        fun addAction(label: Int, icon: ImageVector, enabled: Boolean = true, action: () -> Unit) {
            add {
                ArcileDropdownMenuItem(
                    text = { Text(stringResource(label)) },
                    leadingIcon = { Icon(icon, contentDescription = null) },
                    enabled = enabled,
                    onClick = { onDismiss(); action() }
                )
            }
        }
        if (pinned != null) {
            addAction(
                R.string.browser_move_tab_left, Icons.AutoMirrored.Filled.KeyboardArrowLeft, onMoveLeft != null
            ) { onMoveLeft?.invoke() }
            addAction(
                R.string.browser_move_tab_right, Icons.AutoMirrored.Filled.KeyboardArrowRight, onMoveRight != null
            ) { onMoveRight?.invoke() }
            addAction(
                if (pinned) R.string.browser_unpin_tab else R.string.browser_pin_tab, Icons.Default.PushPin
            ) { onSetPinned(!pinned) }
        }
        addAction(R.string.browser_duplicate_tab, Icons.Default.ContentCopy, canDuplicate, onDuplicate)
        addAction(R.string.browser_close_other_tabs, Icons.Default.Close, canCloseOthers, onCloseOthers)
        if (onClose != null) addAction(R.string.browser_close_tab, Icons.Default.Close, action = onClose)
    }
    ArcileDropdownMenu(expanded = expanded, onDismissRequest = onDismiss, items = items)
}

data class WorkspaceScrollPosition(val isScrolling: Boolean, val index: Int, val offset: Int) {
    fun tabsVisible(previous: WorkspaceScrollPosition, visible: Boolean): Boolean {
        if (index == 0 && offset == 0) return true
        if (!isScrolling) return visible
        return when {
            index != previous.index -> index < previous.index
            offset != previous.offset -> offset < previous.offset
            else -> visible
        }
    }
}

@Composable
fun rememberWorkspaceTabsVisible(observationKey: Any?, position: () -> WorkspaceScrollPosition): Boolean {
    var visible by rememberSaveable { mutableStateOf(true) }
    val currentPosition by rememberUpdatedState(position)
    LaunchedEffect(observationKey) {
        var previous = WorkspaceScrollPosition(false, 0, 0)
        snapshotFlow { currentPosition() }.collect { current ->
            visible = current.tabsVisible(previous, visible)
            previous = current
        }
    }
    return visible
}

@Composable
fun CollapsingWorkspaceTabs(visible: Boolean, content: @Composable () -> Unit) {
    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically { -it } + expandVertically(expandFrom = Alignment.Top) + fadeIn(),
        exit = slideOutVertically { -it } + shrinkVertically(shrinkTowards = Alignment.Top) + fadeOut()
    ) { content() }
}

@Composable
fun WorkspaceTabsRow(
    tabs: List<WorkspaceTabPresentation>,
    activeTabId: Int,
    onSelectTab: (Int) -> Unit,
    onNewTab: () -> Unit,
    tabMenu: @Composable (Int, Boolean, () -> Unit) -> Unit,
    modifier: Modifier = Modifier,
    isRouteVisible: Boolean = true,
    enabled: Boolean = true,
    newTabEnabled: Boolean = true,
    newTabMenu: (@Composable (Boolean, () -> Unit) -> Unit)? = null
) {
    val listState = rememberLazyListState()
    var menuTabId by remember { mutableStateOf<Int?>(null) }
    var showNewTabMenu by remember { mutableStateOf(false) }
    val tabActionsLabel = stringResource(R.string.browser_tab_actions)
    val newTabLabel = stringResource(R.string.browser_new_tab)
    val tabIds = tabs.map(WorkspaceTabPresentation::id)
    LaunchedEffect(activeTabId, tabIds) {
        val activeIndex = tabIds.indexOf(activeTabId)
        if (activeIndex >= 0) listState.animateScrollToItem(activeIndex)
        if (menuTabId !in tabIds) menuTabId = null
    }
    LaunchedEffect(isRouteVisible, enabled) {
        if (!isRouteVisible || !enabled) {
            menuTabId = null
            showNewTabMenu = false
        }
    }
    Box(modifier.heightIn(min = 48.dp)) {
        LazyRow(
            state = listState,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            contentPadding = PaddingValues(start = 8.dp, end = 56.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            items(tabs, key = WorkspaceTabPresentation::id) { tab ->
                val selectedTab = tab.id == activeTabId
                val openMenu = { menuTabId = tab.id }
                Box(
                    modifier = Modifier.heightIn(min = 48.dp)
                        .clip(MaterialTheme.shapes.extraLarge)
                        .semantics {
                            selected = selectedTab
                            if (enabled) onLongClick(label = tabActionsLabel) { openMenu(); true }
                        }
                        .bounceCombinedClickable(
                            enabled = enabled,
                            role = Role.Tab,
                            onClick = { onSelectTab(tab.id) },
                            onLongClick = openMenu
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        shape = MaterialTheme.shapes.extraLarge,
                        color = if (selectedTab) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceContainerHigh,
                        contentColor = if (selectedTab) MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    ) {
                        Row(
                            modifier = Modifier.heightIn(min = 40.dp).padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (tab.icon != null) Icon(tab.icon, tab.title, Modifier.size(20.dp))
                            else Text(
                                tab.title, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }
                    tabMenu(tab.id, menuTabId == tab.id && enabled) { menuTabId = null }
                }
            }
        }
        val canAdd = enabled && newTabEnabled
        val openNewMenu: (() -> Unit)? = newTabMenu?.let { { showNewTabMenu = true } }
        Box(
            modifier = Modifier.align(Alignment.CenterEnd).padding(end = 8.dp).size(48.dp)
                .clip(MaterialTheme.shapes.extraLarge)
                .semantics {
                    if (canAdd && openNewMenu != null) onLongClick(label = newTabLabel) { openNewMenu(); true }
                }
                .bounceCombinedClickable(enabled = canAdd, onClick = onNewTab, onLongClick = openNewMenu),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier.size(40.dp),
                shape = MaterialTheme.shapes.extraLarge,
                color = if (canAdd) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceContainerHigh,
                contentColor = if (canAdd) MaterialTheme.colorScheme.onPrimaryContainer
                else MaterialTheme.colorScheme.onSurfaceVariant
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Add, newTabLabel, Modifier.size(22.dp))
                }
            }
            newTabMenu?.invoke(showNewTabMenu && canAdd) { showNewTabMenu = false }
        }
    }
}
