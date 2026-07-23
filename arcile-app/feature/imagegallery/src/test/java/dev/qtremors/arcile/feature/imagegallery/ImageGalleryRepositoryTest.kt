package dev.qtremors.arcile.feature.imagegallery

import dev.qtremors.arcile.core.storage.domain.FileModel
import dev.qtremors.arcile.core.storage.domain.FileCategories
import dev.qtremors.arcile.core.storage.domain.ImageCatalogItem
import dev.qtremors.arcile.core.storage.domain.ImageCatalogRepository
import dev.qtremors.arcile.core.storage.domain.ImageCatalogSnapshot
import dev.qtremors.arcile.core.storage.domain.NoOpStorageMutationNotifier
import dev.qtremors.arcile.core.storage.domain.SearchRepository
import dev.qtremors.arcile.core.runtime.di.ArcileDispatchers
import io.mockk.mockk
import io.mockk.coEvery
import io.mockk.coVerify
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ImageGalleryRepositoryTest {
    @Test
    fun `videos category uses category search and builds gallery albums`() = runTest {
        val first = galleryVideo("first.mp4", "/storage/emulated/0/Movies")
        val second = galleryVideo("second.mp4", "/storage/emulated/0/Download")
        val catalog = mockk<ImageCatalogRepository>(relaxed = true)
        val search = mockk<SearchRepository>()
        coEvery {
            search.getFilesByCategory(any(), FileCategories.Videos.name)
        } returns Result.success(listOf(first, second))
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val repository = DefaultImageGalleryRepository(
            imageCatalogRepository = catalog,
            searchRepository = search,
            storageMutationNotifier = NoOpStorageMutationNotifier,
            dispatchers = ArcileDispatchers(dispatcher, dispatcher, dispatcher, dispatcher)
        )

        val snapshot = repository.loadImages(
            volumeId = "primary",
            categoryName = FileCategories.Videos.name
        )

        assertEquals(setOf(first.absolutePath, second.absolutePath), snapshot.files.map { it.absolutePath }.toSet())
        assertEquals(setOf("Movies", "Download"), snapshot.albums.map { it.label }.toSet())
        assertEquals(emptyMap<String, Float>(), snapshot.aspectRatios)
        coVerify(exactly = 0) { catalog.loadImages(any(), any()) }
    }

    @Test
    fun `broad invalidation clears cached snapshot and reloads catalog`() = runTest {
        val first = galleryFile("first.jpg")
        val second = galleryFile("second.jpg")
        val catalog = RecordingImageCatalogRepository(
            snapshots = ArrayDeque(
                listOf(
                    ImageCatalogSnapshot(listOf(ImageCatalogItem(first, 100, 100)), isStale = false),
                    ImageCatalogSnapshot(listOf(ImageCatalogItem(second, 100, 100)), isStale = false)
                )
            )
        )
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val repository = DefaultImageGalleryRepository(
            imageCatalogRepository = catalog,
            searchRepository = mockk<SearchRepository>(relaxed = true),
            storageMutationNotifier = NoOpStorageMutationNotifier,
            dispatchers = ArcileDispatchers(dispatcher, dispatcher, dispatcher, dispatcher)
        )

        assertEquals(listOf(first.absolutePath), repository.loadImages(null, forceRefresh = false).files.map { it.absolutePath })
        assertEquals(listOf(first.absolutePath), repository.loadImages(null, forceRefresh = false).files.map { it.absolutePath })

        repository.invalidate(emptyList())

        assertEquals(listOf(second.absolutePath), repository.loadImages(null, forceRefresh = false).files.map { it.absolutePath })
        assertEquals(2, catalog.loadCalls)
        assertEquals(1, catalog.invalidateCalls)
    }

    @Test
    fun `gallery snapshot cache evicts least recently used volume`() = runTest {
        val catalog = mockk<ImageCatalogRepository>(relaxed = true)
        val search = mockk<SearchRepository>()
        coEvery {
            search.getFilesByCategory(any(), FileCategories.Videos.name)
        } returns Result.success(emptyList())
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val repository = DefaultImageGalleryRepository(
            imageCatalogRepository = catalog,
            searchRepository = search,
            storageMutationNotifier = NoOpStorageMutationNotifier,
            dispatchers = ArcileDispatchers(dispatcher, dispatcher, dispatcher, dispatcher)
        )

        repeat(9) { volume ->
            repository.loadImages(volume.toString(), categoryName = FileCategories.Videos.name)
        }
        repository.loadImages("0", categoryName = FileCategories.Videos.name)

        coVerify(exactly = 10) {
            search.getFilesByCategory(any(), FileCategories.Videos.name)
        }
    }
}

private class RecordingImageCatalogRepository(
    private val snapshots: ArrayDeque<ImageCatalogSnapshot>
) : ImageCatalogRepository {
    var loadCalls = 0
    var invalidateCalls = 0

    override suspend fun loadImages(volumeId: String?, forceRefresh: Boolean): Result<ImageCatalogSnapshot> {
        loadCalls += 1
        return Result.success(snapshots.removeFirst())
    }

    override fun invalidate(paths: Collection<String>) {
        invalidateCalls += 1
    }
}

private fun galleryFile(name: String) = FileModel(
    name = name,
    absolutePath = "/storage/emulated/0/Pictures/$name",
    size = 100L,
    lastModified = name.hashCode().toLong(),
    isDirectory = false,
    extension = "jpg",
    mimeType = "image/jpeg"
)

private fun galleryVideo(name: String, parent: String) = FileModel(
    name = name,
    absolutePath = "$parent/$name",
    size = 100L,
    lastModified = name.hashCode().toLong(),
    isDirectory = false,
    extension = "mp4",
    mimeType = "video/mp4"
)
