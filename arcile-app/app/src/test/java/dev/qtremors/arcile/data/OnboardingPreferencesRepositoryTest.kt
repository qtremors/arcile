package dev.qtremors.arcile.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.test.core.app.ApplicationProvider
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
class OnboardingPreferencesRepositoryTest {

    private lateinit var context: Context
    private lateinit var dataStoreFile: File
    private lateinit var dataStoreScope: CoroutineScope
    private lateinit var dataStore: DataStore<Preferences>

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        dataStoreFile = File(
            context.filesDir,
            "datastore/onboarding-prefs-test-${UUID.randomUUID()}.preferences_pb"
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
    fun `defaults to incomplete onboarding`() = runBlocking {
        val repository = OnboardingPreferencesRepository(context, dataStore)

        val preferences = repository.preferencesFlow.first()

        assertFalse(preferences.isCompleted)
        assertEquals(0, preferences.completedVersion)
        assertFalse(preferences.notificationPermissionHandled)
        assertFalse(preferences.wasManuallyReset)
    }

    @Test
    fun `markCompleted persists version and notification state`() = runBlocking {
        val repository = OnboardingPreferencesRepository(context, dataStore)

        repository.markCompleted(completedVersion = 52, notificationPermissionHandled = true)

        val preferences = repository.preferencesFlow.first()
        assertTrue(preferences.isCompleted)
        assertEquals(52, preferences.completedVersion)
        assertTrue(preferences.notificationPermissionHandled)
        assertFalse(preferences.wasManuallyReset)
    }

    @Test
    fun `markNotificationPermissionHandled does not complete onboarding`() = runBlocking {
        val repository = OnboardingPreferencesRepository(context, dataStore)

        repository.markNotificationPermissionHandled()

        val preferences = repository.preferencesFlow.first()
        assertFalse(preferences.isCompleted)
        assertTrue(preferences.notificationPermissionHandled)
        assertFalse(preferences.wasManuallyReset)
    }

    @Test
    fun `resetOnboarding clears completion state`() = runBlocking {
        val repository = OnboardingPreferencesRepository(context, dataStore)

        repository.markCompleted(completedVersion = 52, notificationPermissionHandled = true)
        repository.resetOnboarding()

        val preferences = repository.preferencesFlow.first()
        assertFalse(preferences.isCompleted)
        assertEquals(0, preferences.completedVersion)
        assertFalse(preferences.notificationPermissionHandled)
        assertTrue(preferences.wasManuallyReset)
    }
}
