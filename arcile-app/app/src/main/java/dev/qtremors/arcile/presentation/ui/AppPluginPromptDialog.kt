package dev.qtremors.arcile.presentation.ui

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import dev.qtremors.arcile.core.plugin.android.PluginFileResolution
import dev.qtremors.arcile.core.plugin.android.PluginManager
import dev.qtremors.arcile.core.ui.R

@Composable
internal fun AppPluginPromptDialog(
    prompt: PluginFileResolution?,
    onDismiss: () -> Unit
) {
    val uriHandler = LocalUriHandler.current
    when (prompt) {
        is PluginFileResolution.Missing -> AlertDialog(
            onDismissRequest = onDismiss,
            title = {
                Text(stringResource(R.string.plugin_missing_title, prompt.catalogEntry.name))
            },
            text = {
                Text(stringResource(R.string.plugin_missing_message, prompt.catalogEntry.name))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDismiss()
                        uriHandler.openUri(PluginManager.RELEASES_URL)
                    }
                ) {
                    Text(stringResource(R.string.install))
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )

        is PluginFileResolution.Incompatible -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.plugin_incompatible_title)) },
            text = {
                Text(stringResource(R.string.plugin_incompatible_message, prompt.plugin.name))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDismiss()
                        uriHandler.openUri(PluginManager.RELEASES_URL)
                    }
                ) {
                    Text(stringResource(R.string.view_releases))
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )

        else -> Unit
    }
}
