package dev.qtremors.arcile.shared.ui.dialogs

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dev.qtremors.arcile.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateFakeFileDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, Long) -> Unit
) {
    var fileName by remember { mutableStateOf("") }
    var extension by remember { mutableStateOf("bin") }
    var sizeText by remember { mutableStateOf("10") }
    var unit by remember { mutableStateOf(FileSizeUnit.MB) }
    var expanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.title_create_fake_file)) },
        shape = MaterialTheme.shapes.extraLarge,
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedTextField(
                    value = fileName,
                    onValueChange = { fileName = it },
                    label = { Text(stringResource(R.string.label_fake_file_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = extension,
                    onValueChange = { extension = it },
                    label = { Text(stringResource(R.string.label_fake_file_extension)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = sizeText,
                        onValueChange = { 
                            if (it.all { char -> char.isDigit() }) {
                                sizeText = it
                            }
                        },
                        label = { Text(stringResource(R.string.label_fake_file_size)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    
                    ExposedDropdownMenuBox(
                        expanded = expanded,
                        onExpandedChange = { expanded = !expanded },
                        modifier = Modifier.weight(0.8f)
                    ) {
                        OutlinedTextField(
                            value = unit.name,
                            onValueChange = {},
                            readOnly = true,
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                            modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
                            label = { Text(stringResource(R.string.label_fake_file_unit)) }
                        )
                        ExposedDropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false }
                        ) {
                            FileSizeUnit.entries.forEach { selectedUnit ->
                                DropdownMenuItem(
                                    text = { Text(stringResource(selectedUnit.labelRes)) },
                                    onClick = {
                                        unit = selectedUnit
                                        expanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            val size = sizeText.toLongOrNull() ?: 0L
            val isEnabled = fileName.isNotBlank() && extension.isNotBlank() && size > 0
            
            FilledTonalButton(
                onClick = {
                    val fullFileName = if (extension.startsWith(".")) "$fileName$extension" else "$fileName.$extension"
                    val sizeInBytes = size * unit.multiplier
                    onConfirm(fullFileName, sizeInBytes)
                },
                enabled = isEnabled
            ) {
                Text(stringResource(R.string.action_create))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

enum class FileSizeUnit(val labelRes: Int, val multiplier: Long) {
    KB(R.string.unit_kb, 1024L),
    MB(R.string.unit_mb, 1024L * 1024L),
    GB(R.string.unit_gb, 1024L * 1024L * 1024L)
}
