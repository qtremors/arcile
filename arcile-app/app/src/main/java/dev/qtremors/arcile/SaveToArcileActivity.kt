package dev.qtremors.arcile

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.database.Cursor
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
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dagger.hilt.android.AndroidEntryPoint
import dev.qtremors.arcile.core.operation.BulkFileOperationCoordinator
import dev.qtremors.arcile.core.operation.SaveToArcileImportItem
import dev.qtremors.arcile.core.storage.domain.BrowserPreferencesStore
import dev.qtremors.arcile.core.storage.domain.StorageVolume
import dev.qtremors.arcile.core.storage.domain.VolumeRepository
import dev.qtremors.arcile.core.ui.R
import dev.qtremors.arcile.ui.theme.ArcileTheme
import dev.qtremors.arcile.ui.theme.ExpressiveShapes
import dev.qtremors.arcile.ui.theme.ThemeState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.io.InputStream
import javax.inject.Inject

@AndroidEntryPoint
class SaveToArcileActivity : ComponentActivity() {

    @Inject
    lateinit var volumeRepository: VolumeRepository

    @Inject
    lateinit var bulkFileOperationCoordinator: BulkFileOperationCoordinator

    @Inject
    lateinit var browserPreferencesStore: BrowserPreferencesStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val preflight = IncomingShareReader.preflightFromIntent(this, intent)
        if (preflight.accepted.isEmpty()) {
            Toast.makeText(
                this,
                preflight.messageOrDefault(getString(R.string.save_to_arcile_no_files)),
                Toast.LENGTH_LONG
            ).show()
            finish()
            return
        }

