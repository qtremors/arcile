package dev.qtremors.arcile.feature.audio

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.qtremors.arcile.core.ui.keyboardInputField
import dev.qtremors.arcile.core.ui.theme.ExpressiveShapes

@Composable
internal fun AudioTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: (@Composable () -> Unit)? = null,
    supportingText: (@Composable () -> Unit)? = null,
    singleLine: Boolean = false,
    minLines: Int = 1,
    enabled: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.keyboardInputField(),
        label = label,
        supportingText = supportingText,
        singleLine = singleLine,
        minLines = minLines,
        enabled = enabled,
        keyboardOptions = keyboardOptions,
        shape = ExpressiveShapes.medium
    )
}
