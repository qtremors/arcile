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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.qtremors.arcile.core.storage.domain.StorageVolume
import dev.qtremors.arcile.core.ui.R
import dev.qtremors.arcile.core.ui.theme.ExpressiveShapes
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SaveToArcileScreen(
    incoming: List<IncomingSharedFile>,
    loadVolumes: suspend () -> List<StorageVolume>,
    loadDefaultPath: suspend () -> String?,
    saveDefaultPath: suspend (String) -> Unit,
    copyTo: suspend (File) -> Result<SaveIncomingResult>,
    onCancel: () -> Unit,
    onDefaultSaved: () -> Unit,
    onFinished: (SaveIncomingResult) -> Unit,
    onFailed: (Throwable) -> Unit
) {
    val scope = rememberCoroutineScope()
    var volumes by remember { mutableStateOf<List<StorageVolume>>(emptyList()) }
    var currentDir by remember { mutableStateOf<File?>(null) }
    var childDirs by remember { mutableStateOf<List<File>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var isSaving by remember { mutableStateOf(false) }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    var backProgress by remember { mutableStateOf(0f) }
    var isBackPredicting by remember { mutableStateOf(false) }

    PredictiveBackHandler(enabled = currentDir != null) { progressFlow ->
        isBackPredicting = true
        try {
            progressFlow.collect { backEvent -> backProgress = backEvent.progress }
            currentDir = currentDir?.parentFile
        } finally {
            isBackPredicting = false
            backProgress = 0f
        }
    }

    LaunchedEffect(Unit) {
        val loadedVolumes = loadVolumes()
        volumes = loadedVolumes
        currentDir = resolveInitialSaveToArcileDirectory(loadDefaultPath(), loadedVolumes)
        isLoading = false
    }

    LaunchedEffect(currentDir) {
        childDirs = withContext(Dispatchers.IO) {
            currentDir
                ?.listFiles { file -> file.isDirectory && file.canRead() }
                ?.asSequence()
                ?.sortedBy { it.name.lowercase() }
                ?.toList()
                .orEmpty()
        }
    }

    Scaffold(
        topBar = {
            LargeTopAppBar(
                title = { Text(stringResource(R.string.save_to_arcile_title)) },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            val parent = currentDir?.parentFile
                            if (currentDir != null && parent != null) currentDir = parent else onCancel()
                        },
                        modifier = Modifier.clip(CircleShape)
                    ) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                scrollBehavior = scrollBehavior
            )
        },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = resourcesPluralString(
                            R.plurals.save_to_arcile_selected_files,
                            incoming.size,
                            incoming.size
                        ),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val selectedDirectory = currentDir
                        val canUseSelectedDirectory = selectedDirectory != null &&
                            isValidSaveToArcileDirectory(selectedDirectory, volumes)
                        if (selectedDirectory != null) {
                            TextButton(
                                enabled = !isSaving && canUseSelectedDirectory,
                                shape = ExpressiveShapes.medium,
                                onClick = {
                                    scope.launch {
                                        saveDefaultPath(selectedDirectory.absolutePath)
                                        onDefaultSaved()
                                    }
                                }
                            ) {
                                Text(stringResource(R.string.save_to_arcile_set_default))
                            }
                            Spacer(Modifier.size(8.dp))
                        }
                        Button(
                            enabled = !isSaving && canUseSelectedDirectory,
                            shape = ExpressiveShapes.medium,
                            onClick = {
                                val destination = selectedDirectory?.takeIf {
                                    isValidSaveToArcileDirectory(it, volumes)
                                } ?: return@Button
                                isSaving = true
                                scope.launch {
                                    copyTo(destination).onSuccess(onFinished).onFailure(onFailed)
                                    isSaving = false
                                }
                            }
                        ) {
                            Icon(Icons.Outlined.SaveAlt, contentDescription = null)
                            Spacer(Modifier.size(8.dp))
                            Text(stringResource(R.string.save_to_arcile_save_here))
                        }
                    }
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (isLoading || isSaving) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(vertical = 8.dp),
                    modifier = Modifier.fillMaxSize().graphicsLayer {
                        if (isBackPredicting) {
                            translationX = backProgress * 100.dp.toPx()
                            alpha = 1f - backProgress * 0.5f
                        }
                    }
                ) {
                    item {
                        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                            Text(
                                text = currentDir?.absolutePath
                                    ?: stringResource(R.string.save_to_arcile_choose_storage),
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = incoming.joinToString(limit = 3) { it.displayName },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        HorizontalDivider()
                    }
                    if (currentDir == null) {
                        items(volumes, key = { it.id }) { volume ->
                            ListItem(
                                headlineContent = { Text(volume.name) },
                                supportingContent = {
                                    Text(volume.path, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                },
                                leadingContent = { Icon(Icons.Outlined.Storage, contentDescription = null) },
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                    .clip(ExpressiveShapes.medium)
                                    .clickable { currentDir = File(volume.path) }
                            )
                        }
                    } else {
                        items(childDirs, key = { it.absolutePath }) { directory ->
                            ListItem(
                                headlineContent = { Text(directory.name) },
                                supportingContent = {
                                    Text(
                                        directory.absolutePath,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                },
                                leadingContent = { Icon(Icons.Outlined.Folder, contentDescription = null) },
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                    .clip(ExpressiveShapes.medium)
                                    .clickable { currentDir = directory }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun resourcesPluralString(id: Int, quantity: Int, vararg args: Any): String =
    LocalContext.current.resources.getQuantityString(id, quantity, *args)
