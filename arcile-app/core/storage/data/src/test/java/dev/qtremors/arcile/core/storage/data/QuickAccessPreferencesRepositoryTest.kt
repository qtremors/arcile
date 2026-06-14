package dev.qtremors.arcile.core.storage.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.test.core.app.ApplicationProvider
import dev.qtremors.arcile.core.storage.domain.QuickAccessItem
import dev.qtremors.arcile.core.storage.domain.QuickAccessType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
class QuickAccessPreferencesRepositoryTest {
    private lateinit var context: Context
    private lateinit var dataStoreFile: File
    private lateinit var dataStoreScope: CoroutineScope
    private lateinit var dataStore: DataStore<Preferences>

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        dataStoreFile = File(
            context.filesDir,
            "datastore/quick-access-prefs-test-${UUID.randomUUID()}.preferences_pb"
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
    fun `removeItem persists custom shortcut deletion`() = runBlocking {
        val repository = QuickAccessPreferencesRepository(context, dataStore)
        val custom = QuickAccessItem(
            id = "custom_test",
            label = "Custom",
            path = "/storage/emulated/0/Custom",
            type = QuickAccessType.CUSTOM
        )

        repository.addItem(custom)
        assertTrue(repository.quickAccessItems.first().any { it.id == custom.id })

        repository.removeItem(custom.id)

        assertFalse(repository.quickAccessItems.first().any { it.id == custom.id })
    }

    @Test
    fun `removeItem tombstones files app default shortcut`() = runBlocking {
        val repository = QuickAccessPreferencesRepository(context, dataStore)

        assertTrue(repository.quickAccessItems.first().any { it.id == "handoff_files_app" })

        repository.removeItem("handoff_files_app")

        assertFalse(repository.quickAccessItems.first().any { it.id == "handoff_files_app" })
    }

    @Test
    fun `default shortcuts include whatsapp media and files app`() = runBlocking {
        val repository = QuickAccessPreferencesRepository(context, dataStore)

        val items = repository.quickAccessItems.first()
        val whatsApp = items.single { it.id == "standard_whatsapp_media" }
        val files = items.single { it.id == "handoff_files_app" }

        assertEquals("WhatsApp", whatsApp.label)
        assertEquals("/storage/emulated/0/Android/media/com.whatsapp/WhatsApp/Media", whatsApp.path)
        assertEquals(QuickAccessType.STANDARD, whatsApp.type)
        assertTrue(whatsApp.isPinned)
        assertEquals("Files", files.label)
        assertEquals(QuickAccessType.FILES_APP, files.type)
        assertTrue(files.path.startsWith("content://com.android.externalstorage.documents/tree/primary"))
    }

    @Test
    fun `files app shortcut keeps files app type when stored legacy item exists`() = runBlocking {
        val repository = QuickAccessPreferencesRepository(context, dataStore)

        repository.updateItems(
            listOf(
                QuickAccessItem(
                    id = "handoff_files_app",
                    label = "Files",
                    path = QuickAccessItem.FILES_APP_PATH,
                    type = QuickAccessType.EXTERNAL_HANDOFF
                )
            )
        )

        val files = repository.quickAccessItems.first().single { it.id == "handoff_files_app" }

        assertEquals(QuickAccessType.FILES_APP, files.type)
        assertEquals("Files", files.label)
        assertTrue(files.path.startsWith("content://com.android.externalstorage.documents/tree/primary"))
    }
}
