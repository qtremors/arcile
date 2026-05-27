package dev.qtremors.arcile.shared.ui.dialogs

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dev.qtremors.arcile.R
import kotlinx.coroutines.delay

enum class FileNameValidationError {
    Blank,
    InvalidCharacters,
    ParentPath,
    Duplicate
}

data class FileNameValidationResult(
    val sanitizedName: String,
    val error: FileNameValidationError?
) {
    val isValid: Boolean = error == null
}

private val invalidFileNameChars = setOf('/', '\\', ':', '*', '?', '"', '<', '>', '|')

fun validateFileName(
    value: String,
    existingNames: Set<String> = emptySet(),
    ignoredName: String? = null
): FileNameValidationResult {
    val sanitizedName = value.trim()
    val normalizedExistingNames = existingNames
        .filterNot { ignoredName != null && it.equals(ignoredName, ignoreCase = true) }
        .map { it.lowercase() }
        .toSet()
    val error = when {
        sanitizedName.isBlank() -> FileNameValidationError.Blank
        sanitizedName == "." || sanitizedName == ".." || sanitizedName.contains("..") -> FileNameValidationError.ParentPath
        invalidFileNameChars.any { it in sanitizedName } -> FileNameValidationError.InvalidCharacters
        sanitizedName.lowercase() in normalizedExistingNames -> FileNameValidationError.Duplicate
        else -> null
    }
    return FileNameValidationResult(sanitizedName, error)
}

@Composable
fun FileNameInput(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    existingNames: Set<String> = emptySet(),
    ignoredName: String? = null,
    validationValue: String = value,
    showValidationErrors: Boolean = true,
    autoFocus: Boolean = false,
    onDone: () -> Unit = {}
) {
    val validation = remember(validationValue, existingNames, ignoredName) {
        validateFileName(validationValue, existingNames, ignoredName)
    }
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    LaunchedEffect(autoFocus) {
        if (autoFocus) {
            delay(100)
            focusRequester.requestFocus()
            keyboardController?.show()
        }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label) },
            isError = validation.error != null && showValidationErrors,
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Text,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(
                onDone = {
                    if (validation.isValid) {
                        onDone()
                    }
                }
            )
        )
        validation.error?.takeIf { showValidationErrors }?.let { error ->
            val errorText = when (error) {
                FileNameValidationError.Blank,
                FileNameValidationError.InvalidCharacters,
                FileNameValidationError.ParentPath -> stringResource(R.string.error_invalid_name)
                FileNameValidationError.Duplicate -> stringResource(R.string.error_duplicate_name)
            }
            Text(
                text = errorText,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(start = 16.dp, top = 4.dp)
            )
        }
    }
}
