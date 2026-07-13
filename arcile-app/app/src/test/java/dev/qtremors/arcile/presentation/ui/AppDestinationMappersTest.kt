package dev.qtremors.arcile.presentation.ui

import dev.qtremors.arcile.feature.archive.ArchiveDestination
import dev.qtremors.arcile.core.storage.domain.FileModel
import dev.qtremors.arcile.feature.imagegallery.GalleryDestination
import dev.qtremors.arcile.feature.quickaccess.QuickAccessDestination
import dev.qtremors.arcile.feature.recentfiles.RecentFilesDestination
import dev.qtremors.arcile.feature.storagecleaner.StorageCleanerDestination
import dev.qtremors.arcile.navigation.AppRoutes
import org.junit.Assert.assertEquals
import org.junit.Test

class AppDestinationMappersTest {
    private val browserRoutes = mutableListOf<AppRoutes.Main>()
    private val openedPaths = mutableListOf<String>()
    private val galleryContexts = mutableListOf<Pair<List<String>, Set<String>>>()
    private val externalFolders = mutableListOf<String>()
    private val mappers = AppDestinationMappers(
        navigateToBrowser = browserRoutes::add,
        openPath = openedPaths::add,
        openGalleryPath = { path, files, selectedPaths ->
            openedPaths += path
            galleryContexts += files.map(FileModel::absolutePath) to selectedPaths
        },
        openExternalFolder = externalFolders::add
    )

    @Test
    fun `recent containing folder creates a focused browser entry`() {
        mappers.recentFiles.map(RecentFilesDestination.ContainingFolder("/files"))

        assertEquals(
            AppRoutes.Main(
                initialPage = BROWSER_PAGE,
                path = "/files",
                seedInitialPathHistory = false
            ),
            browserRoutes.single()
        )
    }

    @Test
    fun `cleaner open file remains a file action`() {
        mappers.storageCleaner.map(StorageCleanerDestination.OpenFile("/cache/file.tmp"))

        assertEquals(listOf("/cache/file.tmp"), openedPaths)
        assertEquals(emptyList<AppRoutes.Main>(), browserRoutes)
    }

    @Test
    fun `cleaner containing folder preserves focus path`() {
        mappers.storageCleaner.map(
            StorageCleanerDestination.ContainingFolder(
                path = "/cache",
                focusPath = "/cache/file.tmp"
            )
        )

        assertEquals(
            AppRoutes.Main(
                initialPage = BROWSER_PAGE,
                path = "/cache",
                focusPath = "/cache/file.tmp",
                seedInitialPathHistory = false
            ),
            browserRoutes.single()
        )
    }

    @Test
    fun `quick access local path creates a browser entry`() {
        mappers.quickAccess.map(QuickAccessDestination.LocalPath("/documents"))

        assertEquals(
            AppRoutes.Main(
                initialPage = BROWSER_PAGE,
                path = "/documents",
                seedInitialPathHistory = false
            ),
            browserRoutes.single()
        )
    }

    @Test
    fun `quick access external folder remains external`() {
        mappers.quickAccess.map(QuickAccessDestination.ExternalFolder("content://folder"))

        assertEquals(listOf("content://folder"), externalFolders)
        assertEquals(emptyList<AppRoutes.Main>(), browserRoutes)
    }

    @Test
    fun `archive destination preserves archive entry`() {
        mappers.archive.map(ArchiveDestination.OpenInBrowser("/files/archive.zip"))

        assertEquals(
            AppRoutes.Main(
                initialPage = BROWSER_PAGE,
                archivePath = "/files/archive.zip",
                seedInitialPathHistory = false
            ),
            browserRoutes.single()
        )
    }

    @Test
    fun `gallery destination remains a file action`() {
        mappers.gallery.map(
            GalleryDestination.ViewImage(
                path = "/images/photo.jpg",
                surroundingFiles = listOf(
                    FileModel("photo.jpg", "/images/photo.jpg", extension = "jpg")
                ),
                selectedPaths = setOf("/images/photo.jpg")
            )
        )

        assertEquals(listOf("/images/photo.jpg"), openedPaths)
        assertEquals(
            listOf(listOf("/images/photo.jpg") to setOf("/images/photo.jpg")),
            galleryContexts
        )
        assertEquals(emptyList<AppRoutes.Main>(), browserRoutes)
    }
}
