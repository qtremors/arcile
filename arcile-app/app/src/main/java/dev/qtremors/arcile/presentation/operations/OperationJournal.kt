package dev.qtremors.arcile.presentation.operations

import android.content.Context
import dev.qtremors.arcile.core.operation.BulkFileOperationProgress
import dev.qtremors.arcile.core.operation.BulkFileOperationRequest
import dev.qtremors.arcile.core.operation.OperationRecoveryRecord
import dev.qtremors.arcile.core.storage.domain.toArcileError
import dev.qtremors.arcile.core.storage.domain.userMessage
import dev.qtremors.arcile.utils.AppLogger
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

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
    private val preferences by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        context.getSharedPreferences("operation_journal", Context.MODE_PRIVATE)
    }
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    override fun activeRecord(): OperationJournalRecord? {
        val encoded = preferences.getString(KEY_ACTIVE, null) ?: return null
        return runCatching { json.decodeFromString<OperationJournalRecord>(encoded) }
            .getOrElse {
                AppLogger.w("OperationJournal", "Dropping unreadable operation journal", it)
                preferences.edit().remove(KEY_ACTIVE).apply()
                null
            }
    }

    override fun upsertActive(record: OperationJournalRecord) {
        preferences.edit().putString(KEY_ACTIVE, json.encodeToString(record)).apply()
    }

    override fun update(operationId: String, transform: (OperationJournalRecord) -> OperationJournalRecord) {
        synchronized(preferences) {
            val current = activeRecord() ?: return
            if (current.request.operationId != operationId) return
            upsertActive(transform(current).copy(updatedAtMillis = System.currentTimeMillis()))
        }
    }

    override fun clearActive(operationId: String) {
        val current = activeRecord() ?: return
        if (current.request.operationId == operationId) {
            preferences.edit().remove(KEY_ACTIVE).apply()
        }
    }

    override fun recoveryRecords(): List<OperationJournalRecord> {
        val encoded = preferences.getString(KEY_RECOVERY, null) ?: return emptyList()
        return runCatching { json.decodeFromString<List<OperationJournalRecord>>(encoded) }
            .getOrElse {
                AppLogger.w("OperationJournal", "Dropping unreadable operation recovery journal", it)
                preferences.edit().remove(KEY_RECOVERY).apply()
                emptyList()
            }
    }

    override fun dismissRecovery(operationId: String) {
        writeRecoveryRecords(recoveryRecords().filterNot { it.request.operationId == operationId })
    }

    override fun recoverInterrupted(): List<OperationJournalRecord> {
        val current = activeRecord()
        if (current == null) return recoveryRecords()
        if (current.phase.isTerminal) {
            clearActive(current.request.operationId)
            return recoveryRecords()
        }
        val recovered = current.copy(
            phase = OperationPhase.CLEANUP_REQUIRED,
            error = "File operation was interrupted and needs cleanup.",
            updatedAtMillis = System.currentTimeMillis()
        )
        writeRecoveryRecords(
            recoveryRecords().filterNot { it.request.operationId == recovered.request.operationId } + recovered
        )
        clearActive(current.request.operationId)
        return recoveryRecords()
    }

    private fun writeRecoveryRecords(records: List<OperationJournalRecord>) {
        preferences.edit().putString(KEY_RECOVERY, json.encodeToString(records)).apply()
    }

    private companion object {
        const val KEY_ACTIVE = "active_operation"
        const val KEY_RECOVERY = "recovery_operations"
    }
}

@Serializable
data class OperationJournalRecord(
    val request: BulkFileOperationRequest,
    val phase: OperationPhase,
    val startedAtMillis: Long,
    val updatedAtMillis: Long,
    val progress: BulkFileOperationProgress? = null,
    val stagedPaths: List<String> = emptyList(),
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
        rollbackHints = rollbackHints,
        trashResultIds = trashResultIds,
        error = error
    )

fun Throwable.toOperationMessage(): String = toArcileError().userMessage.toString()
