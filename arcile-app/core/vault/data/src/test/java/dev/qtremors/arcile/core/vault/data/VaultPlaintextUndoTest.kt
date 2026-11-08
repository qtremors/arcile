package dev.qtremors.arcile.core.vault.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.qtremors.arcile.core.runtime.di.ArcileDispatchers
import dev.qtremors.arcile.core.storage.domain.FileModel
import dev.qtremors.arcile.core.storage.domain.StorageKind
import dev.qtremors.arcile.core.storage.domain.StorageMutationResult
import dev.qtremors.arcile.core.storage.domain.StorageVolume
import dev.qtremors.arcile.core.storage.domain.TrashMetadata
import dev.qtremors.arcile.core.storage.domain.TrashRepository
import dev.qtremors.arcile.core.storage.domain.VolumeRepository
import dev.qtremors.arcile.core.vault.domain.*
import io.mockk.coEvery
import io.mockk.mockk
import java.io.File
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@OptIn(ExperimentalCoroutinesApi::class)
class VaultPlaintextUndoTest {
    @get:Rule val temporary = TemporaryFolder()
    private lateinit var context: Context

    @Before fun setup() {
        context = ApplicationProvider.getApplicationContext()
        File(context.noBackupFilesDir, ROOT_DIRECTORY).deleteRecursively()
    }

    @After fun cleanup() { File(context.noBackupFilesDir, ROOT_DIRECTORY).deleteRecursively() }

    @Test fun `export undo deletes only the unchanged plaintext copy`() = runTest {
        fixture { f ->
            val original = f.file("Report.txt", "private report")
            val destination = temporary.newFolder("export")
            val proof = requireNotNull(f.export(original, destination, move = false).items.single().plaintextUndo)
            assertNull(proof.origin)
            assertTrue(File(destination, "Report.txt").isFile)

            val undone = f.coordinator.undoPlaintext(listOf(proof), neverCancelled)

            assertEquals(VaultItemOutcome.COMPLETED, undone.items.single().outcome)
            assertFalse(File(destination, "Report.txt").exists())
            assertEquals("private report", f.text(f.rows().single()))
        }
    }

    @Test fun `edited exported plaintext is never deleted by undo`() = runTest {
        fixture { f ->
            val destination = temporary.newFolder("export")
            val proof = requireNotNull(f.export(f.file("Report.txt", "private"), destination, move = false).items.single().plaintextUndo)
            File(destination, "Report.txt").writeText("edited by another app, longer")

            val undone = f.coordinator.undoPlaintext(listOf(proof), neverCancelled)

            assertEquals(VaultItemOutcome.FAILED, undone.items.single().outcome)
            assertTrue(undone.items.single().failure is VaultFailure.SourceChanged)
            assertEquals("edited by another app, longer", File(destination, "Report.txt").readText())
        }
    }

    @Test fun `move out undo re-encrypts a folder into its original location before deleting plaintext`() = runTest {
        fixture { f ->
            val parent = f.folder("Source")
            val parentId = requireNotNull(parent.ref.directoryId)
            val folder = f.folder("Docs", parentId)
            f.file("Inside.txt", "inside text", requireNotNull(folder.ref.directoryId))
            f.folder("Empty", requireNotNull(folder.ref.directoryId))
            val destination = temporary.newFolder("export")
            val proof = requireNotNull(f.export(folder, destination, move = true).items.single().plaintextUndo)
            assertEquals(VaultUndoOrigin.VaultFolder(f.vault, parentId, "Docs"), proof.origin)
            assertTrue(f.rows(parentId).isEmpty())

            val undone = f.coordinator.undoPlaintext(listOf(proof), neverCancelled)

            assertEquals(VaultItemOutcome.COMPLETED, undone.items.single().outcome)
            assertFalse(File(destination, "Docs").exists())
            val restored = f.rows(parentId).single()
            assertEquals("Docs", restored.name)
            val children = f.rows(requireNotNull(restored.ref.directoryId))
            assertEquals(setOf("Inside.txt", "Empty"), children.map { it.name }.toSet())
            assertEquals("inside text", f.text(children.single { it.name == "Inside.txt" }))
        }
    }

    @Test fun `move out undo never replaces an item now using the original name`() = runTest {
        fixture { f ->
            val original = f.file("Report.txt", "moved out")
            val destination = temporary.newFolder("export")
            val proof = requireNotNull(f.export(original, destination, move = true).items.single().plaintextUndo)
            val occupant = f.file("Report.txt", "new vault file")

            val undone = f.coordinator.undoPlaintext(listOf(proof), neverCancelled)

            assertEquals(VaultItemOutcome.FAILED, undone.items.single().outcome)
            assertTrue(undone.items.single().failure is VaultFailure.NameConflict)
            assertEquals("moved out", File(destination, "Report.txt").readText())
            assertEquals(listOf(occupant), f.rows())
            assertEquals("new vault file", f.text(occupant))
        }
    }

