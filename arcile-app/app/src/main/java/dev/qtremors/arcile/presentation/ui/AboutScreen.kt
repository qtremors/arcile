@file:Suppress("LocalContextGetResourceValueCall")
@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package dev.qtremors.arcile.presentation.ui

import android.content.ClipData
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Source
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import dev.qtremors.arcile.core.ui.ArcileScreenScaffold
import dev.qtremors.arcile.core.ui.R
import dev.qtremors.arcile.core.ui.settings.SettingsSection
import dev.qtremors.arcile.core.ui.theme.bounceClickable
import dev.qtremors.arcile.core.ui.theme.expressiveSegmentedShapes
import dev.qtremors.arcile.core.ui.theme.spacing
import kotlinx.coroutines.launch

@Composable
fun AboutSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    SettingsSection(title = title, content = content)
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AboutScreen(
    onNavigateBack: () -> Unit,
    onNavigateToLicenses: () -> Unit
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val clipboard = LocalClipboard.current
    val coroutineScope = rememberCoroutineScope()

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    val copyToClipboard = { text: String ->
        coroutineScope.launch {
            clipboard.setClipEntry(
                ClipEntry(
                    ClipData.newPlainText(context.getString(R.string.app_name), text)
                )
            )
            Toast.makeText(context, context.getString(R.string.copied_to_clipboard), Toast.LENGTH_SHORT).show()
        }
    }

    ArcileScreenScaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text(stringResource(R.string.about_title)) },
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier
                            .clip(CircleShape)
                            .bounceClickable(onClick = onNavigateBack)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = padding.calculateTopPadding())
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(
                top = 16.dp,
                bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + MaterialTheme.spacing.screenGutter
            ),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            item {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    AsyncImage(
                        model = dev.qtremors.arcile.R.mipmap.ic_launcher,
                        contentDescription = stringResource(R.string.app_name),
                        modifier = Modifier.size(96.dp)
                    )
                }
            }

            item {
                AboutSection(title = stringResource(R.string.section_app_info)) {
                    SegmentedListItem(
                        onClick = { copyToClipboard("Arcile v${dev.qtremors.arcile.BuildConfig.VERSION_NAME}") },
                        shapes = expressiveSegmentedShapes(index = 0, count = 4),
                        content = { Text(stringResource(R.string.version)) },
                        supportingContent = { Text(dev.qtremors.arcile.BuildConfig.VERSION_NAME) },
                        leadingContent = {
                            Box(modifier = Modifier.fillMaxHeight(), contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            }
                        },
                        colors = ListItemDefaults.segmentedColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                        modifier = Modifier.height(IntrinsicSize.Min)
                    )
                    SegmentedListItem(
                        onClick = { uriHandler.openUri(context.getString(R.string.developer_url)) },
                        shapes = expressiveSegmentedShapes(index = 1, count = 4),
                        content = { Text(stringResource(R.string.developer)) },
                        supportingContent = { Text(stringResource(R.string.developer_name)) },
                        leadingContent = {
                            Box(modifier = Modifier.fillMaxHeight(), contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Code, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            }
                        },
                        trailingContent = {
                            Box(modifier = Modifier.fillMaxHeight(), contentAlignment = Alignment.Center) {
                                Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                            }
                        },
                        colors = ListItemDefaults.segmentedColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                        modifier = Modifier.height(IntrinsicSize.Min)
                    )
                    SegmentedListItem(
                        onClick = { uriHandler.openUri(context.getString(R.string.repository_full_url)) },
                        shapes = expressiveSegmentedShapes(index = 2, count = 4),
                        content = { Text(stringResource(R.string.repository)) },
                        supportingContent = { Text(stringResource(R.string.repository_url)) },
                        leadingContent = {
                            Box(modifier = Modifier.fillMaxHeight(), contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Source, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            }
                        },
                        trailingContent = {
                            Box(modifier = Modifier.fillMaxHeight(), contentAlignment = Alignment.Center) {
                                Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                            }
                        },
                        colors = ListItemDefaults.segmentedColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                        modifier = Modifier.height(IntrinsicSize.Min)
                    )
                    SegmentedListItem(
                        onClick = {
                            copyToClipboard("${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL} (Android ${android.os.Build.VERSION.RELEASE})")
                        },
                        shapes = expressiveSegmentedShapes(index = 3, count = 4),
                        content = { Text(stringResource(R.string.device)) },
                        supportingContent = { Text("${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL} (Android ${android.os.Build.VERSION.RELEASE})") },
                        leadingContent = {
                            Box(modifier = Modifier.fillMaxHeight(), contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.PhoneAndroid, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            }
                        },
                        colors = ListItemDefaults.segmentedColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                        modifier = Modifier.height(IntrinsicSize.Min)
                    )
                }
            }

            item {
                AboutSection(title = stringResource(R.string.section_privacy)) {
                    SegmentedListItem(
                        onClick = { uriHandler.openUri(context.getString(R.string.privacy_policy_url)) },
                        shapes = expressiveSegmentedShapes(index = 0, count = 1),
                        content = { Text(stringResource(R.string.privacy_policy)) },
                        supportingContent = { Text(stringResource(R.string.privacy_description)) },
                        leadingContent = {
                            Box(modifier = Modifier.fillMaxHeight(), contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            }
                        },
                        trailingContent = {
                            Box(modifier = Modifier.fillMaxHeight(), contentAlignment = Alignment.Center) {
                                Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                            }
                        },
                        colors = ListItemDefaults.segmentedColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                        modifier = Modifier.height(IntrinsicSize.Min)
                    )
                }
            }

            item {
                AboutSection(title = stringResource(R.string.section_changelogs)) {
                    SegmentedListItem(
                        onClick = { uriHandler.openUri("https://github.com/qtremors/arcile/releases") },
                        shapes = expressiveSegmentedShapes(index = 0, count = 3),
                        content = { Text(stringResource(R.string.view_releases)) },
                        supportingContent = { Text(stringResource(R.string.view_releases_description)) },
                        leadingContent = {
                            Box(modifier = Modifier.fillMaxHeight(), contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.History, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            }
                        },
                        trailingContent = {
                            Box(modifier = Modifier.fillMaxHeight(), contentAlignment = Alignment.Center) {
                                Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                            }
                        },
                        colors = ListItemDefaults.segmentedColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                        modifier = Modifier.height(IntrinsicSize.Min)
                    )
                    SegmentedListItem(
                        onClick = { uriHandler.openUri(context.getString(R.string.report_issue_url)) },
                        shapes = expressiveSegmentedShapes(index = 1, count = 3),
                        content = { Text(stringResource(R.string.report_issue)) },
                        supportingContent = { Text(stringResource(R.string.report_issue_description)) },
                        leadingContent = {
                            Box(modifier = Modifier.fillMaxHeight(), contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.BugReport, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            }
                        },
                        trailingContent = {
                            Box(modifier = Modifier.fillMaxHeight(), contentAlignment = Alignment.Center) {
                                Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                            }
                        },
                        colors = ListItemDefaults.segmentedColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                        modifier = Modifier.height(IntrinsicSize.Min)
                    )
                    SegmentedListItem(
                        onClick = onNavigateToLicenses,
                        shapes = expressiveSegmentedShapes(index = 2, count = 3),
                        content = { Text(stringResource(R.string.open_source_licenses)) },
                        supportingContent = { Text(stringResource(R.string.open_source_licenses_description)) },
                        leadingContent = {
                            Box(modifier = Modifier.fillMaxHeight(), contentAlignment = Alignment.Center) {
                                Icon(Icons.AutoMirrored.Filled.Assignment, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            }
                        },
                        colors = ListItemDefaults.segmentedColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                        modifier = Modifier.height(IntrinsicSize.Min)
                    )
                }
            }
        }
    }
}
