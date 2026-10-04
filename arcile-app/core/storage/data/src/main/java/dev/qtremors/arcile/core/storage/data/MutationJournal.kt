package dev.qtremors.arcile.core.storage.data

import android.content.Context
import androidx.core.content.edit
import dev.qtremors.arcile.core.storage.data.provider.VolumeProvider
import dev.qtremors.arcile.core.runtime.di.ArcileDispatchers
import dev.qtremors.arcile.core.runtime.logging.AppLogger
import kotlinx.coroutines.withContext
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import dev.qtremors.arcile.core.runtime.ProcessOwnership

interface MutationJournal {
    fun recordTemporaryPath(path: String)
    fun forgetTemporaryPath(path: String)
    fun recordTrashFallback(sourcePath: String, payloadPath: String, metadataPath: String)
    fun forgetTrashFallback(payloadPath: String, metadataPath: String)
    fun recordSourceCleanup(sourcePath: String, destinationPath: String) = Unit
    fun forgetSourceCleanup(sourcePath: String, destinationPath: String) = Unit
    fun recordReplacement(targetPath: String, stagingPath: String, backupPath: String, sourcePath: String? = null) = Unit
    fun markReplacementPublished(targetPath: String, backupPath: String) = Unit
    fun forgetReplacement(targetPath: String, backupPath: String) = Unit
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
    private val dispatchers: ArcileDispatchers,
    private val rename: (File, File) -> Boolean = { from, to -> from.renameTo(to) },
    private val recoveryFileKey: ((File) -> String?)? = null,
    private val ownerToken: String? = ProcessOwnership.token,
    private val ownerIsAlive: (String) -> Boolean = ProcessOwnership::isAlive
) : MutationJournal {
    private val store by lazy { storeFile(context) }
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
            entries.filterNot { it.path == path } + MutationJournalEntry(type = EntryType.TEMPORARY_PATH, path = path, owner = ownerToken)
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
                owner = ownerToken,
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
        val snapshot = SourceCleanupSnapshot.capture(File(sourcePath), File(destinationPath), recoveryFileKey)
        updateEntries { entries ->
            entries.map {
                if (it.sourcePath == sourcePath &&
                    ((it.type == EntryType.TRASH_FALLBACK && it.payloadPath == destinationPath) ||
                        (it.type == EntryType.REPLACEMENT && it.destinationPath == destinationPath))
                ) it.copy(sourceCleanupStarted = true) else it
            }.filterNot {
                it.type == EntryType.SOURCE_CLEANUP &&
                    it.sourcePath == sourcePath &&
                    it.destinationPath == destinationPath
            } + MutationJournalEntry(
                type = EntryType.SOURCE_CLEANUP,
                owner = ownerToken,
                sourcePath = sourcePath,
                destinationPath = destinationPath,
                cleanupSnapshot = snapshot
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

    override fun recordReplacement(targetPath: String, stagingPath: String, backupPath: String, sourcePath: String?) {
        val original = MutationTreeSnapshot.capture(File(targetPath), recoveryFileKey)
        val published = MutationTreeSnapshot.capture(File(stagingPath), recoveryFileKey)
        updateEntries { entries ->
            entries + MutationJournalEntry(
                type = EntryType.REPLACEMENT, destinationPath = targetPath,
                owner = ownerToken,
                stagingPath = stagingPath, backupPath = backupPath, sourcePath = sourcePath,
                originalSnapshot = original, publishedSnapshot = published
            )
        }
    }

    override fun markReplacementPublished(targetPath: String, backupPath: String) {
        val published = MutationTreeSnapshot.capture(File(targetPath), recoveryFileKey)
        updateEntries { entries -> entries.map {
            if (it.type == EntryType.REPLACEMENT && it.destinationPath == targetPath && it.backupPath == backupPath) {
                val staged = it.publishedSnapshot?.entries?.associateBy { entry -> entry.relativePath }
                    ?: throw IOException("Replacement staging verification is unavailable")
                if (published.entries.size != staged.size || published.entries.any { entry ->
                    val expected = staged[entry.relativePath]
                    expected == null || expected.identity.isDirectory != entry.identity.isDirectory ||
                        expected.size != entry.size || expected.sha256 != entry.sha256
                }) throw IOException("Replacement changed during publication")
                it.copy(replacementPublished = true, publishedSnapshot = published)
            } else it
        } }
    }

    override fun forgetReplacement(targetPath: String, backupPath: String) {
        updateEntries { entries -> entries.filterNot {
            it.type == EntryType.REPLACEMENT && it.destinationPath == targetPath && it.backupPath == backupPath
        } }
    }

    override suspend fun cleanupAbandonedMutations() = withContext(dispatchers.io) {
        val recoveryContext = currentCoroutineContext()
        withStoreLock { cleanupLocked { recoveryContext.ensureActive() } }
    }

    private fun cleanupLocked(checkCancellation: () -> Unit) {
        val roots = volumeProvider.activeStorageRoots.map { File(it).canonicalFile }
        val remaining = mutableListOf<MutationJournalEntry>()
        val entries = runCatchingPreservingCancellation { readEntries() }.getOrElse {
            AppLogger.w(TAG, "Recovery journal preserved for manual recovery", it)
            return
        }
        for (entry in entries) {
            checkCancellation()
            if (entry.owner?.let(ownerIsAlive) == true) {
                remaining += entry
                continue
            }
            try {
                when (entry.type) {
                    EntryType.TEMPORARY_PATH -> cleanupTemporaryPath(entry, roots, remaining)
                    EntryType.TRASH_FALLBACK -> cleanupTrashFallback(entry, roots, remaining, entries)
                    EntryType.SOURCE_CLEANUP -> cleanupSourceCleanup(entry, roots, remaining, checkCancellation)
                    EntryType.REPLACEMENT -> cleanupReplacement(entry, roots, remaining)
                }
            } catch (e: Exception) {
                e.rethrowIfCancellation()
                AppLogger.w("MutationJournal", "Failed to clean abandoned mutation entry", e)
                remaining += entry
            }
        }
        runCatchingPreservingCancellation { writeEntries(remaining) }.onFailure {
            AppLogger.w(TAG, "Unable to update recovery journal; existing records preserved", it)
        }
    }

    private fun cleanupTemporaryPath(
        entry: MutationJournalEntry,
        roots: List<File>,
        remaining: MutableList<MutationJournalEntry>
    ) {
        val file = entry.path?.let(::File) ?: return
        if (!isWithinRoots(file, roots)) {
            remaining += entry
            return
        }
        // Older versions treated replacement originals as disposable temporary
        // files. Migrate them without deleting a possibly unique original.
        if (file.name.contains(".arcile-replace-")) {
            val targetName = Regex("^\\.(.+)\\.arcile-replace-[0-9a-fA-F-]+\\.bak$").matchEntire(file.name)?.groupValues?.get(1)
            if (targetName == null) remaining += entry
            else cleanupReplacement(
                MutationJournalEntry(type = EntryType.REPLACEMENT,
                    destinationPath = File(file.parentFile, targetName).absolutePath, backupPath = file.absolutePath),
                roots, remaining
            )
            return
        }
        if (!file.exists()) return
        if (!isKnownTemporaryName(file.name) || !isWithinRoots(file, roots)) {
            remaining += entry
            return
        }
        if (!deleteFileOrDirectory(file)) remaining += entry
    }

    private fun cleanupReplacement(
        entry: MutationJournalEntry,
        roots: List<File>,
        remaining: MutableList<MutationJournalEntry>
    ) {
        val target = entry.destinationPath?.let(::File) ?: return
        val backup = entry.backupPath?.let(::File) ?: return
        if (!isWithinRoots(target, roots) || !isWithinRoots(backup, roots) ||
            target.parentFile != backup.parentFile || !backup.name.contains(".arcile-replace-")
        ) {
            remaining += entry
            return
        }
        val original = entry.originalSnapshot
        val published = entry.publishedSnapshot
        if (!backup.exists()) {
            if (!target.exists() || (original != null && !original.matches(target, recoveryFileKey) && published?.matches(target, recoveryFileKey) != true)) {
                remaining += entry
            }
            return
        }
        // Restoring a complete verified original into an absent destination is
        // safe even when this filesystem does not expose stable file identities.
        if (!target.exists() && original?.matches(backup, requireIdentity = false) == true) {
            if (!rename(backup, target)) remaining += entry
            return
        }
        if (original != null && !original.matches(backup, recoveryFileKey)) {
            remaining += entry
            return
        }
        if (entry.sourceCleanupStarted) {
            if (published?.matches(target, recoveryFileKey) != true || entry.sourcePath?.let { File(it).exists() } != false ||
                !deleteFileOrDirectory(backup)) remaining += entry
            return
        }
        if (!target.exists()) {
            if (!rename(backup, target)) remaining += entry
        } else if (published?.matches(target, recoveryFileKey) == true) {
            if (entry.sourcePath == null) {
                if (!deleteFileOrDirectory(backup)) remaining += entry
            } else if (!deleteFileOrDirectory(target) || !rename(backup, target)) {
                remaining += entry
            }
        } else {
            remaining += entry
        }
    }

    private fun cleanupTrashFallback(
        entry: MutationJournalEntry,
        roots: List<File>,
        remaining: MutableList<MutationJournalEntry>,
        entries: List<MutationJournalEntry>
    ) {
        val sourcePath = entry.sourcePath ?: return
        val payload = entry.payloadPath?.let(::File) ?: return
        val metadata = entry.metadataPath?.let(::File) ?: return
        if (!isWithinRoots(payload, roots) || !isWithinRoots(metadata, roots)) {
            remaining += entry
            return
        }

        // Older journals have a separate source-cleanup entry but no phase flag.
        // Read the original snapshot so recovery order cannot hide that evidence.
        val cleanupStarted = entry.sourceCleanupStarted || entries.any {
            it.type == EntryType.SOURCE_CLEANUP &&
                it.sourcePath == sourcePath && it.destinationPath == payload.absolutePath
        }
        if (cleanupStarted) {
            if (File(sourcePath).exists() || !payload.exists()) {
                remaining += entry.copy(sourceCleanupStarted = true)
            }
        } else if (File(sourcePath).exists()) {
            deleteFileOrDirectory(payload)
            metadata.delete()
        }
    }

    private fun cleanupSourceCleanup(
        entry: MutationJournalEntry,
        roots: List<File>,
        remaining: MutableList<MutationJournalEntry>,
        checkCancellation: () -> Unit
    ) {
        val source = entry.sourcePath?.let(::File) ?: return
        val destination = entry.destinationPath?.let(::File) ?: return
        if (!isWithinRoots(source, roots) || !isWithinRoots(destination, roots)) {
            remaining += entry
            return
        }
        if (!source.exists()) return
        if (!destination.exists()) {
            remaining += entry
            return
        }

        val snapshot = entry.cleanupSnapshot
        if (snapshot == null) {
            // A legacy path pair does not prove which files were verified.
            remaining += entry
            return
        }
        cleanupVerifiedSource(source, destination, snapshot, recoveryFileKey, checkCancellation)
        if (source.exists()) remaining += entry
    }

    private fun deleteFileOrDirectory(file: File): Boolean =
        if (!file.exists()) true else if (file.isDirectory) file.deleteRecursively() else file.delete()

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
        withStoreLock {
            writeEntries(transform(readEntries()))
        }
    }

    private fun <T> withStoreLock(block: () -> T): T = synchronized(processLock) {
        check(store.parentFile?.let { it.mkdirs() || it.isDirectory } == true)
        // The JVM lock prevents overlapping locks between instances in this
        // process; the file lock also serializes viewer-process writers.
        RandomAccessFile(File(store.parentFile, "${store.name}.lock"), "rw").use { file ->
            file.channel.lock().use { block() }
        }
    }

    private fun readEntries(): List<MutationJournalEntry> {
        ensureLegacyPreferencesCleaned()
        if (!store.exists()) return emptyList()
        if (store.length() > MAX_STORE_BYTES) {
            throw IOException("Mutation journal is too large; recovery information preserved")
        }
        return runCatchingPreservingCancellation {
            store.bufferedReader().use { reader ->
                json.decodeFromString<List<MutationJournalEntry>>(reader.readText())
            }.also { require(it.size <= MAX_ENTRIES) }
        }
            .getOrElse {
                throw IOException("Mutation journal cannot be read; recovery information preserved", it)
            }
    }

    private fun writeEntries(entries: List<MutationJournalEntry>) {
        ensureLegacyPreferencesCleaned()
        val encoded = json.encodeToString(entries).encodeToByteArray()
        if (entries.size > MAX_ENTRIES || encoded.size > MAX_STORE_BYTES) {
            throw IOException("Mutation journal is full; existing recovery information preserved")
        }
        if (entries.isEmpty()) {
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
        private val processLock = Any()
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
    val owner: String? = null,
    val path: String? = null,
    val sourcePath: String? = null,
    val destinationPath: String? = null,
    val payloadPath: String? = null,
    val metadataPath: String? = null,
    val sourceCleanupStarted: Boolean = false,
    val cleanupSnapshot: SourceCleanupSnapshot? = null,
    val stagingPath: String? = null,
    val backupPath: String? = null,
    val originalSnapshot: MutationTreeSnapshot? = null,
    val publishedSnapshot: MutationTreeSnapshot? = null,
    val replacementPublished: Boolean = false
)

@Serializable
private enum class EntryType {
    TEMPORARY_PATH,
    TRASH_FALLBACK,
    SOURCE_CLEANUP,
    REPLACEMENT
}
