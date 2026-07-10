package dev.qtremors.arcile.feature.home.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.qtremors.arcile.core.storage.domain.StorageKind
import dev.qtremors.arcile.core.storage.domain.StorageVolume
import dev.qtremors.arcile.core.ui.R
import dev.qtremors.arcile.core.ui.theme.ExpressiveShapes
import dev.qtremors.arcile.core.ui.theme.spacing
import dev.qtremors.arcile.core.ui.theme.titleMediumBold

@Composable
internal fun StorageClassificationPrompt(
    volume: StorageVolume,
    onClassify: (StorageKind) -> Unit,
    onDecideLater: () -> Unit
) {
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(MaterialTheme.spacing.medium),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    ) {
        Column(modifier = Modifier.padding(MaterialTheme.spacing.space20)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(MaterialTheme.spacing.space12))
                Text(
                    text = stringResource(R.string.new_storage_detected),
                    style = MaterialTheme.typography.titleMediumBold
                )
            }

            Spacer(modifier = Modifier.height(MaterialTheme.spacing.space12))
            Text(
                text = stringResource(R.string.how_should_be_treated, volume.name),
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(modifier = Modifier.height(MaterialTheme.spacing.medium))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Button(
                        onClick = { onClassify(StorageKind.SD_CARD) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = ExpressiveShapes.medium
                    ) {
                        Text(stringResource(R.string.sd_card), maxLines = 1)
                    }
                    Text(
                        stringResource(R.string.sd_card_description),
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(top = MaterialTheme.spacing.extraSmall),
                        textAlign = TextAlign.Center
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    OutlinedButton(
                        onClick = { onClassify(StorageKind.OTG) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = ExpressiveShapes.medium
                    ) {
                        Text(stringResource(R.string.otg_usb), maxLines = 1)
                    }
                    Text(
                        stringResource(R.string.otg_usb_description),
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(top = MaterialTheme.spacing.extraSmall),
                        textAlign = TextAlign.Center
                    )
                }
            }

            Spacer(modifier = Modifier.height(MaterialTheme.spacing.medium))
            TextButton(
                onClick = onDecideLater,
                shape = ExpressiveShapes.medium,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            ) {
                Text(stringResource(R.string.decide_later))
            }
        }
    }
}
