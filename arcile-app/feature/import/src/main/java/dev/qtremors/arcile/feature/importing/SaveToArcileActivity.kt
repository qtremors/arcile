package dev.qtremors.arcile.feature.importing

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
import androidx.compose.material.icons.Icons
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import dagger.hilt.android.AndroidEntryPoint
import dev.qtremors.arcile.core.operation.BulkFileOperationCoordinator
import dev.qtremors.arcile.core.operation.SaveToArcileImportItem
import dev.qtremors.arcile.core.operation.android.FREE_SPACE_SAFETY_BUFFER_BYTES
import dev.qtremors.arcile.core.operation.android.MAX_IMPORT_BYTES
import dev.qtremors.arcile.core.operation.android.MAX_IMPORT_ITEMS
import dev.qtremors.arcile.core.operation.android.STREAM_BUFFER_SIZE
import dev.qtremors.arcile.core.operation.android.sanitizeIncomingFileName
import dev.qtremors.arcile.core.storage.domain.BrowserPreferencesStore
import dev.qtremors.arcile.core.storage.domain.StorageVolume
import dev.qtremors.arcile.core.storage.domain.VolumeRepository
import dev.qtremors.arcile.core.ui.R
import dev.qtremors.arcile.ui.theme.ArcileTheme
import dev.qtremors.arcile.ui.theme.ThemeState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
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
