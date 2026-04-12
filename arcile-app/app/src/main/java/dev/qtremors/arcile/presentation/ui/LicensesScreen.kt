package dev.qtremors.arcile.presentation.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import dev.qtremors.arcile.R
import dev.qtremors.arcile.ui.theme.ExpressiveShapes

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
    LibraryInfo("MaterialKolor", "MIT", "https://github.com/jordond/MaterialKolor")
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LicensesScreen(
    onNavigateBack: () -> Unit
) {
    val uriHandler = LocalUriHandler.current
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text(stringResource(R.string.open_source_licenses)) },
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
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
                .padding(padding)
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(top = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // ── License notice ──
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    shape = ExpressiveShapes.large
                ) {
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
                    Text(
                        text = stringResource(R.string.licenses_section_libraries),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 8.dp, bottom = 8.dp)
                    )
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        shape = ExpressiveShapes.large
                    ) {
                        Column(modifier = Modifier.padding(vertical = 4.dp)) {
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
                                        .clickable { uriHandler.openUri(lib.url) }
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
}
