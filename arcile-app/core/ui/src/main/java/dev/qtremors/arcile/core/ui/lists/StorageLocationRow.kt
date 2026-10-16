package dev.qtremors.arcile.core.ui.lists

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.InsertDriveFile
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.qtremors.arcile.core.ui.theme.ExpressiveShapes

@Composable
fun StorageLocationRow(
    name: String,
    isDirectory: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    path: String? = null,
    isVolume: Boolean = false,
    selected: Boolean? = null
) {
    val icon = when {
        isVolume -> Icons.Outlined.Storage
        isDirectory -> Icons.Outlined.Folder
        else -> Icons.AutoMirrored.Outlined.InsertDriveFile
    }
    val interaction = if (selected == null) Modifier.clickable(onClick = onClick)
    else Modifier.toggleable(selected, role = Role.Checkbox, onValueChange = { onClick() })
    val supporting: (@Composable () -> Unit)? = path?.let {
        { Text(it, maxLines = 1, overflow = TextOverflow.Ellipsis) }
    }
    val trailing: (@Composable () -> Unit)? = selected?.let {
        { Checkbox(checked = it, onCheckedChange = null) }
    }
    ListItem(
        content = { Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = supporting,
        leadingContent = { Icon(icon, contentDescription = null) },
        trailingContent = trailing,
        modifier = modifier.padding(horizontal = 8.dp, vertical = 2.dp)
            .clip(ExpressiveShapes.medium)
            .then(interaction)
    )
}
