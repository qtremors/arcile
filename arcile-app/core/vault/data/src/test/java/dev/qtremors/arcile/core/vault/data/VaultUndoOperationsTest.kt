package dev.qtremors.arcile.core.vault.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.qtremors.arcile.core.runtime.di.ArcileDispatchers
import dev.qtremors.arcile.core.storage.domain.StorageVolume
import dev.qtremors.arcile.core.storage.domain.VolumeRepository
import dev.qtremors.arcile.core.vault.domain.*
import java.io.File
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
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@OptIn(ExperimentalCoroutinesApi::class)
class VaultUndoOperationsTest {
    private lateinit var context: Context

    @Before fun setup() {
        context = ApplicationProvider.getApplicationContext()
        File(context.noBackupFilesDir, ROOT_DIRECTORY).deleteRecursively()
    }

    @After fun cleanup() { File(context.noBackupFilesDir, ROOT_DIRECTORY).deleteRecursively() }

    @Test fun `undo new file removes only its exact unchanged node`() = runTest {
        fixture { f ->
            val created = f.file("Created.txt")
            val unrelated = f.file("Keep.txt")
            val undone = f.repository.undoCreated(created).getOrThrow()
            assertEquals(setOf(created.ref.nodeId), undone.removedNodeIds)
            assertTrue(undone.removedDirectoryIds.isEmpty())
            assertEquals(listOf(unrelated), f.rows())
        }
    }

    @Test fun `undo synthetic creation preserves other contents and reports no folder removals`() = runTest {
        fixture { f ->
            val created = f.repository.createSyntheticFile(f.vault, DirectoryId.Root, "Generated.bin", 4096).getOrThrow()
            assertEquals(4096, f.bytes(created).size)
            val undone = f.repository.undoCreated(created).getOrThrow()
            assertEquals(setOf(created.ref.nodeId), undone.removedNodeIds)
            assertTrue(f.rows().isEmpty())
        }
    }

    @Test fun `undo creation rejects an edited file and keeps its verified bytes`() = runTest {
        fixture { f ->
            val created = f.file("Created.txt")
            val edited = f.write(created, "user edit")
            assertTrue(f.repository.undoCreated(created).exceptionOrNull() is VaultFailure.ConcurrentMutation)
            assertEquals("user edit", f.bytes(edited).decodeToString())
            assertEquals(listOf(edited), f.rows())
        }
    }

    @Test fun `undo creation does not remove a replacement with the same name`() = runTest {
        fixture { f ->
            val created = f.file("Created.txt")
            f.repository.deletePermanently(created.ref).getOrThrow()
            val replacement = f.file("Created.txt")
            assertTrue(f.repository.undoCreated(created).isFailure)
            assertEquals(listOf(replacement), f.rows())
        }
    }

    @Test fun `undo empty folder reports its directory identity`() = runTest {
        fixture { f ->
            val created = f.folder("Created")
            val undone = f.repository.undoCreated(created).getOrThrow()
            assertEquals(setOf(created.ref.nodeId), undone.removedNodeIds)
            assertEquals(setOf(created.ref.directoryId), undone.removedDirectoryIds)
            assertTrue(f.rows().isEmpty())
        }
    }

    @Test fun `undo folder creation cannot delete subsequently added children`() = runTest {
        fixture { f ->
            val created = f.folder("Created")
            val child = f.file("Keep.txt", requireNotNull(created.ref.directoryId))
            assertTrue(f.repository.undoCreated(created).exceptionOrNull() is VaultFailure.ConcurrentMutation)
            assertEquals(listOf(child), f.rows(requireNotNull(created.ref.directoryId)))
        }
    }

    @Test fun `folder use consumes creation undo even when the folder becomes empty again`() = runTest {
        fixture { f ->
            val created = f.folder("Created")
            val child = f.file("Temporary.txt", requireNotNull(created.ref.directoryId))
            f.repository.deletePermanently(child.ref).getOrThrow()
            assertTrue(f.repository.undoCreated(created).exceptionOrNull() is VaultFailure.ConcurrentMutation)
            assertEquals(listOf(created), f.rows())
        }
    }

