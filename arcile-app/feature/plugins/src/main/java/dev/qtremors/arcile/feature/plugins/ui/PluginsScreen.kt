package dev.qtremors.arcile.feature.plugins.ui

import android.content.Intent
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
import androidx.compose.material.icons.filled.HourglassEmpty
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
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import dev.qtremors.arcile.core.ui.R
import dev.qtremors.arcile.plugin.api.PluginCompatibility
import dev.qtremors.arcile.plugin.api.PluginMetadata
import dev.qtremors.arcile.core.plugin.android.PluginCatalogEntry
import dev.qtremors.arcile.core.plugin.android.PluginManager
import dev.qtremors.arcile.core.ui.ArcileScreenScaffold
import dev.qtremors.arcile.core.ui.ArcileSectionHeader
import dev.qtremors.arcile.core.ui.theme.bounceClickable

private data class PluginRowModel(
    val catalog: PluginCatalogEntry?,
    val installed: PluginMetadata?
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PluginsScreen(onNavigateBack: () -> Unit) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val manager = remember(context) { PluginManager(context) }
    var installed by remember { mutableStateOf(manager.getInstalledPlugins()) }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    DisposableEffect(lifecycleOwner, manager) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) installed = manager.getInstalledPlugins()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val catalogRows = PluginManager.catalog.map { catalog ->
        PluginRowModel(catalog, installed.firstOrNull { catalog.matchesPackage(it.packageName) })
    }
    val discoveredRows = installed
        .filterNot { plugin -> PluginManager.catalog.any { it.matchesPackage(plugin.packageName) } }
        .map { PluginRowModel(null, it) }

    ArcileScreenScaffold(
        topBar = {
            LargeTopAppBar(
                title = { Text(stringResource(R.string.plugins_title)) },
                scrollBehavior = scrollBehavior,
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
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(top = padding.calculateTopPadding()),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item { ArcileSectionHeader(text = stringResource(R.string.plugins_available)) }
            items(catalogRows + discoveredRows, key = { it.installed?.packageName ?: it.catalog!!.packageName }) { row ->
                val plugin = row.installed
                val compatible = plugin?.compatibility == PluginCompatibility.COMPATIBLE
                val status = when {
                    plugin != null && compatible -> stringResource(
                        R.string.plugin_status_installed,
                        plugin.versionName,
                        plugin.apiVersion
                    )
                    plugin != null -> stringResource(R.string.plugin_status_incompatible, plugin.apiVersion)
                    row.catalog?.available == true -> stringResource(R.string.plugin_status_not_installed)
                    else -> stringResource(R.string.plugin_status_coming_soon)
                }
                val statusIcon = when {
                    plugin != null && compatible -> Icons.Default.CheckCircle
                    plugin != null -> Icons.Default.Error
                    row.catalog?.available == true -> Icons.Default.RemoveCircleOutline
                    else -> Icons.Default.HourglassEmpty
                }
                ListItem(
                    headlineContent = { Text(plugin?.name ?: row.catalog!!.name) },
                    supportingContent = { Text(status) },
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
                            if (plugin != null) {
                                IconButton(onClick = {
                                    context.startActivity(
                                        Intent(Intent.ACTION_DELETE, Uri.parse("package:${plugin.packageName}"))
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
