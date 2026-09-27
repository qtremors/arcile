package dev.qtremors.arcile.feature.plugins.ui

import dev.qtremors.arcile.core.ui.arcileTopAppBarNestedScroll
import android.content.Intent
import androidx.core.net.toUri
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import dev.qtremors.arcile.core.ui.R
import dev.qtremors.arcile.plugin.api.PluginCompatibility
import dev.qtremors.arcile.plugin.api.PluginMetadata
import dev.qtremors.arcile.core.plugin.android.PluginCatalogEntry
import dev.qtremors.arcile.core.plugin.android.PluginManager
import dev.qtremors.arcile.core.ui.ArcileScreenScaffold
import dev.qtremors.arcile.core.ui.ArcileSectionHeader
import dev.qtremors.arcile.core.ui.EmptyState
import dev.qtremors.arcile.core.ui.EmptyStateVariant
import dev.qtremors.arcile.core.ui.theme.bounceClickable
import dev.qtremors.arcile.core.ui.showArcileToast

import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.runtime.produceState
import androidx.compose.ui.text.font.FontWeight
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import dev.qtremors.arcile.core.operation.android.apk.ApkUpdateCandidate
import dev.qtremors.arcile.core.operation.android.apk.OnDeviceApkDiscovery
import kotlinx.coroutines.launch

@EntryPoint
@InstallIn(SingletonComponent::class)
internal interface PluginsScreenEntryPoint {
    fun onDeviceApkDiscovery(): OnDeviceApkDiscovery
}

private data class PluginRowModel(
    val catalog: PluginCatalogEntry?,
    val installed: PluginMetadata?
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PluginsScreen(
    onNavigateBack: () -> Unit,
    onInstallUpdate: (String) -> Unit
) {
    val scrollBehavior = androidx.compose.material3.TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()
    val manager = remember(context) { PluginManager(context) }
    var installed by remember { mutableStateOf(manager.getInstalledPlugins()) }

    val discovery = remember(context) {
        runCatching {
            EntryPointAccessors.fromApplication(
                context.applicationContext,
                PluginsScreenEntryPoint::class.java
            ).onDeviceApkDiscovery()
        }.getOrNull()
    }

    val updateCandidates by produceState<List<ApkUpdateCandidate>>(
        initialValue = emptyList(),
        installed,
        discovery
    ) {
        value = discovery?.discoverPluginUpdates() ?: emptyList()
    }

    DisposableEffect(lifecycleOwner, manager) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) installed = manager.getInstalledPlugins()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val catalogRows = PluginManager.catalog.mapNotNull { catalog ->
        val installedPlugin = installed.firstOrNull { catalog.matchesPackage(it.packageName) }
        if (catalog.available || installedPlugin != null) {
            PluginRowModel(catalog, installedPlugin)
        } else {
            null
        }
    }
    val discoveredRows = installed
        .filterNot { plugin -> PluginManager.catalog.any { it.matchesPackage(plugin.packageName) } }
        .map { PluginRowModel(null, it) }
    val rows = catalogRows + discoveredRows

    ArcileScreenScaffold(
        modifier = Modifier.arcileTopAppBarNestedScroll(scrollBehavior),
        topBar = {
            LargeTopAppBar(
                title = { Text(stringResource(R.string.plugins_title), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                scrollBehavior = dev.qtremors.arcile.core.ui.arcileTopAppBarScrollBehavior(scrollBehavior),
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.clip(CircleShape).bounceClickable(onClick = onNavigateBack)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                    }
                }
            )
        }
    ) { padding ->
        if (rows.isEmpty()) {
            EmptyState(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = padding.calculateTopPadding()),
                variant = EmptyStateVariant.Generic,
                icon = Icons.Default.Extension,
                title = stringResource(R.string.plugins_empty_title),
                description = stringResource(R.string.plugins_empty_description)
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(top = padding.calculateTopPadding()),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item { ArcileSectionHeader(text = stringResource(R.string.plugins_available)) }
                items(rows, key = { it.installed?.packageName ?: it.catalog!!.packageName }) { row ->
                    val plugin = row.installed
                    val compatible = plugin?.compatibility == PluginCompatibility.COMPATIBLE
                    val updateCandidate = plugin?.let { inst ->
                        updateCandidates.firstOrNull { it.metadata.packageName == inst.packageName }
                    }
                    val status = when {
                        plugin != null && compatible -> stringResource(
                            R.string.plugin_status_installed,
                            plugin.versionName,
                            plugin.apiVersion
                        )
                        plugin != null -> stringResource(R.string.plugin_status_incompatible, plugin.apiVersion)
                        else -> stringResource(R.string.plugin_status_not_installed)
                    }
                    val statusIcon = when {
                        plugin != null && compatible -> Icons.Default.CheckCircle
                        plugin != null -> Icons.Default.Error
                        else -> Icons.Default.RemoveCircleOutline
                    }
                    ListItem(
                        content = { Text(plugin?.name ?: row.catalog!!.name) },
                        supportingContent = {
                            Column {
                                Text(status)
                                if (updateCandidate != null) {
                                    Text(
                                        text = stringResource(
                                            R.string.plugin_update_available,
                                            updateCandidate.metadata.versionName
                                        ),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        },
                        leadingContent = {
                            Icon(
                                statusIcon,
                                contentDescription = null,
                                tint = if (plugin != null && !compatible) {
                                    MaterialTheme.colorScheme.error
                                } else {
                                    MaterialTheme.colorScheme.primary
                                }
                            )
                        },
                        trailingContent = {
                            Row {
                                if (updateCandidate != null) {
                                    IconButton(onClick = {
                                        coroutineScope.launch {
                                            val freshCandidate = discovery?.revalidateCandidate(updateCandidate)
                                            if (freshCandidate?.isValid == true) {
                                                onInstallUpdate(freshCandidate.metadata.filePath)
                                            } else {
                                                context.showArcileToast(
                                                    context.getString(R.string.error_file_operation_failed)
                                                )
                                            }
                                        }
                                    }) {
                                        Icon(
                                            Icons.Default.SystemUpdate,
                                            stringResource(R.string.plugin_update_action),
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                                if (plugin != null) {
                                    IconButton(onClick = {
                                        context.startActivity(
                                            Intent(Intent.ACTION_DELETE, "package:${plugin.packageName}".toUri())
                                        )
                                    }) {
                                        Icon(Icons.Default.Delete, stringResource(R.string.uninstall))
                                    }
                                } else if (row.catalog?.available == true) {
                                    IconButton(onClick = { uriHandler.openUri(PluginManager.RELEASES_URL) }) {
                                        Icon(Icons.Default.CloudDownload, stringResource(R.string.install))
                                    }
                                }
                                IconButton(onClick = {
                                    uriHandler.openUri(plugin?.homepage ?: PluginManager.RELEASES_URL)
                                }) {
                                    Icon(Icons.Default.Link, stringResource(R.string.view_github))
                                }
                            }
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                    )
                }
            }
        }
    }
}