    @Test fun `undo rename restores the name and retains encrypted content identity`() = runTest {
        fixture { f ->
            val original = f.write(f.file("Original.txt"), "secret bytes")
            val renamed = f.repository.rename(original.ref, "Renamed.txt").getOrThrow()
            val restored = f.repository.undoRenames(listOf(VaultRenameUndoEntry(renamed, original.name))).getOrThrow().updated.single()
            assertEquals(original.ref, restored.ref)
            assertEquals(original.name, restored.name)
            assertEquals(original.revision, restored.revision)
            assertEquals("secret bytes", f.bytes(restored).decodeToString())
        }
    }

    @Test fun `undo rename rejects a subsequent edit without changing its name`() = runTest {
        fixture { f ->
            val original = f.file("Original.txt")
            val renamed = f.repository.rename(original.ref, "Renamed.txt").getOrThrow()
            val edited = f.write(renamed, "new bytes")
            assertTrue(f.repository.undoRenames(listOf(VaultRenameUndoEntry(renamed, original.name))).isFailure)
            assertEquals(listOf(edited), f.rows())
            assertEquals("new bytes", f.bytes(edited).decodeToString())
        }
    }

    @Test fun `undo batch rename restores swapped names in one transaction`() = runTest {
        fixture { f ->
            val first = f.file("A.txt")
            val second = f.file("B.txt")
            val renamed = f.repository.batchRename(listOf(
                VaultRenameItem(first.ref, first.name, second.name), VaultRenameItem(second.ref, second.name, first.name)
            )).getOrThrow()
            val undone = f.repository.undoRenames(listOf(
                VaultRenameUndoEntry(renamed[0], first.name), VaultRenameUndoEntry(renamed[1], second.name)
            )).getOrThrow()
            assertEquals(listOf(first.ref, second.ref), undone.updated.map { it.ref })
            assertEquals(listOf(first.name, second.name), undone.updated.map { it.name })
        }
    }

    @Test fun `one conflicting original name blocks the whole rename undo`() = runTest {
        fixture { f ->
            val first = f.file("A.txt")
            val second = f.file("B.txt")
            val renamed = f.repository.batchRename(listOf(
                VaultRenameItem(first.ref, first.name, "New A.txt"), VaultRenameItem(second.ref, second.name, "New B.txt")
            )).getOrThrow()
            val replacement = f.file("B.txt")
            assertTrue(f.repository.undoRenames(listOf(
                VaultRenameUndoEntry(renamed[0], first.name), VaultRenameUndoEntry(renamed[1], second.name)
            )).exceptionOrNull() is VaultFailure.NameConflict)
            assertEquals(setOf(renamed[0], renamed[1], replacement), f.rows().toSet())
        }
    }

    @Test fun `one changed entry blocks every rename in a batch undo`() = runTest {
        fixture { f ->
            val first = f.file("A.txt")
            val second = f.file("B.txt")
            val renamed = f.repository.batchRename(listOf(
                VaultRenameItem(first.ref, first.name, "New A.txt"), VaultRenameItem(second.ref, second.name, "New B.txt")
            )).getOrThrow()
            val edited = f.write(renamed[1], "keep edit")
            assertTrue(f.repository.undoRenames(listOf(
                VaultRenameUndoEntry(renamed[0], first.name), VaultRenameUndoEntry(renamed[1], second.name)
            )).isFailure)
            assertEquals(setOf(renamed[0], edited), f.rows().toSet())
        }
    }

    @Test fun `undo move restores the original parent without rewriting the file`() = runTest {
        fixture { f ->
            val original = f.write(f.file("Original.txt"), "private content")
            val target = f.folder("Target")
            val moved = f.move(original, requireNotNull(target.ref.directoryId))
            val restored = f.repository.undoMoves(listOf(requireNotNull(moved.moveUndo))).getOrThrow().updated.single()
            assertEquals(original.ref, restored.ref)
            assertEquals(original.revision, restored.revision)
            assertEquals(original.name, restored.name)
            assertEquals("private content", f.bytes(restored).decodeToString())
            assertTrue(f.rows(requireNotNull(target.ref.directoryId)).isEmpty())
        }
    }

