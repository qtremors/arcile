package dev.qtremors.arcile.presentation.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.CopyAll
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.qtremors.arcile.R
import dev.qtremors.arcile.core.storage.domain.CleanerCandidate
import dev.qtremors.arcile.core.storage.domain.CleanerGroup
import dev.qtremors.arcile.core.storage.domain.CleanerGroupType
import dev.qtremors.arcile.presentation.storagecleaner.StorageCleanerState
import dev.qtremors.arcile.ui.theme.bodyLargeMedium
import dev.qtremors.arcile.ui.theme.bodyMediumBold
import dev.qtremors.arcile.ui.theme.spacing
import dev.qtremors.arcile.ui.theme.titleMediumBold
import dev.qtremors.arcile.utils.formatFileSize
import coil.compose.SubcomposeAsyncImage
import androidx.compose.ui.layout.ContentScale
import dev.qtremors.arcile.core.storage.domain.FileCategories
import dev.qtremors.arcile.core.storage.domain.FileModel
import dev.qtremors.arcile.presentation.ui.components.getFileIconVector
import dev.qtremors.arcile.presentation.utils.rememberDateFormatter
import java.io.File
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun StorageCleanerScreen(
    state: StorageCleanerState,
    onNavigateBack: () -> Unit,
    onRefresh: () -> Unit,
    onCleanFiles: (List<String>) -> Unit,
    onClearMessages: () -> Unit
) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    var activeCleanerGroup by remember { mutableStateOf<CleanerGroupType?>(null) }
    var selectedCleanerPaths by remember { mutableStateOf(emptySet<String>()) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    val context = LocalContext.current

    LaunchedEffect(state.successMessage) {
        state.successMessage?.let { message ->
            Toast.makeText(
                context,
                context.getString(R.string.clean_success, message.cleanedCount),
                Toast.LENGTH_SHORT
            ).show()
            onClearMessages()
            activeCleanerGroup = null
            selectedCleanerPaths = emptySet()
        }
    }
    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let { message ->
            Toast.makeText(
                context,
                message.ifBlank { context.getString(R.string.clean_failed) },
                Toast.LENGTH_SHORT
            ).show()
            onClearMessages()
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.storage_cleaner_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    IconButton(onClick = onRefresh, enabled = !state.isScanning && !state.isCleaning) {
                        Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.refresh))
                    }
                },
                scrollBehavior = scrollBehavior
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = padding.calculateTopPadding())
        ) {
            if (state.isScanning && state.scannedFiles == 0) {
                CleanerLoading()
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        top = 12.dp,
                        bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + MaterialTheme.spacing.screenGutter
                    )
                ) {
                    if (state.isPartial) {
                        item {
                            Text(
                                text = stringResource(R.string.cleaner_partial_results),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                        }
                    }

                    items(CleanerGroupType.entries, key = { it.name }) { type ->
                        CleanerCategoryCard(
                            group = state.group(type),
                            onClick = {
                                selectedCleanerPaths = emptySet()
                                activeCleanerGroup = type
                            }
                        )
                    }
                }
            }
        }
    }

    val activeGroup = activeCleanerGroup?.let(state::group)
    if (activeGroup != null) {
        CleanerDetailsSheet(
            group = activeGroup,
            isCleaning = state.isCleaning,
            selectedFiles = selectedCleanerPaths,
            onSelectedFilesChange = { selectedCleanerPaths = it },
            onDismiss = {
                activeCleanerGroup = null
                selectedCleanerPaths = emptySet()
            },
            onRequestClean = { showDeleteConfirm = true }
        )
    }

    if (showDeleteConfirm && activeGroup != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(stringResource(R.string.clean_confirm_title)) },
            text = {
                Text(stringResource(R.string.clean_confirm_message, selectedCleanerPaths.size))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        onCleanFiles(selectedCleanerPaths.toList())
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

@Composable
private fun CleanerLoading() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.cleaner_scanning),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun CleanerCategoryCard(
    group: CleanerGroup,
    onClick: () -> Unit
) {
    val type = group.type
    val color = cleanerColor(type)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(MaterialTheme.shapes.extraLarge)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        shape = MaterialTheme.shapes.extraLarge
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = cleanerIcon(type),
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = cleanerTitle(type),
                    style = MaterialTheme.typography.bodyLargeMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = cleanerDescription(type),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (group.candidates.isNotEmpty()) {
                        stringResource(R.string.cleaner_group_stat, group.candidates.size, formatFileSize(group.totalBytes))
                    } else {
                        stringResource(R.string.cleaner_empty)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = color,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CleanerDetailsSheet(
    group: CleanerGroup,
    isCleaning: Boolean,
    selectedFiles: Set<String>,
    onSelectedFilesChange: (Set<String>) -> Unit,
    onDismiss: () -> Unit,
    onRequestClean: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = cleanerTitle(group.type),
                    style = MaterialTheme.typography.titleMediumBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = group.candidates.isNotEmpty() && selectedFiles.size == group.candidates.size,
                        onCheckedChange = { checked ->
                            onSelectedFilesChange(if (checked) group.candidates.map { it.absolutePath }.toSet() else emptySet())
                        }
                    )
                    Text(
                        text = stringResource(R.string.cleaner_select_all),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.clickable {
                            onSelectedFilesChange(if (selectedFiles.size == group.candidates.size) {
                                emptySet()
                            } else {
                                group.candidates.map { it.absolutePath }.toSet()
                            })
                        }
                    )
                }
            }

            HorizontalDivider()

            if (group.candidates.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.cleaner_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else if (group.type == CleanerGroupType.Duplicates) {
                val duplicateGroups = remember(group.candidates) {
                    group.candidates.groupBy { it.name.lowercase(Locale.ROOT) to it.size }.values.toList()
                }
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    items(duplicateGroups, key = { it.first().absolutePath }) { filesInGroup ->
                        DuplicateGroupCard(
                            filesInGroup = filesInGroup,
                            selectedFiles = selectedFiles,
                            onSelectedFilesChange = onSelectedFilesChange
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    items(group.candidates, key = { it.absolutePath }) { file ->
                        CleanerCandidateRow(
                            file = file,
                            selected = file.absolutePath in selectedFiles,
                            onToggle = {
                                onSelectedFilesChange(if (file.absolutePath in selectedFiles) {
                                    selectedFiles - file.absolutePath
                                } else {
                                    selectedFiles + file.absolutePath
                                })
                            }
                        )
                    }
                }
            }

            val totalSelectedSize = remember(selectedFiles, group.candidates) {
                group.candidates.filter { it.absolutePath in selectedFiles }.sumOf { it.size }
            }

            Button(
                onClick = onRequestClean,
                enabled = selectedFiles.isNotEmpty() && !isCleaning,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp)
                    .height(56.dp)
            ) {
                Icon(Icons.Default.Delete, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.clean_selected_summary, selectedFiles.size, formatFileSize(totalSelectedSize))
                )
            }
        }
    }
}

