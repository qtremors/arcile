package dev.qtremors.arcile.feature.browser.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import dev.qtremors.arcile.core.ui.theme.bounceClickable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import dev.qtremors.arcile.core.storage.domain.ArchiveCompressionLevel
import dev.qtremors.arcile.core.storage.domain.ArchiveFormat
import dev.qtremors.arcile.feature.browser.ArchiveExtractionTarget

@Composable
internal fun CreateArchiveDialog(
    defaultName: String, selectedCount: Int, destinationPath: String, existingNames: Set<String>,
    onDismiss: () -> Unit, onConfirm: (String, ArchiveFormat, ArchiveCompressionLevel, String?) -> Unit
) = dev.qtremors.arcile.core.ui.archive.CreateArchiveDialog(
    defaultName, selectedCount, destinationPath, existingNames, onDismiss, onConfirm
)

@Composable
private fun ArchiveFormatChoice(
    option: ArchiveFormat,
    selected: ArchiveFormat,
    onSelect: (ArchiveFormat) -> Unit
) {
    val onChoiceClick = { onSelect(option) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .bounceClickable(onClick = onChoiceClick)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = option == selected, onClick = { onSelect(option) })
        Text(option.displayName, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
internal fun ExtractArchiveDialog(
    archiveName: String,
    defaultDestinationPath: String,
    onDismiss: () -> Unit,
    onConfirm: (ArchiveExtractionTarget, String?) -> Unit
) = dev.qtremors.arcile.core.ui.archive.ExtractArchiveDialog(
    archiveName, defaultDestinationPath, onDismiss,
    onConfirm = { target, path -> onConfirm(ArchiveExtractionTarget.valueOf(target.name), path) }
)
