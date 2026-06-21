package dev.qtremors.arcile.feature.quickaccess

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import dev.qtremors.arcile.ui.theme.spacing
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.FolderSpecial
import androidx.compose.material.icons.filled.Reorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import dev.qtremors.arcile.shared.ui.rememberArcileHaptics
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.geometry.Offset
import kotlinx.coroutines.delay
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import dev.qtremors.arcile.core.ui.R
import dev.qtremors.arcile.core.storage.domain.QuickAccessItem
import dev.qtremors.arcile.core.storage.domain.QuickAccessType
import dev.qtremors.arcile.feature.quickaccess.QuickAccessState
import dev.qtremors.arcile.shared.ui.QuickAccessAppIcon
import dev.qtremors.arcile.shared.ui.keyboardInputField
import dev.qtremors.arcile.shared.ui.menus.ExpandableFabMenu
import dev.qtremors.arcile.shared.ui.menus.FabMenuItem
import dev.qtremors.arcile.ui.theme.ExpressiveShapes
import dev.qtremors.arcile.ui.theme.bounceClickable

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
    onAddSafFolder: (String, String) -> Unit,
    onReorderItems: (List<QuickAccessItem>) -> Unit
) {
    var showCustomDialog by rememberSaveable { mutableStateOf(false) }
    var showReorderSheet by remember { mutableStateOf(false) }
    
    val filesItem = remember {
        QuickAccessItem(
            id = "handoff_files_app",
            label = "Files",
            path = "",
            type = QuickAccessType.FILES_APP
        )
    }
    val dataItem = remember {
        QuickAccessItem(
            id = "handoff_android_data",
            label = "Android/data",
            path = "Android/data",
            type = QuickAccessType.EXTERNAL_HANDOFF
        )
    }
    val obbItem = remember {
        QuickAccessItem(
            id = "handoff_android_obb",
            label = "Android/obb",
            path = "Android/obb",
            type = QuickAccessType.EXTERNAL_HANDOFF
        )
    }
    
    var tempPath by rememberSaveable { mutableStateOf("") }
    var tempLabel by rememberSaveable { mutableStateOf("") }

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

    var isFabExpanded by rememberSaveable { mutableStateOf(false) }
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
                        shape = ExpressiveShapes.medium,
                        modifier = Modifier.fillMaxWidth().keyboardInputField()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = tempPath,
                        onValueChange = { tempPath = it },
                        label = { Text(stringResource(R.string.quick_access_path_hint)) },
                        singleLine = true,
                        shape = ExpressiveShapes.medium,
                        modifier = Modifier.fillMaxWidth().keyboardInputField()
                    )
                }
            },
            confirmButton = {
                val addClick = {
                    if (tempPath.isNotBlank() && tempLabel.isNotBlank()) {
                        onAddCustomFolder(tempPath, tempLabel)
                        showCustomDialog = false
                    }
                }
                TextButton(
                    onClick = addClick,
                    shape = ExpressiveShapes.medium,
                    modifier = Modifier.bounceClickable(onClick = addClick)
                ) {
                    Text(stringResource(R.string.add))
                }
            },
            dismissButton = {
                val dismissClick = { showCustomDialog = false }
                TextButton(
                    onClick = dismissClick,
                    shape = ExpressiveShapes.medium,
                    modifier = Modifier.bounceClickable(onClick = dismissClick)
                ) { Text(stringResource(R.string.cancel)) }
            }
        )
    }

    if (showReorderSheet) {
        val pinnedItems = state.items.filter { it.isPinned }
        ArrangeQuickAccessDialog(
            pinnedItems = pinnedItems,
            onDismiss = { showReorderSheet = false },
            onApply = { reordered ->
                onReorderItems(reordered)
                showReorderSheet = false
            }
        )
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.quick_access_manage_title)) },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier
                            .clip(CircleShape)
                            .bounceClickable(onClick = onNavigateBack)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    val pinnedItems = state.items.filter { it.isPinned }
                    if (pinnedItems.isNotEmpty()) {
                        val reorderClick = { showReorderSheet = true }
                        IconButton(
                            onClick = reorderClick,
                            modifier = Modifier
                                .clip(CircleShape)
                                .bounceClickable(onClick = reorderClick)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Reorder,
                                contentDescription = stringResource(R.string.quick_access_arrange_order)
                            )
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            Box(modifier = Modifier.navigationBarsPadding()) {
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
                                label = stringResource(R.string.item_type_files),
                                onClick = {
                                    isFabExpanded = false
                                    onAddSafFolder(buildRestrictedFolderUri(""), "Files")
                                },
                                iconContent = {
                                    QuickAccessAppIcon(
                                        item = filesItem,
                                        fallbackIcon = Icons.Default.FolderSpecial,
                                        modifier = Modifier.size(24.dp),
                                        fallbackTint = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                }
                            ),
                            FabMenuItem(
                                label = stringResource(R.string.add_android_data),
                                onClick = {
                                    isFabExpanded = false
                                    onAddSafFolder(buildRestrictedFolderUri("Android/data"), "Android/data")
                                },
                                iconContent = {
                                    QuickAccessAppIcon(
                                        item = dataItem,
                                        fallbackIcon = Icons.Default.FolderSpecial,
                                        modifier = Modifier.size(24.dp),
                                        fallbackTint = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                }
                            ),
                            FabMenuItem(
                                label = stringResource(R.string.add_android_obb),
                                onClick = {
                                    isFabExpanded = false
                                    onAddSafFolder(buildRestrictedFolderUri("Android/obb"), "Android/obb")
                                },
                                iconContent = {
                                    QuickAccessAppIcon(
                                        item = obbItem,
                                        fallbackIcon = Icons.Default.FolderSpecial,
                                        modifier = Modifier.size(24.dp),
                                        fallbackTint = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
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
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + MaterialTheme.spacing.toolbarBottomGap
                )
            ) {
                val systemFolders = state.items.filter { it.type == QuickAccessType.STANDARD && it.id != "standard_whatsapp_media" }
                val appFolders = state.items.filter { it.id == "standard_whatsapp_media" || it.path.contains("com.whatsapp") || it.path.contains("whatsapp") }
                val filesFolders = state.items.filter { it.type == QuickAccessType.FILES_APP || it.type == QuickAccessType.SAF_TREE || it.type == QuickAccessType.EXTERNAL_HANDOFF }
                val customFolders = state.items.filter { it.type == QuickAccessType.CUSTOM }

                item {
                    QuickAccessSectionGroup(
                        title = stringResource(R.string.quick_access_section_custom),
                        items = customFolders,
                        onNavigateToPath = onNavigateToPath,
                        onNavigateToSaf = onNavigateToSaf,
                        onTogglePin = onTogglePin,
                        onRemoveItem = onRemoveItem
                    )
                }

                item {
                    QuickAccessSectionGroup(
                        title = stringResource(R.string.quick_access_section_system),
                        items = systemFolders,
                        onNavigateToPath = onNavigateToPath,
                        onNavigateToSaf = onNavigateToSaf,
                        onTogglePin = onTogglePin,
                        onRemoveItem = onRemoveItem
                    )
                }

                item {
                    QuickAccessSectionGroup(
                        title = stringResource(R.string.quick_access_section_apps),
                        items = appFolders,
                        onNavigateToPath = onNavigateToPath,
                        onNavigateToSaf = onNavigateToSaf,
                        onTogglePin = onTogglePin,
                        onRemoveItem = onRemoveItem
                    )
                }

                item {
                    QuickAccessSectionGroup(
                        title = stringResource(R.string.quick_access_section_files),
                        items = filesFolders,
                        onNavigateToPath = onNavigateToPath,
                        onNavigateToSaf = onNavigateToSaf,
                        onTogglePin = onTogglePin,
                        onRemoveItem = onRemoveItem
                    )
                }
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
fun QuickAccessSectionGroup(
    title: String,
    items: List<QuickAccessItem>,
    onNavigateToPath: (String) -> Unit,
    onNavigateToSaf: (String) -> Unit,
    onTogglePin: (QuickAccessItem) -> Unit,
    onRemoveItem: (QuickAccessItem) -> Unit
) {
    if (items.isEmpty()) return

    Column(modifier = Modifier.fillMaxWidth()) {
        SectionHeader(title)
        
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp)
        ) {
            Column {
                items.forEachIndexed { index, item ->
                    QuickAccessListItem(
                        item = item,
                        onNavigate = {
                            if (item.type == QuickAccessType.SAF_TREE ||
                                item.type == QuickAccessType.EXTERNAL_HANDOFF ||
                                item.type == QuickAccessType.FILES_APP) {
                                onNavigateToSaf(item.path)
                            } else {
                                onNavigateToPath(item.path)
                            }
                        },
                        onTogglePin = { onTogglePin(item) },
                        onRemove = { onRemoveItem(item) }
                    )
                    
                    if (index < items.size - 1) {
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun QuickAccessListItem(
    item: QuickAccessItem,
    onNavigate: () -> Unit,
    onTogglePin: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = rememberArcileHaptics()
    var showRemoveDialog by remember { mutableStateOf(false) }
    val fallbackIcon = iconForQuickAccessItem(item)

    if (showRemoveDialog) {
        AlertDialog(
            onDismissRequest = { showRemoveDialog = false },
            title = { Text(stringResource(R.string.quick_access_remove_title)) },
            text = { Text(stringResource(R.string.quick_access_remove_message, item.label)) },
            confirmButton = {
                val removeClick = {
                    onRemove()
                    showRemoveDialog = false
                }
                TextButton(
                    onClick = removeClick,
                    shape = ExpressiveShapes.medium,
                    modifier = Modifier.bounceClickable(onClick = removeClick)
                ) {
                    Text(stringResource(R.string.remove))
                }
            },
            dismissButton = {
                val dismissClick = { showRemoveDialog = false }
                TextButton(
                    onClick = dismissClick,
                    shape = ExpressiveShapes.medium,
                    modifier = Modifier.bounceClickable(onClick = dismissClick)
                ) { Text(stringResource(R.string.cancel)) }
            }
        )
    }

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Main Navigation Area (Left)
        Row(
            modifier = Modifier
                .weight(1f)
                .clip(ExpressiveShapes.medium)
                .bounceClickable(onClick = onNavigate)
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
                    QuickAccessAppIcon(
                        item = item,
                        fallbackIcon = fallbackIcon,
                        fallbackTint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(24.dp),
                        appIconModifier = Modifier.size(40.dp)
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
            modifier = Modifier.padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val showOnHomeLabel = stringResource(R.string.quick_access_show_on_home)
            val showOnHomeContentDescription = stringResource(
                R.string.quick_access_home_toggle_description,
                item.label,
                showOnHomeLabel
            )
            Switch(
                checked = item.isPinned,
                onCheckedChange = {
                    haptics.selectionChanged()
                    onTogglePin()
                },
                thumbContent = {
                    Icon(
                        imageVector = if (item.isPinned) Icons.Filled.Check else Icons.Filled.Close,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                },
                modifier = Modifier.semantics {
                    contentDescription = showOnHomeContentDescription
                }
            )

            if (item.type != QuickAccessType.STANDARD) {
                val removeClick = { showRemoveDialog = true }
                IconButton(
                    onClick = removeClick,
                    modifier = Modifier
                        .clip(CircleShape)
                        .bounceClickable(onClick = removeClick)
                ) {
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

private fun iconForQuickAccessItem(item: QuickAccessItem): ImageVector {
    return when (item.type) {
        QuickAccessType.SAF_TREE -> Icons.Default.FolderSpecial
        QuickAccessType.EXTERNAL_HANDOFF -> Icons.Default.FolderSpecial
        QuickAccessType.FILES_APP -> Icons.Default.FolderSpecial
        else -> Icons.Default.Folder
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArrangeQuickAccessDialog(
    pinnedItems: List<QuickAccessItem>,
    onDismiss: () -> Unit,
    onApply: (List<QuickAccessItem>) -> Unit
) {
    var draftItems by remember(pinnedItems) { mutableStateOf(pinnedItems) }
    val haptics = rememberArcileHaptics()
    val lazyListState = rememberLazyListState()
    val density = LocalDensity.current

    var draggedIndex by remember { mutableStateOf<Int?>(null) }
    var dragOffset by remember { mutableStateOf(0f) }

    val currentDraftItems = rememberUpdatedState(draftItems)
    val currentDraggedIndex = rememberUpdatedState(draggedIndex)

    fun checkAndPerformSwap(currentIndex: Int, currentOffset: Float) {
        val visibleItems = lazyListState.layoutInfo.visibleItemsInfo
        val draggedItemInfo = visibleItems.find { it.index == currentIndex }
        val itemHeight = draggedItemInfo?.size ?: with(density) { 64.dp.toPx() }.toInt()

        if (currentOffset > itemHeight / 2f && currentIndex < draftItems.size - 1) {
            haptics.selectionChanged()
            val newList = draftItems.toMutableList()
            val temp = newList[currentIndex]
            newList[currentIndex] = newList[currentIndex + 1]
            newList[currentIndex + 1] = temp
            draftItems = newList
            dragOffset = currentOffset - itemHeight
            draggedIndex = currentIndex + 1
        } else if (currentOffset < -itemHeight / 2f && currentIndex > 0) {
            haptics.selectionChanged()
            val newList = draftItems.toMutableList()
            val temp = newList[currentIndex]
            newList[currentIndex] = newList[currentIndex - 1]
            newList[currentIndex - 1] = temp
            draftItems = newList
            dragOffset = currentOffset + itemHeight
            draggedIndex = currentIndex - 1
        }
    }

    val isDragging = draggedIndex != null
    LaunchedEffect(isDragging) {
        if (isDragging) {
            while (true) {
                val currentIndex = draggedIndex ?: break
                val visibleItems = lazyListState.layoutInfo.visibleItemsInfo
                val draggedItemInfo = visibleItems.find { it.index == currentIndex }
                if (draggedItemInfo != null) {
                    val viewportHeight = lazyListState.layoutInfo.viewportSize.height
                    val itemHeight = draggedItemInfo.size
                    val itemTop = draggedItemInfo.offset + dragOffset
                    val itemBottom = itemTop + itemHeight
                    
                    val threshold = with(density) { 48.dp.toPx() }
                    var scrollAmount = 0f
                    
                    if (itemTop < threshold) {
                        scrollAmount = (itemTop - threshold) / 5f
                    } else if (itemBottom > viewportHeight - threshold) {
                        scrollAmount = (itemBottom - (viewportHeight - threshold)) / 5f
                    }
                    
                    if (scrollAmount != 0f) {
                        lazyListState.scrollBy(scrollAmount)
                        dragOffset += scrollAmount
                        checkAndPerformSwap(currentIndex, dragOffset)
                    }
                }
                delay(16)
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Scaffold(
                contentWindowInsets = WindowInsets(0, 0, 0, 0),
                topBar = {
                    TopAppBar(
                        title = { Text(stringResource(R.string.quick_access_arrange_title)) },
                        navigationIcon = {
                            IconButton(
                                onClick = onDismiss,
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .bounceClickable(onClick = onDismiss)
                            ) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                            }
                        },
                        actions = {
                            val applyClick = { onApply(draftItems) }
                            Button(
                                onClick = applyClick,
                                shape = ExpressiveShapes.medium,
                                modifier = Modifier
                                    .padding(end = 8.dp)
                                    .bounceClickable(onClick = applyClick)
                            ) {
                                Text(
                                    text = stringResource(R.string.apply),
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    )
                }
            ) { padding ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                ) {
                    Text(
                        text = stringResource(R.string.quick_access_arrange_description),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Surface(
                        shape = RoundedCornerShape(24.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                            .weight(1f)
                    ) {
                        LazyColumn(
                            state = lazyListState,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            itemsIndexed(
                                items = draftItems,
                                key = { _, item -> item.id }
                            ) { index, item ->
                                val isItemDragging = index == draggedIndex
                                val translationY = if (isItemDragging) dragOffset else 0f
                                val fallbackIcon = iconForQuickAccessItem(item)

                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .animateItem()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .graphicsLayer {
                                                this.translationY = translationY
                                                if (isItemDragging) {
                                                    scaleX = 1.03f
                                                    scaleY = 1.03f
                                                    shadowElevation = 8.dp.toPx()
                                                }
                                            }
                                            .background(
                                                if (isItemDragging) MaterialTheme.colorScheme.surfaceContainerHigh 
                                                else Color.Transparent
                                            )
                                            .padding(horizontal = 16.dp, vertical = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Surface(
                                            shape = CircleShape,
                                            color = MaterialTheme.colorScheme.primaryContainer,
                                            modifier = Modifier.size(40.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                QuickAccessAppIcon(
                                                    item = item,
                                                    fallbackIcon = fallbackIcon,
                                                    fallbackTint = MaterialTheme.colorScheme.onPrimaryContainer,
                                                    modifier = Modifier.size(24.dp),
                                                    appIconModifier = Modifier.size(40.dp)
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.width(16.dp))

                                        Text(
                                            text = item.label,
                                            style = MaterialTheme.typography.titleMedium,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier.weight(1f),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )

                                        val itemId = item.id
                                        var itemCoordinates: LayoutCoordinates? by remember { mutableStateOf(null) }
                                        var previousRootPosition by remember { mutableStateOf(Offset.Zero) }

                                        Icon(
                                            imageVector = Icons.Default.DragHandle,
                                            contentDescription = stringResource(R.string.quick_access_arrange_order),
                                            modifier = Modifier
                                                .onGloballyPositioned { layoutCoordinates ->
                                                    itemCoordinates = layoutCoordinates
                                                }
                                                .pointerInput(itemId) {
                                                    detectDragGestures(
                                                        onDragStart = { offset ->
                                                            val startIdx = currentDraftItems.value.indexOfFirst { it.id == itemId }
                                                            if (startIdx != -1) {
                                                                draggedIndex = startIdx
                                                                dragOffset = 0f
                                                                val coords = itemCoordinates
                                                                if (coords != null && coords.isAttached) {
                                                                    previousRootPosition = coords.localToRoot(offset)
                                                                }
                                                            }
                                                        },
                                                        onDrag = { change, dragAmount ->
                                                            change.consume()
                                                            
                                                            val currentIndex = currentDraftItems.value.indexOfFirst { it.id == itemId }
                                                            if (currentIndex == -1 || currentDraggedIndex.value != currentIndex) return@detectDragGestures
                                                            
                                                            val coords = itemCoordinates
                                                            val deltaY = if (coords != null && coords.isAttached) {
                                                                val currentRootPosition = coords.localToRoot(change.position)
                                                                val dy = currentRootPosition.y - previousRootPosition.y
                                                                previousRootPosition = currentRootPosition
                                                                dy
                                                            } else {
                                                                dragAmount.y
                                                            }
                                                            
                                                            dragOffset += deltaY
                                                            checkAndPerformSwap(currentIndex, dragOffset)
                                                        },
                                                        onDragCancel = {
                                                            draggedIndex = null
                                                            dragOffset = 0f
                                                        },
                                                        onDragEnd = {
                                                            draggedIndex = null
                                                            dragOffset = 0f
                                                        }
                                                    )
                                                }
                                                .padding(8.dp),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    if (index < draftItems.size - 1 && !isItemDragging) {
                                        HorizontalDivider(
                                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                                            modifier = Modifier.padding(horizontal = 16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }
}
