package dev.qtremors.arcile.feature.audio

import android.content.Context
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AudioMusicStoreTest {
    private lateinit var context: Context

    @Before
    fun reset() {
        context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("audio_music", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun `playlist edits persist in order without duplicating songs`() = runBlocking {
        val store = AudioMusicStore(context)
        val playlist = store.createPlaylist("  Drive  ", listOf("/a.mp3", "/a.mp3"))
        store.appendPlaylistTracks(playlist.id, listOf("/b.mp3", "/a.mp3"))
        store.renamePlaylist(playlist.id, "Evening")
        store.setPlaylistTracks(playlist.id, listOf("/b.mp3", "/a.mp3"))
        store.replaceTrackPath("/a.mp3", "/renamed.mp3")

        val reopened = AudioMusicStore(context).playlists.value.single()
        assertEquals("Evening", reopened.name)
        assertEquals(listOf("/b.mp3", "/renamed.mp3"), reopened.trackPaths)

        store.deletePlaylist(playlist.id)
        assertTrue(AudioMusicStore(context).playlists.value.isEmpty())
    }

    @Test
    fun `each music page retains its own layout and sort`() = runBlocking {
        val store = AudioMusicStore(context)
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

        val reopened = AudioMusicStore(context)
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
        assertEquals(AudioCollectionKind.GENRES,
            reopened.defaultSection(AudioCollectionKind.SONGS))
    }
}
