package dev.qtremors.arcile.core.operation.android

import android.content.Context
import android.net.Uri
import android.os.storage.StorageManager
import androidx.test.core.app.ApplicationProvider
import dev.qtremors.arcile.core.operation.BulkFileOperationRequest
import dev.qtremors.arcile.core.operation.BulkFileOperationType
import dev.qtremors.arcile.core.operation.SaveToArcileImportItem
import dev.qtremors.arcile.core.storage.data.NoOpMutationJournal
import dev.qtremors.arcile.core.storage.domain.ArcileError
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.io.File
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SharedFileImporterTest {
    private lateinit var context: Context
    private lateinit var destination: File

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        destination = File(context.cacheDir, "shared-importer-test").apply {
            deleteRecursively()
            mkdirs()
        }
    }

    @Test
    fun `known files are reserved staged and committed as one batch`() = runTest {
        val first = source("first.txt", "first")
        val second = source("second.txt", "second")
        val allocator = RecordingSpaceAllocator()
        val importer = importer(allocator)

        val result = importer.import(
            request(
                item(first, "same.txt", first.length()),
                item(second, "same.txt", second.length())
            )
        ) {}

        assertTrue(result.isSuccess)
        assertEquals(first.length() + second.length(), allocator.knownBytes)
        assertFalse(allocator.hasUnknownSizes)
        assertEquals(1, allocator.commitChecks)
        assertEquals("first", File(destination, "same.txt").readText())
        assertEquals("second", File(destination, "same (1).txt").readText())
        assertTrue(destination.listFiles().orEmpty().none { it.name.contains(".arcile-import-") })
    }

    @Test
    fun `commit space loss removes every staged file and preserves destination`() = runTest {
        val existing = File(destination, "same.txt").apply { writeText("existing") }
        val first = source("pending-first.txt", "first")
        val second = source("pending-second.txt", "second")
        val allocator = RecordingSpaceAllocator(failAtCommit = true)

        val result = importer(allocator).import(
            request(
                item(first, "same.txt", first.length()),
                item(second, "new.txt", second.length())
            )
        ) {}

        assertTrue(result.exceptionOrNull() is ArcileError.InsufficientSpace)
        assertEquals("existing", existing.readText())
        assertFalse(File(destination, "same (1).txt").exists())
        assertFalse(File(destination, "new.txt").exists())
        assertTrue(destination.listFiles().orEmpty().none { it.name.contains(".arcile-import-") })
    }

    @Test
    fun `unknown file sizes remain streaming bounded and request headroom`() = runTest {
        val source = source("unknown.txt", "unknown")
        val allocator = RecordingSpaceAllocator()

        val result = importer(allocator).import(
            request(item(source, "unknown.txt", null))
        ) {}

        assertTrue(result.isSuccess)
        assertEquals(0L, allocator.knownBytes)
        assertTrue(allocator.hasUnknownSizes)
        assertEquals("unknown", File(destination, "unknown.txt").readText())
    }

    @Test
    fun `destination created during staging is preserved with a keep-both import`() = runTest {
        val source = source("racing-source.txt", "incoming")
        val importer = importer(RecordingSpaceAllocator()) {
            File(destination, "racing.txt").writeText("external")
        }

        val result = importer.import(
            request(item(source, "racing.txt", source.length()))
        ) {}

        assertTrue(result.isSuccess)
        assertEquals("external", File(destination, "racing.txt").readText())
        assertEquals("incoming", File(destination, "racing (1).txt").readText())
    }

    @Test
    fun `platform allocator reserves reclaimable bytes and rechecks commit headroom`() {
        val storageManager = mockk<StorageManager>()
        val uuid = UUID.randomUUID()
        val requestedBytes = 1_024L + FREE_SPACE_SAFETY_BUFFER_BYTES
        every { storageManager.getUuidForPath(destination) } returns uuid
        every { storageManager.getAllocatableBytes(uuid) } returnsMany listOf(
            requestedBytes,
            FREE_SPACE_SAFETY_BUFFER_BYTES
        )
        every { storageManager.allocateBytes(uuid, requestedBytes) } returns Unit
        val allocator = AndroidImportSpaceAllocator(context, storageManager)

        val reservation = allocator.reserve(destination, 1_024L, hasUnknownSizes = false)
        reservation.verifyBeforeCommit()

        verify(exactly = 1) { storageManager.allocateBytes(uuid, requestedBytes) }
        verify(exactly = 2) { storageManager.getAllocatableBytes(uuid) }
    }

    @Test
    fun `platform allocator maps full storage and allocation races to insufficient space`() {
        val storageManager = mockk<StorageManager>()
        val uuid = UUID.randomUUID()
        every { storageManager.getUuidForPath(destination) } returns uuid
        every { storageManager.getAllocatableBytes(uuid) } returns Long.MAX_VALUE
        every { storageManager.allocateBytes(uuid, any()) } throws IOException("ENOSPC")
        val allocator = AndroidImportSpaceAllocator(context, storageManager)

        val error = runCatching {
            allocator.reserve(destination, 1_024L, hasUnknownSizes = false)
        }.exceptionOrNull()

        assertTrue(error is ArcileError.InsufficientSpace)
    }

    @Test
    fun `platform allocator rejects genuinely full and concurrently consumed storage`() {
        val uuid = UUID.randomUUID()
        val fullStorage = mockk<StorageManager>()
        every { fullStorage.getUuidForPath(destination) } returns uuid
        every { fullStorage.getAllocatableBytes(uuid) } returns 1L
        val initialError = runCatching {
            AndroidImportSpaceAllocator(context, fullStorage)
                .reserve(destination, 1_024L, hasUnknownSizes = false)
        }.exceptionOrNull()

        val concurrentStorage = mockk<StorageManager>()
        val requestedBytes = 1_024L + FREE_SPACE_SAFETY_BUFFER_BYTES
        every { concurrentStorage.getUuidForPath(destination) } returns uuid
        every { concurrentStorage.getAllocatableBytes(uuid) } returnsMany listOf(
            requestedBytes,
            FREE_SPACE_SAFETY_BUFFER_BYTES - 1L
        )
        every { concurrentStorage.allocateBytes(uuid, requestedBytes) } returns Unit
        val reservation = AndroidImportSpaceAllocator(context, concurrentStorage)
            .reserve(destination, 1_024L, hasUnknownSizes = false)
        val commitError = runCatching { reservation.verifyBeforeCommit() }.exceptionOrNull()

        assertTrue(initialError is ArcileError.InsufficientSpace)
        assertTrue(commitError is ArcileError.InsufficientSpace)
        verify(exactly = 0) { fullStorage.allocateBytes(any<UUID>(), any<Long>()) }
    }

    private fun importer(
        spaceAllocator: ImportSpaceAllocator,
        beforeCommit: () -> Unit = {}
    ) = SharedFileImporter(
        context = context,
        mutationJournal = NoOpMutationJournal(),
        mutationFinalizer = null,
        onCheckpoint = { _, _, _ -> },
        spaceAllocator = spaceAllocator,
        beforeCommit = beforeCommit
    )

    private fun request(vararg items: SaveToArcileImportItem) = BulkFileOperationRequest(
        operationId = "shared-import-test",
        type = BulkFileOperationType.SAVE_TO_ARCILE_IMPORT,
        sourcePaths = emptyList(),
        destinationPath = destination.absolutePath,
        importItems = items.toList()
    )

    private fun item(source: File, displayName: String, sizeBytes: Long?) = SaveToArcileImportItem(
        uri = Uri.fromFile(source).toString(),
        displayName = displayName,
        sizeBytes = sizeBytes
    )

    private fun source(name: String, contents: String) = File(context.cacheDir, name).apply {
        writeText(contents)
    }

    private class RecordingSpaceAllocator(
        private val failAtCommit: Boolean = false
    ) : ImportSpaceAllocator {
        var knownBytes: Long = -1L
        var hasUnknownSizes: Boolean = false
        var commitChecks: Int = 0

        override fun reserve(
            destination: File,
            knownContentBytes: Long,
            hasUnknownSizes: Boolean
        ): ImportSpaceReservation {
            knownBytes = knownContentBytes
            this.hasUnknownSizes = hasUnknownSizes
            return ImportSpaceReservation {
                commitChecks += 1
                if (failAtCommit) throw ArcileError.InsufficientSpace()
            }
        }
    }
}
