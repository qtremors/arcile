package dev.qtremors.arcile.presentation.ui

import dev.qtremors.arcile.shared.ui.ToolItem
import dev.qtremors.arcile.shared.ui.ToolCard
import dev.qtremors.arcile.shared.ui.ArcileScreenScaffold

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import dev.qtremors.arcile.ui.theme.spacing
import dev.qtremors.arcile.core.ui.R
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
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
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = stringResource(R.string.quick_access_show_on_home),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Switch(
                                checked = definition.id in homeUtilityIds,
                                onCheckedChange = { checked ->
                                    onUtilityHomeVisibilityChange(definition.id, checked)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
