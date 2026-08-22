package dev.qtremors.arcile.core.operation.android

import android.content.Context
import androidx.core.content.edit
import dev.qtremors.arcile.core.operation.BulkFileOperationProgress
import dev.qtremors.arcile.core.operation.BulkFileOperationRequest
import dev.qtremors.arcile.core.operation.OperationRecoveryRecord
import dev.qtremors.arcile.core.runtime.logging.AppLogger
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

interface OperationJournal {
    fun activeRecord(): OperationJournalRecord?
    fun upsertActive(record: OperationJournalRecord)
    fun update(operationId: String, transform: (OperationJournalRecord) -> OperationJournalRecord)
    fun clearActive(operationId: String)
    fun recoveryRecords(): List<OperationJournalRecord>
    fun dismissRecovery(operationId: String)
    fun recoverInterrupted(): List<OperationJournalRecord>
}

class NoOpOperationJournal : OperationJournal {
    override fun activeRecord(): OperationJournalRecord? = null
    override fun upsertActive(record: OperationJournalRecord) = Unit
    override fun update(operationId: String, transform: (OperationJournalRecord) -> OperationJournalRecord) = Unit
    override fun clearActive(operationId: String) = Unit
    override fun recoveryRecords(): List<OperationJournalRecord> = emptyList()
    override fun dismissRecovery(operationId: String) = Unit
    override fun recoverInterrupted(): List<OperationJournalRecord> = emptyList()
}

class DefaultOperationJournal(context: Context) : OperationJournal {
    private val store = storeFile(context)
    private val lock = Any()
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    init {
        // Remove records written by versions that used backup-eligible SharedPreferences.
        context.getSharedPreferences(LEGACY_PREFERENCES, Context.MODE_PRIVATE).edit { clear() }
    }

    override fun activeRecord(): OperationJournalRecord? = synchronized(lock) { readState().active }

    override fun upsertActive(record: OperationJournalRecord) = synchronized(lock) {
        val state = readState()
        writeState(state.copy(active = record.withoutSecrets()))
    }

    override fun update(
        operationId: String,
        transform: (OperationJournalRecord) -> OperationJournalRecord
    ): Unit =
        synchronized(lock) {
            val state = readState()
            val current = state.active ?: return
            if (current.request.operationId != operationId) return
            writeState(
                state.copy(
                    active = transform(current)
                        .copy(updatedAtMillis = System.currentTimeMillis())
                        .withoutSecrets()
                )
            )
        }

    override fun clearActive(operationId: String): Unit = synchronized(lock) {
        val state = readState()
        val current = state.active ?: return
        if (current.request.operationId == operationId) {
            writeState(state.copy(active = null))
        }
    }

    override fun recoveryRecords(): List<OperationJournalRecord> = synchronized(lock) { readState().recovery }

    override fun dismissRecovery(operationId: String) = synchronized(lock) {
        val state = readState()
        writeState(state.copy(recovery = state.recovery.filterNot { it.request.operationId == operationId }))
    }

    override fun recoverInterrupted(): List<OperationJournalRecord> = synchronized(lock) {
        val state = readState()
        val current = state.active ?: return state.recovery
        if (current.phase.isTerminal) {
            writeState(state.copy(active = null))
            return readState().recovery
        }
        val recovered = current.copy(
            phase = OperationPhase.CLEANUP_REQUIRED,
            error = "File operation was interrupted and needs cleanup.",
            updatedAtMillis = System.currentTimeMillis()
        ).withoutSecrets()
        writeState(
            OperationJournalState(
                active = null,
                recovery = state.recovery.filterNot {
                    it.request.operationId == recovered.request.operationId
                } + recovered
            )
        )
        readState().recovery
    }

