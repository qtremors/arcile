package dev.qtremors.arcile.presentation.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.shape.CircleShape
import dev.qtremors.arcile.ui.theme.spacing
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.qtremors.arcile.core.ui.R
import dev.qtremors.arcile.ui.theme.ExpressiveShapes
import dev.qtremors.arcile.ui.theme.bounceClickable
import dev.qtremors.arcile.shared.ui.ArcileScreenScaffold
import dev.qtremors.arcile.shared.ui.ArcileSectionHeader
import dev.qtremors.arcile.shared.ui.ArcileListSurface

// ──────────────────────────────────────────────────────
// Data model for a third-party library entry
// ──────────────────────────────────────────────────────
private data class LibraryInfo(
    val name: String,
    val license: String,
    val url: String
)

// ──────────────────────────────────────────────────────
// Runtime dependencies used by this application
// ──────────────────────────────────────────────────────
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

@OptIn(ExperimentalMaterial3Api::class)
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
            // ── License notice ──
            item {
                ArcileListSurface {
                    Text(
                        text = stringResource(R.string.licenses_notice),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }

            // ── Library list ──
            item {
                Column {
                    ArcileSectionHeader(text = stringResource(R.string.licenses_section_libraries))
                    ArcileListSurface {
                        libraries.forEachIndexed { index, lib ->
                            ListItem(
                                headlineContent = { Text(lib.name) },
                                supportingContent = { Text(lib.license) },
                                trailingContent = {
                                    Icon(
                                        Icons.AutoMirrored.Filled.OpenInNew,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                },
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                modifier = Modifier
                                    .clip(ExpressiveShapes.medium)
                                    .bounceClickable { uriHandler.openUri(lib.url) }
                            )
                            if (index < libraries.lastIndex) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(horizontal = 16.dp),
                                    color = MaterialTheme.colorScheme.outlineVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
