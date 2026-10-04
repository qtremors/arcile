package dev.qtremors.arcile.feature.audio

import android.content.Context
import java.io.File
import dev.qtremors.arcile.core.storage.domain.FileListingPreferences
import dev.qtremors.arcile.core.storage.domain.FileSortOption
import dev.qtremors.arcile.core.storage.domain.FileViewMode
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.json.JSONArray
import org.json.JSONObject

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AudioCollectionStoreTest {
    private lateinit var context: Context

    @Before
    fun reset() {
        context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("audio_music", Context.MODE_PRIVATE).edit().clear().commit()
        File(context.filesDir, "audio_playlists.json").delete()
        File(context.filesDir, "audio_playlists.json.tmp").delete()
    }

    @Test
    fun `playlist edits persist in order without duplicating songs`() = runBlocking {
        val store = AudioCollectionStore(context)
        val playlist = store.createPlaylist("  Drive  ", listOf("/a.mp3", "/a.mp3"))
        store.appendPlaylistTracks(playlist.id, listOf("/b.mp3", "/a.mp3"))
        store.renamePlaylist(playlist.id, "Evening")
        store.setPlaylistTracks(playlist.id, listOf("/b.mp3", "/a.mp3"))
        store.replaceTrackPath("/a.mp3", "/renamed.mp3")

        assertEquals("Evening", store.playlists.value.single().name)
        assertTrue(File(context.filesDir, "audio_playlists.json").readText().contains("Evening"))

        val reopened = AudioCollectionStore(context).playlists.value.single()
        assertEquals("Evening", reopened.name)
        assertEquals(listOf("/b.mp3", "/renamed.mp3"), reopened.trackPaths)

        store.deletePlaylist(playlist.id)
        assertTrue(AudioCollectionStore(context).playlists.value.isEmpty())
    }

    @Test
    fun `legacy playlists migrate when saved`() = runBlocking {
        val legacy = JSONArray().put(JSONObject()
            .put("id", "old")
            .put("name", "Saved")
            .put("paths", JSONArray().put("/song.mp3"))
            .put("updatedAt", 1L))
        val preferences = context.getSharedPreferences("audio_music", Context.MODE_PRIVATE)
        preferences.edit().putString("playlists_v1", legacy.toString()).commit()

        val store = AudioCollectionStore(context)
        assertEquals(listOf("/song.mp3"), store.playlists.value.single().trackPaths)
        store.renamePlaylist("old", "Renamed")

        assertTrue(File(context.filesDir, "audio_playlists.json").isFile)
        assertTrue(!preferences.contains("playlists_v1"))
        assertEquals("Renamed", AudioCollectionStore(context).playlists.value.single().name)
    }

    @Test
    fun `each music page retains its own layout and sort`() = runBlocking {
        val store = AudioCollectionStore(context)
        store.savePresentation(AudioCollectionKind.ALBUMS, FileListingPreferences(
            sortOption = FileSortOption.DATE_NEWEST,
            viewMode = FileViewMode.LIST,
            gridMinCellSize = 184f
        ))
        store.savePresentation(AudioCollectionKind.ARTISTS, FileListingPreferences(
            sortOption = FileSortOption.FILE_COUNT_HIGHEST,
            viewMode = FileViewMode.GRID,
            gridMinCellSize = 208f
        ))
        store.saveDefaultSection(AudioCollectionKind.GENRES)

        val reopened = AudioCollectionStore(context)
        assertEquals(FileSortOption.DATE_NEWEST,
            reopened.presentation(AudioCollectionKind.ALBUMS).sortOption)
        assertEquals(FileViewMode.LIST,
            reopened.presentation(AudioCollectionKind.ALBUMS).viewMode)
        assertEquals(FileSortOption.FILE_COUNT_HIGHEST,
            reopened.presentation(AudioCollectionKind.ARTISTS).sortOption)
        assertEquals(FileViewMode.GRID,
            reopened.presentation(AudioCollectionKind.ARTISTS).viewMode)
        assertEquals(FileSortOption.NAME_ASC,
            reopened.presentation(AudioCollectionKind.GENRES).sortOption)
        assertEquals(AudioCollectionKind.SONGS,
            reopened.defaultSection(AudioCollectionKind.SONGS))
    }
}
