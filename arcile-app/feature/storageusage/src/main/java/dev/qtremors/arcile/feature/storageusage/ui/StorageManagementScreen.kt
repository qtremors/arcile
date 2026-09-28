package dev.qtremors.arcile.feature.storageusage.ui

import dev.qtremors.arcile.core.ui.arcileTopAppBarNestedScroll
import dev.qtremors.arcile.core.ui.R
import dev.qtremors.arcile.core.ui.storage.StorageManagementCard
import androidx.compose.ui.res.stringResource

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import dev.qtremors.arcile.core.ui.theme.bounceClickable
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
import androidx.compose.ui.draw.clip
import kotlinx.coroutines.delay
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextOverflow
import dev.qtremors.arcile.core.storage.domain.StorageKind
import androidx.compose.foundation.shape.CircleShape
import dev.qtremors.arcile.feature.storageusage.StorageOverviewState
import dev.qtremors.arcile.core.ui.EmptyState
import dev.qtremors.arcile.core.ui.EmptyStateVariant

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun StorageManagementScreen(
    state: StorageOverviewState,
    onNavigateBack: () -> Unit,
    onSetVolumeClassification: (String, StorageKind) -> Unit,
    onResetVolumeClassification: (String) -> Unit
) {
    val volumes = state.allStorageVolumes
    val scrollBehavior = androidx.compose.material3.TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

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
        modifier = Modifier.arcileTopAppBarNestedScroll(scrollBehavior),
        topBar = {
            LargeTopAppBar(
                title = { Text(stringResource(R.string.storage_management_title), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier
                            .clip(CircleShape)
                            .bounceClickable(onClick = onNavigateBack)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                scrollBehavior = dev.qtremors.arcile.core.ui.arcileTopAppBarScrollBehavior(scrollBehavior)
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
                            variant = EmptyStateVariant.StorageAccess,
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
