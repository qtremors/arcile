package dev.qtremors.arcile.presentation

import dev.qtremors.arcile.domain.FileModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class FilePresentationTest {

    @Test
    fun `filterAndSortFiles filters by name ignoring case`() {
        val files = listOf(
            fileModel(name = "Alpha.txt"),
            fileModel(name = "notes.md"),
            fileModel(name = "alphabet.png")
        )

        val result = filterAndSortFiles(files, query = "ALP", sortOption = FileSortOption.NAME_ASC)

        assertEquals(listOf("Alpha.txt", "alphabet.png"), result.map { it.name })
    }

    @Test
    fun `filterAndSortFiles keeps directories before files`() {
        val files = listOf(
            fileModel(name = "zeta.txt", isDirectory = false),
            fileModel(name = "alpha", isDirectory = true),
            fileModel(name = "beta.txt", isDirectory = false)
        )

        val result = filterAndSortFiles(files, query = "", sortOption = FileSortOption.NAME_ASC)

        assertTrue(result.first().isDirectory)
        assertEquals(listOf("alpha", "beta.txt", "zeta.txt"), result.map { it.name })
    }

    @Test
    fun `filterAndSortFiles applies selected sort mode`() {
        val files = listOf(
            fileModel(name = "small.txt", size = 128),
            fileModel(name = "large.txt", size = 4096),
            fileModel(name = "medium.txt", size = 1024)
        )

        val result = filterAndSortFiles(files, query = "", sortOption = FileSortOption.SIZE_LARGEST)

        assertEquals(listOf("large.txt", "medium.txt", "small.txt"), result.map { it.name })
    }

    private fun fileModel(
        name: String,
        isDirectory: Boolean = false,
        size: Long = 0,
        lastModified: Long = 0
    ): FileModel {
        val path = "C:/tmp/$name"
        return FileModel(
            file = File(path),
            name = name,
            absolutePath = path,
            size = size,
            lastModified = lastModified,
            isDirectory = isDirectory,
            extension = name.substringAfterLast('.', ""),
            isHidden = false
        )
    }
}
