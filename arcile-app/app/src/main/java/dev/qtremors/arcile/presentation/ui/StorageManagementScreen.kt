package dev.qtremors.arcile.presentation.ui

import dev.qtremors.arcile.R
import androidx.compose.ui.res.stringResource

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.SdCard
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import kotlinx.coroutines.delay
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.qtremors.arcile.domain.StorageKind
import dev.qtremors.arcile.domain.StorageVolume
import androidx.compose.foundation.shape.CircleShape
import dev.qtremors.arcile.presentation.home.HomeState
import dev.qtremors.arcile.presentation.ui.components.EmptyState
import dev.qtremors.arcile.presentation.ui.components.lists.VolumeRootList

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun StorageManagementScreen(
    state: HomeState,
    onNavigateBack: () -> Unit,
    onSetVolumeClassification: (String, StorageKind) -> Unit,
    onResetVolumeClassification: (String) -> Unit
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val volumes = state.allStorageVolumes

    var showLoading by remember { mutableStateOf(false) }
    LaunchedEffect(state.isLoading, state.isCalculatingStorage) {
        if (state.isLoading || state.isCalculatingStorage) {
            delay(150)
            showLoading = true
        } else {
            showLoading = false
        }
    }

    Scaffold(
        topBar = {
            LargeTopAppBar(
                title = { Text(stringResource(R.string.storage_management_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                scrollBehavior = scrollBehavior
            )
        }
    ) { padding ->
        androidx.compose.foundation.layout.Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Card(
                        shape = MaterialTheme.shapes.extraLarge,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = stringResource(R.string.storage_management_intro_title),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = stringResource(R.string.storage_management_intro_body),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                if (volumes.isEmpty() && !state.isLoading && !state.isCalculatingStorage) {
                    item {
                        EmptyState(
                            icon = Icons.Default.Storage,
                            title = stringResource(R.string.storage_management_empty_title),
                            description = stringResource(R.string.storage_management_empty_description),
                            modifier = Modifier.fillParentMaxSize()
                        )
                    }
                } else {
                    items(volumes, key = { it.id }) { volume ->
                        StorageManagementCard(
                            volume = volume,
                            onSetVolumeClassification = onSetVolumeClassification,
                            onResetVolumeClassification = onResetVolumeClassification
                        )
                    }
                }
            }

            if (showLoading && volumes.isEmpty()) {
                androidx.compose.foundation.layout.Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    LoadingIndicator()
                }
            }
        }
    }
}

@Composable
private fun StorageManagementCard(
    volume: StorageVolume,
    onSetVolumeClassification: (String, StorageKind) -> Unit,
    onResetVolumeClassification: (String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = volume.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(text = volume.path, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                AssistChip(
                    onClick = {},
                    label = { Text(storageKindLabel(volume.kind)) },
                    leadingIcon = {
                        Icon(
                            imageVector = when (volume.kind) {
                                StorageKind.INTERNAL -> Icons.Default.Storage
                                StorageKind.SD_CARD -> Icons.Default.SdCard
                                StorageKind.OTG -> Icons.Default.Usb
                                StorageKind.EXTERNAL_UNCLASSIFIED -> Icons.Default.Info
                            },
                            contentDescription = null
                        )
                    },
                    shape = CircleShape
                )
            }

            Text(
                text = when (volume.kind) {
                    StorageKind.INTERNAL -> stringResource(R.string.storage_kind_internal_description)
                    StorageKind.SD_CARD -> stringResource(R.string.storage_kind_sd_description)
                    StorageKind.OTG -> stringResource(R.string.storage_kind_otg_description)
                    StorageKind.EXTERNAL_UNCLASSIFIED -> stringResource(R.string.storage_kind_unclassified_description)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(
                    onClick = { onSetVolumeClassification(volume.storageKey, StorageKind.SD_CARD) },
                    enabled = !volume.isPrimary && volume.kind != StorageKind.SD_CARD,
                    shape = CircleShape
                ) {
                    Text(stringResource(R.string.classify_as_sd))
                }
                TextButton(
                    onClick = { onSetVolumeClassification(volume.storageKey, StorageKind.OTG) },
                    enabled = !volume.isPrimary && volume.kind != StorageKind.OTG,
                    shape = CircleShape
                ) {
                    Text(stringResource(R.string.classify_as_otg))
                }
                if (!volume.isPrimary) {
                    TextButton(
                        onClick = { onResetVolumeClassification(volume.storageKey) },
                        enabled = volume.isUserClassified,
                        shape = CircleShape
                    ) {
                        Text(stringResource(R.string.reset))
                    }
                }
            }

            if (volume.isUserClassified) {
                Text(
                    text = stringResource(R.string.storage_classification_saved),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable(enabled = false) {}
                )
            }
        }
    }
}

@Composable
private fun storageKindLabel(kind: StorageKind): String = when (kind) {
    StorageKind.INTERNAL -> stringResource(R.string.storage_kind_internal)
    StorageKind.SD_CARD -> stringResource(R.string.storage_kind_sd)
    StorageKind.OTG -> stringResource(R.string.storage_kind_otg)
    StorageKind.EXTERNAL_UNCLASSIFIED -> stringResource(R.string.storage_kind_unclassified)
}
