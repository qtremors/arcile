package dev.qtremors.arcile.core.storage.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.test.core.app.ApplicationProvider
import dev.qtremors.arcile.core.storage.domain.CleanerGroupType
import dev.qtremors.arcile.core.storage.domain.CleanerSectionRule
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
class StorageCleanerPreferencesRepositoryTest {
    private lateinit var context: Context
    private lateinit var dataStoreFile: File
    private lateinit var dataStoreScope: CoroutineScope
    private lateinit var dataStore: DataStore<Preferences>

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        dataStoreFile = File(
            context.filesDir,
            "datastore/storage-cleaner-prefs-test-${UUID.randomUUID()}.preferences_pb"
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
    fun `rules default to all cleaner sections enabled`() = runBlocking {
        val repository = StorageCleanerPreferencesRepository(context, dataStore)

        val rules = repository.rulesFlow.first()

        CleanerGroupType.entries.forEach { type ->
            assertTrue(type.name, rules.section(type).enabled)
        }
    }

    @Test
    fun `ignored paths and section rules persist`() = runBlocking {
        val repository = StorageCleanerPreferencesRepository(context, dataStore)

        repository.ignorePath("/storage/emulated/0/Download/skip.tmp")
        repository.updateSectionRule(
            CleanerGroupType.LargeFiles,
            CleanerSectionRule(
                enabled = false,
                ignoredNamePatterns = setOf("*.iso"),
                largeFileThresholdBytes = 2048L
            )
        )

        val rules = repository.rulesFlow.first()
        assertTrue("/storage/emulated/0/Download/skip.tmp" in rules.ignoredPaths)
        assertFalse(rules.section(CleanerGroupType.LargeFiles).enabled)
        assertEquals(setOf("*.iso"), rules.section(CleanerGroupType.LargeFiles).ignoredNamePatterns)
        assertEquals(2048L, rules.section(CleanerGroupType.LargeFiles).largeFileThresholdBytes)
    }

    @Test
    fun `section reset restores default rule without clearing ignored paths`() = runBlocking {
        val repository = StorageCleanerPreferencesRepository(context, dataStore)

        repository.ignorePath("/storage/emulated/0/Download/skip.tmp")
        repository.updateSectionRule(CleanerGroupType.Apks, CleanerSectionRule(enabled = false))
        repository.resetSection(CleanerGroupType.Apks)

        val rules = repository.rulesFlow.first()
        assertTrue("/storage/emulated/0/Download/skip.tmp" in rules.ignoredPaths)
        assertTrue(rules.section(CleanerGroupType.Apks).enabled)
    }
}
