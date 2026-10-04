package dev.qtremors.arcile.feature.audio

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.room.Room
import dev.qtremors.arcile.core.storage.domain.AudioLibraryPreferences
import dev.qtremors.arcile.core.storage.domain.AudioLibraryPreferencesStore
import io.mockk.every
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AudioListeningStoreTest {
    private lateinit var context: Context
    private lateinit var legacy: AudioLibraryPreferencesStore
    private lateinit var database: AudioListeningDatabase

    @Before fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, AudioListeningDatabase::class.java).build()
        legacy = mockk {
            every { audioLibraryPreferencesFlow } returns flowOf(
                AudioLibraryPreferences(favoriteFiles = setOf("/Music/old.mp3"))
            )
            coEvery { clearMigratedFavorites() } returns Unit
        }
    }

    @After fun tearDown() {
        database.close()
    }

    @Test fun `legacy favorites import only once and stay editable`() = runTest {
        val store = AudioListeningStore(legacy, database)
        assertEquals(setOf("/Music/old.mp3"), store.favoritePaths.first())
        coVerify(exactly = 1) { legacy.clearMigratedFavorites() }
        store.setFavorite("/Music/old.mp3", false)
        assertTrue(store.favoritePaths.first().isEmpty())
    }

    @Test fun `qualified plays update counts and chronological history`() = runTest {
        val store = AudioListeningStore(legacy, database)
        store.recordQualifiedPlay("/Music/first.mp3", playedAt = 10L)
        store.recordQualifiedPlay("/Music/second.mp3", playedAt = 20L)
        store.recordQualifiedPlay("/Music/first.mp3", playedAt = 30L)

        val tracks = store.trackRecords.first().associateBy(AudioTrackRecord::path)
        assertEquals(2, tracks.getValue("/Music/first.mp3").playCount)
        assertEquals(1, tracks.getValue("/Music/second.mp3").playCount)
        assertEquals(
            listOf("/Music/first.mp3", "/Music/second.mp3", "/Music/first.mp3"),
            store.historyPaths.first()
        )
    }

    @Test fun `song details observe only that songs listening record`() = runTest {
        val store = AudioListeningStore(legacy, database)
        assertEquals(null, store.trackRecord("/Music/first.mp3").first())
        store.recordQualifiedPlay("/Music/first.mp3", playedAt = 30L)
        store.recordQualifiedPlay("/Music/second.mp3", playedAt = 50L)

        val record = store.trackRecord("/Music/first.mp3").first()
        assertEquals(1, record?.playCount)
        assertEquals(30L, record?.lastPlayedAt)
        store.clearHistory()
        assertEquals(0, store.trackRecord("/Music/first.mp3").first()?.playCount)
    }

    @Test fun `renaming keeps favorite count and history together`() = runTest {
        val store = AudioListeningStore(legacy, database)
        store.setFavorite("/Music/old.mp3", true)
        store.recordQualifiedPlay("/Music/old.mp3", playedAt = 50L)
        store.replacePath("/Music/old.mp3", "/Music/new.mp3")

        val tracks = store.trackRecords.first().associateBy(AudioTrackRecord::path)
        assertFalse(tracks.containsKey("/Music/old.mp3"))
        assertTrue(tracks.getValue("/Music/new.mp3").favorite)
        assertEquals(1, tracks.getValue("/Music/new.mp3").playCount)
        assertEquals(listOf("/Music/new.mp3"), store.historyPaths.first())
    }

    @Test fun `clearing listening history keeps favorites`() = runTest {
        val store = AudioListeningStore(legacy, database)
        store.setFavorite("/Music/old.mp3", true)
        store.recordQualifiedPlay("/Music/old.mp3", playedAt = 50L)
        store.clearHistory()

        assertTrue(store.historyPaths.first().isEmpty())
        assertEquals(0, store.trackRecords.first().single().playCount)
        assertEquals(setOf("/Music/old.mp3"), store.favoritePaths.first())
    }
}
