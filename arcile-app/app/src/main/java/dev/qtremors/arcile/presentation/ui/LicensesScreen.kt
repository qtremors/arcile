@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package dev.qtremors.arcile.presentation.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.qtremors.arcile.core.ui.ArcileScreenScaffold
import dev.qtremors.arcile.core.ui.R
import dev.qtremors.arcile.core.ui.settings.SettingsSection
import dev.qtremors.arcile.core.ui.theme.bounceClickable
import dev.qtremors.arcile.core.ui.theme.expressiveSegmentedShapes
import dev.qtremors.arcile.core.ui.theme.spacing

private data class LibraryInfo(
    val name: String,
    val license: String,
    val url: String
)

private val libraries = listOf(
    LibraryInfo("AndroidX Core KTX", "Apache 2.0", "https://developer.android.com/jetpack/androidx/releases/core"),
    LibraryInfo("AndroidX Activity Compose", "Apache 2.0", "https://developer.android.com/jetpack/androidx/releases/activity"),
    LibraryInfo("AndroidX Lifecycle Runtime KTX", "Apache 2.0", "https://developer.android.com/jetpack/androidx/releases/lifecycle"),
    LibraryInfo("AndroidX Lifecycle ViewModel Compose", "Apache 2.0", "https://developer.android.com/jetpack/androidx/releases/lifecycle"),
    LibraryInfo("AndroidX Navigation Compose", "Apache 2.0", "https://developer.android.com/jetpack/androidx/releases/navigation"),
    LibraryInfo("AndroidX DataStore Preferences", "Apache 2.0", "https://developer.android.com/jetpack/androidx/releases/datastore"),
    LibraryInfo("AndroidX Core Splash Screen", "Apache 2.0", "https://developer.android.com/jetpack/androidx/releases/core"),
    LibraryInfo("Jetpack Compose UI", "Apache 2.0", "https://developer.android.com/jetpack/compose"),
    LibraryInfo("Jetpack Compose Material 3", "Apache 2.0", "https://developer.android.com/jetpack/compose"),
    LibraryInfo("Jetpack Compose Material Icons Extended", "Apache 2.0", "https://developer.android.com/jetpack/compose"),
    LibraryInfo("Kotlin Coroutines", "Apache 2.0", "https://github.com/Kotlin/kotlinx.coroutines"),
    LibraryInfo("Kotlin Serialization", "Apache 2.0", "https://github.com/Kotlin/kotlinx.serialization"),
    LibraryInfo("Coil (Image Loading)", "Apache 2.0", "https://github.com/coil-kt/coil"),
    LibraryInfo("Hilt (Dependency Injection)", "Apache 2.0", "https://dagger.dev/hilt/"),
    LibraryInfo("MaterialKolor", "MIT", "https://github.com/jordond/MaterialKolor"),
    LibraryInfo("Apache Commons Compress 1.28.0", "Apache 2.0", "https://commons.apache.org/proper/commons-compress/"),
    LibraryInfo("Zip4j 2.11.6", "Apache 2.0", "https://github.com/srikanth-lingala/zip4j"),
    LibraryInfo("Tukaani XZ 1.10", "Public domain", "https://tukaani.org/xz/java.html")
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun LicensesScreen(
    onNavigateBack: () -> Unit
) {
    val uriHandler = LocalUriHandler.current
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    ArcileScreenScaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text(stringResource(R.string.open_source_licenses)) },
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier
                            .clip(CircleShape)
                            .bounceClickable(onClick = onNavigateBack)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
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
                SegmentedListItem(
                    onClick = {},
                    shapes = expressiveSegmentedShapes(index = 0, count = 1),
                    content = {
                        Text(
                            text = stringResource(R.string.licenses_notice),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    colors = ListItemDefaults.segmentedColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer
                    )
                )
            }

            item {
                SettingsSection(title = stringResource(R.string.licenses_section_libraries)) {
                    libraries.forEachIndexed { index, lib ->
                        SegmentedListItem(
                            onClick = { uriHandler.openUri(lib.url) },
                            shapes = expressiveSegmentedShapes(index = index, count = libraries.size),
                            content = { Text(lib.name) },
                            supportingContent = { Text(lib.license) },
                            trailingContent = {
                                Box(
                                    modifier = Modifier.fillMaxHeight(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.AutoMirrored.Filled.OpenInNew,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            },
                            colors = ListItemDefaults.segmentedColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainer
                            ),
                            modifier = Modifier.height(IntrinsicSize.Min)
                        )
                    }
                }
            }
        }
    }
}
