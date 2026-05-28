package dev.qtremors.arcile.core.storage.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.test.core.app.ApplicationProvider
import dev.qtremors.arcile.core.storage.domain.StorageKind
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class StorageClassificationRepositoryTest {

    private lateinit var context: Context
    private lateinit var dataStoreFile: File
    private lateinit var dataStoreScope: CoroutineScope
    private lateinit var dataStore: DataStore<Preferences>

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        dataStoreFile = File(
            context.filesDir,
            "datastore/storage-classifications-test-${UUID.randomUUID()}.preferences_pb"
        )
        dataStoreScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        dataStore = PreferenceDataStoreFactory.create(
            scope = dataStoreScope,
            produceFile = { dataStoreFile }
        )
    }

    @After
    fun tearDown() {
        dataStoreScope.cancel()
        dataStoreFile.delete()
    }

    @Test
    fun `set and get classification round trip persists metadata`() = runBlocking {
        val repository = StorageClassificationRepository(context, dataStore)

        repository.setClassification("usb", StorageKind.OTG, lastSeenName = "USB Drive", lastSeenPath = "/storage/usb")

        val classification = repository.getClassification("usb")

        assertNotNull(classification)
        assertEquals(StorageKind.OTG, classification?.assignedKind)
        assertEquals("USB Drive", classification?.lastSeenName)
        assertEquals("/storage/usb", classification?.lastSeenPath)
    }

    @Test
    fun `resetClassification removes stored value`() = runBlocking {
        val repository = StorageClassificationRepository(context, dataStore)
        repository.setClassification("usb", StorageKind.OTG, lastSeenName = "USB", lastSeenPath = "/storage/usb")

        repository.resetClassification("usb")

        assertNull(repository.getClassification("usb"))
    }
}
