package dev.qtremors.arcile.presentation.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.qtremors.arcile.core.ui.ArcileScreenScaffold
import dev.qtremors.arcile.core.ui.R
import dev.qtremors.arcile.core.ui.theme.bounceClickable
import dev.qtremors.arcile.core.ui.theme.spacing
import dev.qtremors.arcile.core.ui.utilities.ArcileUtilityCatalog
import dev.qtremors.arcile.core.ui.utilities.UtilityAction
import dev.qtremors.arcile.core.ui.utilities.UtilityDefinition

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToCleaner: () -> Unit,
    onNavigateToTrash: () -> Unit,
    onNavigateToActivity: () -> Unit,
    onNavigateToOnlyFiles: () -> Unit,
    homeUtilityIds: List<String>,
    onUtilityHomeVisibilityChange: (String, Boolean) -> Unit,
    onMoveUtility: (String, Int) -> Unit
) {
    val selectedDefinitions = homeUtilityIds.mapNotNull { id ->
        ArcileUtilityCatalog.firstOrNull { it.id == id }
    }
    val displayed = selectedDefinitions + ArcileUtilityCatalog.filterNot { it.id in homeUtilityIds }
    fun open(definition: UtilityDefinition) = when (definition.action) {
        UtilityAction.Cleaner -> onNavigateToCleaner()
        UtilityAction.Trash -> onNavigateToTrash()
        UtilityAction.Activity -> onNavigateToActivity()
        UtilityAction.OnlyFiles -> onNavigateToOnlyFiles()
    }

    ArcileScreenScaffold(
        topBar = {
            LargeTopAppBar(
                title = { Text(stringResource(R.string.tools_title)) },
                navigationIcon = {
                    Box(
                        Modifier.size(48.dp).clip(CircleShape).bounceClickable(onClick = onNavigateBack),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(top = padding.calculateTopPadding()),
            contentPadding = PaddingValues(
                start = MaterialTheme.spacing.screenGutter,
                end = MaterialTheme.spacing.screenGutter,
                top = MaterialTheme.spacing.small,
                bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() +
                    MaterialTheme.spacing.screenGutter
            ),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.space12)
        ) {
            item {
                Text(
                    stringResource(R.string.tools_home_description),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp)
                )
            }
            items(displayed, key = UtilityDefinition::id) { definition ->
                val homeIndex = homeUtilityIds.indexOf(definition.id)
                val shown = homeIndex >= 0
                Surface(
                    onClick = { open(definition) },
                    shape = MaterialTheme.shapes.extraLarge,
                    color = if (shown) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = if (shown) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.secondaryContainer
                        ) {
                            Icon(
                                definition.icon,
                                contentDescription = null,
                                modifier = Modifier.padding(12.dp).size(26.dp),
                                tint = if (shown) MaterialTheme.colorScheme.onPrimary
                                else MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(definition.nameRes), style = MaterialTheme.typography.titleMedium)
                            Text(
                                stringResource(if (shown) R.string.tools_visible_on_home else R.string.tools_hidden_from_home),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (shown) {
                            IconButton(
                                onClick = { onMoveUtility(definition.id, -1) },
                                enabled = homeIndex > 0
                            ) { Icon(Icons.Default.ArrowUpward, stringResource(R.string.tools_move_up)) }
                            IconButton(
                                onClick = { onMoveUtility(definition.id, 1) },
                                enabled = homeIndex < homeUtilityIds.lastIndex
                            ) { Icon(Icons.Default.ArrowDownward, stringResource(R.string.tools_move_down)) }
                        }
                        Switch(
                            checked = shown,
                            onCheckedChange = { onUtilityHomeVisibilityChange(definition.id, it) }
                        )
                    }
                }
            }
        }
    }
}
