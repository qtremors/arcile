package dev.qtremors.arcile.feature.quickaccess

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.qtremors.arcile.core.storage.domain.QuickAccessItem
import dev.qtremors.arcile.core.storage.domain.QuickAccessType
import dev.qtremors.arcile.core.ui.R
import dev.qtremors.arcile.core.ui.theme.spacing

@Composable
internal fun QuickAccessSections(
    state: QuickAccessState,
    actions: QuickAccessActions,
    modifier: Modifier = Modifier
) {
    val pinnedItems = remember(state.items) { state.items.filter(QuickAccessItem::isPinned) }
    val hiddenSections = remember(state.items) {
        state.items.filterNot(QuickAccessItem::isPinned).toQuickAccessSections()
    }
    val shownOnHomeTitle = stringResource(R.string.quick_access_show_on_home)
    val storageTitle = stringResource(R.string.quick_access_section_storage)
    val customTitle = stringResource(R.string.quick_access_section_custom)
    val systemTitle = stringResource(R.string.quick_access_section_system)
    val appsTitle = stringResource(R.string.quick_access_section_apps)
    val filesTitle = stringResource(R.string.quick_access_section_files)

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(
            bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() +
                MaterialTheme.spacing.toolbarBottomGap
        )
    ) {
        quickAccessSection(
            title = shownOnHomeTitle,
            items = pinnedItems,
            actions = actions,
            orderedPinnedCount = pinnedItems.size
        )
        quickAccessSection(
            title = storageTitle,
            items = hiddenSections.storage,
            actions = actions
        )
        quickAccessSection(
            title = customTitle,
            items = hiddenSections.custom,
            actions = actions
        )
        quickAccessSection(
            title = systemTitle,
            items = hiddenSections.system,
            actions = actions
        )
        quickAccessSection(
            title = appsTitle,
            items = hiddenSections.apps,
            actions = actions
        )
        quickAccessSection(
            title = filesTitle,
            items = hiddenSections.files,
            actions = actions
        )
    }
}

private fun LazyListScope.quickAccessSection(
    title: String,
    items: List<QuickAccessItem>,
    actions: QuickAccessActions,
    orderedPinnedCount: Int? = null
) {
    if (items.isEmpty()) return

    item(key = "header_$title") {
        SectionHeader(title)
    }
    itemsIndexed(
        items = items,
        key = { _, item -> item.id }
    ) { index, item ->
        QuickAccessListItem(
            item = item,
            index = index,
            count = items.size,
            onNavigate = {
                if (item.type == QuickAccessType.EXTERNAL_HANDOFF) {
                    actions.navigateToRestrictedFolder(item)
                } else if (
                    item.type == QuickAccessType.SAF_TREE ||
                    item.type == QuickAccessType.FILES_APP
                ) {
                    actions.navigateToSaf(item.targetReference)
                } else {
                    actions.navigateToPath(item.targetReference)
                }
            },
            onTogglePin = { actions.togglePin(item) },
            onRemove = { actions.removeItem(item) },
            reorderPosition = orderedPinnedCount?.let { index },
            reorderCount = orderedPinnedCount ?: 0,
            onMoveUp = { actions.movePinnedItem(item.id, -1) },
            onMoveDown = { actions.movePinnedItem(item.id, 1) },
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 3.dp)
                .animateItem()
        )
    }
}

internal data class QuickAccessSectionItems(
    val storage: List<QuickAccessItem>,
    val custom: List<QuickAccessItem>,
    val system: List<QuickAccessItem>,
    val apps: List<QuickAccessItem>,
    val files: List<QuickAccessItem>
)

internal fun List<QuickAccessItem>.toQuickAccessSections(): QuickAccessSectionItems =
    QuickAccessSectionItems(
        storage = filter { it.id == ARCILE_STORAGE_ID || it.id == ROOT_STORAGE_ID },
        custom = filter { it.type == QuickAccessType.CUSTOM },
        system = filter {
            it.type == QuickAccessType.STANDARD &&
                it.id != ARCILE_STORAGE_ID &&
                it.id != ROOT_STORAGE_ID &&
                !it.isAppFolderShortcut()
        },
        apps = filter { it.type == QuickAccessType.STANDARD && it.isAppFolderShortcut() },
        files = filter {
            it.type == QuickAccessType.FILES_APP ||
                it.type == QuickAccessType.SAF_TREE ||
                it.type == QuickAccessType.EXTERNAL_HANDOFF
        }
    )

private fun QuickAccessItem.isAppFolderShortcut(): Boolean =
    id == WHATSAPP_MEDIA_ID ||
        targetReference.contains("com.whatsapp", ignoreCase = true) ||
        targetReference.contains("whatsapp", ignoreCase = true)

private const val WHATSAPP_MEDIA_ID = "standard_whatsapp_media"
private const val ARCILE_STORAGE_ID = "internal_all_files"
internal const val ROOT_STORAGE_ID = "standard_root_storage"