    @Test fun `trash undo restores the vault item and then removes it from main trash`() = runTest {
        val trashRoot = temporary.newFolder("trash")
        val trash = mockk<TrashRepository>()
        val deleted = mutableListOf<String>()
        val published = mutableListOf<TrashMetadata>()
        coEvery { trash.acceptExternalItem(any(), any(), any()) } coAnswers {
            val id = UUID.randomUUID().toString()
            val stage = File(trashRoot, "staging/$id").apply { mkdirs() }
            val payload = File(stage, "payload")
            secondArg<suspend (String) -> Unit>()(payload.path)
            val final = File(trashRoot, id)
            check(payload.renameTo(final))
            published += TrashMetadata(id, "", 0L, FileModel(firstArg(), final.path), "test", StorageKind.INTERNAL)
            thirdArg<suspend () -> Unit>()()
            Result.success(Unit)
        }
        coEvery { trash.getTrashFiles() } answers { Result.success(published.toList()) }
        coEvery { trash.deletePermanentlyFromTrash(any()) } coAnswers {
            deleted += firstArg<List<String>>()
            StorageMutationResult.Completed
        }
        fixture(trash) { f ->
            val original = f.file("Notes.txt", "trashed notes")
            val reservation = f.coordinator.prepareExport(listOf(original.ref)).getOrThrow()
            val proof = requireNotNull(f.coordinator.moveToMainTrash(reservation, neverCancelled).items.single().plaintextUndo)
            assertTrue(proof.trashed)
            assertTrue(f.rows().isEmpty())

            val undone = f.coordinator.undoPlaintext(listOf(proof), neverCancelled)

            assertEquals(VaultItemOutcome.COMPLETED, undone.items.single().outcome)
            assertEquals(listOf(published.single().id), deleted)
            assertEquals("trashed notes", f.text(f.rows().single()))
        }
    }

    private inner class Fixture(trash: TrashRepository) {
        private val dispatcher = UnconfinedTestDispatcher()
        val scope = CoroutineScope(SupervisorJob() + dispatcher)
        private val volumes = object : VolumeRepository {
            val root = context.cacheDir
            val volume = StorageVolume("test", "test", "Test", root.path, root.totalSpace, root.freeSpace, true, false)
            override fun observeStorageVolumes() = MutableStateFlow(listOf(volume))
            override suspend fun getStorageVolumes() = Result.success(listOf(volume))
            override suspend fun getVolumeForPath(path: String) = Result.success(volume)
            override fun getStandardFolders(): Map<String, String?> = emptyMap()
        }
        val repository = DefaultVaultRepository(context, ArcileDispatchers(dispatcher, dispatcher, dispatcher, dispatcher),
            scope, VaultPortableLocationResolver(volumes))
        val coordinator = DefaultVaultBoundaryTransferCoordinator(repository, trash, ArcileDispatchers.Standard)
        var vault = VaultId.of("pending")

        suspend fun file(name: String, text: String, parent: DirectoryId = DirectoryId.Root): VaultNodeMetadata {
            val empty = repository.createEmptyFile(vault, parent, name).getOrThrow()
            return repository.replaceFileContents(empty.ref, empty.revision, text.encodeToByteArray()).getOrThrow()
        }
        suspend fun folder(name: String, parent: DirectoryId = DirectoryId.Root) = repository.createDirectory(vault, parent, name).getOrThrow()
        suspend fun rows(parent: DirectoryId = DirectoryId.Root) = repository.listDirectory(vault, parent, VaultListOptions()).getOrThrow().items
        suspend fun export(node: VaultNodeMetadata, destination: File, move: Boolean) = coordinator.exportToDestination(
            coordinator.prepareExport(listOf(node.ref)).getOrThrow(), destination.absolutePath, move,
            VaultConflictResolver { VaultConflictDecision.SKIP }, neverCancelled
        )
        fun text(node: VaultNodeMetadata): String = repository.openReader(node.ref).getOrThrow().use { reader ->
            val bytes = ByteArray(reader.sizeBytes.toInt())
            var offset = 0
            while (offset < bytes.size) {
                val count = reader.readAt(offset.toLong(), bytes, offset, bytes.size - offset)
                check(count > 0)
                offset += count
            }
            bytes.decodeToString()
        }
    }

    private suspend fun fixture(trash: TrashRepository = mockk(relaxed = true), test: suspend (Fixture) -> Unit) {
        val fixture = Fixture(trash)
        try {
            fixture.vault = fixture.repository.createAppPrivateVault("Plaintext", "password".toCharArray()).getOrThrow()
            test(fixture)
        } finally { fixture.repository.lockAll(); fixture.scope.cancel() }
    }

    private companion object {
        val neverCancelled = VaultCancellationSignal { false }
    }
}