    private fun readState(): OperationJournalState {
        if (!store.exists()) return OperationJournalState()
        if (store.length() > MAX_STORE_BYTES) {
            AppLogger.w(TAG, "Dropping oversized operation journal")
            store.delete()
            return OperationJournalState()
        }
        val decoded = runCatching {
            store.bufferedReader().use { reader ->
                json.decodeFromString<OperationJournalState>(reader.readText())
            }
        }.getOrElse {
            AppLogger.w(TAG, "Dropping unreadable operation journal", it)
            store.delete()
            return OperationJournalState()
        }
        val cutoff = System.currentTimeMillis() - RECOVERY_RETENTION_MS
        val retained = decoded.copy(
            active = decoded.active?.takeIf { it.updatedAtMillis >= cutoff },
            recovery = decoded.recovery
                .filter { it.updatedAtMillis >= cutoff }
                .takeLast(MAX_RECOVERY_RECORDS)
        )
        if (retained != decoded) writeState(retained)
        return retained
    }

    private fun writeState(state: OperationJournalState) {
        var bounded = state.copy(
            active = state.active?.withoutSecrets(),
            recovery = state.recovery.map { it.withoutSecrets() }
                .takeLast(MAX_RECOVERY_RECORDS)
        )
        var encoded = json.encodeToString(bounded).encodeToByteArray()
        while (encoded.size > MAX_STORE_BYTES && bounded.recovery.isNotEmpty()) {
            bounded = bounded.copy(recovery = bounded.recovery.drop(1))
            encoded = json.encodeToString(bounded).encodeToByteArray()
        }
        if (encoded.size > MAX_STORE_BYTES && bounded.active != null) {
            AppLogger.w(TAG, "Active operation is too large for the bounded journal")
            bounded = bounded.copy(active = null)
            encoded = json.encodeToString(bounded).encodeToByteArray()
        }
        if (bounded.active == null && bounded.recovery.isEmpty()) {
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

    private fun OperationJournalRecord.withoutSecrets(): OperationJournalRecord =
        copy(request = request.copy(archivePassword = null))

    companion object {
        private const val TAG = "OperationJournal"
        private const val LEGACY_PREFERENCES = "operation_journal"
        private const val STORE_FILE = "operation_journal.json"
        private const val MAX_STORE_BYTES = 1024 * 1024
        private const val MAX_RECOVERY_RECORDS = 16
        private const val RECOVERY_RETENTION_MS = 7L * 24 * 60 * 60 * 1000

        internal fun storeFile(context: Context) = File(context.noBackupFilesDir, STORE_FILE)
        internal fun clearForTest(context: Context) {
            val file = storeFile(context)
            file.delete()
            File(file.parentFile, "${file.name}.new").delete()
        }
    }
}

@Serializable
private data class OperationJournalState(
    val active: OperationJournalRecord? = null,
    val recovery: List<OperationJournalRecord> = emptyList()
)

@Serializable
data class OperationJournalRecord(
    val request: BulkFileOperationRequest,
    val phase: OperationPhase,
    val startedAtMillis: Long,
    val updatedAtMillis: Long,
    val progress: BulkFileOperationProgress? = null,
    val stagedPaths: List<String> = emptyList(),
    val finalizedPaths: List<String> = emptyList(),
    val rollbackHints: List<String> = emptyList(),
    val trashResultIds: List<String> = emptyList(),
    val error: String? = null
)

@Serializable
enum class OperationPhase {
    QUEUED,
    RUNNING,
    CANCELLING,
    COMPLETED,
    FAILED,
    CANCELLED,
    CLEANUP_REQUIRED;

    val isTerminal: Boolean
        get() = this == COMPLETED || this == FAILED || this == CANCELLED || this == CLEANUP_REQUIRED
}

fun BulkFileOperationRequest.toJournalRecord(phase: OperationPhase): OperationJournalRecord {
    val now = System.currentTimeMillis()
    return OperationJournalRecord(
        request = this,
        phase = phase,
        startedAtMillis = now,
        updatedAtMillis = now
    )
}

fun OperationJournalRecord.toRecoveryRecord(): OperationRecoveryRecord =
    OperationRecoveryRecord(
        request = request,
        phase = phase.name,
        startedAtMillis = startedAtMillis,
        updatedAtMillis = updatedAtMillis,
        progress = progress,
        stagedPaths = stagedPaths,
        finalizedPaths = finalizedPaths,
        rollbackHints = rollbackHints,
        trashResultIds = trashResultIds,
        error = error
    )
