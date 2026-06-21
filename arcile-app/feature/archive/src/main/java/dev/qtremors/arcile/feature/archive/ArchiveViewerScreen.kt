package dev.qtremors.arcile.feature.archive

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import dev.qtremors.arcile.ui.theme.spacing
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Surface
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ListItem
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.qtremors.arcile.core.ui.R
import dev.qtremors.arcile.feature.archive.ArchiveOperationStatusMessage
import dev.qtremors.arcile.feature.archive.ArchiveOperationUiState
import dev.qtremors.arcile.feature.archive.ArchiveViewerState
import dev.qtremors.arcile.core.operation.OperationCompletionStatus
import dev.qtremors.arcile.core.storage.domain.ArchiveFormat
import dev.qtremors.arcile.core.storage.domain.ArchiveNameEncoding
import dev.qtremors.arcile.core.storage.domain.ConflictResolution
import dev.qtremors.arcile.shared.ui.EmptyState
import dev.qtremors.arcile.shared.ui.EmptyStateVariant
import dev.qtremors.arcile.shared.ui.rememberArcileHaptics
import dev.qtremors.arcile.shared.ui.ArcileScreenScaffold
import dev.qtremors.arcile.shared.ui.ArcileSnackbarHost
import dev.qtremors.arcile.shared.ui.ConflictCard
import dev.qtremors.arcile.utils.formatFileSize
import java.io.File
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

