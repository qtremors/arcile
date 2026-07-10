package dev.qtremors.arcile.feature.storageusage.ui
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import dev.qtremors.arcile.core.ui.ExpressiveTab
import dev.qtremors.arcile.core.ui.theme.bounceClickable
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.qtremors.arcile.core.ui.R
import dev.qtremors.arcile.core.ui.storage.MultiColorStorageBar
import dev.qtremors.arcile.core.storage.domain.CategoryStorage
import dev.qtremors.arcile.core.storage.domain.StorageVolume
import dev.qtremors.arcile.core.storage.domain.isIndexed
import dev.qtremors.arcile.feature.storageusage.StorageOverviewState
import dev.qtremors.arcile.core.storage.domain.StorageUsageNode
import dev.qtremors.arcile.feature.storageusage.StorageUsageUiState
import dev.qtremors.arcile.core.ui.EmptyState
import dev.qtremors.arcile.core.ui.EmptyStateVariant
import dev.qtremors.arcile.feature.storageusage.ui.StorageUsageMap
import dev.qtremors.arcile.core.ui.theme.LocalCategoryColors
import dev.qtremors.arcile.core.ui.theme.bodyLargeMedium
import dev.qtremors.arcile.core.ui.theme.bodyMediumBold
import dev.qtremors.arcile.core.ui.theme.bodyMediumMedium
import dev.qtremors.arcile.core.ui.theme.spacing
import dev.qtremors.arcile.core.ui.theme.titleLargeBold
import dev.qtremors.arcile.core.ui.theme.titleMediumBold
import dev.qtremors.arcile.core.ui.theme.titleSmallSemiBold
import dev.qtremors.arcile.core.presentation.formatFileSize
import dev.qtremors.arcile.core.ui.theme.getCategoryColor
import kotlinx.coroutines.delay
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun StorageDashboardScreen(
    state: StorageOverviewState,
    usageState: StorageUsageUiState,
    selectedVolumeId: String? = null,
    onNavigateBack: () -> Unit,
    onCategoryClick: (String, String?) -> Unit,
    onOpenPath: (String) -> Unit,
    onOpenFile: (String) -> Unit,
    onLoadUsage: (String?) -> Unit,
    onSelectUsageNode: (StorageUsageNode) -> Unit,
    onDrillIntoUsageNode: (StorageUsageNode) -> Unit,
    onUsageBreadcrumbClick: (Int) -> Unit,
    onRefreshUsage: () -> Unit
) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val allVolumes = state.allStorageVolumes
    val selectedVolume = selectedVolumeId?.let { requestedId ->
        allVolumes.firstOrNull { it.id == requestedId }
    }
    val isTemporarySelection = selectedVolume != null && !selectedVolume.kind.isIndexed
    val indexedVolumes = state.indexedVolumes
    val singleIndexedVolumeId = indexedVolumes.singleOrNull()?.id
    val volumes = if (selectedVolumeId != null) {
        if (isTemporarySelection) emptyList() else state.storageInfo?.volumes?.filter { it.id == selectedVolumeId }.orEmpty()
    } else {
        indexedVolumes
    }
    val categoryStorages = if (selectedVolumeId != null) {
        if (isTemporarySelection) {
            emptyList()
        } else {
            state.categoryStoragesByVolume[selectedVolumeId]
                ?: if (selectedVolumeId == singleIndexedVolumeId) state.sortedCategoryStorages else emptyList()
        }
    } else {
        state.sortedCategoryStorages
    }
    val totalBytes = volumes.sumOf { it.totalBytes }
    val freeBytes = volumes.sumOf { it.freeBytes }
    val trashBytes = if (selectedVolumeId != null) {
        state.trashStorageUsage.byVolumeId[selectedVolumeId]
            ?: if (selectedVolumeId == singleIndexedVolumeId) state.trashStorageUsage.totalBytes else 0L
    } else {
        state.trashStorageUsage.totalBytes
    }
    val hasTemporaryMountedVolumes = state.allStorageVolumes.any { !it.kind.isIndexed }

    val categoryColors = LocalCategoryColors.current
    val unassignedColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)

    var showLoading by remember { mutableStateOf(false) }
    var selectedTabIndex by remember { mutableStateOf(0) }

    LaunchedEffect(state.isLoading, state.isCalculatingStorage) {
        if (state.isLoading || state.isCalculatingStorage) {
            delay(150)
            showLoading = true
        } else {
            showLoading = false
        }
    }
    LaunchedEffect(selectedTabIndex, selectedVolumeId) {
        if (selectedTabIndex == 1) {
            onLoadUsage(selectedVolumeId)
        }
    }

    val displayCategories = categoryStorages
        .map { cat ->
            val icon = when (cat.name) {
                "Images" -> Icons.Default.Image
                "Videos" -> Icons.Default.VideoFile
                "Audio" -> Icons.Default.AudioFile
                "Docs" -> Icons.Default.Description
                "Archives" -> Icons.Default.FolderZip
                "APKs" -> Icons.Default.Android
                else -> Icons.Default.Description
            }
            val color = getCategoryColor(cat.name, categoryColors, unassignedColor)
            Triple(cat, icon, color)
        }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.storage_dashboard_title)) },
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
                scrollBehavior = scrollBehavior
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = padding.calculateTopPadding())
        ) {
            PrimaryTabRow(
                selectedTabIndex = selectedTabIndex
            ) {
                ExpressiveTab(
                    selected = selectedTabIndex == 0,
                    onClick = { selectedTabIndex = 0 },
                    index = 0,
                    selectedIndex = selectedTabIndex,
                    text = { Text(stringResource(R.string.storage_dashboard_summary_tab)) }
                )
                ExpressiveTab(
                    selected = selectedTabIndex == 1,
                    onClick = { selectedTabIndex = 1 },
                    index = 1,
                    selectedIndex = selectedTabIndex,
                    text = { Text(stringResource(R.string.storage_dashboard_usage_map_tab)) }
                )
            }

            Box(modifier = Modifier.weight(1f)) {
                if (selectedTabIndex == 0) {
                    StorageSummaryTab(
                        state = state,
                        selectedVolume = selectedVolume,
                        selectedVolumeId = selectedVolumeId,
                        isTemporarySelection = isTemporarySelection,
                        hasTemporaryMountedVolumes = hasTemporaryMountedVolumes,
                        volumes = volumes,
                        categoryStorages = categoryStorages,
                        displayCategories = displayCategories,
                        totalBytes = totalBytes,
                        freeBytes = freeBytes,
                        trashBytes = trashBytes,
                        singleIndexedVolumeId = singleIndexedVolumeId,
                        unassignedColor = unassignedColor,
                        onCategoryClick = onCategoryClick
                    )

                    if (showLoading && volumes.isEmpty() && categoryStorages.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            LoadingIndicator()
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                            top = 12.dp,
                            bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + MaterialTheme.spacing.screenGutter
                        )
                    ) {
                        item {
                            StorageUsageMap(
                                state = usageState,
                                onSelectNode = onSelectUsageNode,
                                onDrillInto = onDrillIntoUsageNode,
                                onBreadcrumbClick = onUsageBreadcrumbClick,
                                onOpenPath = onOpenPath,
                                onOpenFile = onOpenFile,
                                onRefresh = onRefreshUsage,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StorageSummaryTab(
    state: StorageOverviewState,
    selectedVolume: StorageVolume?,
    selectedVolumeId: String?,
    isTemporarySelection: Boolean,
    hasTemporaryMountedVolumes: Boolean,
    volumes: List<StorageVolume>,
    categoryStorages: List<CategoryStorage>,
    displayCategories: List<Triple<CategoryStorage, ImageVector, Color>>,
    totalBytes: Long,
    freeBytes: Long,
    trashBytes: Long,
    singleIndexedVolumeId: String?,
    unassignedColor: Color,
    onCategoryClick: (String, String?) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + MaterialTheme.spacing.screenGutter
        )
    ) {
        if (isTemporarySelection && selectedVolume != null) {
            item { TemporaryDashboardUnavailableCard(selectedVolume) }
        }

        if (selectedVolumeId == null && hasTemporaryMountedVolumes) {
            item {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    shape = MaterialTheme.shapes.extraLarge,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh
                ) {
                    Text(
                        text = stringResource(R.string.temp_storage_notice),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        }

        if (volumes.isEmpty() && !state.isLoading && !state.isCalculatingStorage) {
            item {
                EmptyState(
                    variant = EmptyStateVariant.StorageAccess,
                    title = stringResource(R.string.insights_unavailable),
                    description = stringResource(R.string.no_indexed_volumes),
                    modifier = Modifier.fillParentMaxSize()
                )
            }
        } else {
            items(volumes) { volume ->
                val used = volume.totalBytes - volume.freeBytes
                val volumeCategoryBreakdown = state.categoryStoragesByVolume[volume.id]
                    ?: if (volume.id == singleIndexedVolumeId) state.categoryStorages else null
                val isVolumeBreakdownReady = volumeCategoryBreakdown != null
                val volumeTrashBytes = if (isVolumeBreakdownReady) {
                    state.trashStorageUsage.byVolumeId[volume.id]
                        ?: if (volume.id == singleIndexedVolumeId) state.trashStorageUsage.totalBytes else 0L
                } else {
                    0L
                }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = volume.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.used_of, formatFileSize(used), formatFileSize(volume.totalBytes)),
                        style = MaterialTheme.typography.titleLargeBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    if (volume.totalBytes > 0) {
                        MultiColorStorageBar(
                            totalBytes = volume.totalBytes,
                            freeBytes = volume.freeBytes,
                            categoryStorages = volumeCategoryBreakdown.orEmpty(),
                            trashBytes = volumeTrashBytes,
                            isCalculating = state.isCalculatingStorage || !isVolumeBreakdownReady
                        )
                    }
                }
                androidx.compose.material3.HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                    thickness = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant
                )
            }

            if (trashBytes > 0) {
                item {
                    Text(
                        text = stringResource(R.string.storage_usage_title),
                        style = MaterialTheme.typography.titleMediumBold,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
                    )
                }
                item {
                    val percentage = if (totalBytes > 0) {
                        (trashBytes.toFloat() / totalBytes.toFloat() * 100).toInt()
                    } else {
                        0
                    }
                    StorageUsageTile(
                        name = stringResource(R.string.trash_bin),
                        sizeBytes = trashBytes,
                        percentage = percentage,
                        icon = Icons.Default.Delete,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            if (displayCategories.isNotEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.categories_title),
                        style = MaterialTheme.typography.titleMediumBold,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
                    )
                }

                items(displayCategories.size) { index ->
                    val (cat, icon, color) = displayCategories[index]
                    if (cat.sizeBytes > 0 || totalBytes == 0L) {
                        val percentage = if (totalBytes > 0) {
                            (cat.sizeBytes.toFloat() / totalBytes.toFloat() * 100).toInt()
                        } else {
                            0
                        }

                        CategoryListTile(
                            name = cat.name,
                            sizeBytes = cat.sizeBytes,
                            percentage = percentage,
                            icon = icon,
                            color = color,
                            onClick = { onCategoryClick(cat.name, selectedVolumeId) }
                        )
                    }
                }
            }

            val categorizedBytes = categoryStorages.sumOf { it.sizeBytes }
            val actualUsedBytes = totalBytes - freeBytes
            val otherUsedBytes = (actualUsedBytes - categorizedBytes - trashBytes).coerceAtLeast(0)

            if (otherUsedBytes > 0) {
                item {
                    val percentage = if (totalBytes > 0) {
                        (otherUsedBytes.toFloat() / totalBytes.toFloat() * 100).toInt()
                    } else {
                        0
                    }

                    CategoryListTile(
                        name = stringResource(R.string.other_files_system),
                        sizeBytes = otherUsedBytes,
                        percentage = percentage,
                        icon = Icons.Default.Android,
                        color = unassignedColor,
                        onClick = { }
                    )
                }
            }
        }
    }
}

@Composable
private fun TemporaryDashboardUnavailableCard(volume: StorageVolume) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = volume.name,
                style = MaterialTheme.typography.titleSmallSemiBold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.temp_storage_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