@Composable
private fun CleanerCandidateRow(
    file: CleanerCandidate,
    selected: Boolean,
    onToggle: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(checked = selected, onCheckedChange = { onToggle() })
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = file.name,
                style = MaterialTheme.typography.bodyLargeMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = file.absolutePath,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = formatFileSize(file.size),
            style = MaterialTheme.typography.bodyMediumBold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun cleanerTitle(type: CleanerGroupType): String = when (type) {
    CleanerGroupType.LargeFiles -> stringResource(R.string.cleaner_large_files)
    CleanerGroupType.OldDownloads -> stringResource(R.string.cleaner_old_downloads)
    CleanerGroupType.Duplicates -> stringResource(R.string.cleaner_duplicates)
    CleanerGroupType.Apks -> stringResource(R.string.cleaner_apks)
    CleanerGroupType.Videos -> stringResource(R.string.cleaner_videos)
    CleanerGroupType.Junk -> stringResource(R.string.cleaner_junk)
}

@Composable
private fun cleanerDescription(type: CleanerGroupType): String = when (type) {
    CleanerGroupType.LargeFiles -> stringResource(R.string.cleaner_large_files_desc)
    CleanerGroupType.OldDownloads -> stringResource(R.string.cleaner_old_downloads_desc)
    CleanerGroupType.Duplicates -> stringResource(R.string.cleaner_duplicates_desc)
    CleanerGroupType.Apks -> stringResource(R.string.cleaner_apks_desc)
    CleanerGroupType.Videos -> stringResource(R.string.cleaner_videos_desc)
    CleanerGroupType.Junk -> stringResource(R.string.cleaner_junk_desc)
}

@Composable
private fun cleanerColor(type: CleanerGroupType): Color = when (type) {
    CleanerGroupType.LargeFiles -> MaterialTheme.colorScheme.primary
    CleanerGroupType.OldDownloads -> MaterialTheme.colorScheme.secondary
    CleanerGroupType.Duplicates -> MaterialTheme.colorScheme.tertiary
    CleanerGroupType.Apks -> MaterialTheme.colorScheme.primary
    CleanerGroupType.Videos -> MaterialTheme.colorScheme.secondary
    CleanerGroupType.Junk -> MaterialTheme.colorScheme.error
}

private fun cleanerIcon(type: CleanerGroupType): ImageVector = when (type) {
    CleanerGroupType.LargeFiles -> Icons.Default.Storage
    CleanerGroupType.OldDownloads -> Icons.Default.Download
    CleanerGroupType.Duplicates -> Icons.Default.CopyAll
    CleanerGroupType.Apks -> Icons.Default.Android
    CleanerGroupType.Videos -> Icons.Default.VideoFile
    CleanerGroupType.Junk -> Icons.Default.DeleteSweep
}

@Composable
private fun DuplicateGroupCard(
    filesInGroup: List<CleanerCandidate>,
    selectedFiles: Set<String>,
    onSelectedFilesChange: (Set<String>) -> Unit
) {
    val firstFile = filesInGroup.first()
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = firstFile.name,
                        style = MaterialTheme.typography.bodyLargeMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = stringResource(R.string.cleaner_duplicate_count, filesInGroup.size),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.tertiary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = formatFileSize(firstFile.size),
                    style = MaterialTheme.typography.bodyMediumBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            Spacer(modifier = Modifier.height(4.dp))

            filesInGroup.forEach { file ->
                DuplicateFileRow(
                    file = file,
                    selected = file.absolutePath in selectedFiles,
                    onToggle = {
                        onSelectedFilesChange(
                            if (file.absolutePath in selectedFiles) {
                                selectedFiles - file.absolutePath
                            } else {
                                selectedFiles + file.absolutePath
                            }
                        )
                    }
                )
            }
        }
    }
}