    @Test fun `undo keep-both move restores the original name and keeps the earlier target`() = runTest {
        fixture { f ->
            val source = f.file("Same.txt")
            val target = f.folder("Target")
            val targetId = requireNotNull(target.ref.directoryId)
            val existing = f.write(f.file(source.name, targetId), "regular vault target")
            val moved = f.move(source, targetId, VaultConflictDecision.KEEP_BOTH)
            val restored = f.repository.undoMoves(listOf(requireNotNull(moved.moveUndo))).getOrThrow().updated.single()
            assertEquals(source.name, restored.name)
            assertEquals(source.ref.parentId, restored.ref.parentId)
            assertEquals(listOf(existing), f.rows(targetId))
            assertEquals("regular vault target", f.bytes(existing).decodeToString())
        }
    }

    @Test fun `undo move never overwrites a newly created source-name collision`() = runTest {
        fixture { f ->
            val source = f.file("Same.txt")
            val target = f.folder("Target")
            val moved = f.move(source, requireNotNull(target.ref.directoryId))
            val replacement = f.file(source.name)
            assertTrue(f.repository.undoMoves(listOf(requireNotNull(moved.moveUndo))).exceptionOrNull() is VaultFailure.NameConflict)
            assertTrue(f.rows().contains(replacement))
            assertEquals(1, f.rows(requireNotNull(target.ref.directoryId)).size)
        }
    }

    @Test fun `edited item blocks an entire multi-parent move undo`() = runTest {
        fixture { f ->
            val parent = f.folder("Other source")
            val first = f.file("A.txt")
            val second = f.file("B.txt", requireNotNull(parent.ref.directoryId))
            val target = f.folder("Target")
            val targetId = requireNotNull(target.ref.directoryId)
            val moved = f.repository.moveWithinVault(listOf(first.ref, second.ref), targetId, replace, neverCancelled)
            val published = f.rows(targetId)
            val edited = f.write(published.single { it.name == "B.txt" }, "edited at destination")
            assertTrue(f.repository.undoMoves(moved.items.map { requireNotNull(it.moveUndo) }).isFailure)
            assertEquals(2, f.rows(targetId).size)
            assertFalse(f.rows().any { it.ref.nodeId == first.ref.nodeId })
            assertTrue(f.rows(requireNotNull(parent.ref.directoryId)).isEmpty())
            assertEquals("edited at destination", f.bytes(edited).decodeToString())
        }
    }

    @Test fun `locking expires a move proof even after unlocking the same vault`() = runTest {
        fixture { f ->
            val source = f.file("Original.txt")
            val target = f.folder("Target")
            val targetId = requireNotNull(target.ref.directoryId)
            val moved = f.move(source, targetId)
            f.repository.lockAll()
            f.repository.unlock(f.vault, "password".toCharArray()).getOrThrow()
            assertTrue(f.repository.undoMoves(listOf(requireNotNull(moved.moveUndo))).exceptionOrNull() is VaultFailure.ConcurrentMutation)
            assertEquals(1, f.rows(targetId).size)
            assertFalse(f.rows().any { it.ref.nodeId == source.ref.nodeId })
        }
    }

    @Test fun `undo refuses a folder cycle introduced after the original move`() = runTest {
        fixture { f ->
            val parent = f.folder("Parent")
            val parentId = requireNotNull(parent.ref.directoryId)
            val folder = f.folder("Moved", parentId)
            val target = f.folder("Target")
            val moved = f.move(folder, requireNotNull(target.ref.directoryId))
            f.move(parent, requireNotNull(folder.ref.directoryId))
            assertTrue(f.repository.undoMoves(listOf(requireNotNull(moved.moveUndo))).exceptionOrNull() is VaultFailure.InvalidPath)
            val preserved = f.rows(requireNotNull(folder.ref.directoryId)).single()
            assertEquals(parent.ref.nodeId, preserved.ref.nodeId)
            assertEquals(parent.name, preserved.name)
        }
    }

