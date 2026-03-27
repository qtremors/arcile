package dev.qtremors.arcile.presentation.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.FolderSpecial
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.VerticalDivider
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import dev.qtremors.arcile.R
import dev.qtremors.arcile.domain.QuickAccessItem
import dev.qtremors.arcile.domain.QuickAccessType
import dev.qtremors.arcile.presentation.quickaccess.QuickAccessState
import dev.qtremors.arcile.presentation.ui.components.menus.ExpandableFabMenu
import dev.qtremors.arcile.presentation.ui.components.menus.FabMenuItem

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun QuickAccessScreen(
    state: QuickAccessState,
    onNavigateBack: () -> Unit,
    onNavigateToPath: (String) -> Unit,
    onNavigateToSaf: (String) -> Unit,
    onTogglePin: (QuickAccessItem) -> Unit,
    onRemoveItem: (QuickAccessItem) -> Unit,
    onAddCustomFolder: (String, String) -> Unit,
    onAddSafFolder: (String, String) -> Unit
) {
    var showCustomDialog by remember { mutableStateOf(false) }
    
    var tempPath by remember { mutableStateOf("") }
    var tempLabel by remember { mutableStateOf("") }

    fun buildRestrictedFolderUri(relativeDocPath: String): String {
        val treeUri = android.provider.DocumentsContract.buildTreeDocumentUri(
            "com.android.externalstorage.documents",
            "primary"
        )
        return android.provider.DocumentsContract.buildDocumentUriUsingTree(treeUri, "primary:$relativeDocPath").toString()
    }

    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri?.let {
            val label = it.lastPathSegment?.split(":")?.lastOrNull() ?: "New Folder"
            onAddSafFolder(it.toString(), label)
        }
    }

    var isFabExpanded by remember { mutableStateOf(false) }
    val fabIconRotation by animateFloatAsState(
        targetValue = if (isFabExpanded) 45f else 0f,
        label = "fabRotation"
    )

    if (showCustomDialog) {
        AlertDialog(
            onDismissRequest = { showCustomDialog = false },
            title = { Text(stringResource(R.string.quick_access_add_custom_title)) },
            text = {
                Column {
                    OutlinedTextField(
                        value = tempLabel,
                        onValueChange = { tempLabel = it },
                        label = { Text(stringResource(R.string.quick_access_label_hint)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = tempPath,
                        onValueChange = { tempPath = it },
                        label = { Text(stringResource(R.string.quick_access_path_hint)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (tempPath.isNotBlank() && tempLabel.isNotBlank()) {
                            onAddCustomFolder(tempPath, tempLabel)
                            showCustomDialog = false
                        }
                    }
                ) {
                    Text(stringResource(R.string.add))
                }
            },
            dismissButton = {
                TextButton(onClick = { showCustomDialog = false }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.quick_access_manage_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                }
            )
        },
        floatingActionButton = {
            Box {
                Box(modifier = Modifier.align(Alignment.BottomEnd)) {
                    ExpandableFabMenu(
                        isExpanded = isFabExpanded,
                        onToggleExpand = { isFabExpanded = !isFabExpanded },
                        fabIconRotation = fabIconRotation,
                        items = listOf(
                            FabMenuItem(
                                label = stringResource(R.string.select_folder),
                                icon = Icons.Default.FolderOpen,
                                onClick = {
                                    isFabExpanded = false
                                    folderPickerLauncher.launch(null)
                                }
                            ),
                            FabMenuItem(
                                label = stringResource(R.string.add_custom_path),
                                icon = Icons.Default.Add,
                                onClick = {
                                    isFabExpanded = false
                                    tempPath = ""
                                    tempLabel = ""
                                    showCustomDialog = true
                                }
                            ),
                            FabMenuItem(
                                label = stringResource(R.string.add_android_data),
                                icon = Icons.Default.FolderSpecial,
                                onClick = {
                                    isFabExpanded = false
                                    onAddSafFolder(buildRestrictedFolderUri("Android/data"), "Android/data")
                                }
                            ),
                            FabMenuItem(
                                label = stringResource(R.string.add_android_obb),
                                icon = Icons.Default.FolderSpecial,
                                onClick = {
                                    isFabExpanded = false
                                    onAddSafFolder(buildRestrictedFolderUri("Android/obb"), "Android/obb")
                                }
                            )
                        )
                    )
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize()
            ) {
                val systemFolders = state.items.filter { it.type == QuickAccessType.STANDARD }
                val customFolders = state.items.filter { it.type == QuickAccessType.CUSTOM }
                val scopedFolders = state.items.filter { it.type == QuickAccessType.SAF_TREE }
                val handoffFolders = state.items.filter { it.type == QuickAccessType.EXTERNAL_HANDOFF }

                if (systemFolders.isNotEmpty()) {
                    item { SectionHeader(stringResource(R.string.quick_access_section_system)) }
                    items(systemFolders, key = { it.id }) { item ->
                        QuickAccessListItem(
                            item = item,
                            onNavigate = { onNavigateToPath(item.path) },
                            onTogglePin = { onTogglePin(item) },
                            onRemove = { onRemoveItem(item) }
                        )
                    }
                }

                if (customFolders.isNotEmpty()) {
                    item { SectionHeader(stringResource(R.string.quick_access_section_custom)) }
                    items(customFolders, key = { it.id }) { item ->
                        QuickAccessListItem(
                            item = item,
                            onNavigate = { onNavigateToPath(item.path) },
                            onTogglePin = { onTogglePin(item) },
                            onRemove = { onRemoveItem(item) }
                        )
                    }
                }

                if (scopedFolders.isNotEmpty()) {
                    item { SectionHeader(stringResource(R.string.quick_access_section_scoped)) }
                    items(scopedFolders, key = { it.id }) { item ->
                        QuickAccessListItem(
                            item = item,
                            onNavigate = { onNavigateToSaf(item.path) },
                            onTogglePin = { onTogglePin(item) },
                            onRemove = { onRemoveItem(item) }
                        )
                    }
                }

                if (handoffFolders.isNotEmpty()) {
                    item { SectionHeader(stringResource(R.string.quick_access_section_handoff)) }
                    items(handoffFolders, key = { it.id }) { item ->
                        QuickAccessListItem(
                            item = item,
                            onNavigate = { onNavigateToSaf(item.path) },
                            onTogglePin = { onTogglePin(item) },
                            onRemove = { onRemoveItem(item) }
                        )
                    }
                }

                item { Spacer(modifier = Modifier.height(80.dp)) }
            }

            if (isFabExpanded) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { isFabExpanded = false }
                        )
                )
            }
        }
    }
}

@Composable
fun SectionHeader(title: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 8.dp)
    ) {
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(4.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    }
}

