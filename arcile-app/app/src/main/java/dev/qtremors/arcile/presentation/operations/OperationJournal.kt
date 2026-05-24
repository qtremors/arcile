package dev.qtremors.arcile.presentation.operations

import android.content.Context
import dev.qtremors.arcile.domain.toArcileError
import dev.qtremors.arcile.utils.AppLogger
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

interface OperationJournal {
    fun activeRecord(): OperationJournalRecord?
    fun upsert(record: OperationJournalRecord)
    fun update(operationId: String, transform: (OperationJournalRecord) -> OperationJournalRecord)
    fun clearActive(operationId: String)
    fun recoverInterrupted(): OperationJournalRecord?
}

class NoOpOperationJournal : OperationJournal {
    override fun activeRecord(): OperationJournalRecord? = null
    override fun upsert(record: OperationJournalRecord) = Unit
    override fun update(operationId: String, transform: (OperationJournalRecord) -> OperationJournalRecord) = Unit
    override fun clearActive(operationId: String) = Unit
    override fun recoverInterrupted(): OperationJournalRecord? = null
}

class DefaultOperationJournal(context: Context) : OperationJournal {
    private val preferences = context.getSharedPreferences("operation_journal", Context.MODE_PRIVATE)
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

    override fun upsert(record: OperationJournalRecord) {
        preferences.edit().putString(KEY_ACTIVE, json.encodeToString(record)).apply()
    }

    override fun update(operationId: String, transform: (OperationJournalRecord) -> OperationJournalRecord) {
        synchronized(preferences) {
            val current = activeRecord() ?: return
            if (current.request.operationId != operationId) return
            upsert(transform(current).copy(updatedAtMillis = System.currentTimeMillis()))
        }
    }

    override fun clearActive(operationId: String) {
        val current = activeRecord() ?: return
        if (current.request.operationId == operationId) {
            preferences.edit().remove(KEY_ACTIVE).apply()
        }
    }

    override fun recoverInterrupted(): OperationJournalRecord? {
        val current = activeRecord() ?: return null
        if (current.phase.isTerminal) return current
        val recovered = current.copy(
            phase = OperationPhase.CLEANUP_REQUIRED,
            error = "File operation was interrupted and needs cleanup.",
            updatedAtMillis = System.currentTimeMillis()
        )
        upsert(recovered)
        return recovered
    }

    private companion object {
        const val KEY_ACTIVE = "active_operation"
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

fun Throwable.toOperationMessage(): String = toArcileError().userMessage.toString()
