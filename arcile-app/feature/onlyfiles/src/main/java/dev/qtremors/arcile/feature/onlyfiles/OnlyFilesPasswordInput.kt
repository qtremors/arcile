package dev.qtremors.arcile.feature.onlyfiles

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.outlined.Dialpad
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.qtremors.arcile.core.ui.ExpressiveSwitch
import dev.qtremors.arcile.core.vault.domain.VaultPasswordPolicy
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
        if (allowKeyboardSwitch && showKeyboardSwitch) {
            PasswordKeyboardToggle(keyboard.numeric, keyboard.setNumeric, Modifier.align(Alignment.End).padding(top = 8.dp))
        }
    }
}

/** ABC / 123 connected toggle that picks the keyboard layout. It never filters what is typed. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun PasswordKeyboardToggle(numeric: Boolean, onNumericChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)) {
        listOf(false to R.string.onlyfiles_keyboard_letters, true to R.string.onlyfiles_keyboard_numbers)
            .forEachIndexed { index, (isNumeric, label) ->
                val description = stringResource(
                    if (isNumeric) R.string.onlyfiles_use_numeric_keyboard else R.string.onlyfiles_use_text_keyboard
                )
                ToggleButton(
                    checked = numeric == isNumeric,
                    onCheckedChange = { if (it) onNumericChange(isNumeric) },
                    shapes = if (index == 0) ButtonGroupDefaults.connectedLeadingButtonShapes()
                    else ButtonGroupDefaults.connectedTrailingButtonShapes(),
                    modifier = Modifier.semantics { contentDescription = description }
                ) {
                    Icon(
                        if (isNumeric) Icons.Outlined.Dialpad else Icons.Outlined.Keyboard,
                        contentDescription = null,
                        modifier = Modifier.size(ToggleButtonDefaults.IconSize)
                    )
                    Spacer(Modifier.width(ToggleButtonDefaults.IconSpacing))
                    Text(stringResource(label))
                }
            }
    }
}

/** True when [password] is short or simple. The temporary copy is wiped after the check. */
@Composable
internal fun rememberPasswordWeak(password: String): Boolean = remember(password) {
    if (password.isEmpty()) false else {
        val secret = password.toCharArray()
        try { VaultPasswordPolicy.isWeak(secret) } finally { secret.fill('\u0000') }
    }
}

/**
 * The one way every password form accepts a short or simple password, a PIN included: shown
 * only when the password is weak, and the form stays disabled until it is switched on.
 */
@Composable
internal fun ShortPasswordConsent(accepted: Boolean, onAcceptedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(ExpressiveShapes.medium)
            .background(MaterialTheme.colorScheme.tertiaryContainer)
            .toggleable(value = accepted, role = Role.Switch, onValueChange = onAcceptedChange)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                stringResource(R.string.onlyfiles_short_password_title),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onTertiaryContainer
            )
            Text(
                stringResource(R.string.onlyfiles_short_password_allow),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onTertiaryContainer
            )
        }
        ExpressiveSwitch(checked = accepted, onCheckedChange = null)
    }
}
