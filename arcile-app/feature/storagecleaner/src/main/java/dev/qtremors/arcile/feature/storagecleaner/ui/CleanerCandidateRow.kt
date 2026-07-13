package dev.qtremors.arcile.feature.storagecleaner.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.qtremors.arcile.core.presentation.formatFileSize
import dev.qtremors.arcile.core.storage.domain.CleanerCandidate
import dev.qtremors.arcile.core.ui.R
import dev.qtremors.arcile.core.ui.theme.ExpressiveShapes
import dev.qtremors.arcile.core.ui.theme.bodyLargeMedium
import dev.qtremors.arcile.core.ui.theme.bodyMediumBold
import dev.qtremors.arcile.core.ui.theme.bounceClickable

@Composable
internal fun CleanerCandidateRow(
    file: CleanerCandidate,
    selected: Boolean,
    onToggle: () -> Unit,
    onOpenFile: (String) -> Unit = {},
    onOpenContainingFolder: (String) -> Unit = {},
    onIgnoreFile: (String) -> Unit = {}
) {
    val appContext = rememberCleanerAppContext(file)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(ExpressiveShapes.medium)
            .bounceClickable(onClick = onToggle)
            .testTag("checkbox_${file.absolutePath}")
            .padding(vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(checked = selected, onCheckedChange = null)
            Spacer(modifier = Modifier.width(4.dp))
            CleanerFilePreview(
                file = file,
                badgeBgColor = MaterialTheme.colorScheme.surface,
                modifier = Modifier
                    .clip(CircleShape)
                    .bounceClickable {
                        if (file.isDirectory) {
                            onOpenContainingFolder(file.absolutePath)
                        } else {
                            onOpenFile(file.absolutePath)
                        }
                    }
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = file.name,
                    style = MaterialTheme.typography.bodyLargeMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = cleanFilePath(file.absolutePath),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .clip(MaterialTheme.shapes.small)
                        .bounceClickable { onOpenContainingFolder(file.absolutePath) }
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = formatFileSize(file.size),
                style = MaterialTheme.typography.bodyMediumBold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        Spacer(modifier = Modifier.height(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 104.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CleanerRiskSummary(file = file, appContext = appContext, modifier = Modifier.weight(1f))
            Spacer(modifier = Modifier.width(8.dp))
            TextButton(
                onClick = { onIgnoreFile(file.absolutePath) },
                shape = ExpressiveShapes.medium
            ) {
                Text(stringResource(R.string.cleaner_ignore))
            }
        }
    }
}

internal const val DAY_MS = 24L * 60L * 60L * 1000L
