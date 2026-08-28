package dev.qtremors.arcile.core.storage.data

import android.content.Context
import androidx.core.content.edit
import dev.qtremors.arcile.core.storage.data.provider.VolumeProvider
import dev.qtremors.arcile.core.runtime.di.ArcileDispatchers
import dev.qtremors.arcile.core.runtime.logging.AppLogger
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

interface MutationJournal {
    fun recordTemporaryPath(path: String)
    fun forgetTemporaryPath(path: String)
    fun recordTrashFallback(sourcePath: String, payloadPath: String, metadataPath: String)
    fun forgetTrashFallback(payloadPath: String, metadataPath: String)
    fun recordSourceCleanup(sourcePath: String, destinationPath: String) = Unit
    fun forgetSourceCleanup(sourcePath: String, destinationPath: String) = Unit
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
    private val context: Context,
    private val volumeProvider: VolumeProvider,
    private val dispatchers: ArcileDispatchers
) : MutationJournal {
    private val store by lazy { storeFile(context) }
    private val lock = Any()
    private var legacyPreferencesCleaned = false
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private fun ensureLegacyPreferencesCleaned() {
        if (!legacyPreferencesCleaned) {
            legacyPreferencesCleaned = true
            runCatchingPreservingCancellation {
                context.getSharedPreferences(LEGACY_PREFERENCES, Context.MODE_PRIVATE).edit { clear() }
            }
        }
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

    override fun recordSourceCleanup(sourcePath: String, destinationPath: String) {
        updateEntries { entries ->
            entries.filterNot {
                it.type == EntryType.SOURCE_CLEANUP &&
                    it.sourcePath == sourcePath &&
                    it.destinationPath == destinationPath
            } + MutationJournalEntry(
                type = EntryType.SOURCE_CLEANUP,
                sourcePath = sourcePath,
                destinationPath = destinationPath
            )
        }
    }

    override fun forgetSourceCleanup(sourcePath: String, destinationPath: String) {
        updateEntries { entries ->
            entries.filterNot {
                it.type == EntryType.SOURCE_CLEANUP &&
                    it.sourcePath == sourcePath &&
                    it.destinationPath == destinationPath
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
                    EntryType.SOURCE_CLEANUP -> cleanupSourceCleanup(entry, roots, remaining)
                }
            } catch (e: Exception) {
                e.rethrowIfCancellation()
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

    private suspend fun cleanupSourceCleanup(
        entry: MutationJournalEntry,
        roots: List<File>,
        remaining: MutableList<MutationJournalEntry>
    ) {
        val source = entry.sourcePath?.let(::File) ?: return
        val destination = entry.destinationPath?.let(::File) ?: return
        if (!source.exists()) return
        if (!destination.exists() || !isWithinRoots(source, roots) || !isWithinRoots(destination, roots)) {
            remaining += entry
            return
        }

        val result = deleteSourceTree(source)
        if (!result.isComplete) remaining += entry
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
        synchronized(lock) {
            writeEntries(transform(readEntries()))
        }
    }

    private fun readEntries(): List<MutationJournalEntry> {
        ensureLegacyPreferencesCleaned()
        if (!store.exists()) return emptyList()
        if (store.length() > MAX_STORE_BYTES) {
            AppLogger.w(TAG, "Dropping oversized mutation journal")
            store.delete()
            return emptyList()
        }
        return runCatchingPreservingCancellation {
            store.bufferedReader().use { reader ->
                json.decodeFromString<List<MutationJournalEntry>>(reader.readText())
            }.takeLast(MAX_ENTRIES)
        }
            .getOrElse {
                AppLogger.w(TAG, "Dropping unreadable mutation journal", it)
                store.delete()
                emptyList()
            }
    }

    private fun writeEntries(entries: List<MutationJournalEntry>) {
        ensureLegacyPreferencesCleaned()
        var bounded = entries.takeLast(MAX_ENTRIES)
        var encoded = json.encodeToString(bounded).encodeToByteArray()
        while (encoded.size > MAX_STORE_BYTES && bounded.isNotEmpty()) {
            bounded = bounded.drop(1)
            encoded = json.encodeToString(bounded).encodeToByteArray()
        }
        if (bounded.isEmpty()) {
            store.delete()
            return
        }
        store.parentFile?.mkdirs()
        val pending = File(store.parentFile, "${store.name}.new")
        try {
            FileOutputStream(pending).use { output ->
                output.write(encoded)
                output.flush()
                output.fd.sync()
            }
            try {
                Files.move(
                    pending.toPath(),
                    store.toPath(),
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING
                )
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(pending.toPath(), store.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        } finally {
            pending.delete()
        }
    }

    companion object {
        private const val TAG = "MutationJournal"
        private const val LEGACY_PREFERENCES = "mutation_journal"
        private const val STORE_FILE = "mutation_journal.json"
        private const val MAX_STORE_BYTES = 512 * 1024
        private const val MAX_ENTRIES = 512

        internal fun storeFile(context: Context) = File(context.noBackupFilesDir, STORE_FILE)
        internal fun clearForTest(context: Context) {
            val file = storeFile(context)
            file.delete()
            File(file.parentFile, "${file.name}.new").delete()
        }
    }
}

@Serializable
private data class MutationJournalEntry(
    val type: EntryType,
    val path: String? = null,
    val sourcePath: String? = null,
    val destinationPath: String? = null,
    val payloadPath: String? = null,
    val metadataPath: String? = null
)

@Serializable
private enum class EntryType {
    TEMPORARY_PATH,
    TRASH_FALLBACK,
    SOURCE_CLEANUP
}
