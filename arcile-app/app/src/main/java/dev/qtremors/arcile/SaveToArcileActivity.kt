package dev.qtremors.arcile

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.SaveAlt
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dagger.hilt.android.AndroidEntryPoint
import dev.qtremors.arcile.core.storage.data.MutationFinalizer
import dev.qtremors.arcile.core.storage.domain.StorageVolume
import dev.qtremors.arcile.core.storage.domain.StorageWorkCoordinator
import dev.qtremors.arcile.core.storage.domain.VolumeRepository
import dev.qtremors.arcile.core.ui.R
import dev.qtremors.arcile.di.ArcileDispatchers
import dev.qtremors.arcile.ui.theme.ArcileTheme
import dev.qtremors.arcile.ui.theme.ThemeState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

@AndroidEntryPoint
class SaveToArcileActivity : ComponentActivity() {

    @Inject
    lateinit var volumeRepository: VolumeRepository

    @Inject
    lateinit var storageWorkCoordinator: StorageWorkCoordinator

    @Inject
    lateinit var mutationFinalizer: MutationFinalizer

    @Inject
    lateinit var dispatchers: ArcileDispatchers

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val incoming = IncomingShareReader.fromIntent(this, intent)
        if (incoming.isEmpty()) {
            Toast.makeText(this, getString(R.string.save_to_arcile_no_files), Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        setContent {
            ArcileTheme(themeState = ThemeState()) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    SaveToArcileScreen(
                        incoming = incoming,
                        loadVolumes = { volumeRepository.getStorageVolumes().getOrElse { emptyList() } },
                        copyTo = { destination -> saveIncoming(destination, incoming) },
                        onCancel = { finish() },
                        onFinished = { count ->
                            Toast.makeText(
                                this,
                                resources.getQuantityString(R.plurals.save_to_arcile_saved_files, count, count),
                                Toast.LENGTH_SHORT
                            ).show()
                            finish()
                        },
                        onFailed = { error ->
                            Toast.makeText(
                                this,
                                getString(R.string.save_to_arcile_failed, error.message ?: ""),
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    )
                }
            }
        }
    }

    private suspend fun saveIncoming(destination: File, incoming: List<IncomingSharedFile>): Result<Int> =
        withContext(dispatchers.io) {
            storageWorkCoordinator.beginMutation()
            try {
                require(destination.exists() && destination.isDirectory) { getString(R.string.save_to_arcile_invalid_destination) }
                incoming.forEach { item ->
                    val target = keepBothTarget(destination, item.displayName)
                    contentResolver.openInputStream(item.uri).use { input ->
                        requireNotNull(input) { getString(R.string.save_to_arcile_failed_open_stream) }
                        target.outputStream().buffered().use { output ->
                            input.copyTo(output, bufferSize = STREAM_BUFFER_SIZE)
                        }
                    }
                }
                mutationFinalizer.finalize(destination.absolutePath)
                Result.success(incoming.size)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Result.failure(e)
            } finally {
                storageWorkCoordinator.endMutation()
            }
        }

    private companion object {
        const val STREAM_BUFFER_SIZE = 128 * 1024
    }
}

data class IncomingSharedFile(
    val uri: Uri,
    val displayName: String,
    val sizeBytes: Long? = null
)

object IncomingShareReader {
    fun fromIntent(context: Context, intent: Intent): List<IncomingSharedFile> {
        val uris = buildList {
            intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)?.let(::add)
            intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)?.let(::addAll)
            collectClipData(intent.clipData).forEach(::add)
        }.distinct()
        return uris.map { uri ->
            val metadata = resolveMetadata(context, uri)
            IncomingSharedFile(
                uri = uri,
                displayName = metadata.name ?: uri.lastPathSegment?.substringAfterLast('/') ?: "shared-file",
                sizeBytes = metadata.size
            )
        }
    }

    private fun collectClipData(clipData: ClipData?): List<Uri> {
        if (clipData == null) return emptyList()
        return (0 until clipData.itemCount).mapNotNull { index -> clipData.getItemAt(index).uri }
    }

    private fun resolveMetadata(context: Context, uri: Uri): IncomingMetadata {
        var name: String? = null
        var size: Long? = null
        runCatching {
            context.contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (nameIndex >= 0) name = cursor.getString(nameIndex)
                    if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) size = cursor.getLong(sizeIndex)
                }
            }
        }
        return IncomingMetadata(name?.takeIf { it.isNotBlank() }, size)
    }

    private data class IncomingMetadata(val name: String?, val size: Long?)
}

