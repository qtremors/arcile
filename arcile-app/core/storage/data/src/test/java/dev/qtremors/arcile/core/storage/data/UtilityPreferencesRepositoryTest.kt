package dev.qtremors.arcile.core.storage.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class UtilityPreferencesRepositoryTest {
    private lateinit var context: Context
    private lateinit var dataStoreFile: File
    private lateinit var dataStoreScope: CoroutineScope
    private lateinit var dataStore: DataStore<Preferences>

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        dataStoreFile = File(
            context.filesDir,
            "datastore/utility-prefs-test-${UUID.randomUUID()}.preferences_pb"
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
    fun `home utility ids default to trash and cleaner`() = runBlocking {
        val repository = UtilityPreferencesRepository(context, dataStore)

        val ids = repository.homeUtilityIds.first()

        assertEquals(listOf("trash", "cleaner"), ids)
    }

    @Test
    fun `home utility ids persist updates`() = runBlocking {
        val repository = UtilityPreferencesRepository(context, dataStore)

        repository.setHomeUtilityIds(listOf("activity", "trash", "onlyfiles"))

        assertEquals(listOf("activity", "trash", "onlyfiles"), repository.homeUtilityIds.first())
    }

    @Test
    fun `home utility ids discard stale cleaner sub tools`() = runBlocking {
        val repository = UtilityPreferencesRepository(context, dataStore)

        repository.setHomeUtilityIds(listOf("trash", "cleaner", "large", "duplicates", "analyze", "trash"))

        assertEquals(listOf("trash", "cleaner"), repository.homeUtilityIds.first())
    }

    @Test
    fun `legacy unordered utility ids migrate in catalog order`() = runBlocking {
        dataStore.edit { preferences ->
            preferences[stringSetPreferencesKey("home_utility_ids")] = setOf("onlyfiles", "trash", "unknown")
        }

        val ids = UtilityPreferencesRepository(context, dataStore).homeUtilityIds.first()

        assertEquals(listOf("trash", "onlyfiles"), ids)
    }
}
