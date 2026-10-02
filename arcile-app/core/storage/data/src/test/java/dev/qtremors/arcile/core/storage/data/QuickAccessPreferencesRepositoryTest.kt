package dev.qtremors.arcile.core.storage.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.test.core.app.ApplicationProvider
import dev.qtremors.arcile.core.storage.domain.QuickAccessItem
import dev.qtremors.arcile.core.storage.domain.QuickAccessType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
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
            targetReference = "/storage/emulated/0/Custom",
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
    fun `default shortcuts include root storage whatsapp media and files app`() = runBlocking {
        val repository = QuickAccessPreferencesRepository(context, dataStore)

        val items = repository.quickAccessItems.first()
        val rootStorage = items.single { it.id == "standard_root_storage" }
        val whatsApp = items.single { it.id == "standard_whatsapp_media" }
        val files = items.single { it.id == "handoff_files_app" }

        assertEquals("Root Storage", rootStorage.label)
        assertEquals("/", rootStorage.targetReference)
        assertEquals(QuickAccessType.STANDARD, rootStorage.type)
        assertFalse(rootStorage.isPinned)
        assertEquals("WhatsApp", whatsApp.label)
        assertEquals("/storage/emulated/0/Android/media/com.whatsapp/WhatsApp/Media", whatsApp.targetReference)
        assertEquals(QuickAccessType.STANDARD, whatsApp.type)
        assertFalse(whatsApp.isPinned)
        assertEquals("Files", files.label)
        assertEquals(QuickAccessType.FILES_APP, files.type)
        assertTrue(files.targetReference.startsWith("content://com.android.externalstorage.documents/tree/primary"))
    }

    @Test
    fun `files app shortcut keeps files app type when stored legacy item exists`() = runBlocking {
        val repository = QuickAccessPreferencesRepository(context, dataStore)

        repository.updateItems(
            listOf(
                QuickAccessItem(
                    id = "handoff_files_app",
                    label = "Files",
                    targetReference = QuickAccessItem.FILES_APP_PATH,
                    type = QuickAccessType.EXTERNAL_HANDOFF
                )
            )
        )

        val files = repository.quickAccessItems.first().single { it.id == "handoff_files_app" }

        assertEquals(QuickAccessType.FILES_APP, files.type)
        assertEquals("Files", files.label)
        assertTrue(files.targetReference.startsWith("content://com.android.externalstorage.documents/tree/primary"))
    }

    @Test
    fun `stored shortcut lists gain the root storage default`() = runBlocking {
        val repository = QuickAccessPreferencesRepository(context, dataStore)
        repository.updateItems(
            listOf(
                QuickAccessItem(
                    id = "standard_downloads",
                    label = "Downloads",
                    targetReference = "/storage/emulated/0/Download",
                    type = QuickAccessType.STANDARD
                )
            )
        )

        val items = repository.quickAccessItems.first()

        assertTrue(items.any { it.id == "standard_root_storage" && it.targetReference == "/" })
    }

    @Test
    fun `existing automatic root pin is removed once and can be enabled by user`() = runBlocking {
        dataStore.edit { preferences ->
            preferences[stringPreferencesKey("quick_access_items")] = Json.encodeToString(
                listOf(
                    QuickAccessItem(
                        id = "standard_root_storage",
                        label = "Root Storage",
                        targetReference = "/",
                        type = QuickAccessType.STANDARD,
                        isPinned = true
                    )
                )
            )
        }
        val repository = QuickAccessPreferencesRepository(context, dataStore)
        val migrated = repository.quickAccessItems.first()

        assertFalse(migrated.single { it.id == "standard_root_storage" }.isPinned)

        repository.updateItems(
            migrated.map { item ->
                if (item.id == "standard_root_storage") item.copy(isPinned = true) else item
            }
        )

        assertTrue(
            repository.quickAccessItems.first()
                .single { it.id == "standard_root_storage" }
                .isPinned
        )
    }
}
