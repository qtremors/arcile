package dev.qtremors.arcile.core.storage.data

import android.content.Context
import dev.qtremors.arcile.core.storage.data.provider.VolumeProvider
import dev.qtremors.arcile.core.runtime.di.ArcileDispatchers
import dev.qtremors.arcile.core.runtime.logging.AppLogger
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

interface MutationJournal {
    fun recordTemporaryPath(path: String)
    fun forgetTemporaryPath(path: String)
    fun recordTrashFallback(sourcePath: String, payloadPath: String, metadataPath: String)
    fun forgetTrashFallback(payloadPath: String, metadataPath: String)
    suspend fun cleanupAbandonedMutations()
}

class NoOpMutationJournal : MutationJournal {
    override fun recordTemporaryPath(path: String) = Unit
    override fun forgetTemporaryPath(path: String) = Unit
    override fun recordTrashFallback(sourcePath: String, payloadPath: String, metadataPath: String) = Unit
    override fun forgetTrashFallback(payloadPath: String, metadataPath: String) = Unit
    override suspend fun cleanupAbandonedMutations() = Unit
}

class DefaultMutationJournal(
    context: Context,
    private val volumeProvider: VolumeProvider,
    private val dispatchers: ArcileDispatchers
) : MutationJournal {
    private val preferences = context.getSharedPreferences("mutation_journal", Context.MODE_PRIVATE)
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    override fun recordTemporaryPath(path: String) {
        updateEntries { entries ->
            entries.filterNot { it.path == path } + MutationJournalEntry(type = EntryType.TEMPORARY_PATH, path = path)
        }
    }

    override fun forgetTemporaryPath(path: String) {
        updateEntries { entries -> entries.filterNot { it.type == EntryType.TEMPORARY_PATH && it.path == path } }
    }

    override fun recordTrashFallback(sourcePath: String, payloadPath: String, metadataPath: String) {
        updateEntries { entries ->
            entries.filterNot {
                it.type == EntryType.TRASH_FALLBACK &&
                    it.payloadPath == payloadPath &&
                    it.metadataPath == metadataPath
            } + MutationJournalEntry(
                type = EntryType.TRASH_FALLBACK,
                sourcePath = sourcePath,
                payloadPath = payloadPath,
                metadataPath = metadataPath
            )
        }
    }

    override fun forgetTrashFallback(payloadPath: String, metadataPath: String) {
        updateEntries { entries ->
            entries.filterNot {
                it.type == EntryType.TRASH_FALLBACK &&
                    it.payloadPath == payloadPath &&
                    it.metadataPath == metadataPath
            }
        }
    }

    override suspend fun cleanupAbandonedMutations() = withContext(dispatchers.io) {
        val roots = volumeProvider.activeStorageRoots.map { File(it).canonicalFile }
        val remaining = mutableListOf<MutationJournalEntry>()
        for (entry in readEntries()) {
            try {
                when (entry.type) {
                    EntryType.TEMPORARY_PATH -> cleanupTemporaryPath(entry, roots, remaining)
                    EntryType.TRASH_FALLBACK -> cleanupTrashFallback(entry, roots, remaining)
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                AppLogger.w("MutationJournal", "Failed to clean abandoned mutation entry", e)
                remaining += entry
            }
        }
        writeEntries(remaining)
    }

    private fun cleanupTemporaryPath(
        entry: MutationJournalEntry,
        roots: List<File>,
        remaining: MutableList<MutationJournalEntry>
    ) {
        val file = entry.path?.let(::File) ?: return
        if (!file.exists()) return
        if (!isKnownTemporaryName(file.name) || !isWithinRoots(file, roots)) {
            remaining += entry
            return
        }
        deleteFileOrDirectory(file)
    }

    private fun cleanupTrashFallback(
        entry: MutationJournalEntry,
        roots: List<File>,
        remaining: MutableList<MutationJournalEntry>
    ) {
        val sourcePath = entry.sourcePath ?: return
        val payload = entry.payloadPath?.let(::File) ?: return
        val metadata = entry.metadataPath?.let(::File) ?: return
        if (!isWithinRoots(payload, roots) || !isWithinRoots(metadata, roots)) {
            remaining += entry
            return
        }

        if (File(sourcePath).exists()) {
            deleteFileOrDirectory(payload)
            metadata.delete()
        }
    }

    private fun deleteFileOrDirectory(file: File) {
        if (file.isDirectory) file.deleteRecursively() else file.delete()
    }

    private fun isKnownTemporaryName(name: String): Boolean {
        return name.contains(".arcile-transfer-") ||
            name.contains(".arcile-replace-") ||
            name.contains(".arcile-archive-") ||
            name.contains(".arcile-import-")
    }

    private fun isWithinRoots(file: File, roots: List<File>): Boolean {
        val canonical = file.canonicalFile
        return roots.any { root ->
            canonical == root || canonical.path.startsWith("${root.path}${File.separator}")
        }
    }

    private fun updateEntries(transform: (List<MutationJournalEntry>) -> List<MutationJournalEntry>) {
        synchronized(preferences) {
            writeEntries(transform(readEntries()))
        }
    }

    private fun readEntries(): List<MutationJournalEntry> {
        val encoded = preferences.getString(KEY_ENTRIES, null) ?: return emptyList()
        return runCatching { json.decodeFromString<List<MutationJournalEntry>>(encoded) }
            .getOrElse {
                AppLogger.w("MutationJournal", "Dropping unreadable mutation journal", it)
                emptyList()
            }
    }

    private fun writeEntries(entries: List<MutationJournalEntry>) {
        preferences.edit()
            .putString(KEY_ENTRIES, json.encodeToString(entries))
            .apply()
    }

    private companion object {
        const val KEY_ENTRIES = "entries"
    }
}

@Serializable
private data class MutationJournalEntry(
    val type: EntryType,
    val path: String? = null,
    val sourcePath: String? = null,
    val payloadPath: String? = null,
    val metadataPath: String? = null
)

@Serializable
private enum class EntryType {
    TEMPORARY_PATH,
    TRASH_FALLBACK
}
