package dev.qtremors.arcile.core.presentation

import dev.qtremors.arcile.core.storage.domain.FileModel
import dev.qtremors.arcile.core.storage.domain.FileSortOption
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

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

    @Test
    fun `newest sort orders folders and files by their actual timestamp`() {
        val files = listOf(
            fileModel(name = "old-folder", isDirectory = true, lastModified = 100),
            fileModel(name = "latest.txt", lastModified = 300),
            fileModel(name = "middle-folder", isDirectory = true, lastModified = 200)
        )

        val result = filterAndSortFiles(
            files,
            query = "",
            sortOption = FileSortOption.DATE_NEWEST,
            foldersFirst = false
        )

        assertEquals(listOf("latest.txt", "middle-folder", "old-folder"), result.map { it.name })
    }

    @Test
    fun `folders first applies to every sort mode and can be disabled`() {
        FileSortOption.entries.forEach { option ->
            val (file, folder) = when (option) {
                FileSortOption.NAME_ASC ->
                    fileModel("alpha.txt") to fileModel("zeta", isDirectory = true)
                FileSortOption.NAME_DESC ->
                    fileModel("zeta.txt") to fileModel("alpha", isDirectory = true)
                FileSortOption.DATE_NEWEST ->
                    fileModel("file.txt", lastModified = 2) to
                        fileModel("folder", isDirectory = true, lastModified = 1)
                FileSortOption.DATE_OLDEST ->
                    fileModel("file.txt", lastModified = 1) to
                        fileModel("folder", isDirectory = true, lastModified = 2)
                FileSortOption.SIZE_LARGEST ->
                    fileModel("file.txt", size = 2) to
                        fileModel("folder", isDirectory = true, size = 1)
                FileSortOption.SIZE_SMALLEST ->
                    fileModel("file.txt", size = 1) to
                        fileModel("folder", isDirectory = true, size = 2)
                FileSortOption.FILE_COUNT_HIGHEST,
                FileSortOption.FILE_COUNT_LOWEST ->
                    fileModel("file.txt") to fileModel("folder", isDirectory = true)
            }

            val grouped = filterAndSortFiles(
                files = listOf(file, folder),
                query = "",
                sortOption = option,
                foldersFirst = true
            )
            val ungrouped = filterAndSortFiles(
                files = listOf(file, folder),
                query = "",
                sortOption = option,
                foldersFirst = false
            )

            assertTrue("$option should group folders first", grouped.first().isDirectory)
            assertTrue("$option should use only its sort comparator when disabled", !ungrouped.first().isDirectory)
        }
    }

    @Test
    fun `file count sorts folders from highest and lowest counts`() {
        val files = listOf(
            fileModel(name = "small", isDirectory = true),
            fileModel(name = "large", isDirectory = true),
            fileModel(name = "middle", isDirectory = true)
        )
        val counts = mapOf("small" to 2L, "large" to 20L, "middle" to 8L)

        val highest = filterAndSortFiles(
            files = files,
            query = "",
            sortOption = FileSortOption.FILE_COUNT_HIGHEST,
            fileCountFor = { counts[it.name] }
        )
        val lowest = filterAndSortFiles(
            files = files,
            query = "",
            sortOption = FileSortOption.FILE_COUNT_LOWEST,
            fileCountFor = { counts[it.name] }
        )

        assertEquals(listOf("large", "middle", "small"), highest.map(FileModel::name))
        assertEquals(listOf("small", "middle", "large"), lowest.map(FileModel::name))
    }

    private fun fileModel(
        name: String,
        isDirectory: Boolean = false,
        size: Long = 0,
        lastModified: Long = 0
    ): FileModel {
        val path = "C:/tmp/$name"
        return FileModel(
            name = name,
            reference = path,
            size = size,
            lastModified = lastModified,
            isDirectory = isDirectory,
            extension = name.substringAfterLast('.', ""),
            isHidden = false
        )
    }
}
