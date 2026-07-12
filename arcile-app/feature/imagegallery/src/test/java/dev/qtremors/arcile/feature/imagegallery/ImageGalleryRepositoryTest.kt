package dev.qtremors.arcile.feature.imagegallery

import dev.qtremors.arcile.core.storage.domain.FileModel
import dev.qtremors.arcile.core.storage.domain.ImageCatalogItem
import dev.qtremors.arcile.core.storage.domain.ImageCatalogRepository
import dev.qtremors.arcile.core.storage.domain.ImageCatalogSnapshot
import dev.qtremors.arcile.core.storage.domain.NoOpStorageMutationNotifier
import dev.qtremors.arcile.core.runtime.di.ArcileDispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ImageGalleryRepositoryTest {
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
            catalog,
            NoOpStorageMutationNotifier,
            ArcileDispatchers(dispatcher, dispatcher, dispatcher, dispatcher)
        )

        assertEquals(listOf(first.absolutePath), repository.loadImages(null, forceRefresh = false).files.map { it.absolutePath })
        assertEquals(listOf(first.absolutePath), repository.loadImages(null, forceRefresh = false).files.map { it.absolutePath })

        repository.invalidate(emptyList())

        assertEquals(listOf(second.absolutePath), repository.loadImages(null, forceRefresh = false).files.map { it.absolutePath })
        assertEquals(2, catalog.loadCalls)
        assertEquals(1, catalog.invalidateCalls)
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
