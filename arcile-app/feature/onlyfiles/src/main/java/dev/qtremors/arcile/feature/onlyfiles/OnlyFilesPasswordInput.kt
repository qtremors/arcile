package dev.qtremors.arcile.feature.onlyfiles

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import dev.qtremors.arcile.core.ui.theme.ExpressiveShapes

private data class VaultPasswordKeyboard(val numeric: Boolean, val setNumeric: (Boolean) -> Unit)
private val LocalVaultPasswordKeyboard = staticCompositionLocalOf { VaultPasswordKeyboard(false, {}) }

@Composable
internal fun VaultPasswordKeyboardProvider(numeric: Boolean, onChange: (Boolean) -> Unit, content: @Composable () -> Unit) {
    var selected by remember(numeric) { mutableStateOf(numeric) }
    CompositionLocalProvider(LocalVaultPasswordKeyboard provides VaultPasswordKeyboard(selected) {
        selected = it
        onChange(it)
    }, content = content)
}

internal fun vaultPasswordKeyboardOptions(numeric: Boolean) = KeyboardOptions(
    keyboardType = if (numeric) KeyboardType.NumberPassword else KeyboardType.Password,
    capitalization = KeyboardCapitalization.None,
    autoCorrectEnabled = false
)

@Composable
internal fun PasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    reveal: Boolean,
    onReveal: () -> Unit,
    label: Int = R.string.onlyfiles_password,
    isError: Boolean = false,
    allowKeyboardSwitch: Boolean = true,
    showKeyboardSwitch: Boolean = true
) {
    val keyboard = LocalVaultPasswordKeyboard.current
    Column {
        OutlinedTextField(
            value = value,
            // Keyboard layout is a preference, never a character filter or password conversion.
            onValueChange = onValueChange,
            label = { Text(stringResource(label)) },
            visualTransformation = if (reveal) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = vaultPasswordKeyboardOptions(allowKeyboardSwitch && keyboard.numeric),
            trailingIcon = { IconButton(onClick = onReveal) {
                Icon(
                    if (reveal) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                    stringResource(if (reveal) R.string.onlyfiles_hide_password else R.string.onlyfiles_show_password)
                )
            } },
            isError = isError,
            singleLine = true,
            shape = ExpressiveShapes.medium,
            modifier = Modifier.fillMaxWidth()
        )
        if (allowKeyboardSwitch && showKeyboardSwitch) TextButton(onClick = { keyboard.setNumeric(!keyboard.numeric) }) {
            Text(stringResource(if (keyboard.numeric) R.string.onlyfiles_use_text_keyboard else R.string.onlyfiles_use_numeric_keyboard))
        }
    }
}
