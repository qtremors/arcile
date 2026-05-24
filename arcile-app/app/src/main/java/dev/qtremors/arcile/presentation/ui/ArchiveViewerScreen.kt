package dev.qtremors.arcile.presentation.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import dev.qtremors.arcile.ui.theme.spacing
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ListItem
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.qtremors.arcile.R
import dev.qtremors.arcile.presentation.archive.ArchiveOperationStatusMessage
import dev.qtremors.arcile.presentation.archive.ArchiveOperationUiState
import dev.qtremors.arcile.presentation.archive.ArchiveViewerState
import dev.qtremors.arcile.presentation.operations.OperationCompletionStatus
import dev.qtremors.arcile.presentation.ui.components.EmptyState
import dev.qtremors.arcile.presentation.ui.components.EmptyStateVariant
import dev.qtremors.arcile.presentation.ui.components.rememberArcileHaptics
import dev.qtremors.arcile.utils.formatFileSize
import java.io.File
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArchiveViewerScreen(
    state: ArchiveViewerState,
    onNavigateBack: () -> Unit,
    onNavigateUpInArchive: () -> Boolean,
    onOpenFolder: (String) -> Unit,
    onExtractAll: (String?) -> Unit,
    onExtractCurrentFolder: (String?) -> Unit,
    onSubmitPassword: (String) -> Unit,
    onClearError: () -> Unit,
    onCancelExtraction: () -> Unit,
    onClearOperationStatusMessage: () -> Unit,
    onClearActiveOperation: () -> Unit
) {
    val haptics = rememberArcileHaptics()
    val snackbarHostState = remember { SnackbarHostState() }
    val archiveFile = remember(state.archivePath) { File(state.archivePath) }
    val extractionDestination = remember(state.archivePath) {
        File(archiveFile.parentFile ?: archiveFile, archiveFile.nameWithoutExtension).absolutePath
    }
    val operationStatusMessage = state.operationStatusMessage?.let { stringResource(it.stringRes()) }
    LaunchedEffect(state.error) {
        state.error?.let {
            haptics.error()
            onClearError()
            snackbarHostState.showSnackbar(it)
            if (state.activeOperation?.terminalStatus == OperationCompletionStatus.FAILED) {
                onClearActiveOperation()
            }
        }
    }
    LaunchedEffect(operationStatusMessage) {
        operationStatusMessage?.let { message ->
            when (state.activeOperation?.terminalStatus) {
                OperationCompletionStatus.SUCCESS -> haptics.success()
                OperationCompletionStatus.FAILED,
                OperationCompletionStatus.CANCELLED -> haptics.error()
                null -> Unit
            }
            onClearOperationStatusMessage()
            snackbarHostState.showSnackbar(message)
            onClearActiveOperation()
        }
    }
    BackHandler {
        if (!onNavigateUpInArchive()) onNavigateBack()
    }

    if (state.passwordRequired) {
        ArchivePasswordDialog(
            onDismiss = onNavigateBack,
            onConfirm = onSubmitPassword
        )
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = {
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.navigationBarsPadding()
            )
        },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = archiveFile.name,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        state.currentPrefix?.let {
                            Text(
                                text = it,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (!onNavigateUpInArchive()) onNavigateBack()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    if (state.currentPrefix != null) {
                        IconButton(onClick = { onExtractCurrentFolder(null) }) {
                            Icon(Icons.Default.Unarchive, contentDescription = stringResource(R.string.archive_extract_folder))
                        }
                    }
                    IconButton(onClick = { onExtractAll(null) }) {
                        Icon(Icons.Default.FolderZip, contentDescription = stringResource(R.string.archive_extract_archive))
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = padding.calculateTopPadding()),
            contentPadding = PaddingValues(
                bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + MaterialTheme.spacing.screenGutter
            ),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            item {
                ArchiveContextHeader(
                    archiveName = archiveFile.name,
                    currentPrefix = state.currentPrefix,
                    extractionDestination = extractionDestination
                )
            }
            state.activeOperation?.let { operation ->
                item {
                    ArchiveOperationCard(
                        operation = operation,
                        onCancel = onCancelExtraction
                    )
                }
            }
            state.summary?.let { summary ->
                item {
                    ArchiveSummaryHeader(state)
                }
            }
            if (!state.isLoading && state.error == null && !state.passwordRequired && state.visibleItems.isEmpty()) {
                item {
                    EmptyState(
                        variant = EmptyStateVariant.Archive,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 72.dp)
                    )
                }
            }
            items(state.visibleItems, key = { it.path }) { item ->
                ListItem(
                    headlineContent = {
                        Text(item.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    },
                    supportingContent = {
                        Text(
                            if (item.isDirectory) stringResource(R.string.folder_label) else formatFileSize(item.size),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    leadingContent = {
                        Icon(
                            if (item.isDirectory) Icons.Default.Folder else Icons.AutoMirrored.Filled.InsertDriveFile,
                            contentDescription = null
                        )
                    },
                    modifier = Modifier.clickable(enabled = item.isDirectory) {
                        onOpenFolder(item.path)
                    }
                )
            }
        }
    }
}

private fun ArchiveOperationStatusMessage.stringRes(): Int =
    when (this) {
        ArchiveOperationStatusMessage.ExtractionComplete -> R.string.archive_extraction_complete
        ArchiveOperationStatusMessage.ExtractionCancelled -> R.string.archive_extraction_cancelled
    }

@Composable
private fun ArchiveContextHeader(
    archiveName: String,
    currentPrefix: String?,
    extractionDestination: String
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = MaterialTheme.spacing.screenGutter, vertical = MaterialTheme.spacing.compactGap),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.compactGap)
    ) {
        Text(
            text = currentPrefix ?: stringResource(R.string.archive_root),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = stringResource(R.string.archive_breadcrumb, archiveName, currentPrefix ?: stringResource(R.string.archive_root)),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = stringResource(R.string.archive_destination_preview, extractionDestination),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun ArchiveSummaryHeader(state: ArchiveViewerState) {
    val summary = state.summary ?: return
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = MaterialTheme.spacing.screenGutter, vertical = MaterialTheme.spacing.compactGap),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.compactGap)
    ) {
        Text(
            text = summary.format.displayName,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold
        )
        Row(horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sectionGap)) {
            Text(
                text = pluralStringResource(R.plurals.archive_entry_count, summary.entryCount, summary.entryCount),
                style = MaterialTheme.typography.bodySmall
            )
            Text(formatFileSize(summary.totalUncompressedSize), style = MaterialTheme.typography.bodySmall)
        }
        val ratio = summary.compressionRatio?.let { "${(it * 100).toInt()}%" }
            ?: stringResource(R.string.archive_ratio_unavailable)
        Text(
            text = stringResource(R.string.archive_summary_size_ratio, formatFileSize(summary.archiveSize), ratio),
            style = MaterialTheme.typography.bodySmall
        )
        summary.newestModifiedAt?.let {
            Text(
                text = stringResource(R.string.archive_newest_modified, DateFormat.getDateTimeInstance().format(Date(it))),
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun ArchiveOperationCard(
    operation: ArchiveOperationUiState,
    onCancel: () -> Unit
) {
    val progress = operation.totalItems.takeIf { it > 0 }
        ?.let { operation.completedItems.toFloat() / it.toFloat() }
        ?.coerceIn(0f, 1f)
    val title = if (operation.isCancelling) {
        stringResource(R.string.file_operation_cancelling)
    } else {
        stringResource(R.string.file_operation_extracting_archive)
    }
    val currentName = operation.currentPath
        ?.substringAfterLast('/')
        ?.substringAfterLast(File.separatorChar)
        ?.takeIf { it.isNotBlank() }
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = MaterialTheme.spacing.screenGutter, vertical = MaterialTheme.spacing.compactGap)
    ) {
        Column(
            modifier = Modifier.padding(MaterialTheme.spacing.screenGutter),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.compactGap)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                TextButton(
                    enabled = !operation.isCancelling && operation.terminalStatus == null,
                    onClick = onCancel
                ) {
                    Text(stringResource(R.string.cancel))
                }
            }
            if (progress == null) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            } else {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Text(
                text = if (operation.totalItems > 0) {
                    stringResource(
                        R.string.file_operation_progress,
                        title,
                        operation.completedItems,
                        operation.totalItems
                    )
                } else {
                    title
                },
                style = MaterialTheme.typography.bodySmall
            )
            currentName?.let {
                Text(
                    text = stringResource(R.string.archive_operation_current_entry, it),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun ArchivePasswordDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var password by rememberSaveable { mutableStateOf("") }
    var passwordVisible by rememberSaveable { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.FolderZip, contentDescription = null) },
        title = { Text(stringResource(R.string.archive_password_title)) },
        text = {
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                singleLine = true,
                label = { Text(stringResource(R.string.archive_password)) },
                supportingText = { Text(stringResource(R.string.archive_password_description)) },
                visualTransformation = if (passwordVisible) {
                    VisualTransformation.None
                } else {
                    PasswordVisualTransformation()
                },
                trailingIcon = {
                    val icon = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility
                    val label = stringResource(
                        if (passwordVisible) {
                            R.string.archive_password_hide
                        } else {
                            R.string.archive_password_show
                        }
                    )
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(icon, contentDescription = label)
                    }
                }
            )
        },
        confirmButton = {
            TextButton(
                enabled = password.isNotEmpty(),
                onClick = { onConfirm(password) }
            ) {
                Text(stringResource(R.string.open))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}
