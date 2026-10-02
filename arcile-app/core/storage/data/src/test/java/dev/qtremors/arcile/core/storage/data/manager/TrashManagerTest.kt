package dev.qtremors.arcile.core.storage.data.manager

import android.content.Context
import android.content.ContentResolver
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import dev.qtremors.arcile.core.storage.data.FolderStatsStore
import dev.qtremors.arcile.core.storage.data.MutationFinalizer
import dev.qtremors.arcile.core.storage.data.MutationJournal
import dev.qtremors.arcile.core.storage.data.DefaultMutationJournal
import dev.qtremors.arcile.core.runtime.di.ArcileDispatchers
import dev.qtremors.arcile.core.storage.data.provider.VolumeProvider
import dev.qtremors.arcile.core.storage.data.source.FileTransferEngine
import dev.qtremors.arcile.core.storage.data.source.StorageQueryClient
import dev.qtremors.arcile.core.storage.domain.FolderStatUpdate
import dev.qtremors.arcile.core.storage.domain.FolderStats
import dev.qtremors.arcile.core.storage.domain.DestinationRequiredException
import dev.qtremors.arcile.core.storage.domain.FileModel
import dev.qtremors.arcile.core.storage.domain.StorageKind
import dev.qtremors.arcile.core.storage.domain.StorageNodeRef
import dev.qtremors.arcile.core.storage.domain.TrashMetadata
import dev.qtremors.arcile.core.storage.domain.TrashRestoreStatus
import dev.qtremors.arcile.core.storage.domain.TrashStorageUsage
import dev.qtremors.arcile.testutil.createTempStorageRoot
import dev.qtremors.arcile.testutil.testVolume
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.BasicFileAttributes

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TrashManagerTest {

    private lateinit var context: Context
    private lateinit var volumeProvider: VolumeProvider
    private lateinit var storageQueryClient: StorageQueryClient
    private lateinit var folderStatsStore: FolderStatsStore
    private lateinit var trashManager: DefaultTrashManager
    private lateinit var root: File

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        DefaultMutationJournal.clearForTest(context)
        root = createTempStorageRoot("trash-test")

        volumeProvider = mockk(relaxed = true)
        val vol = testVolume("primary", root.absolutePath, kind = StorageKind.INTERNAL)
        every { volumeProvider.activeStorageRoots } returns listOf(root.absolutePath)
        coEvery { volumeProvider.currentVolumes() } returns listOf(vol)
        every { volumeProvider.observeStorageVolumes() } returns flowOf(listOf(vol))

        storageQueryClient = mockk(relaxed = true)
        folderStatsStore = object : FolderStatsStore {
            override suspend fun getCached(paths: Collection<String>): Map<String, FolderStats> = emptyMap()
            override fun observeUpdates() = emptyFlow<FolderStatUpdate>()
            override fun queue(paths: List<String>) = Unit
            override suspend fun invalidate(paths: Collection<String>) = Unit
            override suspend fun clear() = Unit
        }

        trashManager = DefaultTrashManager(
            context,
            volumeProvider,
            MutationFinalizer(context, storageQueryClient, volumeProvider, folderStatsStore)
        )
    }

    @After
    fun teardown() {
        root.deleteRecursively()
        DefaultMutationJournal.clearForTest(context)
    }

    private fun newTrashManager(): DefaultTrashManager {
        return DefaultTrashManager(
            context,
            volumeProvider,
            MutationFinalizer(context, storageQueryClient, volumeProvider, folderStatsStore)
        )
    }

    @Test
    fun `moveToTrash moves file to trash directory and creates metadata`() = runTest {
        val file = File(root, "test.txt").apply { 
            createNewFile()
            writeText("trash me")
        }

        val result = trashManager.moveToTrash(listOf(file.absolutePath))
        
        assertTrue(result.isSuccess)
        assertFalse(file.exists()) // original is gone

        val arcileDir = File(root, ".arcile")
        val trashDir = File(arcileDir, ".trash")
        val metadataDir = File(arcileDir, ".metadata")

        assertTrue(trashDir.exists())
        assertTrue(metadataDir.exists())

        // One file in trash, one metadata in metadata
        assertEquals(2, trashDir.listFiles()?.size ?: 0) // contains the trashed file + .nomedia
        assertEquals(1, metadataDir.listFiles()?.size ?: 0) // contains one .json file
        val metadataText = metadataDir.listFiles()?.single { it.extension == "json" }?.readText().orEmpty()
        assertTrue(metadataText.trimStart().startsWith("{"))
        assertTrue(metadataText.contains("\"schemaVersion\""))
        assertTrue(metadataText.contains(file.absolutePath.replace("\\", "\\\\")))
    }

    @Test
    fun `getTrashFiles returns correct metadata`() = runTest {
        val file = File(root, "file1.txt").apply { createNewFile() }
        trashManager.moveToTrash(listOf(file.absolutePath))

        val result = trashManager.getTrashFiles()
        assertTrue(result.isSuccess)
        
        val trashItems = result.getOrThrow()
        assertEquals(1, trashItems.size)
        assertEquals(file.absolutePath, trashItems.first().originalPath)
        assertEquals("primary", trashItems.first().sourceVolumeId)
        assertEquals(TrashRestoreStatus.ORIGINAL_AVAILABLE, trashItems.first().restoreStatus)
    }

    @Test
    fun `new manager can list plaintext trash after private crypto prefs are cleared`() = runTest {
        val file = File(root, "survives-reinstall.txt").apply {
            createNewFile()
            writeText("still here")
        }
        val originalPath = file.absolutePath

        assertTrue(trashManager.moveToTrash(listOf(originalPath)).isSuccess)
        context.getSharedPreferences("trash_crypto_prefs", Context.MODE_PRIVATE).edit().clear().commit()

        val listed = newTrashManager().getTrashFiles().getOrThrow()

        assertEquals(1, listed.size)
        assertEquals(originalPath, listed.first().originalPath)
        assertEquals("survives-reinstall.txt", listed.first().fileModel.name)
    }

    @Test
    fun `restoreFromTrash restores file to original location`() = runTest {
        val file = File(root, "important.txt").apply { 
            createNewFile()
            writeText("data")
        }
        val originalPath = file.absolutePath
        trashManager.moveToTrash(listOf(originalPath))
        assertFalse(file.exists())

        val trashItems = trashManager.getTrashFiles().getOrThrow()
        val trashId = trashItems.first().id

        val restoreResult = trashManager.restoreFromTrash(listOf(trashId), null)
        assertTrue(restoreResult.isSuccess)

        val restoredFile = File(originalPath)
        assertTrue(restoredFile.exists())
        assertEquals("data", restoredFile.readText())

        // Ensure trash is empty now
        val newTrashItems = trashManager.getTrashFiles().getOrThrow()
        assertTrue(newTrashItems.isEmpty())
    }

    @Test
    fun `restoreFromTrash restores to conflict name when original path exists`() = runTest {
        val file = File(root, "conflict.txt").apply {
            createNewFile()
            writeText("trashed")
        }
        val originalPath = file.absolutePath
        trashManager.moveToTrash(listOf(originalPath))
        File(originalPath).writeText("new file")

        val trashItem = trashManager.getTrashFiles().getOrThrow().single()
        assertEquals(TrashRestoreStatus.ORIGINAL_CONFLICT_RENAME, trashItem.restoreStatus)

        val restoreResult = trashManager.restoreFromTrash(listOf(trashItem.id), null)

        assertTrue(restoreResult.isSuccess)
        assertEquals("new file", File(originalPath).readText())
        val restoredConflict = root.listFiles()?.single { it.name.startsWith("conflict.restore-conflict-") && it.name.endsWith(".txt") }
        assertEquals("trashed", restoredConflict?.readText())
    }

    @Test
    fun `getTrashFiles skips corrupted metadata gracefully`() = runTest {
        val metadataDir = File(File(root, ".arcile"), ".metadata")
        metadataDir.mkdirs()
        File(metadataDir, "corrupted.json").writeBytes(byteArrayOf(0x01, 0x02, 0x03)) // too short

        val result = trashManager.getTrashFiles()
        assertTrue(result.isSuccess)
        assertEquals(0, result.getOrThrow().size)
        assertFalse(File(metadataDir, "corrupted.json").exists())
    }

    @Test
    fun `restoreFromTrash restores recovered item to selected destination`() = runTest {
        val arcileDir = File(root, ".arcile")
        val metadataDir = File(arcileDir, ".metadata").apply { mkdirs() }
        val trashDir = File(arcileDir, ".trash").apply { mkdirs() }
        val trashId = "corrupted"
        File(metadataDir, "$trashId.json").writeBytes(byteArrayOf(0x01, 0x02, 0x03))
        File(trashDir, trashId).writeText("recoverable")
        val destination = File(root, "restore-destination").apply { mkdirs() }

        val listed = trashManager.getTrashFiles().getOrThrow()
        assertEquals(1, listed.size)
        assertEquals("Recovered Item ($trashId)", listed.first().fileModel.name)
        assertEquals(TrashRestoreStatus.RECOVERED_ITEM, listed.first().restoreStatus)

        val originalRestore = trashManager.restoreFromTrash(listOf(trashId), null)
        assertTrue(originalRestore.isFailure)
        assertTrue(originalRestore.exceptionOrNull() is DestinationRequiredException)

        val destinationRestore = trashManager.restoreFromTrash(listOf(trashId), destination.absolutePath)
        assertTrue(destinationRestore.isSuccess)
        assertEquals("recoverable", File(destination, "Recovered Item ($trashId)").readText())
        assertFalse(File(trashDir, trashId).exists())
        assertFalse(File(metadataDir, "$trashId.json").exists())
    }

    @Test
    fun `orphan metadata without trash payload is cleaned up`() = runTest {
        val metadataDir = File(File(root, ".arcile"), ".metadata").apply { mkdirs() }
        val metadataFile = File(metadataDir, "orphan.json").apply {
            writeText("""{"schemaVersion":1,"id":"orphan","originalPath":"${root.absolutePath}/gone.txt","deletionTime":123}""")
        }

        val result = trashManager.getTrashFiles()

        assertTrue(result.isSuccess)
        assertTrue(result.getOrThrow().isEmpty())
        assertFalse(metadataFile.exists())
    }

    @Test
    fun `moveToTrash fails on unsupported storage without moving file`() = runTest {
        val file = File(root, "temporary.txt").apply {
            createNewFile()
            writeText("keep me")
        }
        val temporaryVolume = testVolume("temporary", root.absolutePath, kind = StorageKind.OTG)
        coEvery { volumeProvider.currentVolumes() } returns listOf(temporaryVolume)

        val result = trashManager.moveToTrash(listOf(file.absolutePath))

        assertTrue(result.isFailure)
        assertTrue(file.exists())
        assertEquals("keep me", file.readText())
        assertFalse(File(root, ".arcile").exists())
    }

    @Test
    fun `copy fallback verifies payload before deleting original`() = runTest {
        val directory = File(root, "folder").apply { mkdirs() }
        File(directory, "child.txt").writeText("copied safely")

        val result = trashManager.moveToTrash(listOf(directory.absolutePath))

        assertTrue(result.isSuccess)
        assertFalse(directory.exists())
        val trashItem = trashManager.getTrashFiles().getOrThrow().single()
        val payload = File(trashItem.fileModel.reference)
        assertTrue(payload.isDirectory)
        assertEquals("copied safely", File(payload, "child.txt").readText())
        assertNotEquals(directory.absolutePath, payload.absolutePath)
    }

    @Test
    fun `copy fallback uses transfer engine progress and clears trash journal`() = runTest {
        val directory = File(root, "fallback-folder").apply { mkdirs() }
        File(directory, "child.txt").writeText("copied with progress")
        val journal = RecordingMutationJournal()
        val fallbackManager = DefaultTrashManager(
            context,
            volumeProvider,
            MutationFinalizer(context, storageQueryClient, volumeProvider, folderStatsStore),
            mutationJournal = journal,
            rename = { source, target ->
                if (source == directory) false else source.renameTo(target)
            }
        )
        val progressPaths = mutableListOf<String?>()

        val result = fallbackManager.moveToTrash(listOf(directory.absolutePath)) {
            progressPaths += it.currentPath
        }

        assertTrue(result.isSuccess)
        assertFalse(directory.exists())
        assertTrue(progressPaths.isNotEmpty())
        assertEquals(1, journal.recordedTrashFallbacks)
        assertEquals(1, journal.forgottenTrashFallbacks)
        assertTrue(journal.temporaryPaths.isEmpty())
        val trashItem = fallbackManager.getTrashFiles().getOrThrow().single()
        assertEquals("copied with progress", File(trashItem.fileModel.reference, "child.txt").readText())
    }

    @Test
    fun `moveToTrashTargets deletes media store row by supplied content uri`() = runTest {
        val file = File(root, "indexed-video.mp4").apply {
            createNewFile()
            writeText("video")
        }
        val resolver = mockk<ContentResolver>(relaxed = true)
        val uri = Uri.parse("content://media/external_primary/video/media/42")
        every { resolver.delete(uri, null, null) } returns 1
        val resolverContext = ResolverContext(context, resolver)
        val manager = DefaultTrashManager(
            resolverContext,
            volumeProvider,
            MutationFinalizer(resolverContext, storageQueryClient, volumeProvider, folderStatsStore)
        )

        val result = manager.moveToTrashTargets(
            listOf(
                TrashTarget(
                    path = file.absolutePath,
                    nodeRef = StorageNodeRef.mediaStore(
                        id = 42L,
                        volumeName = "external_primary",
                        contentUri = uri.toString(),
                        displayPath = file.absolutePath,
                        localPath = file.absolutePath
                    )
                )
            )
        )

        assertTrue(result.isSuccess)
        verify(exactly = 1) { resolver.delete(uri, null, null) }
    }

    @Test
    fun `path only moveToTrash succeeds without media store DATA query`() = runTest {
        val file = File(root, "path-only.txt").apply {
            createNewFile()
            writeText("local")
        }
        val resolver = mockk<ContentResolver>(relaxed = true)
        val resolverContext = ResolverContext(context, resolver)
        val manager = DefaultTrashManager(
            resolverContext,
            volumeProvider,
            MutationFinalizer(resolverContext, storageQueryClient, volumeProvider, folderStatsStore)
        )

        val result = manager.moveToTrash(listOf(file.absolutePath))

        assertTrue(result.isSuccess)
        verify(exactly = 0) { resolver.query(any(), any(), any<String>(), any<Array<String>>(), any()) }
    }

    @Test
    fun `getTrashStorageUsage sums files and nested folder payloads`() = runTest {
        val file = File(root, "one.txt").apply { writeText("1234") }
        val directory = File(root, "folder").apply { mkdirs() }
        File(directory, "nested.txt").writeText("123456")

        assertTrue(trashManager.moveToTrash(listOf(file.absolutePath, directory.absolutePath)).isSuccess)

        val usage = trashManager.getTrashStorageUsage().getOrThrow()

        assertEquals(10L, usage.totalBytes)
        assertEquals(10L, usage.byVolumeId["primary"])
    }

    @Test
    fun `getTrashStorageUsage ignores nomedia and missing trash payloads`() = runTest {
        val usage = trashManager.getTrashStorageUsage().getOrThrow()

        assertEquals(0L, usage.totalBytes)
        assertTrue(usage.byVolumeId.isEmpty())
    }

    @Test
    fun `failed trash cleanup preserves every byte and metadata through restart`() = runTest {
        exercisePartialCleanup(restoring = false, cancel = false)
    }

    @Test
    fun `cancelled trash cleanup preserves every byte and metadata through restart`() = runTest {
        exercisePartialCleanup(restoring = false, cancel = true)
    }

    @Test
    fun `failed restore cleanup preserves complete restored folder through restart`() = runTest {
        exercisePartialCleanup(restoring = true, cancel = false)
    }

    @Test
    fun `cancelled restore cleanup preserves complete restored folder through restart`() = runTest {
        exercisePartialCleanup(restoring = true, cancel = true)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private suspend fun exercisePartialCleanup(restoring: Boolean, cancel: Boolean) {
        val folder = File(root, "partial-folder").apply { mkdirs() }
        val contents = mapOf("a.txt" to "original a", "b.txt" to "original b")
        contents.forEach { (name, text) -> File(folder, name).writeText(text) }
        val item = if (restoring) {
            trashManager.moveToTrash(listOf(folder.absolutePath)).getOrThrow()
            trashManager.getTrashFiles().getOrThrow().single()
        } else null
        val source = item?.let { File(it.fileModel.reference) } ?: folder
        val dispatcher = UnconfinedTestDispatcher()
        val dispatchers = ArcileDispatchers(dispatcher, dispatcher, dispatcher, dispatcher)
        fun newJournal() = DefaultMutationJournal(
            context, volumeProvider, dispatchers,
            recoveryFileKey = { file -> Files.readAttributes(file.toPath(), BasicFileAttributes::class.java)
                .let { it.fileKey()?.toString() ?: it.creationTime().toString() } }
        )
        val journal = newJournal()
        var deleted: File? = null
        val engine = FileTransferEngine(
            validatePath = { Result.success(Unit) },
            mutationJournal = journal,
            deleteSourceEntry = { file ->
                if (deleted == null && file.isFile) {
                    file.delete().also { if (it) deleted = file }
                } else if (cancel) {
                    throw CancellationException("Injected cleanup interruption")
                } else false
            }
        )
        val manager = DefaultTrashManager(
            context, volumeProvider,
            MutationFinalizer(context, storageQueryClient, volumeProvider, folderStatsStore),
            dispatchers = dispatchers, mutationJournal = journal,
            rename = { _, _ -> false }, transferEngine = engine
        )
        val error = try {
            val result = if (restoring) manager.restoreFromTrash(listOf(requireNotNull(item).id), null)
            else manager.moveToTrash(listOf(folder.absolutePath))
            assertTrue(result.isFailure)
            result.exceptionOrNull()
        } catch (error: CancellationException) {
            error
        }
        assertTrue(error != null)
        assertEquals(cancel, error is CancellationException)
        assertTrue(source.exists())
        assertFalse(requireNotNull(deleted).exists())
        val metadata = File(root, ".arcile/.metadata").listFiles().orEmpty().single { it.extension == "json" }
        val output = if (restoring) folder else File(manager.getTrashFiles().getOrThrow().single().fileModel.reference)
        contents.forEach { (name, text) -> assertEquals(text, File(output, name).readText()) }
        assertTrue(metadata.exists())
        assertTrue(DefaultMutationJournal.storeFile(context).exists())

        // Recreate the journal from disk, then repeat recovery to check idempotence.
        newJournal().cleanupAbandonedMutations()
        newJournal().cleanupAbandonedMutations()
        assertFalse(source.exists())
        contents.forEach { (name, text) -> assertEquals(text, File(output, name).readText()) }
        if (!restoring) {
            assertTrue(metadata.exists())
            assertEquals(folder.absolutePath, newTrashManager().getTrashFiles().getOrThrow().single().originalPath)
        }
    }

    @Test
    fun `trash failure before publication rolls back metadata and staging`() = runTest {
        val source = File(root, "failed-copy.txt").apply { writeText("original") }
        val manager = DefaultTrashManager(
            context, volumeProvider,
            MutationFinalizer(context, storageQueryClient, volumeProvider, folderStatsStore),
            rename = { _, _ -> false },
            transferEngine = FileTransferEngine(
                validatePath = { Result.success(Unit) },
                afterCopy = { _, target -> target.appendText("corrupt") }
            )
        )
        assertTrue(manager.moveToTrash(listOf(source.absolutePath)).isFailure)
        assertEquals("original", source.readText())
        assertTrue(manager.getTrashFiles().getOrThrow().isEmpty())
        assertTrue(File(root, ".arcile/.trash").listFiles().orEmpty().all { it.name == ".nomedia" })
    }

    private class RecordingMutationJournal : MutationJournal {
        val temporaryPaths = mutableSetOf<String>()
        var recordedTrashFallbacks = 0
        var forgottenTrashFallbacks = 0

        override fun recordTemporaryPath(path: String) {
            temporaryPaths += path
        }

        override fun forgetTemporaryPath(path: String) {
            temporaryPaths -= path
        }

        override fun recordTrashFallback(sourcePath: String, payloadPath: String, metadataPath: String) {
            recordedTrashFallbacks += 1
        }

        override fun forgetTrashFallback(payloadPath: String, metadataPath: String) {
            forgottenTrashFallbacks += 1
        }

        override suspend fun cleanupAbandonedMutations() = Unit
    }

    private class ResolverContext(
        base: Context,
        private val resolver: ContentResolver
    ) : android.content.ContextWrapper(base) {
        override fun getApplicationContext(): Context = this
        override fun getContentResolver(): ContentResolver = resolver
    }
}
