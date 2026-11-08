package dev.qtremors.arcile.feature.onlyfiles

import dev.qtremors.arcile.core.storage.domain.ArchiveEntryModel
import dev.qtremors.arcile.core.storage.domain.ArchiveNameEncoding
import dev.qtremors.arcile.core.ui.archive.buildArchiveBrowserItems
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OnlyFilesArchivePresentationTest {
    @Test fun `current archive folders keep their paths including an explicitly empty folder`() {
        val selection = VaultZipSelection(listOf("docs"), emptySet())
        assertEquals(listOf("docs"), selection.relativePath(listOf("docs")))
        assertEquals(listOf("docs", "child", "note.txt"), selection.relativePath(listOf("docs", "child", "note.txt")))
        assertEquals(null, selection.relativePath(listOf("outside.txt")))
    }

    @Test
    fun `search keeps matched ancestor folders available for navigation`() {
        val entries = listOf(entry("docs/secret.txt"), entry("docs/other.txt"), entry("outside.txt"))
        val root = buildArchiveBrowserItems(entries, null, "secret")
        assertEquals(listOf("docs"), root.map { it.path })
        assertTrue(root.single().isDirectory)
        assertEquals(listOf("docs/secret.txt"), buildArchiveBrowserItems(entries, "docs", "secret").map { it.path })
    }

    @Test
    fun `selected extraction preserves folder paths and prompts only for selected encrypted descendants`() {
        val listing = VaultArchiveListing(emptyList(), setOf(listOf("docs", "private.txt")), false)
        val selected = listing.selection(listOf("docs"), setOf("docs/public.txt"), ArchiveNameEncoding.CP437)
        assertFalse(selected.requiresPassword)
        assertEquals(listOf("docs", "public.txt"), selected.relativePath(listOf("docs", "public.txt")))
        assertEquals(null, selected.relativePath(listOf("docs", "private.txt")))
        assertEquals(ArchiveNameEncoding.CP437, selected.encoding)
        val entireFolder = listing.selection(emptyList(), setOf("docs"), ArchiveNameEncoding.UTF_8)
        assertTrue(entireFolder.requiresPassword)
        assertEquals(listOf("docs", "private.txt"), entireFolder.relativePath(listOf("docs", "private.txt")))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `invalid selected paths cannot silently become whole archive extraction`() {
        VaultArchiveListing(emptyList(), emptySet(), false)
            .selection(emptyList(), setOf("../outside"), ArchiveNameEncoding.UTF_8)
    }

    private fun entry(path: String) = ArchiveEntryModel(path.substringAfterLast('/'), path, 3L, 3L, null, false)
}