        setContent {
            ArcileTheme(themeState = ThemeState()) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    SaveToArcileScreen(
                        incoming = preflight.accepted,
                        loadVolumes = { volumeRepository.getStorageVolumes().getOrElse { emptyList() } },
                        loadDefaultPath = { browserPreferencesStore.preferencesFlow.first().defaultSaveToArcilePath },
                        saveDefaultPath = { browserPreferencesStore.updateDefaultSaveToArcilePath(it) },
                        copyTo = { destination -> enqueueIncomingImport(destination, preflight.accepted) },
                        onCancel = { finish() },
                        onDefaultSaved = {
                            Toast.makeText(
                                this,
                                getString(R.string.save_to_arcile_default_saved),
                                Toast.LENGTH_SHORT
                            ).show()
                        },
                        onFinished = { result ->
                            Toast.makeText(this, result.userMessage(this), Toast.LENGTH_LONG).show()
                            if (result.queued || result.savedCount > 0 || result.failures.isEmpty()) finish()
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

    private fun enqueueIncomingImport(destination: File, incoming: List<IncomingSharedFile>): Result<SaveIncomingResult> {
        persistReadableUriPermissions(incoming)
        val started = bulkFileOperationCoordinator.startImportOperation(
            destinationPath = destination.absolutePath,
            importItems = incoming.map {
                SaveToArcileImportItem(
                    uri = it.uri.toString(),
                    displayName = it.displayName,
                    sizeBytes = it.sizeBytes,
                    requiresCountedStream = it.requiresCountedStream
                )
            }
        )
        return if (started) {
            Result.success(SaveIncomingResult(savedCount = 0, failures = emptyList(), queued = true))
        } else {
            Result.failure(IllegalStateException(getString(R.string.file_operation_already_running)))
        }
    }

    private fun persistReadableUriPermissions(incoming: List<IncomingSharedFile>) {
        val persistableRead = intent.flags and
            (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        if (persistableRead !=
            (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        ) {
            return
        }
        incoming.forEach { item ->
            runCatching {
                contentResolver.takePersistableUriPermission(
                    item.uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
        }
    }

}

data class IncomingSharedFile(
    val uri: Uri,
    val displayName: String,
    val sizeBytes: Long? = null,
    val originalName: String? = null,
    val requiresCountedStream: Boolean = false
)

data class IncomingSharePreflightResult(
    val accepted: List<IncomingSharedFile>,
    val rejected: List<IncomingShareFailure>,
    val limitExceeded: Boolean = false
) {
    fun messageOrDefault(defaultMessage: String): String =
        rejected.firstOrNull()?.message ?: defaultMessage
}

data class IncomingShareFailure(
    val uri: Uri?,
    val displayName: String?,
    val reason: IncomingShareFailureReason,
    val message: String
)

enum class IncomingShareFailureReason {
    UnsupportedScheme,
    ExternalFileUri,
    InvalidName,
    TooManyItems,
    TooLarge,
    CopyFailed
}

data class SaveIncomingResult(
    val savedCount: Int,
    val failures: List<IncomingShareFailure>,
    val queued: Boolean = false
) {
    fun userMessage(context: Context): String =
        when {
            queued -> context.getString(R.string.save_to_arcile_import_started)
            savedCount > 0 && failures.isEmpty() -> context.resources.getQuantityString(
                R.plurals.save_to_arcile_saved_files,
                savedCount,
                savedCount
            )
            savedCount > 0 -> context.getString(R.string.save_to_arcile_partial_saved, savedCount, failures.size)
            else -> context.getString(
                R.string.save_to_arcile_failed,
                failures.firstOrNull()?.message.orEmpty()
            )
        }
}

data class CopyResult(val bytesCopied: Long)

suspend fun saveIncomingFiles(
    destination: File,
    incoming: List<IncomingSharedFile>,
    openInputStream: (Uri) -> InputStream?,
    finalizeDestination: suspend (String) -> Unit,
    invalidDestinationMessage: String,
    insufficientSpaceMessage: String,
    failedOpenStreamMessage: String,
    usableSpaceProvider: (File) -> Long = { it.usableSpace }
): Result<SaveIncomingResult> {
    require(destination.exists() && destination.isDirectory && destination.canWrite()) {
        invalidDestinationMessage
    }
    val knownBytes = incoming.sumOf { it.sizeBytes ?: 0L }
    val usableBytes = usableSpaceProvider(destination)
    require(usableBytes <= 0L || usableBytes >= knownBytes + FREE_SPACE_SAFETY_BUFFER_BYTES) {
        insufficientSpaceMessage
    }
    var importedBytes = 0L
    val successes = mutableListOf<String>()
    val failures = mutableListOf<IncomingShareFailure>()
    incoming.forEach { item ->
        val target = keepBothTarget(destination, item.displayName)
        try {
            openInputStream(item.uri).use { input ->
                requireNotNull(input) { failedOpenStreamMessage }
                target.outputStream().buffered().use { output ->
                    val result = copyWithByteLimit(
                        input = input,
                        output = output,
                        remainingBudget = MAX_IMPORT_BYTES - importedBytes
                    )
                    importedBytes += result.bytesCopied
                }
            }
            successes += target.absolutePath
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            runCatching { target.delete() }
            failures += IncomingShareFailure(
                uri = item.uri,
                displayName = item.displayName,
                reason = IncomingShareFailureReason.CopyFailed,
                message = e.message ?: failedOpenStreamMessage
            )
        }
    }
    if (successes.isNotEmpty()) {
        finalizeDestination(destination.absolutePath)
    }
    return Result.success(SaveIncomingResult(successes.size, failures))
}

object IncomingShareReader {
    fun fromIntent(context: Context, intent: Intent): List<IncomingSharedFile> =
        preflightFromIntent(context, intent).accepted

    fun preflightFromIntent(context: Context, intent: Intent): IncomingSharePreflightResult {
        val uris = buildList {
            intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)?.let(::add)
            intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)?.let(::addAll)
            collectClipData(intent.clipData).forEach(::add)
        }.distinct()
        val accepted = mutableListOf<IncomingSharedFile>()
        val rejected = mutableListOf<IncomingShareFailure>()
        if (uris.size > MAX_IMPORT_ITEMS) {
            rejected += IncomingShareFailure(
                uri = null,
                displayName = null,
                reason = IncomingShareFailureReason.TooManyItems,
                message = context.getString(R.string.save_to_arcile_too_many_files, MAX_IMPORT_ITEMS)
            )
            return IncomingSharePreflightResult(emptyList(), rejected, limitExceeded = true)
        }
        var knownBytes = 0L
        uris.forEach { uri ->
            val metadata = resolveMetadata(context, uri)
            val originalName = metadata.name ?: uri.lastPathSegment?.substringAfterLast('/') ?: "shared-file"
            val schemeFailure = validateUriScheme(context, uri)
            if (schemeFailure != null) {
                rejected += schemeFailure.copy(displayName = originalName)
                return@forEach
            }
            val size = metadata.size?.takeIf { it >= 0L }
            if (size != null) {
                knownBytes += size
                if (knownBytes > MAX_IMPORT_BYTES) {
                    rejected += IncomingShareFailure(
                        uri = uri,
                        displayName = originalName,
                        reason = IncomingShareFailureReason.TooLarge,
                        message = context.getString(R.string.save_to_arcile_too_large)
                    )
                    return@forEach
                }
            }
            val existingNames = accepted.map { it.displayName }.toSet()
            val displayName = sanitizeIncomingFileName(originalName, fallbackExtension(uri), existingNames)
            accepted += IncomingSharedFile(
                uri = uri,
                displayName = displayName,
                sizeBytes = size,
                originalName = originalName,
                requiresCountedStream = size == null
            )
        }
        return IncomingSharePreflightResult(accepted, rejected, limitExceeded = rejected.any { it.reason == IncomingShareFailureReason.TooLarge })
    }

    private fun collectClipData(clipData: ClipData?): List<Uri> {
        if (clipData == null) return emptyList()
        return (0 until clipData.itemCount).mapNotNull { index -> clipData.getItemAt(index).uri }
    }

    private fun validateUriScheme(context: Context, uri: Uri): IncomingShareFailure? =
        when (uri.scheme?.lowercase()) {
            "content" -> null
            "file" -> if (isAppOwnedFileUri(context, uri)) {
                null
            } else {
                IncomingShareFailure(
                    uri = uri,
                    displayName = null,
                    reason = IncomingShareFailureReason.ExternalFileUri,
                    message = context.getString(R.string.save_to_arcile_external_file_uri)
                )
            }
            else -> IncomingShareFailure(
                uri = uri,
                displayName = null,
                reason = IncomingShareFailureReason.UnsupportedScheme,
                message = context.getString(R.string.save_to_arcile_unsupported_source)
            )
        }

    private fun isAppOwnedFileUri(context: Context, uri: Uri): Boolean {
        val path = uri.path ?: return false
        val source = runCatching { File(path).canonicalFile }.getOrNull() ?: return false
        return appOwnedRoots(context).any { root ->
            val canonicalRoot = runCatching { root.canonicalFile }.getOrNull() ?: return@any false
            source == canonicalRoot || source.absolutePath.startsWith(canonicalRoot.absolutePath + File.separator)
        }
    }

    private fun appOwnedRoots(context: Context): List<File> =
        listOfNotNull(
            context.filesDir,
            context.cacheDir,
            context.externalCacheDir,
            context.getExternalFilesDir(null)
        )

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
                val metadata = cursor.readOpenableMetadata()
                name = metadata.name
                size = metadata.size
            }
        }
        if (uri.scheme?.lowercase() == "file") {
            uri.path?.let { path ->
                val file = File(path)
                if (name.isNullOrBlank()) name = file.name
                if (size == null && file.exists()) size = file.length()
            }
        }
        return IncomingMetadata(name?.takeIf { it.isNotBlank() }, size)
    }

    private data class IncomingMetadata(val name: String?, val size: Long?)
}

private fun Cursor.readOpenableMetadata(): AnyIncomingMetadata {
    var name: String? = null
    var size: Long? = null
    if (moveToFirst()) {
        val nameIndex = getColumnIndex(OpenableColumns.DISPLAY_NAME)
        val sizeIndex = getColumnIndex(OpenableColumns.SIZE)
        if (nameIndex >= 0) name = getString(nameIndex)
        if (sizeIndex >= 0 && !isNull(sizeIndex)) size = getLong(sizeIndex)
    }
    return AnyIncomingMetadata(name, size)
}

private data class AnyIncomingMetadata(val name: String?, val size: Long?)

fun sanitizeIncomingFileName(rawName: String?, fallbackExtension: String? = null, existingNames: Set<String> = emptySet()): String {
    val cleaned = rawName.orEmpty()
        .filterNot { it.isISOControl() }
        .trim()
        .replace(Regex("""[/\\:*?"<>|]"""), "_")
        .replace(Regex("""\s+"""), " ")
    val withoutTraversal = cleaned
        .split('.', '/', '\\')
        .joinToString(".")
        .replace("..", ".")
        .trim('.', '_', ' ')
    val base = withoutTraversal.takeIf { it.isNotBlank() && it != "." && it != ".." } ?: "shared-file"
    val withReservedFallback = if (base.substringBefore('.').uppercase() in WINDOWS_RESERVED_NAMES) {
        "shared-file${base.substringAfter('.', missingDelimiterValue = "").takeIf { it.isNotBlank() }?.let { ".$it" }.orEmpty()}"
    } else {
        base
    }
    val withExtension = if (!fallbackExtension.isNullOrBlank() && !withReservedFallback.contains('.')) {
        "$withReservedFallback.$fallbackExtension"
    } else {
        withReservedFallback
    }
    val limited = limitFileNameLength(withExtension, MAX_FILE_NAME_CHARS)
    return uniqueName(limited, existingNames)
}

private fun keepBothTarget(destination: File, requestedName: String): File {
    val safeName = sanitizeIncomingFileName(requestedName)
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

fun copyWithByteLimit(input: InputStream, output: java.io.OutputStream, remainingBudget: Long): CopyResult {
    if (remainingBudget <= 0L) throw IOException("Shared import exceeds the 10 GB limit")
    val buffer = ByteArray(STREAM_BUFFER_SIZE)
    var copied = 0L
    while (true) {
        val read = input.read(buffer)
        if (read == -1) break
        copied += read
        if (copied > remainingBudget) {
            throw IOException("Shared import exceeds the 10 GB limit")
        }
        output.write(buffer, 0, read)
    }
    return CopyResult(copied)
}

private fun fallbackExtension(uri: Uri): String? =
    uri.lastPathSegment
        ?.substringAfterLast('/', missingDelimiterValue = "")
        ?.substringAfterLast('.', missingDelimiterValue = "")
        ?.takeIf { it.isNotBlank() && it.length <= 16 && it.all { char -> char.isLetterOrDigit() } }

private fun limitFileNameLength(name: String, maxLength: Int): String {
    if (name.length <= maxLength) return name
    val extension = name.substringAfterLast('.', missingDelimiterValue = "")
        .takeIf { it.isNotBlank() && it.length < 32 }
        ?.let { ".$it" }
        .orEmpty()
    val baseLimit = (maxLength - extension.length).coerceAtLeast(1)
    return name.substringBeforeLast('.', missingDelimiterValue = name).take(baseLimit) + extension
}

private fun uniqueName(name: String, existingNames: Set<String>): String {
    if (existingNames.none { it.equals(name, ignoreCase = true) }) return name
    val extension = name.substringAfterLast('.', missingDelimiterValue = "")
        .takeIf { it.isNotBlank() }
        ?.let { ".$it" }
        .orEmpty()
    val base = name.substringBeforeLast('.', missingDelimiterValue = name)
    var index = 1
    while (true) {
        val suffix = " ($index)"
        val candidateBase = limitFileNameLength(base, MAX_FILE_NAME_CHARS - suffix.length - extension.length)
        val candidate = "$candidateBase$suffix$extension"
        if (existingNames.none { it.equals(candidate, ignoreCase = true) }) return candidate
        index += 1
    }
}

private val WINDOWS_RESERVED_NAMES = setOf(
    "CON",
    "PRN",
    "AUX",
    "NUL",
    "COM1",
    "COM2",
    "COM3",
    "COM4",
    "COM5",
    "COM6",
    "COM7",
    "COM8",
    "COM9",
    "LPT1",
    "LPT2",
    "LPT3",
    "LPT4",
    "LPT5",
    "LPT6",
    "LPT7",
    "LPT8",
    "LPT9"
)

private const val MAX_FILE_NAME_CHARS = 255
const val STREAM_BUFFER_SIZE = 128 * 1024
const val MAX_IMPORT_ITEMS = 200
const val MAX_IMPORT_BYTES = 10L * 1024L * 1024L * 1024L
const val FREE_SPACE_SAFETY_BUFFER_BYTES = 50L * 1024L * 1024L

internal fun resolveInitialSaveToArcileDirectory(
    defaultPath: String?,
    volumes: List<StorageVolume>
): File? {
    val defaultDirectory = defaultPath
        ?.takeIf { it.isNotBlank() }
        ?.let(::File)
        ?: return null
    return defaultDirectory.takeIf { isValidSaveToArcileDirectory(it, volumes) }
}

internal fun isValidSaveToArcileDirectory(
    directory: File,
    volumes: List<StorageVolume>
): Boolean {
    if (!directory.exists() || !directory.isDirectory || !directory.canRead() || !directory.canWrite()) {
        return false
    }
    val canonicalDirectory = runCatching { directory.canonicalFile }.getOrNull() ?: return false
    return volumes.any { volume ->
        val canonicalVolume = runCatching { File(volume.path).canonicalFile }.getOrNull() ?: return@any false
        canonicalDirectory == canonicalVolume ||
            canonicalDirectory.absolutePath.startsWith(canonicalVolume.absolutePath + File.separator)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SaveToArcileScreen(
    incoming: List<IncomingSharedFile>,
    loadVolumes: suspend () -> List<StorageVolume>,
    loadDefaultPath: suspend () -> String?,
    saveDefaultPath: suspend (String) -> Unit,
    copyTo: suspend (File) -> Result<SaveIncomingResult>,
    onCancel: () -> Unit,
    onDefaultSaved: () -> Unit,
    onFinished: (SaveIncomingResult) -> Unit,
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
        val loadedVolumes = loadVolumes()
        volumes = loadedVolumes
        currentDir = resolveInitialSaveToArcileDirectory(loadDefaultPath(), loadedVolumes)
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
                    IconButton(
                        onClick = {
                            val parent = currentDir?.parentFile
                            if (currentDir != null && parent != null) currentDir = parent else onCancel()
                        },
                        modifier = Modifier.clip(CircleShape)
                    ) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                scrollBehavior = scrollBehavior
            )
        },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = resourcesPluralString(R.plurals.save_to_arcile_selected_files, incoming.size, incoming.size),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val selectedDirectory = currentDir
                        val canUseSelectedDirectory = selectedDirectory != null &&
                            isValidSaveToArcileDirectory(selectedDirectory, volumes)
                        if (selectedDirectory != null) {
                            TextButton(
                                enabled = !isSaving && canUseSelectedDirectory,
                                shape = ExpressiveShapes.medium,
                                onClick = {
                                    scope.launch {
                                        saveDefaultPath(selectedDirectory.absolutePath)
                                        onDefaultSaved()
                                    }
                                }
                            ) {
                                Text(stringResource(R.string.save_to_arcile_set_default))
                            }
                            Spacer(Modifier.size(8.dp))
                        }
                        Button(
                            enabled = !isSaving && canUseSelectedDirectory,
                            shape = ExpressiveShapes.medium,
                            onClick = {
                                val destination = selectedDirectory?.takeIf {
                                    isValidSaveToArcileDirectory(it, volumes)
                                } ?: return@Button
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
                                modifier = Modifier
                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                                    .clip(ExpressiveShapes.medium)
                                    .clickable { currentDir = File(volume.path) }
                            )
                        }
                    } else {
                        items(childDirs, key = { it.absolutePath }) { directory ->
                            ListItem(
                                headlineContent = { Text(directory.name) },
                                supportingContent = { Text(directory.absolutePath, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                leadingContent = { Icon(Icons.Outlined.Folder, contentDescription = null) },
                                modifier = Modifier
                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                                    .clip(ExpressiveShapes.medium)
                                    .clickable { currentDir = directory }
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
