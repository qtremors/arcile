package dev.qtremors.arcile.core.storage.data.manager

import dev.qtremors.arcile.core.storage.data.CachedFileModel
import dev.qtremors.arcile.core.storage.data.CachedStorageNodeRef
import dev.qtremors.arcile.core.storage.data.rethrowIfCancellation
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID

@Serializable
internal data class PrivilegedTrashRecord(
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    val id: String,
    val originalNode: CachedStorageNodeRef,
    val payloadNode: CachedStorageNodeRef,
    val originalFile: CachedFileModel,
    val deletionTime: Long,
    val sourceVolumeId: String,
    val sourceStorageKind: String
) {
    init {
        require(schemaVersion in 1..CURRENT_SCHEMA_VERSION)
        require(id.isNotBlank() && id.none { it == '/' || it == '\\' || it == '\u0000' })
    }

    companion object {
        const val CURRENT_SCHEMA_VERSION = 1
    }
}

/** App-private metadata keeps privileged paths out of externally writable sidecars. */
internal class PrivilegedTrashMetadataStore(
    private val directory: File,
    private val json: Json = Json { ignoreUnknownKeys = true }
) {
    fun write(record: PrivilegedTrashRecord) {
        check(directory.mkdirs() || directory.isDirectory) {
            "Unable to create privileged trash metadata directory"
        }
        val destination = fileFor(record.id)
        val temporary = File(directory, ".${record.id}.${UUID.randomUUID()}.tmp")
        try {
            temporary.writeText(json.encodeToString(record), Charsets.UTF_8)
            moveAtomically(temporary, destination)
        } finally {
            if (temporary.exists()) temporary.delete()
        }
    }

    fun read(id: String): PrivilegedTrashRecord? = readFile(fileFor(id))

    fun list(): List<PrivilegedTrashRecord> {
        if (!directory.isDirectory) return emptyList()
        return directory.listFiles()
            .orEmpty()
            .asSequence()
            .filter { it.isFile && it.extension == METADATA_EXTENSION }
            .mapNotNull(::readFile)
            .sortedByDescending(PrivilegedTrashRecord::deletionTime)
            .toList()
    }

    fun delete(id: String): Boolean {
        val file = fileFor(id)
        return !file.exists() || file.delete()
    }

    fun clearMissing(validIds: Set<String>) {
        if (!directory.isDirectory) return
        directory.listFiles().orEmpty().forEach { file ->
            if (file.isFile && file.extension == METADATA_EXTENSION &&
                file.nameWithoutExtension !in validIds
            ) {
                file.delete()
            }
        }
    }

    private fun readFile(file: File): PrivilegedTrashRecord? {
        if (!file.isFile) return null
        return try {
            json.decodeFromString<PrivilegedTrashRecord>(file.readText(Charsets.UTF_8))
        } catch (error: Throwable) {
            error.rethrowIfCancellation()
            null
        }
    }

    private fun fileFor(id: String): File {
        require(id.isNotBlank() && id.none { it == '/' || it == '\\' || it == '\u0000' })
        return File(directory, "$id.$METADATA_EXTENSION")
    }

    private fun moveAtomically(source: File, destination: File) {
        try {
            Files.move(
                source.toPath(),
                destination.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING
            )
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(
                source.toPath(),
                destination.toPath(),
                StandardCopyOption.REPLACE_EXISTING
            )
        }
    }

    private companion object {
        const val METADATA_EXTENSION = "json"
    }
}