    @Test fun `replacement and merged moves never offer an incomplete inverse`() = runTest {
        fixture { f ->
            val target = f.folder("Target")
            val targetId = requireNotNull(target.ref.directoryId)
            val source = f.file("Same.txt")
            f.file(source.name, targetId)
            assertNull(f.move(source, targetId).moveUndo)
            val folder = f.folder("Folder")
            f.folder(folder.name, targetId)
            assertNull(f.move(folder, targetId, VaultConflictDecision.MERGE_DIRECTORIES).moveUndo)
        }
    }

    @Test fun `undo selection moves every item back to its own original parent`() = runTest {
        fixture { f ->
            val firstParent = f.folder("First")
            val secondParent = f.folder("Second")
            val firstId = requireNotNull(firstParent.ref.directoryId)
            val secondId = requireNotNull(secondParent.ref.directoryId)
            val first = f.write(f.file("Same.txt", firstId), "first content")
            val second = f.write(f.file("Same.txt", secondId), "second content")
            val target = f.folder("Target")
            val targetId = requireNotNull(target.ref.directoryId)
            val moved = f.repository.moveWithinVault(listOf(first.ref, second.ref), targetId,
                VaultConflictResolver { VaultConflictDecision.KEEP_BOTH }, neverCancelled)

            val undone = f.repository.undoMoves(moved.completed.map { requireNotNull(it.moveUndo) }).getOrThrow()

            assertEquals(setOf(first.ref, second.ref), undone.updated.map { it.ref }.toSet())
            assertEquals("first content", f.bytes(f.rows(firstId).single()).decodeToString())
            assertEquals("second content", f.bytes(f.rows(secondId).single()).decodeToString())
            assertTrue(f.rows(targetId).isEmpty())
        }
    }

    @Test fun `one original-parent collision blocks all moves without deleting any output`() = runTest {
        fixture { f ->
            val first = f.file("A.txt")
            val second = f.file("B.txt")
            val target = f.folder("Target")
            val targetId = requireNotNull(target.ref.directoryId)
            val moved = f.repository.moveWithinVault(listOf(first.ref, second.ref), targetId, replace, neverCancelled)
            val published = f.rows(targetId)
            val replacement = f.file(second.name)

            assertTrue(f.repository.undoMoves(moved.completed.map { requireNotNull(it.moveUndo) })
                .exceptionOrNull() is VaultFailure.NameConflict)

            assertEquals(published, f.rows(targetId))
            assertEquals(setOf(target, replacement), f.rows().toSet())
        }
    }

    @Test fun `folder move undo preserves children added at the moved location`() = runTest {
        fixture { f ->
            val source = f.folder("Moved")
            val target = f.folder("Target")
            val moved = f.move(source, requireNotNull(target.ref.directoryId))
            val child = f.write(f.file("Added.txt", requireNotNull(source.ref.directoryId)), "keep this child")

            val restored = f.repository.undoMoves(listOf(requireNotNull(moved.moveUndo))).getOrThrow().updated.single()

            assertEquals(source.ref.directoryId, restored.ref.directoryId)
            assertEquals(source.ref.parentId, restored.ref.parentId)
            assertEquals(listOf(child), f.rows(requireNotNull(source.ref.directoryId)))
            assertEquals("keep this child", f.bytes(child).decodeToString())
        }
    }

    @Test fun `cross-vault move does not advertise an atomic same-vault inverse`() = runTest {
        fixture { f ->
            val other = f.repository.createAppPrivateVault("Other", "password".toCharArray()).getOrThrow()
            val source = f.file("Cross.txt")
            val moved = f.repository.transferAcrossVaults(listOf(source.ref), other, DirectoryId.Root, true, replace, neverCancelled)
            assertEquals(1, moved.completed.size)
            assertNull(moved.completed.single().moveUndo)
            assertTrue(f.rows().isEmpty())
            assertEquals(listOf(source.name), f.repository.listDirectory(other, DirectoryId.Root).getOrThrow().items.map { it.name })
        }
    }

