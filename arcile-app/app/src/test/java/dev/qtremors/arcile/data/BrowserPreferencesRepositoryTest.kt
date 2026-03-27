package dev.qtremors.arcile.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.test.core.app.ApplicationProvider
import dev.qtremors.arcile.presentation.FileSortOption
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class BrowserPreferencesRepositoryTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        runBlocking {
            context.browserDataStore.edit { it.clear() }
        }
    }

    @Test
    fun `defaults to name ascending with empty maps`() = runBlocking {
        val repository = BrowserPreferencesRepository(context)

        val preferences = repository.preferencesFlow.first()

        assertEquals(FileSortOption.NAME_ASC, preferences.globalSortOption)
        assertEquals(emptyMap<String, FileSortOption>(), preferences.pathSortOptions)
        assertEquals(emptyMap<String, FileSortOption>(), preferences.exactPathSortOptions)
    }

    @Test
    fun `updatePathSortOption stores exact and recursive keys with normalized path`() = runBlocking {
        val repository = BrowserPreferencesRepository(context)

        repository.updatePathSortOption("/storage/emulated/0/Download/", FileSortOption.SIZE_LARGEST, applyToSubfolders = false)
        repository.updatePathSortOption("/storage/emulated/0/Pictures/", FileSortOption.DATE_NEWEST, applyToSubfolders = true)

        val preferences = repository.preferencesFlow.first()

        assertEquals(FileSortOption.SIZE_LARGEST, preferences.exactPathSortOptions["/storage/emulated/0/Download"])
        assertEquals(FileSortOption.DATE_NEWEST, preferences.pathSortOptions["/storage/emulated/0/Pictures"])
    }

    @Test
    fun `clearing path sort removes both exact and recursive values`() = runBlocking {
        val repository = BrowserPreferencesRepository(context)

        repository.updatePathSortOption("/storage/emulated/0/Download", FileSortOption.NAME_DESC, applyToSubfolders = true)
        repository.updatePathSortOption("/storage/emulated/0/Download", null, applyToSubfolders = false)

        val preferences = repository.preferencesFlow.first()

        assertEquals(null, preferences.pathSortOptions["/storage/emulated/0/Download"])
        assertEquals(null, preferences.exactPathSortOptions["/storage/emulated/0/Download"])
    }

    @Test
    fun `invalid stored sort option falls back to default`() = runBlocking {
        context.browserDataStore.edit { prefs ->
            prefs[stringPreferencesKey("global_sort_option")] = "NOT_REAL"
        }

        val preferences = BrowserPreferencesRepository(context).preferencesFlow.first()

        assertEquals(FileSortOption.NAME_ASC, preferences.globalSortOption)
    }
}