@Composable
fun ArchiveViewerScreen(
    state: ArchiveViewerState,
    onNavigateBack: () -> Unit,
    onNavigateUpInArchive: () -> Boolean,
    onOpenFolder: (String) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onExtractAll: (String?) -> Unit,
    onExtractCurrentFolder: (String?) -> Unit,
    onSubmitPassword: (String) -> Unit,
    onSelectNameEncoding: (ArchiveNameEncoding) -> Unit,
    onSetConflictResolution: (String, ConflictResolution) -> Unit,
    onApplyConflictResolutionToAll: (ConflictResolution) -> Unit,
    onConfirmConflictResolutions: () -> Unit,
    onDismissConflicts: () -> Unit,
    onClearError: () -> Unit,
    onCancelExtraction: () -> Unit,
    onClearOperationStatusMessage: () -> Unit,
    onClearActiveOperation: () -> Unit,
    onToggleItemSelection: (String) -> Unit,
    onClearSelection: () -> Unit,
    onExtractSelected: (String?) -> Unit,
    onSelectAll: () -> Unit
) {
    val haptics = rememberArcileHaptics()
    val isInSelectionMode = state.selectedItems.isNotEmpty()
    val snackbarHostState = remember { SnackbarHostState() }
    val archiveFile = remember(state.archivePath) { File(state.archivePath) }
    var showEncodingDialog by rememberSaveable { mutableStateOf(false) }
    val extractionDestination = remember(state.archivePath) {
        File(archiveFile.parentFile ?: archiveFile, archiveFile.archiveBaseName()).absolutePath
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
    var backProgress by remember { mutableStateOf(0f) }
    var isBackPredicting by remember { mutableStateOf(false) }

    PredictiveBackHandler { progressFlow ->
        isBackPredicting = true
        try {
            progressFlow.collect { backEvent ->
                backProgress = backEvent.progress
            }
            if (!onNavigateUpInArchive()) onNavigateBack()
        } catch (e: Exception) {
            // Cancelled
        } finally {
            isBackPredicting = false
            backProgress = 0f
        }
    }

    if (state.passwordRequired) {
        ArchivePasswordDialog(
            onDismiss = onNavigateBack,
            onConfirm = onSubmitPassword,
            nameEncoding = state.nameEncoding,
            showEncodingSelector = state.archiveFormat == ArchiveFormat.ZIP,
            onSelectNameEncoding = onSelectNameEncoding
        )
    }
    if (showEncodingDialog) {
        ArchiveEncodingDialog(
            selected = state.nameEncoding,
            onDismiss = { showEncodingDialog = false },
            onSelect = {
                showEncodingDialog = false
                onSelectNameEncoding(it)
            }
        )
    }
    if (state.pendingConflicts.isNotEmpty()) {
        ArchiveConflictDialog(
            state = state,
            onSetConflictResolution = onSetConflictResolution,
            onApplyConflictResolutionToAll = onApplyConflictResolutionToAll,
            onConfirm = onConfirmConflictResolutions,
            onDismiss = onDismissConflicts
        )
    }

    ArcileScreenScaffold(
        modifier = Modifier.graphicsLayer {
            if (isBackPredicting) {
                val scale = 1f - (backProgress * 0.08f)
                scaleX = scale
                scaleY = scale
                translationX = backProgress * 100.dp.toPx()
                alpha = 1f - (backProgress * 0.4f)
            }
        },
        isLoading = state.isLoading,
        snackbarHost = {
            ArcileSnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.navigationBarsPadding()
            )
        },
        topBar = {
            TopAppBar(
                title = {
                    if (isInSelectionMode) {
                        Text(
                            text = stringResource(R.string.selected_count, state.selectedItems.size),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    } else {
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
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (isInSelectionMode) {
                                onClearSelection()
                            } else if (!onNavigateUpInArchive()) {
                                onNavigateBack()
                            }
                        },
                        modifier = Modifier.clip(CircleShape)
                    ) {
                        Icon(
                            imageVector = if (isInSelectionMode) Icons.Default.Close else Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(if (isInSelectionMode) R.string.clear_selection else R.string.back)
                        )
                    }
                },
                actions = {
                    if (isInSelectionMode) {
                        IconButton(
                            onClick = onSelectAll,
                            modifier = Modifier.clip(CircleShape)
                        ) {
                            Icon(Icons.Default.SelectAll, contentDescription = stringResource(R.string.select_all))
                        }
                        IconButton(
                            onClick = { onExtractSelected(null) },
                            modifier = Modifier.clip(CircleShape)
                        ) {
                            Icon(Icons.Default.Unarchive, contentDescription = stringResource(R.string.archive_extract_archive))
                        }
                    } else {
                        if (state.archiveFormat == ArchiveFormat.ZIP) {
                            IconButton(
                                onClick = { showEncodingDialog = true },
                                modifier = Modifier.clip(CircleShape)
                            ) {
                                Icon(Icons.Default.TextFields, contentDescription = state.nameEncoding.displayName)
                            }
                        }
                        if (state.currentPrefix != null) {
                            IconButton(
                                onClick = { onExtractCurrentFolder(null) },
                                modifier = Modifier.clip(CircleShape)
                            ) {
                                Icon(Icons.Default.Unarchive, contentDescription = stringResource(R.string.archive_extract_folder))
                            }
                        }
                        IconButton(
                            onClick = { onExtractAll(null) },
                            modifier = Modifier.clip(CircleShape)
                        ) {
                            Icon(Icons.Default.FolderZip, contentDescription = stringResource(R.string.archive_extract_archive))
                        }
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
                    extractionDestination = extractionDestination,
                    breadcrumbs = state.breadcrumbSegments,
                    onOpenFolder = onOpenFolder,
                    searchQuery = state.searchQuery,
                    onSearchQueryChange = onSearchQueryChange,
                    nameEncoding = state.nameEncoding,
                    showEncoding = state.archiveFormat == ArchiveFormat.ZIP
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
                val isSelected = state.selectedItems.contains(item.path)
                val animatedHorizontalPadding by animateDpAsState(
                    targetValue = if (isSelected) 8.dp else 0.dp,
                    label = "archiveItemHPadding"
                )
                val animatedVerticalPadding by animateDpAsState(
                    targetValue = if (isSelected) 4.dp else 0.dp,
                    label = "archiveItemVPadding"
                )
                val itemShape = if (isSelected) MaterialTheme.shapes.large else MaterialTheme.shapes.extraLarge
                Surface(
                    shape = itemShape,
                    color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            horizontal = animatedHorizontalPadding.coerceAtLeast(0.dp),
                            vertical = animatedVerticalPadding.coerceAtLeast(0.dp)
                        )
                ) {
                    ListItem(
                        headlineContent = {
                            Text(item.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        },
                        supportingContent = {
                            Text(
                                if (item.isDirectory) {
                                    if (item.childCount > 0) {
                                        pluralStringResource(R.plurals.archive_entry_count, item.childCount, item.childCount)
                                    } else {
                                        stringResource(R.string.folder_label)
                                    }
                                } else {
                                    formatFileSize(item.size)
                                },
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        leadingContent = {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (item.isDirectory) Icons.Default.Folder else Icons.AutoMirrored.Filled.InsertDriveFile,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                                if (isSelected) {
                                    Surface(
                                        shape = CircleShape,
                                        color = MaterialTheme.colorScheme.primary,
                                        contentColor = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier
                                            .align(Alignment.BottomEnd)
                                            .size(18.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.CheckCircle,
                                            contentDescription = stringResource(R.string.selected),
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .padding(2.dp)
                                        )
                                    }
                                }
                            }
                        },
                        modifier = Modifier
                            .clip(itemShape)
                            .combinedClickable(
                                onClick = {
                                    if (isInSelectionMode) {
                                        onToggleItemSelection(item.path)
                                    } else if (item.isDirectory) {
                                        onOpenFolder(item.path)
                                    }
                                },
                                onLongClick = {
                                    onToggleItemSelection(item.path)
                                }
                            )
                    )
                }
            }
        }
    }
}

