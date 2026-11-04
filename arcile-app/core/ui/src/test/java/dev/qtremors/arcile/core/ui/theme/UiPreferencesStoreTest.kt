package dev.qtremors.arcile.core.ui.theme

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class UiPreferencesStoreTest {

    private lateinit var context: Context
    private lateinit var store: UiPreferencesStore

    @Before
    fun setUp() = runTest {
        context = ApplicationProvider.getApplicationContext()
        store = UiPreferencesStore(context)
        context.dataStore.updateData { emptyPreferences() }
    }

    @Test
    fun `folder icons default to one plain folder icon`() = runTest {
        val preferences = store.uiPreferences.first()

        assertFalse(preferences.specialFolderIconsEnabled)
        assertFalse(preferences.appFolderIconsEnabled)
    }

    @Test
    fun `legacy folder icon switch turns on both icon kinds`() = runTest {
        context.dataStore.edit { it[UiPreferencesStore.FOLDER_ICONS_ENABLED_KEY] = true }

        val preferences = store.uiPreferences.first()

        assertTrue(preferences.specialFolderIconsEnabled)
        assertTrue(preferences.appFolderIconsEnabled)
    }

    @Test
    fun `saving replaces the legacy switch with independent switches`() = runTest {
        context.dataStore.edit { it[UiPreferencesStore.FOLDER_ICONS_ENABLED_KEY] = true }

        store.saveUiPreferences(
            store.uiPreferences.first().copy(specialFolderIconsEnabled = false, appFolderIconsEnabled = true)
        )

        val stored = context.dataStore.data.first()
        val preferences = store.uiPreferences.first()
        assertNull(stored[UiPreferencesStore.FOLDER_ICONS_ENABLED_KEY])
        assertFalse(preferences.specialFolderIconsEnabled)
        assertTrue(preferences.appFolderIconsEnabled)
    }
}