@Composable
fun QuickAccessListItem(
    item: QuickAccessItem,
    onNavigate: () -> Unit,
    onTogglePin: () -> Unit,
    onRemove: () -> Unit
) {
    var showRemoveDialog by remember { mutableStateOf(false) }

    if (showRemoveDialog) {
        AlertDialog(
            onDismissRequest = { showRemoveDialog = false },
            title = { Text(stringResource(R.string.quick_access_remove_title)) },
            text = { Text(stringResource(R.string.quick_access_remove_message, item.label)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onRemove()
                        showRemoveDialog = false
                    }
                ) {
                    Text(stringResource(R.string.remove))
                }
            },
            dismissButton = {
                TextButton(onClick = { showRemoveDialog = false }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Main Navigation Area (Left)
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clickable(onClick = onNavigate)
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(40.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = when (item.type) {
                                QuickAccessType.SAF_TREE -> Icons.Default.FolderSpecial
                                QuickAccessType.EXTERNAL_HANDOFF -> Icons.Default.OpenInNew
                                else -> Icons.Default.Folder
                            },
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.label,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = item.handoffDescription ?: if (item.type == QuickAccessType.SAF_TREE) stringResource(R.string.quick_access_scoped_description) else item.path,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.basicMarquee()
                    )
                }
            }

            // Visual Splitter
            VerticalDivider(
                modifier = Modifier.height(32.dp),
                thickness = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant
            )

            // Control Area (Right)
            Row(
                modifier = Modifier.padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onTogglePin) {
                    Icon(
                        imageVector = if (item.isPinned) Icons.Default.PushPin else Icons.Outlined.PushPin,
                        contentDescription = stringResource(R.string.toggle_pin),
                        tint = if (item.isPinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (item.type != QuickAccessType.STANDARD) {
                    IconButton(onClick = { showRemoveDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = stringResource(R.string.remove),
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }
}
