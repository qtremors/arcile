package dev.qtremors.arcile.feature.quickaccess

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.FolderSpecial
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.qtremors.arcile.core.storage.domain.QuickAccessItem
import dev.qtremors.arcile.core.storage.domain.QuickAccessType
import dev.qtremors.arcile.core.ui.QuickAccessAppIcon
import dev.qtremors.arcile.core.ui.R
import dev.qtremors.arcile.core.ui.keyboardInputField
import dev.qtremors.arcile.core.ui.menus.ExpandableFabMenu
import dev.qtremors.arcile.core.ui.menus.FabMenuItem
import dev.qtremors.arcile.core.ui.theme.ExpressiveShapes

@Composable
internal fun QuickAccessCustomPathDialog(
    label: String,
    path: String,
    onLabelChange: (String) -> Unit,
    onPathChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    val canConfirm = label.isNotBlank() && path.isNotBlank()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.quick_access_add_custom_title)) },
        text = {
            Column {
                OutlinedTextField(
                    value = label,
                    onValueChange = onLabelChange,
                    label = { Text(stringResource(R.string.quick_access_label_hint)) },
                    singleLine = true,
                    shape = ExpressiveShapes.medium,
                    modifier = Modifier.fillMaxWidth().keyboardInputField()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = path,
                    onValueChange = onPathChange,
                    label = { Text(stringResource(R.string.quick_access_path_hint)) },
                    singleLine = true,
                    shape = ExpressiveShapes.medium,
                    modifier = Modifier.fillMaxWidth().keyboardInputField()
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = canConfirm,
                onClick = onConfirm,
                shape = ExpressiveShapes.medium
            ) {
                Text(stringResource(R.string.add))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, shape = ExpressiveShapes.medium) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@Composable
internal fun QuickAccessAddFab(
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onRequestCustomPath: () -> Unit,
    actions: QuickAccessActions
) {
    val rotation = animateFloatAsState(
        targetValue = if (expanded) 45f else 0f,
        label = "quickAccessFabRotation"
    ).value
    val filesItem = remember { quickAccessShortcut("handoff_files_app", "Files", "", QuickAccessType.FILES_APP) }
    val dataItem = remember {
        quickAccessShortcut("handoff_android_data", "Android/data", "Android/data", QuickAccessType.EXTERNAL_HANDOFF)
    }
    val obbItem = remember {
        quickAccessShortcut("handoff_android_obb", "Android/obb", "Android/obb", QuickAccessType.EXTERNAL_HANDOFF)
    }

    Box(modifier = Modifier.navigationBarsPadding()) {
        Box(modifier = Modifier.align(Alignment.BottomEnd)) {
            ExpandableFabMenu(
                isExpanded = expanded,
                onToggleExpand = { onExpandedChange(!expanded) },
                fabIconRotation = rotation,
                items = listOf(
                    FabMenuItem(
                        label = stringResource(R.string.select_folder),
                        icon = Icons.Default.FolderOpen,
                        onClick = { onExpandedChange(false); actions.requestSafFolder() }
                    ),
                    FabMenuItem(
                        label = stringResource(R.string.add_custom_path),
                        icon = Icons.Default.Add,
                        onClick = { onExpandedChange(false); onRequestCustomPath() }
                    ),
                    shortcutFabItem(R.string.item_type_files, filesItem) {
                        onExpandedChange(false)
                        actions.addFilesShortcut()
                    },
                    shortcutFabItem(R.string.add_android_data, dataItem) {
                        onExpandedChange(false)
                        actions.addAndroidDataShortcut()
                    },
                    shortcutFabItem(R.string.add_android_obb, obbItem) {
                        onExpandedChange(false)
                        actions.addAndroidObbShortcut()
                    }
                )
            )
        }
    }
}

@Composable
private fun shortcutFabItem(
    labelRes: Int,
    item: QuickAccessItem,
    onClick: () -> Unit
) = FabMenuItem(
    label = stringResource(labelRes),
    onClick = onClick,
    iconContent = {
        QuickAccessAppIcon(
            item = item,
            fallbackIcon = Icons.Default.FolderSpecial,
            modifier = Modifier.size(24.dp),
            fallbackTint = MaterialTheme.colorScheme.onSecondaryContainer
        )
    }
)

private fun quickAccessShortcut(
    id: String,
    label: String,
    path: String,
    type: QuickAccessType
) = QuickAccessItem(id = id, label = label, path = path, type = type)
