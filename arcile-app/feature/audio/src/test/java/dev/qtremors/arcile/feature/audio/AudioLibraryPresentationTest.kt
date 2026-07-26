package dev.qtremors.arcile.feature.audio

import dev.qtremors.arcile.core.storage.domain.AudioTrack
import dev.qtremors.arcile.core.storage.domain.FileModel
import dev.qtremors.arcile.core.storage.domain.FileListingPreferences
import dev.qtremors.arcile.core.storage.domain.FileSortOption
import dev.qtremors.arcile.core.storage.domain.FileViewMode
import org.junit.Assert.assertEquals
import org.junit.Test

class AudioLibraryPresentationTest {

    @Test
    fun `library groups tracks into folders`() {
        val tracks = listOf(
            track("/Music/One/first.mp3", "First", "Artist", "Album"),
            track("/Music/One/second.mp3", "Second", "Artist", "Album"),
            track("/Music/Two/third.mp3", "Third", "Other", "Other album")
        )

        val state = buildAudioLibraryState(AudioLibraryState(), tracks)

        assertEquals(listOf("One", "Two"), state.folders.map(AudioFolder::title))
        assertEquals(2, state.folders.first().tracks.size)
    }

    @Test
    fun `search matches track artist album and folder metadata`() {
        val tracks = listOf(
            track("/Music/Scores/first.mp3", "Opening", "Composer", "Film"),
            track("/Podcasts/Tech/second.mp3", "Episode", "Host", "Weekly")
        )

        val byArtist = buildAudioLibraryState(
            AudioLibraryState(query = "composer"),
            tracks
        )
        val byFolder = buildAudioLibraryState(
            AudioLibraryState(query = "podcasts"),
            tracks
        )

        assertEquals(listOf("Opening"), byArtist.visibleTracks.map { it.displayTitle })
        assertEquals(listOf("Episode"), byFolder.visibleTracks.map { it.displayTitle })
        assertEquals(listOf("Tech"), byFolder.folders.map(AudioFolder::title))
    }

    @Test
    fun `folder filter only includes tracks from its exact path`() {
        val tracks = listOf(
            track("/Music/folder/one.mp3", "One", "folder", "Album"),
            track("/Music/Elsewhere/two.mp3", "Two", "Artist", "folder")
        )
        val initial = buildAudioLibraryState(AudioLibraryState(), tracks)

        val folder = initial.folders.first { it.title == "folder" }

        assertEquals(
            listOf("One"),
            buildAudioLibraryState(initial.copy(folderFilter = folder)).visibleTracks.map { it.displayTitle }
        )
    }

    @Test
    fun `audio defaults to list view with newest files first`() {
        val tracks = listOf(
            track("/Music/old.mp3", "Old", "Artist", "Album", modified = 10L),
            track("/Music/new.mp3", "New", "Artist", "Album", modified = 30L),
            track("/Music/middle.mp3", "Middle", "Artist", "Album", modified = 20L)
        )

        val state = buildAudioLibraryState(AudioLibraryState(), tracks)

        assertEquals(FileViewMode.LIST, state.audioPresentation.viewMode)
        assertEquals(
            listOf("New", "Middle", "Old"),
            state.visibleTracks.map { it.displayTitle }
        )
    }

    @Test
    fun `folder size sort uses the combined size of its audio`() {
        val tracks = listOf(
            track("/Music/Small/one.mp3", "One", "Artist", "Album", size = 5L),
            track("/Music/Large/two.mp3", "Two", "Artist", "Album", size = 20L),
            track("/Music/Large/three.mp3", "Three", "Artist", "Album", size = 30L)
        )
        val state = buildAudioLibraryState(
            AudioLibraryState(
                folderPresentation = FileListingPreferences(
                    sortOption = FileSortOption.SIZE_LARGEST
                )
            ),
            tracks
        )

        assertEquals(listOf("Large", "Small"), state.folders.map { it.title })
    }

    @Test
    fun `folder cover always uses the latest modified artwork`() {
        val state = buildAudioLibraryState(
            AudioLibraryState(),
            listOf(
                track("/Music/Folder/alphabetical.mp3", "A", "Artist", "Album", modified = 10L),
                track("/Music/Folder/latest.mp3", "Z", "Artist", "Album", modified = 30L),
                track("/Music/Folder/middle.mp3", "M", "Artist", "Album", modified = 20L)
            )
        )

        assertEquals("latest.mp3", state.folders.single().coverTrack.file.name)
    }

    @Test
    fun `duration formatting supports short and long tracks`() {
        assertEquals("0:00", formatAudioDuration(0L))
        assertEquals("3:05", formatAudioDuration(185_000L))
        assertEquals("1:02:03", formatAudioDuration(3_723_000L))
    }

    private fun track(
        path: String,
        title: String,
        artist: String,
        album: String,
        modified: Long = 1L,
        size: Long = 1L
    ) = AudioTrack(
        file = FileModel(
            name = path.substringAfterLast('/'),
            absolutePath = path,
            size = size,
            lastModified = modified,
            extension = "mp3",
            mimeType = "audio/mpeg"
        ),
        title = title,
        artist = artist,
        album = album,
        durationMs = 10_000L
    )
}