private fun keepBothTarget(destination: File, requestedName: String): File {
    val safeName = requestedName
        .replace('/', '_')
        .replace('\\', '_')
        .takeIf { it.isNotBlank() }
        ?: "shared-file"
    val requested = File(destination, safeName)
    if (!requested.exists()) return requested

    val baseName = requested.nameWithoutExtension
    val extension = requested.extension.takeIf { it.isNotBlank() }?.let { ".$it" }.orEmpty()
    var index = 1
    var candidate: File
    do {
        candidate = File(destination, "$baseName ($index)$extension")
        index += 1
    } while (candidate.exists())
    return candidate
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SaveToArcileScreen(
    incoming: List<IncomingSharedFile>,
    loadVolumes: suspend () -> List<StorageVolume>,
    copyTo: suspend (File) -> Result<Int>,
    onCancel: () -> Unit,
    onFinished: (Int) -> Unit,
    onFailed: (Throwable) -> Unit
) {
    val scope = rememberCoroutineScope()
    var volumes by remember { mutableStateOf<List<StorageVolume>>(emptyList()) }
    var currentDir by remember { mutableStateOf<File?>(null) }
    var childDirs by remember { mutableStateOf<List<File>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var isSaving by remember { mutableStateOf(false) }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    LaunchedEffect(Unit) {
        volumes = loadVolumes()
        isLoading = false
    }

    LaunchedEffect(currentDir) {
        childDirs = withContext(kotlinx.coroutines.Dispatchers.IO) {
            currentDir
                ?.listFiles { file -> file.isDirectory && file.canRead() }
                ?.asSequence()
                ?.sortedBy { it.name.lowercase() }
                ?.toList()
                .orEmpty()
        }
    }

    Scaffold(
        topBar = {
            LargeTopAppBar(
                title = { Text(stringResource(R.string.save_to_arcile_title)) },
                navigationIcon = {
                    IconButton(onClick = {
                        val parent = currentDir?.parentFile
                        if (currentDir != null && parent != null) currentDir = parent else onCancel()
                    }) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                scrollBehavior = scrollBehavior
            )
        },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = resourcesPluralString(R.plurals.save_to_arcile_selected_files, incoming.size, incoming.size),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                    Button(
                        enabled = currentDir != null && !isSaving,
                        onClick = {
                            val destination = currentDir ?: return@Button
                            isSaving = true
                            scope.launch {
                                copyTo(destination)
                                    .onSuccess(onFinished)
                                    .onFailure(onFailed)
                                isSaving = false
                            }
                        }
                    ) {
                        Icon(Icons.Outlined.SaveAlt, contentDescription = null)
                        Spacer(Modifier.size(8.dp))
                        Text(stringResource(R.string.save_to_arcile_save_here))
                    }
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (isLoading || isSaving) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(vertical = 8.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    item {
                        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                            Text(
                                text = currentDir?.absolutePath ?: stringResource(R.string.save_to_arcile_choose_storage),
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = incoming.joinToString(limit = 3) { it.displayName },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        HorizontalDivider()
                    }
                    if (currentDir == null) {
                        items(volumes, key = { it.id }) { volume ->
                            ListItem(
                                headlineContent = { Text(volume.name) },
                                supportingContent = { Text(volume.path, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                leadingContent = { Icon(Icons.Outlined.Storage, contentDescription = null) },
                                modifier = Modifier.clickable { currentDir = File(volume.path) }
                            )
                        }
                    } else {
                        items(childDirs, key = { it.absolutePath }) { directory ->
                            ListItem(
                                headlineContent = { Text(directory.name) },
                                supportingContent = { Text(directory.absolutePath, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                leadingContent = { Icon(Icons.Outlined.Folder, contentDescription = null) },
                                modifier = Modifier.clickable { currentDir = directory }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun resourcesPluralString(id: Int, quantity: Int, vararg args: Any): String {
    val resources = androidx.compose.ui.platform.LocalContext.current.resources
    return resources.getQuantityString(id, quantity, *args)
}
