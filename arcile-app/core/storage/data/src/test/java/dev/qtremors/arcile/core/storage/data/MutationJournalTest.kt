package dev.qtremors.arcile.core.storage.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.qtremors.arcile.core.storage.data.provider.VolumeProvider
import dev.qtremors.arcile.core.runtime.di.ArcileDispatchers
import dev.qtremors.arcile.testutil.createTempStorageRoot
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@OptIn(ExperimentalCoroutinesApi::class)
class MutationJournalTest {
    private lateinit var context: Context
    private lateinit var root: File
    private lateinit var volumeProvider: VolumeProvider
    private lateinit var journal: DefaultMutationJournal

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("mutation_journal", Context.MODE_PRIVATE).edit().clear().commit()
        DefaultMutationJournal.clearForTest(context)
        root = createTempStorageRoot("mutation-journal-test")
        volumeProvider = mockk(relaxed = true)
        every { volumeProvider.activeStorageRoots } returns listOf(root.absolutePath)
        val dispatcher = UnconfinedTestDispatcher()
        journal = DefaultMutationJournal(
            context,
            volumeProvider,
            ArcileDispatchers(
                io = dispatcher,
                default = dispatcher,
                main = dispatcher,
                storage = dispatcher
            )
        )
    }

    @After
    fun teardown() {
        root.deleteRecursively()
        context.getSharedPreferences("mutation_journal", Context.MODE_PRIVATE).edit().clear().commit()
        DefaultMutationJournal.clearForTest(context)
    }

    @Test
    fun `cleanup removes abandoned transfer temporary files`() = runTest {
        val temp = File(root, ".file.txt.arcile-transfer-123.tmp").apply { writeText("partial") }

        journal.recordTemporaryPath(temp.absolutePath)
        journal.cleanupAbandonedMutations()

        assertFalse(temp.exists())
    }

    @Test
    fun `cleanup removes abandoned archive temporary files`() = runTest {
        val temp = File(root, ".bundle.zip.arcile-archive-123.tmp").apply { writeText("partial archive") }

        journal.recordTemporaryPath(temp.absolutePath)
        journal.cleanupAbandonedMutations()

        assertFalse(temp.exists())
    }

    @Test
    fun `cleanup preserves archive temporary files outside active roots`() = runTest {
        val outside = createTempDir(prefix = "mutation-journal-outside").canonicalFile
        val temp = File(outside, ".bundle.zip.arcile-archive-123.tmp").apply { writeText("partial archive") }
        try {
            journal.recordTemporaryPath(temp.absolutePath)
            journal.cleanupAbandonedMutations()

            assertTrue(temp.exists())
        } finally {
            outside.deleteRecursively()
        }
    }

    @Test
    fun `journal uses bounded no backup storage`() {
        val temp = File(root, ".file.txt.arcile-transfer-pending.tmp").apply { writeText("partial") }

        journal.recordTemporaryPath(temp.absolutePath)

        val store = DefaultMutationJournal.storeFile(context)
        assertTrue(store.canonicalPath.startsWith(context.noBackupFilesDir.canonicalPath))
        assertTrue(store.length() <= 512 * 1024)
    }

    @Test
    fun `cleanup removes incomplete trash fallback when original source still exists`() = runTest {
        val source = File(root, "source.txt").apply { writeText("original") }
        val arcileDir = File(root, ".arcile").apply { mkdirs() }
        val payload = File(arcileDir, ".trash/payload").apply {
            parentFile?.mkdirs()
            writeText("partial")
        }
        val metadata = File(arcileDir, ".metadata/payload.json").apply {
            parentFile?.mkdirs()
            writeText("{}")
        }

        journal.recordTrashFallback(source.absolutePath, payload.absolutePath, metadata.absolutePath)
        journal.cleanupAbandonedMutations()

        assertTrue(source.exists())
        assertFalse(payload.exists())
        assertFalse(metadata.exists())
    }

    @Test
    fun `cleanup preserves completed trash fallback when original source is gone`() = runTest {
        val source = File(root, "source.txt")
        val arcileDir = File(root, ".arcile").apply { mkdirs() }
        val payload = File(arcileDir, ".trash/payload").apply {
            parentFile?.mkdirs()
            writeText("trashed")
        }
        val metadata = File(arcileDir, ".metadata/payload.json").apply {
            parentFile?.mkdirs()
            writeText("{}")
        }

        journal.recordTrashFallback(source.absolutePath, payload.absolutePath, metadata.absolutePath)
        journal.cleanupAbandonedMutations()

        assertTrue(payload.exists())
        assertTrue(metadata.exists())
    }

    @Test
    fun `cleanup retries pending move source deletion idempotently`() = runTest {
        val source = File(root, "partially-deleted-source").apply { mkdirs() }
        File(source, "remaining.txt").writeText("remaining")
        val destination = File(root, "verified-destination").apply { mkdirs() }
        File(destination, "remaining.txt").writeText("remaining")

        journal.recordSourceCleanup(source.absolutePath, destination.absolutePath)
        journal.cleanupAbandonedMutations()
        journal.cleanupAbandonedMutations()

        assertFalse(source.exists())
        assertTrue(destination.exists())
        assertTrue(File(destination, "remaining.txt").exists())
        assertFalse(DefaultMutationJournal.storeFile(context).exists())
    }

    @Test
    fun `cleanup keeps source when verified destination is unavailable`() = runTest {
        val source = File(root, "source-without-destination").apply { mkdirs() }
        File(source, "keep.txt").writeText("keep")
        val missingDestination = File(root, "missing-destination")

        journal.recordSourceCleanup(source.absolutePath, missingDestination.absolutePath)
        journal.cleanupAbandonedMutations()

        assertTrue(source.exists())
        assertTrue(File(source, "keep.txt").exists())
        assertTrue(DefaultMutationJournal.storeFile(context).exists())
    }
}
