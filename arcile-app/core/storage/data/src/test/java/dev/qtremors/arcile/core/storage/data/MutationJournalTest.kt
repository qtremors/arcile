package dev.qtremors.arcile.core.storage.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.qtremors.arcile.core.storage.data.provider.VolumeProvider
import dev.qtremors.arcile.di.ArcileDispatchers
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
    }

    @Test
    fun `cleanup removes abandoned transfer temporary files`() = runTest {
        val temp = File(root, ".file.txt.arcile-transfer-123.tmp").apply { writeText("partial") }

        journal.recordTemporaryPath(temp.absolutePath)
        journal.cleanupAbandonedMutations()

        assertFalse(temp.exists())
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
}
