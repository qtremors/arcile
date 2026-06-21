package dev.qtremors.arcile.presentation.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.HighlightOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.qtremors.arcile.core.storage.domain.ActivityLogEntry
import dev.qtremors.arcile.core.storage.domain.ActivityLogOperationStatus
import dev.qtremors.arcile.core.storage.domain.ActivityLogStore
import dev.qtremors.arcile.core.ui.R
import dev.qtremors.arcile.shared.ui.EmptyState
import dev.qtremors.arcile.shared.ui.EmptyStateVariant
import dev.qtremors.arcile.ui.theme.spacing
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import androidx.lifecycle.viewModelScope
import javax.inject.Inject

@HiltViewModel
class ActivityLogViewModel @Inject constructor(
    private val activityLogStore: ActivityLogStore
) : ViewModel() {
    val entries = activityLogStore.entries
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun clearActivity() {
        viewModelScope.launch { activityLogStore.clear() }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActivityLogRoute(
    onNavigateBack: () -> Unit,
    viewModel: ActivityLogViewModel = hiltViewModel()
) {
    val entries by viewModel.entries.collectAsStateWithLifecycle()
    ActivityLogScreen(
        entries = entries,
        onNavigateBack = onNavigateBack,
        onClearActivity = viewModel::clearActivity
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActivityLogScreen(
    entries: List<ActivityLogEntry>,
    onNavigateBack: () -> Unit,
    onClearActivity: () -> Unit
) {
    var showClearConfirmation by rememberSaveable { mutableStateOf(false) }

    if (showClearConfirmation) {
        AlertDialog(
            onDismissRequest = { showClearConfirmation = false },
            title = { Text(stringResource(R.string.activity_log_clear_title)) },
            text = { Text(stringResource(R.string.activity_log_clear_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearConfirmation = false
                        onClearActivity()
                    }
                ) {
                    Text(stringResource(R.string.action_clear))
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirmation = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.activity_log_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    IconButton(
                        onClick = { showClearConfirmation = true },
                        enabled = entries.isNotEmpty()
                    ) {
                        Icon(Icons.Default.DeleteSweep, contentDescription = stringResource(R.string.activity_log_clear_action))
                    }
                }
            )
        }
    ) { padding ->
        if (entries.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                EmptyState(
                    variant = EmptyStateVariant.Recent,
                    title = stringResource(R.string.activity_log_empty_title),
                    description = stringResource(R.string.activity_log_empty_message)
                )
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(
                bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() +
                    MaterialTheme.spacing.screenGutter
            ),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraSmall)
        ) {
            items(entries, key = { it.id }) { entry ->
                ActivityLogRow(entry)
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun ActivityLogRow(entry: ActivityLogEntry) {
    when (entry) {
        is ActivityLogEntry.FolderOpened -> ListItem(
            leadingContent = {
                Icon(Icons.Default.FolderOpen, contentDescription = null)
            },
            headlineContent = {
                Text(stringResource(R.string.activity_log_folder_opened))
            },
            supportingContent = {
                Text(
                    text = entry.path,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            },
            trailingContent = { Text(activityTime(entry.timestampMillis), style = MaterialTheme.typography.labelSmall) }
        )
        is ActivityLogEntry.FileOperation -> ListItem(
            leadingContent = {
                Icon(operationStatusIcon(entry.status), contentDescription = null)
            },
            headlineContent = {
                Text(operationTitle(entry.operationType, entry.status))
            },
            supportingContent = {
                Text(
                    text = operationDescription(entry),
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            },
            trailingContent = { Text(activityTime(entry.timestampMillis), style = MaterialTheme.typography.labelSmall) }
        )
    }
}

@Composable
private fun operationTitle(type: String, status: ActivityLogOperationStatus): String =
    stringResource(
        R.string.activity_log_operation_title,
        stringResource(type.stringRes()),
        stringResource(status.stringRes())
    )

@Composable
private fun operationDescription(entry: ActivityLogEntry.FileOperation): String {
    val sourceText = pluralStringResource(
        R.plurals.activity_log_source_count,
        entry.sourceCount,
        entry.sourceCount
    )
    val destination = entry.destinationPath
    val error = entry.errorMessage
    return when {
        !error.isNullOrBlank() -> "$sourceText\n$error"
        !destination.isNullOrBlank() -> "$sourceText\n$destination"
        else -> sourceText
    }
}

@Composable
private fun activityTime(timestampMillis: Long): String =
    android.text.format.DateUtils.getRelativeTimeSpanString(
        timestampMillis,
        System.currentTimeMillis(),
        android.text.format.DateUtils.MINUTE_IN_MILLIS
    ).toString()

private fun operationStatusIcon(status: ActivityLogOperationStatus): ImageVector =
    when (status) {
        ActivityLogOperationStatus.RUNNING -> Icons.Default.HourglassTop
        ActivityLogOperationStatus.COMPLETED -> Icons.Default.CheckCircle
        ActivityLogOperationStatus.FAILED -> Icons.Default.ErrorOutline
        ActivityLogOperationStatus.CANCELLED -> Icons.Default.HighlightOff
    }

private fun ActivityLogOperationStatus.stringRes(): Int =
    when (this) {
        ActivityLogOperationStatus.RUNNING -> R.string.activity_log_status_running
        ActivityLogOperationStatus.COMPLETED -> R.string.activity_log_status_completed
        ActivityLogOperationStatus.FAILED -> R.string.activity_log_status_failed
        ActivityLogOperationStatus.CANCELLED -> R.string.activity_log_status_cancelled
    }

private fun String.stringRes(): Int =
    when (this) {
        "COPY" -> R.string.activity_log_operation_copy
        "MOVE" -> R.string.activity_log_operation_move
        "TRASH" -> R.string.activity_log_operation_trash
        "DELETE" -> R.string.activity_log_operation_delete
        "SHRED" -> R.string.activity_log_operation_shred
        "CREATE_FAKE" -> R.string.activity_log_operation_create_fake
        "EXTRACT_ARCHIVE" -> R.string.activity_log_operation_extract_archive
        "CREATE_ARCHIVE" -> R.string.activity_log_operation_create_archive
        else -> R.string.activity_log_operation_unknown
    }
