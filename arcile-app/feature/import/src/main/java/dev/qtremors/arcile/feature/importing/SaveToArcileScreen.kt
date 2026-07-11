package dev.qtremors.arcile.feature.importing

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.SaveAlt
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.qtremors.arcile.core.ui.R
import dev.qtremors.arcile.core.ui.theme.ExpressiveShapes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SaveToArcileScreen(
    state: SaveToArcileState,
    actions: SaveToArcileActions
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    var backProgress by remember { mutableFloatStateOf(0f) }
    var isBackPredicting by remember { mutableStateOf(false) }

    PredictiveBackHandler(enabled = state.currentDirectory != null) { progressFlow ->
        isBackPredicting = true
        try {
            progressFlow.collect { backEvent -> backProgress = backEvent.progress }
            actions.navigateBack()
        } finally {
            isBackPredicting = false
            backProgress = 0f
        }
    }

    Scaffold(
        topBar = {
            LargeTopAppBar(
                title = { Text(stringResource(R.string.save_to_arcile_title)) },
                navigationIcon = {
                    IconButton(
                        onClick = actions.navigateBack,
                        modifier = Modifier.clip(CircleShape)
                    ) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                scrollBehavior = scrollBehavior
            )
        },
        bottomBar = {
            SaveToArcileBottomBar(state = state, actions = actions)
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (state.isLoading || state.isSaving) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else {
                SaveToArcileDirectoryList(
                    state = state,
                    actions = actions,
                    modifier = Modifier.fillMaxSize().graphicsLayer {
                        if (isBackPredicting) {
                            translationX = backProgress * 100.dp.toPx()
                            alpha = 1f - backProgress * 0.5f
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun SaveToArcileBottomBar(
    state: SaveToArcileState,
    actions: SaveToArcileActions
) {
    Surface(tonalElevation = 3.dp) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = LocalContext.current.resources.getQuantityString(
                    R.plurals.save_to_arcile_selected_files,
                    state.incoming.size,
                    state.incoming.size
                ),
                style = MaterialTheme.typography.bodyMedium
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (state.currentDirectory != null) {
                    TextButton(
                        enabled = !state.isSaving && state.canUseCurrentDirectory,
                        shape = ExpressiveShapes.medium,
                        onClick = actions.saveAsDefault
                    ) {
                        Text(stringResource(R.string.save_to_arcile_set_default))
                    }
                    Spacer(Modifier.size(8.dp))
                }
                Button(
                    enabled = !state.isSaving && state.canUseCurrentDirectory,
                    shape = ExpressiveShapes.medium,
                    onClick = actions.saveHere
                ) {
                    Icon(Icons.Outlined.SaveAlt, contentDescription = null)
                    Spacer(Modifier.size(8.dp))
                    Text(stringResource(R.string.save_to_arcile_save_here))
                }
            }
        }
    }
}

@Composable
private fun SaveToArcileDirectoryList(
    state: SaveToArcileState,
    actions: SaveToArcileActions,
    modifier: Modifier = Modifier
) {
    LazyColumn(contentPadding = PaddingValues(vertical = 8.dp), modifier = modifier) {
        item {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text(
                    text = state.currentDirectory?.absolutePath
                        ?: stringResource(R.string.save_to_arcile_choose_storage),
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = state.incoming.joinToString(limit = 3) { it.displayName },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            HorizontalDivider()
        }
        if (state.currentDirectory == null) {
            items(state.volumes, key = { it.id }) { volume ->
                ListItem(
                    headlineContent = { Text(volume.name) },
                    supportingContent = { Text(volume.path, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    leadingContent = { Icon(Icons.Outlined.Storage, contentDescription = null) },
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        .clip(ExpressiveShapes.medium)
                        .clickable { actions.selectVolume(volume) }
                )
            }
        } else {
            items(state.childDirectories, key = { it.absolutePath }) { directory ->
                ListItem(
                    headlineContent = { Text(directory.name) },
                    supportingContent = {
                        Text(directory.absolutePath, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    },
                    leadingContent = { Icon(Icons.Outlined.Folder, contentDescription = null) },
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        .clip(ExpressiveShapes.medium)
                        .clickable { actions.selectDirectory(directory) }
                )
            }
        }
    }
}