    @Test fun `rename undo uses the name read under the mutation lock rather than stale UI metadata`() = runTest {
        fixture { f ->
            val original = f.file("Original.txt")
            val changed = f.repository.rename(original.ref, "Changed elsewhere.txt").getOrThrow()

            val renamed = f.repository.renameWithPreviousName(original.ref, "Final.txt").getOrThrow()

            assertEquals(changed.name, renamed.previousName)
            val restored = f.repository.undoRenames(listOf(VaultRenameUndoEntry(renamed.updated,
                requireNotNull(renamed.previousName)))).getOrThrow().updated.single()
            assertEquals(changed.name, restored.name)
            assertEquals(original.ref, restored.ref)
        }
    }

    @Test fun `copy undo removes the unchanged copy and keeps the original`() = runTest {
        fixture { f ->
            val original = f.write(f.file("Original.txt"), "keep original")
            val target = f.folder("Target")
            val targetId = requireNotNull(target.ref.directoryId)
            val copied = f.repository.copyWithinVault(listOf(original.ref), targetId, replace, neverCancelled).completed.single()
            val proof = requireNotNull(copied.createdUndo)
            assertNull(proof.origin)

            val undone = f.repository.undoCreatedCopies(listOf(proof)).getOrThrow()

            assertEquals(setOf(proof.expected.ref.nodeId), undone.removedNodeIds)
            assertTrue(f.rows(targetId).isEmpty())
            assertEquals("keep original", f.bytes(f.rows().single { it.name == original.name }).decodeToString())
        }
    }

    @Test fun `copied folder undo removes nested contents and reports every removed folder`() = runTest {
        fixture { f ->
            val folder = f.folder("Docs")
            val nested = f.folder("Nested", requireNotNull(folder.ref.directoryId))
            f.write(f.file("Deep.txt", requireNotNull(nested.ref.directoryId)), "deep")
            val target = f.folder("Target")
            val targetId = requireNotNull(target.ref.directoryId)
            val proof = requireNotNull(f.repository.copyWithinVault(listOf(folder.ref), targetId, replace, neverCancelled)
                .completed.single().createdUndo)

            val undone = f.repository.undoCreatedCopies(listOf(proof)).getOrThrow()

            assertEquals(2, undone.removedDirectoryIds.size)
            assertTrue(f.rows(targetId).isEmpty())
            assertEquals(1, f.rows(requireNotNull(nested.ref.directoryId)).size)
        }
    }

    @Test fun `a change anywhere inside a copied folder blocks the copy undo`() = runTest {
        fixture { f ->
            val folder = f.folder("Docs")
            val nested = f.folder("Nested", requireNotNull(folder.ref.directoryId))
            val target = f.folder("Target")
            val targetId = requireNotNull(target.ref.directoryId)
            val proof = requireNotNull(f.repository.copyWithinVault(listOf(folder.ref), targetId, replace, neverCancelled)
                .completed.single().createdUndo)
            val copiedNested = f.rows(requireNotNull(f.rows(targetId).single().ref.directoryId)).single()
            val added = f.write(f.file("Added later.txt", requireNotNull(copiedNested.ref.directoryId)), "user data")

            assertTrue(f.repository.undoCreatedCopies(listOf(proof)).exceptionOrNull() is VaultFailure.ConcurrentMutation)

            assertEquals(1, f.rows(targetId).size)
            assertEquals("user data", f.bytes(added).decodeToString())
            assertEquals(nested.name, copiedNested.name)
        }
    }

    @Test fun `one changed copy blocks the whole copy undo without deleting any copy`() = runTest {
        fixture { f ->
            val first = f.file("A.txt")
            val second = f.file("B.txt")
            val target = f.folder("Target")
            val targetId = requireNotNull(target.ref.directoryId)
            val copied = f.repository.copyWithinVault(listOf(first.ref, second.ref), targetId, replace, neverCancelled)
            f.write(f.rows(targetId).single { it.name == "B.txt" }, "edited copy")

            assertTrue(f.repository.undoCreatedCopies(copied.completed.map { requireNotNull(it.createdUndo) }).isFailure)

            assertEquals(setOf("A.txt", "B.txt"), f.rows(targetId).map { it.name }.toSet())
        }
    }

    @Test fun `replaced destinations never receive a copy proof`() = runTest {
        fixture { f ->
            val source = f.file("Same.txt")
            val target = f.folder("Target")
            val targetId = requireNotNull(target.ref.directoryId)
            f.file(source.name, targetId)
            val copied = f.repository.copyWithinVault(listOf(source.ref), targetId, replace, neverCancelled).completed.single()
            assertNull(copied.createdUndo)
        }
    }

