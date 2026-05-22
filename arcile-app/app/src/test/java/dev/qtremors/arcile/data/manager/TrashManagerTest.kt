package dev.qtremors.arcile.data.manager

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.qtremors.arcile.data.FolderStatsStore
import dev.qtremors.arcile.data.MutationFinalizer
import dev.qtremors.arcile.data.MutationJournal
import dev.qtremors.arcile.data.provider.VolumeProvider
import dev.qtremors.arcile.data.source.MediaStoreClient
import dev.qtremors.arcile.domain.FolderStatUpdate
import dev.qtremors.arcile.domain.FolderStats
import dev.qtremors.arcile.domain.DestinationRequiredException
import dev.qtremors.arcile.domain.StorageKind
import dev.qtremors.arcile.domain.TrashRestoreStatus
import dev.qtremors.arcile.testutil.createTempStorageRoot
import dev.qtremors.arcile.testutil.testVolume
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.emptyFlow
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TrashManagerTest {

    private lateinit var context: Context
    private lateinit var volumeProvider: VolumeProvider
    private lateinit var mediaStoreClient: MediaStoreClient
    private lateinit var folderStatsStore: FolderStatsStore
    private lateinit var trashManager: DefaultTrashManager
    private lateinit var root: File

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        root = createTempStorageRoot("trash-test")

        volumeProvider = mockk(relaxed = true)
        val vol = testVolume("primary", root.absolutePath, kind = StorageKind.INTERNAL)
        every { volumeProvider.activeStorageRoots } returns listOf(root.absolutePath)
        coEvery { volumeProvider.currentVolumes() } returns listOf(vol)
        every { volumeProvider.observeStorageVolumes() } returns flowOf(listOf(vol))

        mediaStoreClient = mockk(relaxed = true)
        folderStatsStore = object : FolderStatsStore {
            override suspend fun getCached(paths: Collection<String>): Map<String, FolderStats> = emptyMap()
            override fun observeUpdates() = emptyFlow<FolderStatUpdate>()
            override fun queue(paths: List<String>) = Unit
            override fun invalidate(paths: Collection<String>) = Unit
        }

        trashManager = DefaultTrashManager(
            context,
            volumeProvider,
            MutationFinalizer(context, mediaStoreClient, volumeProvider, folderStatsStore)
        )
    }

    @After
    fun teardown() {
        root.deleteRecursively()
    }

    private fun newTrashManager(): DefaultTrashManager {
        return DefaultTrashManager(
            context,
            volumeProvider,
            MutationFinalizer(context, mediaStoreClient, volumeProvider, folderStatsStore)
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
        val payload = File(trashItem.fileModel.absolutePath)
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
            MutationFinalizer(context, mediaStoreClient, volumeProvider, folderStatsStore),
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
        assertEquals("copied with progress", File(trashItem.fileModel.absolutePath, "child.txt").readText())
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
}