@Composable
private fun DuplicateFileRow(
    file: CleanerCandidate,
    selected: Boolean,
    onToggle: () -> Unit
) {
    val context = LocalContext.current
    val formatter = rememberDateFormatter("MMM dd, yyyy  h:mm a")
    val dateString = remember(file.lastModified) {
        try {
            formatter.format(Date(file.lastModified))
        } catch (_: Exception) {
            ""
        }
    }
    val ext = remember(file.name) { file.name.substringAfterLast('.', "").lowercase() }
    val hasPreview = remember(ext) {
        FileCategories.Images.extensions.contains(ext) ||
                FileCategories.Videos.extensions.contains(ext)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(checked = selected, onCheckedChange = { onToggle() })
        Spacer(modifier = Modifier.width(8.dp))
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            if (hasPreview) {
                SubcomposeAsyncImage(
                    model = File(file.absolutePath),
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop,
                    error = {
                        val fileModel = FileModel(
                            name = file.name,
                            absolutePath = file.absolutePath,
                            size = file.size,
                            lastModified = file.lastModified,
                            isDirectory = false,
                            extension = ext,
                            isHidden = file.name.startsWith(".")
                        )
                        Icon(
                            imageVector = getFileIconVector(fileModel),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                )
            } else {
                val fileModel = FileModel(
                    name = file.name,
                    absolutePath = file.absolutePath,
                    size = file.size,
                    lastModified = file.lastModified,
                    isDirectory = false,
                    extension = ext,
                    isHidden = file.name.startsWith(".")
                )
                Icon(
                    imageVector = getFileIconVector(fileModel),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = file.absolutePath,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (dateString.isNotEmpty()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = dateString,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
        }
    }
}
