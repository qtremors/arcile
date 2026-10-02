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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.BasicFileAttributes
import dev.qtremors.arcile.core.storage.data.source.FileTransferEngine
import dev.qtremors.arcile.core.storage.domain.ConflictResolution
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertThrows

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
            ),
            recoveryFileKey = ::testFileKey
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
        val outside = kotlin.io.path.createTempDirectory(
            prefix = "mutation-journal-outside"
        ).toFile().canonicalFile
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
    fun `trash recovery preserves published payload after partial source deletion`() = runTest {
        val source = File(root, "partial-source").apply { mkdirs() }
        val deletedChild = File(source, "a.txt").apply { writeText("a") }
        File(source, "b.txt").writeText("b")
        val payload = File(root, ".arcile/.trash/payload").apply { mkdirs() }
        File(payload, "a.txt").writeText("a")
        File(payload, "b.txt").writeText("b")
        val metadata = File(root, ".arcile/.metadata/payload.json").apply {
            parentFile?.mkdirs()
            writeText("{}")
        }
        journal.recordTrashFallback(source.absolutePath, payload.absolutePath, metadata.absolutePath)
        journal.recordSourceCleanup(source.absolutePath, payload.absolutePath)
        deletedChild.delete()

        journal.cleanupAbandonedMutations()
        journal.cleanupAbandonedMutations()

        assertFalse(source.exists())
        assertEquals("a", File(payload, "a.txt").readText())
        assertEquals("b", File(payload, "b.txt").readText())
        assertTrue(metadata.exists())
    }

    @Test
    fun `legacy source cleanup evidence protects trash independent of entry order`() = runTest {
        val source = File(root, "legacy-source").apply { writeText("complete") }
        val payload = File(root, "legacy-payload").apply { writeText("complete") }
        val metadata = File(root, "legacy-metadata.json").apply { writeText("{}") }
        // This order leaves the fallback without a phase flag and recovers source first.
        journal.recordSourceCleanup(source.absolutePath, payload.absolutePath)
        journal.recordTrashFallback(source.absolutePath, payload.absolutePath, metadata.absolutePath)
        journal.cleanupAbandonedMutations()
        assertEquals("complete", payload.readText())
        assertTrue(metadata.exists())
    }

    @Test
    fun `trash cleanup phase survives removal of separate source cleanup record`() = runTest {
        val source = File(root, "phase-source").apply { writeText("complete") }
        val payload = File(root, "phase-payload").apply { writeText("complete") }
        val metadata = File(root, "phase-metadata.json").apply { writeText("{}") }
        journal.recordTrashFallback(source.absolutePath, payload.absolutePath, metadata.absolutePath)
        journal.recordSourceCleanup(source.absolutePath, payload.absolutePath)
        journal.forgetSourceCleanup(source.absolutePath, payload.absolutePath)
        journal.cleanupAbandonedMutations()
        assertEquals("complete", payload.readText())
        assertTrue(metadata.exists())
        assertTrue(source.exists())
        assertTrue(DefaultMutationJournal.storeFile(context).exists())
    }

    @Test
    fun `replacement restart at each publication boundary preserves a complete destination`() = runTest {
        for (boundary in listOf("record", "backup", "promote", "published", "complete")) {
            val source = File(root, "source-$boundary.txt").apply { writeText("new complete bytes") }
            val destination = File(root, "destination-$boundary").apply { mkdirs() }
            val target = File(destination, source.name).apply { writeText("old complete bytes") }
            val interruptedJournal = object : MutationJournal by journal {
                override fun recordReplacement(targetPath: String, stagingPath: String, backupPath: String, sourcePath: String?) {
                    journal.recordReplacement(targetPath, stagingPath, backupPath, sourcePath)
                    if (boundary == "record") throw SimulatedProcessDeath()
                }
                override fun markReplacementPublished(targetPath: String, backupPath: String) {
                    journal.markReplacementPublished(targetPath, backupPath)
                    if (boundary == "published") throw SimulatedProcessDeath()
                }
                override fun forgetReplacement(targetPath: String, backupPath: String) {
                    if (boundary == "complete") throw SimulatedProcessDeath()
                    journal.forgetReplacement(targetPath, backupPath)
                }
            }
            val engine = FileTransferEngine(
                validatePath = { Result.success(Unit) },
                mutationJournal = interruptedJournal,
                rename = { from, to ->
                    val moved = from.renameTo(to)
                    if (moved && ((boundary == "backup" && from == target) ||
                        (boundary == "promote" && from.name.contains("arcile-transfer")))) throw SimulatedProcessDeath()
                    moved
                }
            )
            try {
                engine.copyFiles(listOf(source.path), destination, mapOf(source.path to ConflictResolution.REPLACE))
                throw AssertionError("Expected process death at $boundary")
            } catch (_: SimulatedProcessDeath) {
                // Recreate the journal from its persisted bytes rather than rolling back.
            }
            newJournal().cleanupAbandonedMutations()
            newJournal().cleanupAbandonedMutations()
            assertEquals("boundary=$boundary", if (boundary in listOf("record", "backup")) "old complete bytes" else "new complete bytes", target.readText())
            assertEquals("new complete bytes", source.readText())
            val retainedBackups = destination.listFiles().orEmpty().filter { it.name.contains("arcile-replace") }
            if (boundary == "promote" && retainedBackups.isNotEmpty()) {
                // Windows may change birth metadata on rename before the updated
                // publication record is durable. Keep the ambiguous original.
                assertEquals("old complete bytes", retainedBackups.single().readText())
                assertTrue(DefaultMutationJournal.storeFile(context).exists())
                DefaultMutationJournal.clearForTest(context)
            } else {
                assertTrue("boundary=$boundary", destination.listFiles().orEmpty().none { it.name.contains("arcile-") })
                assertFalse(DefaultMutationJournal.storeFile(context).exists())
            }
        }
    }

    @Test
    fun `failed replacement rollback keeps backup journal until storage and rename recover`() = runTest {
        val source = File(root, "source.txt").apply { writeText("new") }
        val destination = File(root, "destination").apply { mkdirs() }
        val target = File(destination, source.name).apply { writeText("old") }
        val engine = FileTransferEngine(
            validatePath = { Result.success(Unit) }, mutationJournal = journal,
            rename = { from, to -> if (from.name.contains("arcile-transfer") || from.name.contains("arcile-replace")) false else from.renameTo(to) }
        )
        assertTrue(engine.moveFiles(listOf(source.path), destination, mapOf(source.path to ConflictResolution.REPLACE)).isFailure)
        val backup = destination.listFiles().orEmpty().single { it.name.contains("arcile-replace") }
        assertFalse(target.exists())
        assertEquals("old", backup.readText())
        assertEquals("new", source.readText())
        assertTrue(DefaultMutationJournal.storeFile(context).exists())
        newJournal(rename = { _, _ -> false }).cleanupAbandonedMutations()
        assertEquals("old", backup.readText())
        every { volumeProvider.activeStorageRoots } returns emptyList()
        newJournal().cleanupAbandonedMutations()
        assertEquals("old", backup.readText())
        assertTrue(DefaultMutationJournal.storeFile(context).exists())
        every { volumeProvider.activeStorageRoots } returns listOf(root.absolutePath)
        newJournal().cleanupAbandonedMutations()
        assertEquals("old", target.readText())
        assertFalse(backup.exists())
        assertFalse(DefaultMutationJournal.storeFile(context).exists())
    }

    @Test
    fun `move interrupted after publication rolls back original without deleting source`() = runTest {
        val source = File(root, "source.txt").apply { writeText("new") }
        val destination = File(root, "destination").apply { mkdirs() }
        val target = File(destination, source.name).apply { writeText("old") }
        val interrupted = object : MutationJournal by journal {
            override fun markReplacementPublished(targetPath: String, backupPath: String) {
                journal.markReplacementPublished(targetPath, backupPath)
                throw SimulatedProcessDeath()
            }
        }
        try {
            FileTransferEngine(validatePath = { Result.success(Unit) }, mutationJournal = interrupted)
                .moveFiles(listOf(source.path), destination, mapOf(source.path to ConflictResolution.REPLACE))
            throw AssertionError("Expected process death")
        } catch (_: SimulatedProcessDeath) { }
        newJournal().cleanupAbandonedMutations()
        assertEquals("old", target.readText())
        assertEquals("new", source.readText())
    }

    @Test
    fun `partial replacement move recovery keeps verified new target until source cleanup finishes`() = runTest {
        val source = File(root, "source").apply { mkdirs() }
        File(source, "a.txt").writeText("a")
        File(source, "b.txt").writeText("b")
        val destination = File(root, "destination").apply { mkdirs() }
        val target = File(destination, source.name).apply { mkdirs() }
        File(target, "old.txt").writeText("old")
        val result = FileTransferEngine(
            validatePath = { Result.success(Unit) }, mutationJournal = journal,
            deleteSourceEntry = { file -> if (file.name == "b.txt") false else file.delete() }
        ).moveFiles(listOf(source.path), destination, mapOf(source.path to ConflictResolution.REPLACE))
        assertTrue(result.isFailure)
        assertTrue(destination.listFiles().orEmpty().any { it.name.contains("arcile-replace") })
        newJournal().cleanupAbandonedMutations()
        newJournal().cleanupAbandonedMutations()
        assertFalse(source.exists())
        assertEquals("a", File(target, "a.txt").readText())
        assertEquals("b", File(target, "b.txt").readText())
        assertTrue(destination.listFiles().orEmpty().none { it.name.contains("arcile-") })
    }

    @Test
    fun `legacy replacement backups restore absent target and retain ambiguous originals`() = runTest {
        val backup = File(root, ".target.txt.arcile-replace-123.bak").apply { writeText("original") }
        journal.recordTemporaryPath(backup.path)
        newJournal().cleanupAbandonedMutations()
        assertEquals("original", File(root, "target.txt").readText())
        val ambiguous = File(root, ".target.txt.arcile-replace-456.bak").apply { writeText("another original") }
        journal.recordTemporaryPath(ambiguous.path)
        newJournal().cleanupAbandonedMutations()
        assertEquals("another original", ambiguous.readText())
        assertEquals("original", File(root, "target.txt").readText())
        assertTrue(DefaultMutationJournal.storeFile(context).readText().contains("REPLACEMENT"))
    }

    @Test
    fun `changed destination bytes preserve source even when size and timestamp match`() = runTest {
        val source = File(root, "source.txt").apply { writeText("original") }
        val target = File(root, "target.txt").apply { writeText("original") }
        journal.recordSourceCleanup(source.path, target.path)
        val modifiedAt = target.lastModified()
        target.writeText("modified")
        target.setLastModified(modifiedAt)
        newJournal().cleanupAbandonedMutations()
        assertEquals("original", source.readText())
        assertTrue(DefaultMutationJournal.storeFile(context).exists())
    }

    @Test
    fun `replaced destination with identical bytes cannot authorize deleting source`() = runTest {
        val source = File(root, "source.txt").apply { writeText("same bytes") }
        val target = File(root, "target.txt").apply { writeText("same bytes") }
        var generation = 0
        val identityProvider: (File) -> String = { file ->
            if (file == target) "destination-object-$generation" else testFileKey(file)
        }
        newJournal(fileKey = identityProvider).recordSourceCleanup(source.path, target.path)
        val replacement = File(root, "replacement.txt").apply {
            writeText("same bytes")
            setLastModified(target.lastModified())
        }
        Files.move(replacement.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
        generation++
        newJournal(fileKey = identityProvider).cleanupAbandonedMutations()
        assertEquals("same bytes", source.readText())
        assertTrue(DefaultMutationJournal.storeFile(context).exists())
    }

    @Test
    fun `recovery deletes verified children but preserves newly added source child`() = runTest {
        val source = File(root, "source").apply { mkdirs() }
        File(source, "original.txt").writeText("verified")
        val destination = File(root, "destination").apply { mkdirs() }
        File(destination, "original.txt").writeText("verified")
        journal.recordSourceCleanup(source.path, destination.path)
        val newChild = File(source, "new.txt").apply { writeText("added while closed") }
        newJournal().cleanupAbandonedMutations()
        newJournal().cleanupAbandonedMutations()
        assertFalse(File(source, "original.txt").exists())
        assertEquals("added while closed", newChild.readText())
        assertTrue(DefaultMutationJournal.storeFile(context).exists())
    }

    @Test
    fun `changed source file and replaced source folder survive recovery`() = runTest {
        val source = File(root, "source").apply { mkdirs() }
        File(source, "original.txt").writeText("verified")
        val destination = File(root, "destination").apply { mkdirs() }
        File(destination, "original.txt").writeText("verified")
        journal.recordSourceCleanup(source.path, destination.path)
        File(source, "original.txt").writeText("edited while closed")
        newJournal().cleanupAbandonedMutations()
        assertEquals("edited while closed", File(source, "original.txt").readText())
        val oldSource = File(root, "old-source")
        assertTrue(source.renameTo(oldSource))
        source.mkdirs()
        File(source, "original.txt").writeText("verified")
        newJournal().cleanupAbandonedMutations()
        assertEquals("verified", File(source, "original.txt").readText())
        assertEquals("edited while closed", File(oldSource, "original.txt").readText())
    }

    @Test
    fun `legacy source cleanup lacking verified identities preserves source and recovery record`() = runTest {
        val source = File(root, "source.txt").apply { writeText("source") }
        val target = File(root, "target.txt").apply { writeText("destination") }
        DefaultMutationJournal.storeFile(context).writeText(Json.encodeToString(listOf(buildJsonObject {
            put("type", "SOURCE_CLEANUP")
            put("sourcePath", source.path)
            put("destinationPath", target.path)
        })))
        newJournal().cleanupAbandonedMutations()
        assertEquals("source", source.readText())
        assertTrue(DefaultMutationJournal.storeFile(context).exists())
    }

    @Test
    fun `full or unreadable journal preserves existing recovery information`() = runTest {
        val store = DefaultMutationJournal.storeFile(context)
        store.writeText("unreadable legacy recovery")
        newJournal().cleanupAbandonedMutations()
        assertEquals("unreadable legacy recovery", store.readText())
        assertThrows(IOException::class.java) { journal.recordTemporaryPath(File(root, "new.tmp").path) }
        assertEquals("unreadable legacy recovery", store.readText())
        store.delete()
        store.writeText(Json.encodeToString((0 until 512).map { index -> buildJsonObject {
            put("type", "TEMPORARY_PATH")
            put("path", File(root, ".item-$index.arcile-transfer-123.tmp").path)
        } }))
        val before = store.readText()
        assertThrows(IOException::class.java) { journal.recordTemporaryPath(File(root, "overflow.tmp").path) }
        assertEquals(before, store.readText())
    }

    @Test
    fun `unavailable stable identities preserve source for explicit recovery`() = runTest {
        val source = File(root, "source.txt").apply { writeText("verified") }
        val target = File(root, "target.txt").apply { writeText("verified") }
        val dispatcher = UnconfinedTestDispatcher()
        val conservative = DefaultMutationJournal(
            context, volumeProvider, ArcileDispatchers(dispatcher, dispatcher, dispatcher, dispatcher),
            recoveryFileKey = { null }
        )
        conservative.recordSourceCleanup(source.path, target.path)
        conservative.cleanupAbandonedMutations()
        assertEquals("verified", source.readText())
        assertTrue(DefaultMutationJournal.storeFile(context).exists())
    }

    @Test
    fun `verified backup restores absent destination even without stable identities`() = runTest {
        val target = File(root, "target.txt").apply { writeText("original") }
        val staging = File(root, ".target.txt.arcile-transfer-123.tmp").apply { writeText("replacement") }
        val backup = File(root, ".target.txt.arcile-replace-123.bak")
        val dispatcher = UnconfinedTestDispatcher()
        val conservative = DefaultMutationJournal(
            context, volumeProvider, ArcileDispatchers(dispatcher, dispatcher, dispatcher, dispatcher),
            recoveryFileKey = { null }
        )
        conservative.recordTemporaryPath(staging.path)
        conservative.recordReplacement(target.path, staging.path, backup.path)
        assertTrue(target.renameTo(backup))
        conservative.cleanupAbandonedMutations()
        assertEquals("original", target.readText())
        assertFalse(backup.exists())
        assertFalse(staging.exists())
    }

    private fun newJournal(
        rename: (File, File) -> Boolean = { from, to -> from.renameTo(to) },
        fileKey: (File) -> String? = ::testFileKey
    ): DefaultMutationJournal {
        val dispatcher = UnconfinedTestDispatcher()
        return DefaultMutationJournal(context, volumeProvider, ArcileDispatchers(dispatcher, dispatcher, dispatcher, dispatcher), rename, fileKey)
    }

    // Windows JVMs do not expose inode keys. Birth time is a test-only stand-in
    // for the identity provider; production recovery keeps unknown identities.
    private fun testFileKey(file: File): String = Files.readAttributes(file.toPath(), BasicFileAttributes::class.java)
        .let { it.fileKey()?.toString() ?: it.creationTime().toString() }

    private class SimulatedProcessDeath : Error()

    @Test
    fun `cleanup keeps source when verified destination is unavailable`() = runTest {
        val source = File(root, "source-without-destination").apply { mkdirs() }
        File(source, "keep.txt").writeText("keep")
        val missingDestination = File(root, "missing-destination").apply { mkdirs() }
        File(missingDestination, "keep.txt").writeText("keep")

        journal.recordSourceCleanup(source.absolutePath, missingDestination.absolutePath)
        missingDestination.deleteRecursively()
        journal.cleanupAbandonedMutations()

        assertTrue(source.exists())
        assertTrue(File(source, "keep.txt").exists())
        assertTrue(DefaultMutationJournal.storeFile(context).exists())
    }
}
