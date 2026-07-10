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
import dev.qtremors.arcile.core.ui.theme.spacing
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
import dev.qtremors.arcile.core.ui.keyboardInputField
import dev.qtremors.arcile.core.ui.theme.ExpressiveShapes
import dev.qtremors.arcile.core.ui.theme.bounceClickable
import dev.qtremors.arcile.feature.archive.ArchiveOperationStatusMessage
import dev.qtremors.arcile.feature.archive.ArchiveViewerState
import dev.qtremors.arcile.core.operation.OperationCompletionStatus
import dev.qtremors.arcile.core.storage.domain.ArchiveFormat
import dev.qtremors.arcile.core.storage.domain.ArchiveNameEncoding
import dev.qtremors.arcile.core.storage.domain.ConflictResolution
import dev.qtremors.arcile.core.ui.EmptyState
import dev.qtremors.arcile.core.ui.EmptyStateVariant
import dev.qtremors.arcile.core.ui.ExpressiveFilterChip
import dev.qtremors.arcile.core.ui.rememberArcileHaptics
import dev.qtremors.arcile.core.ui.ArcileScreenScaffold
import dev.qtremors.arcile.core.ui.ConflictCard
import dev.qtremors.arcile.core.presentation.formatFileSize
import java.io.File
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

@Composable
internal fun ArchivePasswordDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
    nameEncoding: ArchiveNameEncoding,
    showEncodingSelector: Boolean,
    onSelectNameEncoding: (ArchiveNameEncoding) -> Unit
) {
    var password by rememberSaveable { mutableStateOf("") }
    var passwordVisible by rememberSaveable { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.FolderZip, contentDescription = null) },
        title = { Text(stringResource(R.string.archive_password_title)) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().keyboardInputField(),
                    label = { Text(stringResource(R.string.archive_password)) },
                    supportingText = { Text(stringResource(R.string.archive_password_description)) },
                    shape = ExpressiveShapes.medium,
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
                        val togglePasswordVisibility = { passwordVisible = !passwordVisible }
                        IconButton(
                            onClick = togglePasswordVisibility,
                            modifier = Modifier.bounceClickable(onClick = togglePasswordVisibility)
                        ) {
                            Icon(icon, contentDescription = label)
                        }
                    }
                )
                if (showEncodingSelector) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = nameEncoding.displayName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            ArchiveNameEncoding.entries.forEach { encoding ->
                                ExpressiveFilterChip(
                                    selected = encoding == nameEncoding,
                                    onClick = { onSelectNameEncoding(encoding) },
                                    label = { Text(encoding.displayName) }
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            val onConfirmClick = { onConfirm(password) }
            TextButton(
                enabled = password.isNotEmpty(),
                onClick = onConfirmClick,
                shape = ExpressiveShapes.medium,
                modifier = Modifier.bounceClickable(enabled = password.isNotEmpty(), onClick = onConfirmClick)
            ) {
                Text(stringResource(R.string.open))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                shape = ExpressiveShapes.medium,
                modifier = Modifier.bounceClickable(onClick = onDismiss)
            ) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@Composable
internal fun ArchiveEncodingDialog(
    selected: ArchiveNameEncoding,
    onDismiss: () -> Unit,
    onSelect: (ArchiveNameEncoding) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.TextFields, contentDescription = null) },
        title = { Text(stringResource(R.string.archive_filename_encoding)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                ArchiveNameEncoding.entries.forEach { encoding ->
                    val onEncodingSelect = { onSelect(encoding) }
                    ListItem(
                        headlineContent = { Text(encoding.displayName) },
                        supportingContent = if (encoding == selected) {
                            { Text(stringResource(R.string.selected)) }
                        } else {
                            null
                        },
                        modifier = Modifier
                            .clip(ExpressiveShapes.medium)
                            .bounceClickable(onClick = onEncodingSelect)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDismiss,
                shape = ExpressiveShapes.medium,
                modifier = Modifier.bounceClickable(onClick = onDismiss)
            ) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@Composable
internal fun ArchiveConflictDialog(
    state: ArchiveViewerState,
    onSetConflictResolution: (String, ConflictResolution) -> Unit,
    onApplyConflictResolutionToAll: (ConflictResolution) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val formatter = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }
    val allResolved = state.pendingConflicts.all { state.conflictResolutions[it.sourcePath] != null }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Unarchive, contentDescription = null) },
        title = { Text(stringResource(R.string.archive_resolve_conflicts)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val onKeepBothAll = { onApplyConflictResolutionToAll(ConflictResolution.KEEP_BOTH) }
                    TextButton(
                        onClick = onKeepBothAll,
                        shape = ExpressiveShapes.medium,
                        modifier = Modifier.bounceClickable(onClick = onKeepBothAll)
                    ) {
                        Text(stringResource(R.string.action_keep_both))
                    }
                    val onReplaceAll = { onApplyConflictResolutionToAll(ConflictResolution.REPLACE) }
                    TextButton(
                        onClick = onReplaceAll,
                        shape = ExpressiveShapes.medium,
                        modifier = Modifier.bounceClickable(onClick = onReplaceAll)
                    ) {
                        Text(stringResource(R.string.action_replace))
                    }
                    val onSkipAll = { onApplyConflictResolutionToAll(ConflictResolution.SKIP) }
                    TextButton(
                        onClick = onSkipAll,
                        shape = ExpressiveShapes.medium,
                        modifier = Modifier.bounceClickable(onClick = onSkipAll)
                    ) {
                        Text(stringResource(R.string.action_skip))
                    }
                }
                LazyColumn(
                    modifier = Modifier.heightIn(max = 420.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(state.pendingConflicts, key = { it.sourcePath }) { conflict ->
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            ConflictCard(
                                conflict = conflict,
                                resolution = state.conflictResolutions[conflict.sourcePath],
                                formatter = formatter,
                                onResolutionChange = { onSetConflictResolution(conflict.sourcePath, it) }
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                val onKeepBoth = { onSetConflictResolution(conflict.sourcePath, ConflictResolution.KEEP_BOTH) }
                                TextButton(
                                    onClick = onKeepBoth,
                                    shape = ExpressiveShapes.medium,
                                    modifier = Modifier.bounceClickable(onClick = onKeepBoth)
                                ) {
                                    Text(stringResource(R.string.action_keep_both))
                                }
                                val onReplace = { onSetConflictResolution(conflict.sourcePath, ConflictResolution.REPLACE) }
                                TextButton(
                                    onClick = onReplace,
                                    shape = ExpressiveShapes.medium,
                                    modifier = Modifier.bounceClickable(onClick = onReplace)
                                ) {
                                    Text(stringResource(R.string.action_replace))
                                }
                                val onSkip = { onSetConflictResolution(conflict.sourcePath, ConflictResolution.SKIP) }
                                TextButton(
                                    onClick = onSkip,
                                    shape = ExpressiveShapes.medium,
                                    modifier = Modifier.bounceClickable(onClick = onSkip)
                                ) {
                                    Text(stringResource(R.string.action_skip))
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            val onConfirmClick = onConfirm
            TextButton(
                enabled = allResolved,
                onClick = onConfirmClick,
                shape = ExpressiveShapes.medium,
                modifier = Modifier.bounceClickable(enabled = allResolved, onClick = onConfirmClick)
            ) {
                Text(stringResource(R.string.archive_extract_archive))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                shape = ExpressiveShapes.medium,
                modifier = Modifier.bounceClickable(onClick = onDismiss)
            ) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}
