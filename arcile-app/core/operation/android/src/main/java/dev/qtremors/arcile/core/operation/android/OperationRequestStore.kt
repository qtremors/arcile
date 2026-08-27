package dev.qtremors.arcile.core.operation.android

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.qtremors.arcile.core.operation.BulkFileOperationRequest
import java.io.File
import java.io.FileOutputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Singleton
class OperationRequestStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val directory by lazy { File(context.noBackupFilesDir, STORE_DIRECTORY) }
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val lock = Any()

    fun store(request: BulkFileOperationRequest): Boolean = synchronized(lock) {
        if (!request.isWithinDurableHandoffLimits()) return false
        val encoded = json.encodeToString(request).encodeToByteArray()
        if (encoded.size > MAX_ENCODED_REQUEST_BYTES) return false
        directory.mkdirs()
        val target = pendingFile(request.operationId)
        val temporary = File(directory, "${target.name}.new")
        try {
            FileOutputStream(temporary).use { output ->
                output.write(encoded)
                output.flush()
                output.fd.sync()
            }
            atomicReplace(temporary, target)
            claimedFile(request.operationId).delete()
            true
        } catch (_: Exception) {
            temporary.delete()
            false
        }
    }

    fun claim(operationId: String): BulkFileOperationRequest? = synchronized(lock) {
        val pending = pendingFile(operationId)
        if (!pending.isFile) return null
        val claimed = claimedFile(operationId)
        return try {
            atomicReplace(pending, claimed)
            if (claimed.length() > MAX_ENCODED_REQUEST_BYTES) return null
            json.decodeFromString<BulkFileOperationRequest>(claimed.readText())
                .takeIf { it.operationId == operationId && it.isWithinDurableHandoffLimits() }
        } catch (_: Exception) {
            null
        }
    }

    fun retire(operationId: String) = synchronized(lock) {
        pendingFile(operationId).delete()
        claimedFile(operationId).delete()
    }

    private fun pendingFile(id: String) = File(directory, "${safeId(id)}.json")
    private fun claimedFile(id: String) = File(directory, "${safeId(id)}.claimed")
    private fun safeId(id: String) = id.filter { it.isLetterOrDigit() || it == '-' || it == '_' }.take(80)

    private fun atomicReplace(source: File, target: File) {
        try {
            Files.move(source.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }

    internal fun clearForTest() = synchronized(lock) { directory.deleteRecursively() }

    private companion object {
        const val STORE_DIRECTORY = "operation_requests"
        const val MAX_ENCODED_REQUEST_BYTES = 768 * 1024L
    }
}

internal fun BulkFileOperationRequest.isWithinDurableHandoffLimits(): Boolean {
    if (sourcePaths.size > MAX_OPERATION_SOURCE_ITEMS || importItems.size > MAX_OPERATION_IMPORT_ITEMS) return false
    val values = buildList {
        addAll(sourcePaths)
        destinationPath?.let(::add)
        addAll(resolutions.keys)
        addAll(importItems.flatMap { listOf(it.uri, it.displayName) })
    }
    return values.all { it.length <= MAX_OPERATION_VALUE_CHARS } &&
        values.sumOf(String::length) <= MAX_OPERATION_TOTAL_CHARS
}

internal const val MAX_OPERATION_SOURCE_ITEMS = 4_096
internal const val MAX_OPERATION_IMPORT_ITEMS = 512
private const val MAX_OPERATION_VALUE_CHARS = 4_096
private const val MAX_OPERATION_TOTAL_CHARS = 600_000
