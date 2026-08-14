package dev.qtremors.arcile.core.privilege.android

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.test.core.app.ApplicationProvider
import dev.qtremors.arcile.core.privilege.PrivilegeMode
import dev.qtremors.arcile.core.runtime.di.ArcileDispatchers
import java.io.File
import java.util.UUID
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AndroidPrivilegePreferencesTest {
    private lateinit var dataStoreFile: File
    private lateinit var dataStoreScope: CoroutineScope
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var repository: AndroidPrivilegePreferences

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        dataStoreFile = File(
            context.filesDir,
            "datastore/privilege-prefs-test-${UUID.randomUUID()}.preferences_pb"
        )
        dataStoreScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        dataStore = PreferenceDataStoreFactory.create(
            scope = dataStoreScope,
            produceFile = { dataStoreFile }
        )
        repository = AndroidPrivilegePreferences(
            dataStore = dataStore,
            dispatchers = ArcileDispatchers(
                io = Dispatchers.IO,
                default = Dispatchers.Default,
                main = Dispatchers.Unconfined,
                storage = Dispatchers.IO
            )
        )
    }

    @After
    fun tearDown() {
        dataStoreScope.cancel()
        dataStoreFile.delete()
    }

    @Test
    fun `defaults preserve passive automatic startup`() = runBlocking {
        val state = repository.state.first()

        assertEquals(PrivilegeMode.AUTOMATIC, state.mode)
        assertFalse(state.rootPreviouslyAuthorized)
        assertFalse(state.shizukuPreviouslyAuthorized)
        assertFalse(state.protectedFilesystemWritesEnabled)
    }

    @Test
    fun `mode authorization and protected write choices persist independently`() = runBlocking {
        repository.setMode(PrivilegeMode.SHIZUKU)
        repository.setRootPreviouslyAuthorized(true)
        repository.setShizukuPreviouslyAuthorized(true)
        repository.setProtectedFilesystemWritesEnabled(true)

        val state = repository.state.first()
        assertEquals(PrivilegeMode.SHIZUKU, state.mode)
        assertTrue(state.rootPreviouslyAuthorized)
        assertTrue(state.shizukuPreviouslyAuthorized)
        assertTrue(state.protectedFilesystemWritesEnabled)
    }
}
