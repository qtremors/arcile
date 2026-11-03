package dev.qtremors.arcile.core.ui.pdf

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Print
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import dev.qtremors.arcile.core.ui.R
import dev.qtremors.arcile.core.ui.dialogs.AlertDialog
import dev.qtremors.arcile.core.ui.theme.ExpressiveShapes
import dev.qtremors.arcile.core.ui.theme.bounceClickable

/** Printing leaves the vault boundary, so private PDFs explain where decrypted pages go before printing. */
@Composable
internal fun PrivatePdfPrintDialog(onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Print, contentDescription = null) },
        title = { Text(stringResource(R.string.pdf_print_private_title)) },
        text = { Text(stringResource(R.string.pdf_print_private_message)) },
        confirmButton = {
            FilledTonalButton(
                onClick = onConfirm,
                modifier = Modifier.bounceClickable(onClick = onConfirm),
                shape = ExpressiveShapes.medium
            ) { Text(stringResource(R.string.pdf_print)) }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.bounceClickable(onClick = onDismiss),
                shape = ExpressiveShapes.medium
            ) { Text(stringResource(R.string.cancel)) }
        }
    )
}
