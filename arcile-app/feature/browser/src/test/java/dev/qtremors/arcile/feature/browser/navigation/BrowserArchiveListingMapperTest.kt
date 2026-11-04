package dev.qtremors.arcile.feature.browser.navigation

import dev.qtremors.arcile.core.storage.domain.ArchiveEntryModel
import dev.qtremors.arcile.core.storage.domain.FolderStats
import dev.qtremors.arcile.core.storage.domain.FolderStatsStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class BrowserArchiveListingMapperTest {
    private val archive = "/storage/emulated/0/Download/photos.zip"

    private fun file(path: String, size: Long) = ArchiveEntryModel(path.substringAfterLast('/'), path, size, null, null, false)
    private fun dir(path: String) = ArchiveEntryModel(path.trimEnd('/').substringAfterLast('/'), path, 0, null, null, true)

    private val entries = listOf(
        dir("trip/"),
        file("trip/a.jpg", 100),
        file("trip/day2/b.jpg", 250),
        file("notes.txt", 5),
        dir("empty/"),
        file("music/song.mp3", 1_000)
    )

    @Test
    fun `every listed folder gets its recursive file count and size`() {
        val rows = BrowserArchiveListingMapper.map(archive, entries, null)
        val stats = BrowserArchiveListingMapper.folderStats(archive, entries, null)

        val folders = rows.filter { it.isDirectory }.map { it.reference }.toSet()
        assertEquals(folders, stats.keys)
        val byName = rows.filter { it.isDirectory }.associate { it.name to stats.getValue(it.reference) }
        assertEquals(FolderStats(2, 350, 0, FolderStatsStatus.Ready), byName["trip"])
        assertEquals(FolderStats(0, 0, 0, FolderStatsStatus.Ready), byName["empty"])
        assertEquals(FolderStats(1, 1_000, 0, FolderStatsStatus.Ready), byName["music"])
    }

    @Test
    fun `nested listings only count folders under the prefix`() {
        val rows = BrowserArchiveListingMapper.map(archive, entries, "trip")
        val stats = BrowserArchiveListingMapper.folderStats(archive, entries, "trip/")

        val day2 = rows.single { it.isDirectory }
        assertEquals(setOf(day2.reference), stats.keys)
        assertEquals(FolderStats(1, 250, 0, FolderStatsStatus.Ready), stats.getValue(day2.reference))
    }
}
