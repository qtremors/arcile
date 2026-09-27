package dev.qtremors.arcile.feature.settings.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Android
import androidx.compose.material.icons.outlined.AudioFile
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.FolderZip
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.VideoFile
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.ToggleButtonShapes
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.qtremors.arcile.core.storage.domain.ArcileOpenExtensions
import dev.qtremors.arcile.core.storage.domain.FileCategories
import dev.qtremors.arcile.core.storage.domain.FileOpenBehavior
import dev.qtremors.arcile.core.storage.domain.FileOpenPreferences
import dev.qtremors.arcile.core.ui.R
import dev.qtremors.arcile.core.ui.settings.SettingsSection
import dev.qtremors.arcile.core.ui.theme.ExpressiveShapes
import dev.qtremors.arcile.core.ui.theme.LocalCategoryColors
import dev.qtremors.arcile.core.ui.theme.expressiveSegmentedShapes
import dev.qtremors.arcile.core.ui.theme.getCategoryColor
import dev.qtremors.arcile.core.ui.theme.sheet

private data class ExtensionGroup(
    val id: String,
    val title: Int,
    val icon: ImageVector,
    val extensions: List<String>
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun SettingsFileOpeningSection(
    behaviors: Map<String, FileOpenBehavior>,
    pluginExtensions: Set<String> = emptySet(),
    onBehaviorChange: (String, FileOpenBehavior) -> Unit,
    onBehaviorRemove: (String) -> Unit,
    showHeading: Boolean = true
) {
    var showSheet by remember { mutableStateOf(false) }

    SettingsSection(
        title = stringResource(R.string.settings_file_opening_title),
        showTitle = showHeading
    ) {
        SegmentedListItem(
            onClick = { showSheet = true },
            shapes = expressiveSegmentedShapes(index = 0, count = 1),
            content = { Text(stringResource(R.string.settings_file_opening_title)) },
            supportingContent = { Text(stringResource(R.string.settings_file_opening_summary)) },
            leadingContent = {
                Box(Modifier.fillMaxHeight(), contentAlignment = Alignment.Center) {
                    Icon(Icons.AutoMirrored.Filled.OpenInNew, null, tint = MaterialTheme.colorScheme.primary)
                }
            },
            trailingContent = {
                Box(Modifier.fillMaxHeight(), contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            colors = ListItemDefaults.segmentedColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer
            ),
            modifier = Modifier.height(IntrinsicSize.Min)
        )
    }

    if (showSheet) {
        val nativeGroups = remember {
            listOf(
                ExtensionGroup("images", R.string.category_images, Icons.Outlined.Image,
                    FileCategories.Images.extensions.sorted()),
                ExtensionGroup("videos", R.string.category_videos, Icons.Outlined.VideoFile,
                    FileCategories.Videos.extensions.sorted()),
                ExtensionGroup("audio", R.string.category_audio, Icons.Outlined.AudioFile,
                    FileCategories.Audio.extensions.sorted()),
                ExtensionGroup("documents", R.string.settings_file_opening_documents_text, Icons.Outlined.Description,
                    ArcileOpenExtensions.documents.sorted()),
                ExtensionGroup("archives", R.string.category_archives, Icons.Outlined.FolderZip,
                    ArcileOpenExtensions.archives.sorted()),
                ExtensionGroup("apps", R.string.category_apks, Icons.Outlined.Android,
                    FileCategories.APKs.extensions.sorted())
            )
        }
        val knownExtensions = nativeGroups.flatMapTo(mutableSetOf()) { it.extensions }
        val installedExtensions = pluginExtensions.mapNotNull(::normalizedExtension).toSet()
        val userAddedExtensions = behaviors.keys.mapNotNull(FileOpenPreferences::extensionFromKey)
            .filterNot { it in knownExtensions || it in installedExtensions }.toSet()
        val additionalExtensions = (
            installedExtensions +
                behaviors.keys.mapNotNull(FileOpenPreferences::extensionFromKey)
            ).filterNot { it in knownExtensions }.distinct().sorted()
        val groups = nativeGroups + ExtensionGroup(
            "other", R.string.settings_file_opening_other, Icons.Outlined.Extension, additionalExtensions
        )
        var expandedGroup by rememberSaveable { mutableStateOf<String?>(null) }
        var customExtension by rememberSaveable { mutableStateOf("") }

        ModalBottomSheet(
            onDismissRequest = { showSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            shape = ExpressiveShapes.sheet,
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            dragHandle = { BottomSheetDefaults.DragHandle() }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .testTag("file_open_sheet_content")
                    .imePadding()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = stringResource(R.string.settings_file_opening_title),
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
                Text(
                    text = stringResource(R.string.settings_file_opening_explanation),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 8.dp, end = 8.dp, bottom = 12.dp)
                )

                groups.forEach { group ->
                    ExtensionGroupCard(
                        group = group,
                        expanded = expandedGroup == group.id,
                        behaviors = behaviors,
                        removableExtensions = if (group.id == "other") userAddedExtensions else emptySet(),
                        onToggle = {
                            expandedGroup = if (expandedGroup == group.id) null else group.id
                        },
                        onBehaviorChange = onBehaviorChange,
                        onBehaviorRemove = onBehaviorRemove,
                        extraContent = if (group.id == "other") {
                            {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(
                                        stringResource(R.string.settings_file_opening_custom_description),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        OutlinedTextField(
                                            value = customExtension,
                                            onValueChange = { customExtension = it.removePrefix(".") },
                                            label = { Text(stringResource(R.string.settings_file_opening_extension)) },
                                            prefix = { Text(".") },
                                            leadingIcon = { Icon(Icons.Outlined.Extension, null) },
                                            shape = ExpressiveShapes.medium,
                                            singleLine = true,
                                            keyboardOptions = KeyboardOptions(
                                                keyboardType = KeyboardType.Ascii,
                                                imeAction = ImeAction.Done
                                            ),
                                            keyboardActions = KeyboardActions(
                                                onDone = {
                                                    normalizedExtension(customExtension)?.let { extension ->
                                                        if (extension !in knownExtensions && extension !in additionalExtensions) {
                                                            onBehaviorChange(
                                                                FileOpenPreferences.extensionKey(extension),
                                                                FileOpenBehavior.EXTERNAL
                                                            )
                                                            customExtension = ""
                                                        }
                                                    }
                                                }
                                            ),
                                            modifier = Modifier
                                                .weight(1f)
                                                .testTag("custom_file_extension")
                                        )
                                        FilledTonalIconButton(
                                            onClick = {
                                                normalizedExtension(customExtension)?.let { extension ->
                                                    onBehaviorChange(
                                                        FileOpenPreferences.extensionKey(extension),
                                                        FileOpenBehavior.EXTERNAL
                                                    )
                                                    customExtension = ""
                                                }
                                            },
                                            enabled = normalizedExtension(customExtension)
                                                ?.let { it !in knownExtensions && it !in additionalExtensions } == true,
                                            shape = CircleShape,
                                            modifier = Modifier
                                                .size(56.dp)
                                                .testTag("custom_file_extension_add")
                                        ) {
                                            Icon(
                                                Icons.Default.Add,
                                                contentDescription = stringResource(R.string.settings_file_opening_add)
                                            )
                                        }
                                    }
                                }
                            }
                        } else null
                    )
                }
                Spacer(Modifier.height(12.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalFoundationApi::class)
@Composable
private fun ExtensionGroupCard(
    group: ExtensionGroup,
    expanded: Boolean,
    behaviors: Map<String, FileOpenBehavior>,
    removableExtensions: Set<String>,
    onToggle: () -> Unit,
    onBehaviorChange: (String, FileOpenBehavior) -> Unit,
    onBehaviorRemove: (String) -> Unit,
    extraContent: (@Composable () -> Unit)? = null
) {
    val categoryColors = LocalCategoryColors.current
    val categoryName = when (group.id) {
        "documents" -> FileCategories.Documents.id.value
        "apps" -> FileCategories.APKs.id.value
        else -> group.id.replaceFirstChar { it.uppercase() }
    }
    val iconColor = getCategoryColor(categoryName, categoryColors, MaterialTheme.colorScheme.primary)
    val enabledCount = group.extensions.count { extension ->
        FileOpenPreferences.effectiveBehavior(
            behaviors, extension, FileCategories.getCategoryForFile(extension, null)
        ) == FileOpenBehavior.ARCILE
    }
    var extensionPendingDeletion by rememberSaveable { mutableStateOf<String?>(null) }
    Surface(
        shape = ExpressiveShapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggle)
                    .testTag("file_open_group_${group.id}")
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(shape = CircleShape, color = iconColor.copy(alpha = 0.16f)) {
                    Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                        Icon(group.icon, null, tint = iconColor)
                    }
                }
                Column(Modifier.weight(1f)) {
                    Text(stringResource(group.title), style = MaterialTheme.typography.titleMedium)
                    if (group.extensions.isNotEmpty()) {
                        Text(
                            stringResource(R.string.settings_file_opening_count, enabledCount, group.extensions.size),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Icon(
                    if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            AnimatedVisibility(
                visible = expanded,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ExtensionChoiceGroup(
                        group.extensions.filterNot { it in removableExtensions },
                        behaviors,
                        onBehaviorChange,
                        rowTag = "file_open_choices_${group.id}"
                    )
                    if (removableExtensions.isNotEmpty()) {
                        Text(
                            stringResource(R.string.settings_file_opening_added_extensions),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            removableExtensions.sorted().forEach { extension ->
                                val selected = FileOpenPreferences.effectiveBehavior(
                                    behaviors, extension, FileCategories.getCategoryForFile(extension, null)
                                ) == FileOpenBehavior.ARCILE
                                val isDeleteVisible = extensionPendingDeletion == extension
                                Row(
                                    modifier = Modifier
                                        .weight(1f)
                                        .widthIn(min = 72.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Surface(
                                        shape = CircleShape,
                                        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLowest,
                                        contentColor = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier
                                            .weight(1f)
                                            .heightIn(min = 44.dp)
                                            .clip(CircleShape)
                                            .combinedClickable(
                                                onClick = {
                                                    if (extensionPendingDeletion != null) {
                                                        extensionPendingDeletion = null
                                                    } else {
                                                        onBehaviorChange(
                                                            FileOpenPreferences.extensionKey(extension),
                                                            if (selected) FileOpenBehavior.EXTERNAL else FileOpenBehavior.ARCILE
                                                        )
                                                    }
                                                },
                                                onLongClick = {
                                                    extensionPendingDeletion = if (isDeleteVisible) null else extension
                                                }
                                            )
                                            .testTag("file_extension_$extension")
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 12.dp, vertical = 10.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = ".$extension",
                                                maxLines = 1,
                                                textAlign = TextAlign.Center
                                            )
                                        }
                                    }
                                    AnimatedVisibility(
                                        visible = isDeleteVisible,
                                        enter = fadeIn() + expandHorizontally(),
                                        exit = fadeOut() + shrinkHorizontally()
                                    ) {
                                        IconButton(
                                            onClick = {
                                                onBehaviorRemove(FileOpenPreferences.extensionKey(extension))
                                                extensionPendingDeletion = null
                                            },
                                            modifier = Modifier
                                                .size(40.dp)
                                                .testTag("remove_file_extension_$extension")
                                        ) {
                                            Icon(
                                                Icons.Outlined.DeleteOutline,
                                                contentDescription = stringResource(
                                                    R.string.settings_file_opening_remove_extension,
                                                    ".$extension"
                                                ),
                                                tint = MaterialTheme.colorScheme.error
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                    extraContent?.invoke()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ExtensionChoiceGroup(
    extensions: List<String>,
    behaviors: Map<String, FileOpenBehavior>,
    onBehaviorChange: (String, FileOpenBehavior) -> Unit,
    rowTag: String? = null
) {
    if (extensions.isEmpty()) return
    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (rowTag == null) Modifier else Modifier.testTag(rowTag)),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        extensions.forEach { extension ->
            val selected = FileOpenPreferences.effectiveBehavior(
                behaviors, extension, FileCategories.getCategoryForFile(extension, null)
            ) == FileOpenBehavior.ARCILE
            ToggleButton(
                checked = selected,
                onCheckedChange = { checked ->
                    onBehaviorChange(
                        FileOpenPreferences.extensionKey(extension),
                        if (checked) FileOpenBehavior.ARCILE else FileOpenBehavior.EXTERNAL
                    )
                },
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 44.dp)
                    .testTag("file_extension_$extension"),
                shapes = ToggleButtonShapes(CircleShape, CircleShape, CircleShape),
                colors = ToggleButtonDefaults.toggleButtonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    checkedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    checkedContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ),
                contentPadding = PaddingValues(horizontal = 12.dp)
            ) {
                Text(
                    text = ".$extension",
                    maxLines = 1,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

private fun normalizedExtension(input: String): String? = input.trim().lowercase()
    .removePrefix(".")
    .takeIf { it.matches(Regex("[a-z0-9]+(\\.[a-z0-9]+)*")) }
