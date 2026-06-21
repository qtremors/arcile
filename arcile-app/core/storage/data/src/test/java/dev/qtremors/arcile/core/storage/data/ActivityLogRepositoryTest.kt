package dev.qtremors.arcile.core.storage.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.test.core.app.ApplicationProvider
import dev.qtremors.arcile.core.storage.domain.ActivityLogEntry
import dev.qtremors.arcile.core.storage.domain.ActivityLogOperationStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ActivityLogRepositoryTest {
    private lateinit var context: Context
    private lateinit var dataStoreFile: File
    private lateinit var dataStoreScope: CoroutineScope
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var repository: ActivityLogRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        dataStoreFile = File(
            context.filesDir,
            "datastore/activity-log-test-${UUID.randomUUID()}.preferences_pb"
        )
        dataStoreScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        dataStore = PreferenceDataStoreFactory.create(
            scope = dataStoreScope,
            produceFile = { dataStoreFile }
        )
        repository = ActivityLogRepository(context, dataStore)
    }

    @After
    fun tearDown() {
        dataStoreScope.cancel()
        dataStoreFile.delete()
    }

    @Test
    fun `folder entries append newest first`() = runBlocking {
        repository.recordFolderOpened("/storage/emulated/0/Download", "primary")
        repository.recordFolderOpened("/storage/emulated/0/Pictures", "primary")

        val entries = repository.entries.first()

        assertEquals("/storage/emulated/0/Pictures", (entries[0] as ActivityLogEntry.FolderOpened).path)
        assertEquals("/storage/emulated/0/Download", (entries[1] as ActivityLogEntry.FolderOpened).path)
    }

    @Test
    fun `file operation upsert replaces matching operation id`() = runBlocking {
        repository.upsertFileOperation(operation("op-1", ActivityLogOperationStatus.RUNNING, errorMessage = null))
        repository.upsertFileOperation(operation("op-1", ActivityLogOperationStatus.FAILED, errorMessage = "Copy failed"))

        val entries = repository.entries.first()

        assertEquals(1, entries.size)
        val operation = entries.single() as ActivityLogEntry.FileOperation
        assertEquals(ActivityLogOperationStatus.FAILED, operation.status)
        assertEquals("Copy failed", operation.errorMessage)
    }

    @Test
    fun `entries trim to recent 1000`() = runBlocking {
        repeat(1_005) { index ->
            repository.upsertFileOperation(operation("op-$index", ActivityLogOperationStatus.COMPLETED))
        }

        val entries = repository.entries.first()

        assertEquals(1_000, entries.size)
        assertTrue(entries.none { it is ActivityLogEntry.FileOperation && it.operationId == "op-0" })
    }

    @Test
    fun `clear removes all entries`() = runBlocking {
        repository.recordFolderOpened("/storage/emulated/0/Download", "primary")

        repository.clear()

        assertTrue(repository.entries.first().isEmpty())
    }

    @Test
    fun `malformed stored json returns empty entries`() = runBlocking {
        dataStore.edit { prefs -> prefs[stringPreferencesKey("entries")] = "not-json" }

        assertTrue(repository.entries.first().isEmpty())
    }

    private fun operation(
        operationId: String,
        status: ActivityLogOperationStatus,
        errorMessage: String? = null
    ) = ActivityLogEntry.FileOperation(
        id = "operation:$operationId",
        timestampMillis = System.currentTimeMillis(),
        operationId = operationId,
        operationType = "COPY",
        status = status,
        sourceCount = 1,
        destinationPath = "/dest",
        errorMessage = errorMessage
    )
}
