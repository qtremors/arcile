package dev.qtremors.arcile.feature.browser.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import dev.qtremors.arcile.core.storage.domain.FileOpenAsType
import dev.qtremors.arcile.core.ui.R
import dev.qtremors.arcile.core.ui.dialogs.AlertDialog

@Composable
internal fun OpenAsDialog(
    onSelect: (FileOpenAsType) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.open_as_title)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                FileOpenAsType.entries.forEach { type ->
                    TextButton(
                        onClick = { onSelect(type) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(type.labelResource()))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}

private fun FileOpenAsType.labelResource(): Int = when (this) {
    FileOpenAsType.IMAGE -> R.string.open_as_image
    FileOpenAsType.VIDEO -> R.string.open_as_video
    FileOpenAsType.AUDIO -> R.string.open_as_audio
    FileOpenAsType.DOCUMENT -> R.string.open_as_document
    FileOpenAsType.ARCHIVE -> R.string.open_as_archive
    FileOpenAsType.TEXT -> R.string.open_as_text
    FileOpenAsType.OTHER -> R.string.open_as_other
}
