package dev.qtremors.arcile.presentation.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.qtremors.arcile.presentation.archive.ArchiveViewerState
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
    onClearError: () -> Unit
) {
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(state.error) {
        state.error?.let {
            onClearError()
            snackbarHostState.showSnackbar(it)
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
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = File(state.archivePath).name,
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
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (state.currentPrefix != null) {
                        IconButton(onClick = { onExtractCurrentFolder(null) }) {
                            Icon(Icons.Default.Unarchive, contentDescription = "Extract folder")
                        }
                    }
                    IconButton(onClick = { onExtractAll(null) }) {
                        Icon(Icons.Default.FolderZip, contentDescription = "Extract archive")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            state.summary?.let { summary ->
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(summary.format.displayName, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            Text("${summary.entryCount} entries", style = MaterialTheme.typography.bodySmall)
                            Text(formatFileSize(summary.totalUncompressedSize), style = MaterialTheme.typography.bodySmall)
                        }
                        val ratio = summary.compressionRatio?.let { "${(it * 100).toInt()}%" } ?: "n/a"
                        Text("Archive ${formatFileSize(summary.archiveSize)} • Ratio $ratio", style = MaterialTheme.typography.bodySmall)
                        summary.newestModifiedAt?.let {
                            Text("Newest ${DateFormat.getDateTimeInstance().format(Date(it))}", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
            items(state.visibleItems, key = { it.path }) { item ->
                ListItem(
                    headlineContent = {
                        Text(item.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    },
                    supportingContent = {
                        Text(
                            if (item.isDirectory) "Folder" else formatFileSize(item.size),
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

@Composable
private fun ArchivePasswordDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var password by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.FolderZip, contentDescription = null) },
        title = { Text("Archive password") },
        text = {
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                singleLine = true,
                label = { Text("Password") },
                supportingText = { Text("Enter the password to open this archive.") }
            )
        },
        confirmButton = {
            androidx.compose.material3.TextButton(
                enabled = password.isNotEmpty(),
                onClick = { onConfirm(password) }
            ) {
                Text("Open")
            }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
