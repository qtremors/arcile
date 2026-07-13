package dev.qtremors.arcile.feature.browser.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.qtremors.arcile.core.ui.R
import dev.qtremors.arcile.core.ui.ArcileDropdownMenuItem
import dev.qtremors.arcile.core.ui.keyboardInputField
import dev.qtremors.arcile.core.ui.theme.ExpressiveShapes
import dev.qtremors.arcile.core.ui.theme.bounceClickable

@Composable
internal fun ArchivePasswordPromptDialog(
    archiveName: String,
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
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(archiveName, maxLines = 2, overflow = TextOverflow.Ellipsis)
                PasswordInputField(
                    value = password,
                    onValueChange = { password = it },
                    label = stringResource(R.string.archive_password),
                    supportingText = stringResource(R.string.archive_password_description),
                    passwordVisible = passwordVisible,
                    onToggleVisibility = { passwordVisible = !passwordVisible }
                )
            }
        },
        confirmButton = {
            val onConfirmClick = { onConfirm(password) }
            FilledTonalButton(
                enabled = password.isNotEmpty(),
                onClick = onConfirmClick,
                modifier = Modifier.bounceClickable(
                    enabled = password.isNotEmpty(),
                    onClick = onConfirmClick
                ),
                shape = ExpressiveShapes.medium
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
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
internal fun PasswordInputField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    passwordVisible: Boolean,
    onToggleVisibility: (() -> Unit)?,
    isError: Boolean = false,
    supportingText: String? = null
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        modifier = Modifier.fillMaxWidth().keyboardInputField(),
        label = { Text(label) },
        leadingIcon = {
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        isError = isError,
        shape = ExpressiveShapes.medium,
        supportingText = supportingText?.let { text -> { Text(text) } },
        visualTransformation = if (passwordVisible) {
            VisualTransformation.None
        } else {
            PasswordVisualTransformation()
        },
        trailingIcon = if (onToggleVisibility != null) {
            {
                val icon = if (passwordVisible) {
                    Icons.Default.VisibilityOff
                } else {
                    Icons.Default.Visibility
                }
                val contentDescription = stringResource(
                    if (passwordVisible) {
                        R.string.archive_password_hide
                    } else {
                        R.string.archive_password_show
                    }
                )
                IconButton(
                    onClick = onToggleVisibility,
                    modifier = Modifier
                        .clip(CircleShape)
                        .bounceClickable(onClick = onToggleVisibility)
                ) {
                    Icon(icon, contentDescription = contentDescription)
                }
            }
        } else {
            null
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ArchiveDropdown(
    label: String,
    selectedOption: String,
    options: List<String>,
    onOptionSelected: (String) -> Unit,
    leadingIcon: ImageVector,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = selectedOption,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            leadingIcon = {
                Icon(
                    imageVector = leadingIcon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            trailingIcon = {
                ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
            },
            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
            shape = ExpressiveShapes.medium,
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            options.forEach { option ->
                ArcileDropdownMenuItem(
                    text = { Text(option) },
                    onClick = {
                        onOptionSelected(option)
                        expanded = false
                    }
                )
            }
        }
    }
}
