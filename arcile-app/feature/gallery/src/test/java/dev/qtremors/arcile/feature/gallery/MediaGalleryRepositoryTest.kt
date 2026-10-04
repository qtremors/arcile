package dev.qtremors.arcile.feature.gallery

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
class MediaGalleryRepositoryTest {
    @Test
    fun `videos category uses category search and builds gallery folders`() = runTest {
        val first = galleryVideo("first.mp4", "/storage/emulated/0/Movies")
        val second = galleryVideo("second.mp4", "/storage/emulated/0/Download")
        val catalog = mockk<ImageCatalogRepository>(relaxed = true)
        val search = mockk<SearchRepository>()
        coEvery {
            search.getFilesByCategory(any(), FileCategories.Videos.storageName)
        } returns Result.success(listOf(first, second))
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val repository = DefaultMediaGalleryRepository(
            imageCatalogRepository = catalog,
            searchRepository = search,
            storageMutationNotifier = NoOpStorageMutationNotifier,
            dispatchers = ArcileDispatchers(dispatcher, dispatcher, dispatcher, dispatcher)
        )

        val snapshot = repository.loadMedia(
            volumeId = "primary",
            categoryId = FileCategories.Videos.id.value
        )

        assertEquals(setOf(first.reference, second.reference), snapshot.files.map { it.reference }.toSet())
        assertEquals(setOf("Movies", "Download"), snapshot.folders.map { it.label }.toSet())
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
        val repository = DefaultMediaGalleryRepository(
            imageCatalogRepository = catalog,
            searchRepository = mockk<SearchRepository>(relaxed = true),
            storageMutationNotifier = NoOpStorageMutationNotifier,
            dispatchers = ArcileDispatchers(dispatcher, dispatcher, dispatcher, dispatcher)
        )

        assertEquals(listOf(first.reference), repository.loadMedia(null, forceRefresh = false).files.map { it.reference })
        assertEquals(listOf(first.reference), repository.loadMedia(null, forceRefresh = false).files.map { it.reference })

        repository.invalidate(emptyList())

        assertEquals(listOf(second.reference), repository.loadMedia(null, forceRefresh = false).files.map { it.reference })
        assertEquals(2, catalog.loadCalls)
        assertEquals(1, catalog.invalidateCalls)
    }

    @Test
    fun `gallery snapshot cache evicts least recently used volume`() = runTest {
        val catalog = mockk<ImageCatalogRepository>(relaxed = true)
        val search = mockk<SearchRepository>()
        coEvery {
            search.getFilesByCategory(any(), FileCategories.Videos.storageName)
        } returns Result.success(emptyList())
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val repository = DefaultMediaGalleryRepository(
            imageCatalogRepository = catalog,
            searchRepository = search,
            storageMutationNotifier = NoOpStorageMutationNotifier,
            dispatchers = ArcileDispatchers(dispatcher, dispatcher, dispatcher, dispatcher)
        )

        repeat(9) { volume ->
            repository.loadMedia(volume.toString(), categoryId = FileCategories.Videos.id.value)
        }
        repository.loadMedia("0", categoryId = FileCategories.Videos.id.value)

        coVerify(exactly = 10) {
            search.getFilesByCategory(any(), FileCategories.Videos.storageName)
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
    reference = "/storage/emulated/0/Pictures/$name",
    size = 100L,
    lastModified = name.hashCode().toLong(),
    isDirectory = false,
    extension = "jpg",
    mimeType = "image/jpeg"
)

private fun galleryVideo(name: String, parent: String) = FileModel(
    name = name,
    reference = "$parent/$name",
    size = 100L,
    lastModified = name.hashCode().toLong(),
    isDirectory = false,
    extension = "mp4",
    mimeType = "video/mp4"
)
