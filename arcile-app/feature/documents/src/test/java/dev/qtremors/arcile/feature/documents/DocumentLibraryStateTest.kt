package dev.qtremors.arcile.feature.documents

import dev.qtremors.arcile.core.storage.domain.FileModel
import dev.qtremors.arcile.core.storage.domain.SearchFilters
import dev.qtremors.arcile.core.ui.category.CategoryFolderSummary
import org.junit.Assert.assertEquals
import org.junit.Test

class DocumentLibraryStateTest {
    @Test
    fun `presentation builds folders and applies an exact folder filter`() {
        val docs = listOf(
            file("/storage/Documents/report.pdf"),
            file("/storage/Documents/notes.txt"),
            file("/storage/Documents-old/archive.pdf")
        )
        val presented = presentDocuments(DocumentLibraryState(allFiles = docs, isLoading = false))

        assertEquals(listOf("Documents", "Documents-old"), presented.folders.map { it.label }.sorted())

        val filtered = presentDocuments(
            presented.copy(
                folderFilter = CategoryFolderSummary(
                    path = "/storage/Documents",
                    label = "Documents",
                    itemCount = 2,
                    totalSize = 2L,
                    lastModified = 2L,
                    preview = docs.first()
                )
            )
        )

        assertEquals(
            setOf("/storage/Documents/report.pdf", "/storage/Documents/notes.txt"),
            filtered.files.mapTo(mutableSetOf(), FileModel::absolutePath)
        )
    }

    @Test
    fun `search filters apply to document files and folders`() {
        val state = presentDocuments(
            DocumentLibraryState(
                allFiles = listOf(
                    file("/storage/Documents/report.pdf"),
                    file("/storage/Notes/notes.txt")
                ),
                searchFilters = SearchFilters(extensions = setOf("pdf")),
                isLoading = false
            )
        )

        assertEquals(listOf("report.pdf"), state.files.map(FileModel::name))
        assertEquals(listOf("Documents"), state.folders.map { it.label })
    }

    private fun file(path: String) = FileModel(
        name = path.substringAfterLast('/'),
        absolutePath = path,
        size = 1L,
        lastModified = 1L,
        extension = path.substringAfterLast('.')
    )
}
