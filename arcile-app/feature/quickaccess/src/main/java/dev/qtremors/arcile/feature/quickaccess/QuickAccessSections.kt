package dev.qtremors.arcile.feature.quickaccess

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
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
    val sections = remember(state.items) { state.items.toQuickAccessSections() }
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(
            bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() +
                MaterialTheme.spacing.toolbarBottomGap
        )
    ) {
        item {
            QuickAccessSection(
                title = stringResource(R.string.quick_access_section_custom),
                items = sections.custom,
                actions = actions
            )
        }
        item {
            QuickAccessSection(
                title = stringResource(R.string.quick_access_section_system),
                items = sections.system,
                actions = actions
            )
        }
        item {
            QuickAccessSection(
                title = stringResource(R.string.quick_access_section_apps),
                items = sections.apps,
                actions = actions
            )
        }
        item {
            QuickAccessSection(
                title = stringResource(R.string.quick_access_section_files),
                items = sections.files,
                actions = actions
            )
        }
    }
}

@Composable
private fun QuickAccessSection(
    title: String,
    items: List<QuickAccessItem>,
    actions: QuickAccessActions
) {
    QuickAccessSectionGroup(
        title = title,
        items = items,
        onNavigateToPath = actions.navigateToPath,
        onNavigateToSaf = actions.navigateToSaf,
        onTogglePin = actions.togglePin,
        onRemoveItem = actions.removeItem
    )
}

internal data class QuickAccessSectionItems(
    val custom: List<QuickAccessItem>,
    val system: List<QuickAccessItem>,
    val apps: List<QuickAccessItem>,
    val files: List<QuickAccessItem>
)

internal fun List<QuickAccessItem>.toQuickAccessSections(): QuickAccessSectionItems =
    QuickAccessSectionItems(
        custom = filter { it.type == QuickAccessType.CUSTOM },
        system = filter { it.type == QuickAccessType.STANDARD && !it.isAppFolderShortcut() },
        apps = filter { it.type == QuickAccessType.STANDARD && it.isAppFolderShortcut() },
        files = filter {
            it.type == QuickAccessType.FILES_APP ||
                it.type == QuickAccessType.SAF_TREE ||
                it.type == QuickAccessType.EXTERNAL_HANDOFF
        }
    )

private fun QuickAccessItem.isAppFolderShortcut(): Boolean =
    id == WHATSAPP_MEDIA_ID ||
        path.contains("com.whatsapp", ignoreCase = true) ||
        path.contains("whatsapp", ignoreCase = true)

private const val WHATSAPP_MEDIA_ID = "standard_whatsapp_media"