    @Test fun `locking expires a copy proof even after unlocking the same vault`() = runTest {
        fixture { f ->
            val source = f.file("Original.txt")
            val target = f.folder("Target")
            val targetId = requireNotNull(target.ref.directoryId)
            val proof = requireNotNull(f.repository.copyWithinVault(listOf(source.ref), targetId, replace, neverCancelled)
                .completed.single().createdUndo)
            f.repository.lockAll()
            f.repository.unlock(f.vault, "password".toCharArray()).getOrThrow()
            assertTrue(f.repository.undoCreatedCopies(listOf(proof)).exceptionOrNull() is VaultFailure.ConcurrentMutation)
            assertEquals(1, f.rows(targetId).size)
        }
    }

    @Test fun `cross-vault move proof names its source folder and verifies the unchanged destination`() = runTest {
        fixture { f ->
            val other = f.repository.createAppPrivateVault("Other", "password".toCharArray()).getOrThrow()
            val parent = f.folder("Source")
            val parentId = requireNotNull(parent.ref.directoryId)
            val source = f.write(f.file("Cross.txt", parentId), "moving")
            val moved = f.repository.transferAcrossVaults(listOf(source.ref), other, DirectoryId.Root, true, replace, neverCancelled)
            val proof = requireNotNull(moved.completed.single().createdUndo)

            assertEquals(VaultUndoOrigin.VaultFolder(f.vault, parentId, source.name), proof.origin)
            assertEquals(other, proof.vaultId)
            assertEquals(listOf(proof.expected), f.repository.verifyCreated(listOf(proof)).getOrThrow())

            val back = f.repository.transferAcrossVaults(listOf(proof.expected.ref), f.vault, parentId, true,
                VaultConflictResolver { VaultConflictDecision.SKIP }, neverCancelled)
            assertEquals(1, back.completed.size)
            assertEquals("moving", f.bytes(f.rows(parentId).single()).decodeToString())
            assertTrue(f.repository.listDirectory(other, DirectoryId.Root).getOrThrow().items.isEmpty())
        }
    }

    private inner class Fixture {
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
        var vault = VaultId.of("pending")
        suspend fun file(name: String, parent: DirectoryId = DirectoryId.Root) = repository.createEmptyFile(vault, parent, name).getOrThrow()
        suspend fun folder(name: String, parent: DirectoryId = DirectoryId.Root) = repository.createDirectory(vault, parent, name).getOrThrow()
        suspend fun write(node: VaultNodeMetadata, text: String) = repository.replaceFileContents(node.ref, node.revision, text.encodeToByteArray()).getOrThrow()
        suspend fun rows(parent: DirectoryId = DirectoryId.Root) = repository.listDirectory(vault, parent, VaultListOptions()).getOrThrow().items
        suspend fun move(node: VaultNodeMetadata, parent: DirectoryId, policy: VaultConflictDecision = VaultConflictDecision.REPLACE) =
            repository.moveWithinVault(listOf(node.ref), parent, VaultConflictResolver { policy }, neverCancelled).completed.single()
        fun bytes(node: VaultNodeMetadata): ByteArray = repository.openReader(node.ref).getOrThrow().use { reader ->
            ByteArray(reader.sizeBytes.toInt()).also { output ->
                var offset = 0
                while (offset < output.size) {
                    val count = reader.readAt(offset.toLong(), output, offset, output.size - offset)
                    require(count > 0)
                    offset += count
                }
            }
        }
    }

    private suspend fun fixture(test: suspend (Fixture) -> Unit) {
        val fixture = Fixture()
        try {
            fixture.vault = fixture.repository.createAppPrivateVault("Undo", "password".toCharArray()).getOrThrow()
            test(fixture)
        } finally { fixture.repository.lockAll(); fixture.scope.cancel() }
    }

    private companion object {
        val neverCancelled = VaultCancellationSignal { false }
        val replace = VaultConflictResolver { VaultConflictDecision.REPLACE }
    }
}
