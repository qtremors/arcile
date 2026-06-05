package dev.qtremors.arcile.presentation.ui

import dev.qtremors.arcile.shared.ui.ToolItem
import dev.qtremors.arcile.shared.ui.ToolCard
import dev.qtremors.arcile.shared.ui.ArcileScreenScaffold

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import dev.qtremors.arcile.ui.theme.spacing
import dev.qtremors.arcile.core.ui.R
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp


/**
 * Utilities grid screen listing planned and implemented tools.
 *
 * Currently only the Trash Bin tool is functional. All other tool cards are marked as
 * "Coming Soon" and are not clickable. See TASKS.md F4 for the UX issue around
 * non-interactive cards being visually identical to actionable ones.
 *
 * @param onNavigateBack Called when the user navigates back.
 */
@Composable
fun ToolsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToCleaner: () -> Unit,
    onNavigateToTrash: () -> Unit,
    homeUtilityIds: Set<String>,
    onUtilityHomeVisibilityChange: (String, Boolean) -> Unit
) {
    ArcileScreenScaffold(
        topBar = {
            @OptIn(ExperimentalMaterial3Api::class)
            TopAppBar(
                title = { Text(stringResource(R.string.tools_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                }
            )
        }
    ) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier
                .fillMaxSize()
                .padding(top = padding.calculateTopPadding())
                .padding(horizontal = 8.dp),
            contentPadding = PaddingValues(
                top = 8.dp,
                bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + MaterialTheme.spacing.screenGutter
            ),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(ArcileUtilityCatalog, key = { it.id }) { definition ->
                val tool = ToolItem(
                    name = stringResource(definition.nameRes),
                    icon = definition.icon,
                    isImplemented = definition.isImplemented
                )
                Box {
                    ToolCard(
                        item = tool,
                        onClick = {
                            when (definition.action) {
                                UtilityAction.Cleaner -> onNavigateToCleaner()
                                UtilityAction.Trash -> onNavigateToTrash()
                                UtilityAction.None -> Unit
                            }
                        }
                    )
                    if (definition.showOnHome) {
                        val isShownOnHome = definition.id in homeUtilityIds
                        IconButton(
                            onClick = { onUtilityHomeVisibilityChange(definition.id, !isShownOnHome) },
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(8.dp)
                                .size(36.dp)
                        ) {
                            Surface(
                                shape = androidx.compose.foundation.shape.CircleShape,
                                color = if (isShownOnHome) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surfaceContainerHighest
                                }
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = if (isShownOnHome) Icons.Default.Home else Icons.Outlined.Home,
                                        contentDescription = stringResource(R.string.quick_access_show_on_home),
                                        tint = if (isShownOnHome) {
                                            MaterialTheme.colorScheme.onPrimaryContainer
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
