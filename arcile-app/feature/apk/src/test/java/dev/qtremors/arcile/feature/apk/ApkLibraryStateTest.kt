package dev.qtremors.arcile.feature.apk

import dev.qtremors.arcile.core.storage.domain.FileModel
import dev.qtremors.arcile.core.storage.domain.FileSortOption
import dev.qtremors.arcile.core.storage.domain.SearchFilters
import org.junit.Assert.assertEquals
import org.junit.Test

class ApkLibraryStateTest {
    @Test
    fun `presentation keeps package folders independent and sorts newest first`() {
        val oldPackage = file("/storage/Download/old.apk", modified = 1L)
        val newBundle = file("/storage/Packages/new.apks", modified = 3L)
        val state = presentApks(
            ApkLibraryState(
                allFiles = listOf(oldPackage, newBundle),
                presentation = ApkLibraryState().presentation.copy(
                    sortOption = FileSortOption.DATE_NEWEST
                ),
                isLoading = false
            )
        )

        assertEquals(listOf(newBundle, oldPackage), state.files)
        assertEquals(setOf("Download", "Packages"), state.folders.map { it.label }.toSet())
    }

    @Test
    fun `search filters apply to packages and their folders`() {
        val state = presentApks(
            ApkLibraryState(
                allFiles = listOf(
                    file("/storage/Packages/base.apk", modified = 1L),
                    file("/storage/Bundles/app.apks", modified = 2L)
                ),
                searchFilters = SearchFilters(extensions = setOf("apk")),
                isLoading = false
            )
        )

        assertEquals(listOf("base.apk"), state.files.map(FileModel::name))
        assertEquals(listOf("Packages"), state.folders.map { it.label })
    }

    private fun file(path: String, modified: Long) = FileModel(
        name = path.substringAfterLast('/'),
        absolutePath = path,
        size = 1L,
        lastModified = modified,
        extension = path.substringAfterLast('.')
    )
}
